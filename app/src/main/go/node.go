package main

// The app's Tailscale node: one per process, driven from Kotlin (FunnelNative), alive only while
// the external access is open (docs/design/funnel-access.md). It publishes the app's address
// through Funnel and speaks the relay's contract (docs/design/mcp-server.md): each HTTPS request
// it receives waits in a queue until Kotlin takes it (next) and answers it (reply).

import (
	"context"
	"crypto/rand"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"os"
	"path/filepath"
	"runtime/debug"
	"strings"
	"sync"
	"time"

	"tailscale.com/envknob"
	"tailscale.com/net/netmon"
	"tailscale.com/tsnet"
)

const (
	// As the relay's REPLY_TIMEOUT: past it, the client gets a 503
	replyTimeout = 25 * time.Second
	// An MCP or OAuth request is a few kilobytes; anything near this is not one
	maxBody = 4 << 20
	// The technical log: two files of this size at most
	logLimit = 1 << 20
)

// Headers about the connection, not the request: the relay drops them too
var transportHeaders = map[string]bool{
	"Connection": true, "Keep-Alive": true, "Proxy-Connection": true,
	"Te": true, "Trailer": true, "Transfer-Encoding": true, "Upgrade": true,
}

type response struct {
	status  int
	headers map[string]string
	body    []byte
}

// A request received through the Funnel, waiting for its answer from Kotlin.
type pending struct {
	id   string
	json string
	done chan response
}

var (
	mu        sync.Mutex
	srv       *tsnet.Server
	listening bool   // ListenFunnel is running: logging in, or opening the Funnel
	address   string // https://<node>.<tailnet>.ts.net once the Funnel listens
	lastErr   error  // what ListenFunnel answered when it failed

	queue   = make(chan *pending, 16)
	waiting = map[string]*pending{}

	userLines []string // messages for the app's log, drained by state()
	lastUser  string
	ifName    = "android" // the default route's interface, as Android last told it
	technical *rotatingLog
	prepared  bool
)

// start creates the node if needed and opens the Funnel, in the background: state() tells how far
// it got. Called again after a failure (a step missing in the console), it tries again.
func start(dir, hostname string) {
	mu.Lock()
	defer mu.Unlock()
	if srv == nil {
		prepare(dir)
		srv = newServer(dir, hostname)
	}
	if listening || address != "" {
		return
	}
	listening, lastErr = true, nil
	go listen(srv)
}

func newServer(dir, hostname string) *tsnet.Server {
	return &tsnet.Server{
		Dir:      filepath.Join(dir, "state"),
		Hostname: hostname,
		Logf:     technical.printf,
		UserLogf: say,
	}
}

func listen(s *tsnet.Server) {
	// Blocks while the node waits for its login, which state() reads meanwhile
	ln, err := s.ListenFunnel("tcp", ":443", tsnet.FunnelOnly())
	mu.Lock()
	defer mu.Unlock()
	listening = false
	if s != srv {
		// Stopped meanwhile
		if ln != nil {
			ln.Close()
		}
		return
	}
	if err != nil {
		lastErr = err
		return
	}
	domains := s.CertDomains()
	if len(domains) == 0 {
		ln.Close()
		lastErr = errors.New("the node has no certificate domain")
		return
	}
	address = "https://" + domains[0]
	sayLocked("funnel listening at %s", address)
	go http.Serve(ln, http.HandlerFunc(serve))
}

// state reports where the node is, as JSON: {"state", "url"?, "address"?, "message"?, "log"}.
// "log" carries the messages since the last call, for the app's log.
func state() string {
	mu.Lock()
	s, addr, err := srv, address, lastErr
	lines := userLines
	userLines = nil
	mu.Unlock()

	out := map[string]any{"log": lines}
	switch {
	case s == nil:
		out["state"] = "stopped"
	case addr != "":
		out["state"], out["address"] = "ready", addr
	case err != nil:
		msg := err.Error()
		switch {
		case strings.Contains(msg, "HTTPS must be enabled"):
			out["state"] = "https_missing"
		case strings.Contains(msg, `"funnel" node attribute not set`):
			out["state"] = "funnel_missing"
		default:
			out["state"], out["message"] = "failed", msg
		}
	default:
		out["state"] = "starting"
		if url := authURL(s); url != "" {
			out["state"], out["url"] = "needs_login", url
		}
	}
	b, _ := json.Marshal(out)
	return string(b)
}

// authURL is the login page's address while the node waits for its login, or "".
func authURL(s *tsnet.Server) string {
	lc, err := s.LocalClient()
	if err != nil {
		return ""
	}
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()
	st, err := lc.StatusWithoutPeers(ctx)
	if err != nil || st.BackendState != "NeedsLogin" {
		return ""
	}
	return st.AuthURL
}

// stop closes the node: the Funnel stops answering, and requests in flight get their connection closed.
func stop() {
	mu.Lock()
	s := srv
	srv, address, lastErr, listening = nil, "", nil, false
	mu.Unlock()
	if s != nil {
		s.Close()
	}
}

// logout takes the node out of the Tailscale account, then erases its identity. The identity is
// erased even when the account could not be reached: the person asked for it. The error, if any,
// goes back to say the node may still be listed in the console.
func logout(dir, hostname string) string {
	mu.Lock()
	s := srv
	if s == nil {
		prepare(dir)
		s = newServer(dir, hostname)
	}
	mu.Unlock()

	var failure string
	if err := s.Start(); err != nil {
		failure = err.Error()
	} else if lc, err := s.LocalClient(); err != nil {
		failure = err.Error()
	} else {
		ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
		if err := lc.Logout(ctx); err != nil {
			failure = err.Error()
		}
		cancel()
	}
	stop()
	s.Close()
	if err := os.RemoveAll(filepath.Join(dir, "state")); err != nil && failure == "" {
		failure = err.Error()
	}
	return failure
}

// networkChanged is what Android's ConnectivityManager reports: the default route's interface,
// "" when the network is lost. The node re-reads its network at once rather than at its next poll.
func networkChanged(name string) {
	mu.Lock()
	if name != "" {
		ifName = name
	}
	s := srv
	mu.Unlock()
	netmon.UpdateLastKnownDefaultRouteInterface(name)
	if s == nil || s.Sys() == nil {
		return
	}
	if nm, ok := s.Sys().NetMon.GetOK(); ok {
		nm.InjectEvent()
	}
}

// serve hands a request received through the Funnel to Kotlin, and writes back its answer.
func serve(w http.ResponseWriter, r *http.Request) {
	body, err := io.ReadAll(http.MaxBytesReader(w, r.Body, maxBody))
	if err != nil {
		http.Error(w, "request too large", http.StatusRequestEntityTooLarge)
		return
	}
	headers := map[string]string{}
	for k, v := range r.Header {
		if !transportHeaders[k] {
			headers[k] = strings.Join(v, ", ")
		}
	}
	id := randomID()
	js, _ := json.Marshal(map[string]any{
		"id": id, "method": r.Method, "path": r.URL.Path, "query": r.URL.RawQuery,
		"headers": headers, "body": base64.StdEncoding.EncodeToString(body),
	})
	p := &pending{id: id, json: string(js), done: make(chan response, 1)}
	mu.Lock()
	waiting[id] = p
	mu.Unlock()
	defer func() {
		mu.Lock()
		delete(waiting, id)
		mu.Unlock()
	}()

	deadline := time.NewTimer(replyTimeout)
	defer deadline.Stop()
	select {
	case queue <- p:
	case <-deadline.C:
		http.Error(w, "no answer from the app", http.StatusServiceUnavailable)
		return
	}
	select {
	case resp := <-p.done:
		for k, v := range resp.headers {
			w.Header().Set(k, v)
		}
		w.WriteHeader(resp.status)
		w.Write(resp.body)
	case <-deadline.C:
		http.Error(w, "no answer from the app", http.StatusServiceUnavailable)
	case <-r.Context().Done():
	}
}

// next is the oldest request still waiting, as the relay's JSON, or "" after waitSeconds.
func next(waitSeconds int) string {
	t := time.NewTimer(time.Duration(waitSeconds) * time.Second)
	defer t.Stop()
	for {
		select {
		case p := <-queue:
			mu.Lock()
			_, alive := waiting[p.id]
			mu.Unlock()
			// A request whose client gave up is skipped
			if alive {
				return p.json
			}
		case <-t.C:
			return ""
		}
	}
}

// reply answers the request id with the relay's JSON ({status, headers, body in base64}); dropped
// when its client no longer waits.
func reply(id, js string) {
	var in struct {
		Status  int               `json:"status"`
		Headers map[string]string `json:"headers"`
		Body    string            `json:"body"`
	}
	resp := response{status: http.StatusInternalServerError}
	if err := json.Unmarshal([]byte(js), &in); err == nil {
		if body, err := base64.StdEncoding.DecodeString(in.Body); err == nil {
			resp = response{status: in.Status, headers: in.Headers, body: body}
		}
	}
	mu.Lock()
	p := waiting[id]
	mu.Unlock()
	if p != nil {
		select {
		case p.done <- resp:
		default:
		}
	}
}

// prepare sets up the process once, before the first node: what an app's environment lacks.
func prepare(dir string) {
	if prepared {
		return
	}
	prepared = true
	// An app has no HOME, cache or temp dir in its environment, and Tailscale's log policy panics
	// when it finds none: all three point into the node's own directory.
	for k, sub := range map[string]string{"HOME": "", "XDG_CACHE_HOME": "cache", "TMPDIR": "tmp"} {
		d := filepath.Join(dir, sub)
		os.MkdirAll(d, 0o700)
		os.Setenv(k, d)
	}
	// No log upload to log.tailscale.com: logpolicy swaps its transport for a no-op
	envknob.SetNoLogsNoSupport()
	technical = &rotatingLog{path: filepath.Join(dir, "tailscale.log")}
	// Android shows nothing of a Go panic: it goes to a file, read back at the next start
	crash := filepath.Join(dir, "crash.txt")
	if previous, err := os.ReadFile(crash); err == nil && len(previous) > 0 {
		sayLocked("previous crash of the Tailscale node: %s", firstLines(string(previous), 30))
	}
	if f, err := os.Create(crash); err == nil {
		debug.SetCrashOutput(f, debug.CrashOptions{})
	}
	netmon.RegisterInterfaceGetter(interfaces)
}

// interfaces lists one interface carrying the outbound source addresses: Android denies netlink to
// apps, so net.Interfaces fails. Dialing UDP sends nothing; the kernel only picks a route and a
// source address. tailscale.com/feature/androidbin does the same, but not in a cgo build.
func interfaces() ([]netmon.Interface, error) {
	var addrs []net.Addr
	for _, t := range [][2]string{{"udp4", "8.8.8.8:53"}, {"udp6", "[2001:4860:4860::8888]:53"}} {
		c, err := net.Dial(t[0], t[1])
		if err != nil {
			continue
		}
		ip := c.LocalAddr().(*net.UDPAddr).IP
		c.Close()
		bits := 128
		if ip.To4() != nil {
			bits = 32
		}
		addrs = append(addrs, &net.IPNet{IP: ip, Mask: net.CIDRMask(bits, bits)})
	}
	if len(addrs) == 0 {
		return nil, errors.New("no outbound route")
	}
	mu.Lock()
	name := ifName
	mu.Unlock()
	return []netmon.Interface{{
		Interface: &net.Interface{Index: 1, MTU: 1500, Name: name, Flags: net.FlagUp | net.FlagRunning},
		AltAddrs:  addrs,
	}}, nil
}

// say keeps a message for the app's log. Tailscale repeats the login URL every few seconds: a
// message equal to the previous one is kept once.
func say(format string, args ...any) {
	mu.Lock()
	defer mu.Unlock()
	sayLocked(format, args...)
}

func sayLocked(format string, args ...any) {
	line := fmt.Sprintf(format, args...)
	if line == lastUser {
		return
	}
	lastUser = line
	userLines = append(userLines, line)
	if len(userLines) > 100 {
		userLines = userLines[len(userLines)-100:]
	}
}

func firstLines(s string, n int) string {
	lines := strings.SplitN(s, "\n", n+1)
	if len(lines) > n {
		lines = lines[:n]
	}
	return strings.Join(lines, "\n")
}

func randomID() string {
	b := make([]byte, 12)
	rand.Read(b)
	return hex.EncodeToString(b)
}

// rotatingLog is Tailscale's technical log: verbose, kept on the phone only, in two files at most.
type rotatingLog struct {
	mu   sync.Mutex
	path string
	f    *os.File
	size int64
}

func (r *rotatingLog) printf(format string, args ...any) {
	line := time.Now().UTC().Format("2006-01-02T15:04:05Z ") + fmt.Sprintf(format, args...) + "\n"
	r.mu.Lock()
	defer r.mu.Unlock()
	if r.f == nil || r.size > logLimit {
		if r.f != nil {
			r.f.Close()
			os.Rename(r.path, r.path+".1")
		}
		f, err := os.OpenFile(r.path, os.O_CREATE|os.O_APPEND|os.O_WRONLY, 0o600)
		if err != nil {
			return
		}
		info, _ := f.Stat()
		r.f, r.size = f, info.Size()
	}
	n, _ := r.f.WriteString(line)
	r.size += int64(n)
}
