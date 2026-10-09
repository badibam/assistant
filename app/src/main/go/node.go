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
	published bool   // the address found in the public DNS
	lastErr   error  // what ListenFunnel answered when it failed
	enableURL string // Tailscale's page that turns Funnel on for the account, while it is off

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

// While Funnel is off in the account, how often the node tries again: the change made in the
// console reaches it in its next network map, and the access opens by itself.
const funnelRetry = 5 * time.Second

func listen(s *tsnet.Server) {
	for {
		// Blocks while the node waits for its login, which state() reads meanwhile
		ln, err := s.ListenFunnel("tcp", ":443", tsnet.FunnelOnly())
		if err != nil && funnelStep(err) != "" {
			// Funnel off in the account: Tailscale's page to turn it on, asked once, then wait for it
			mu.Lock()
			stopped, asked := s != srv, enableURL != ""
			lastErr = err
			mu.Unlock()
			if stopped {
				return
			}
			if !asked {
				url := funnelEnableURL(s)
				mu.Lock()
				enableURL = url
				mu.Unlock()
			}
			time.Sleep(funnelRetry)
			continue
		}
		opened(s, ln, err)
		return
	}
}

// opened records what ListenFunnel finally answered: the address, or the failure.
func opened(s *tsnet.Server, ln net.Listener, err error) {
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
	lastErr, enableURL = err, ""
	if err != nil {
		return
	}
	domains := s.CertDomains()
	if len(domains) == 0 {
		ln.Close()
		lastErr = errors.New("the node has no certificate domain")
		return
	}
	address, published = "https://"+domains[0], false
	sayLocked("funnel listening at %s", address)
	go http.Serve(ln, http.HandlerFunc(serve))
	go watchPublication(s, domains[0])
}

// state reports where the node is, as JSON: {"state", "url"?, "address"?, "message"?, "log"}.
// "log" carries the messages since the last call, for the app's log.
func state() string {
	mu.Lock()
	s, addr, err, pub, enable := srv, address, lastErr, published, enableURL
	lines := userLines
	userLines = nil
	mu.Unlock()

	out := map[string]any{"log": lines}
	switch {
	case s == nil:
		out["state"] = "stopped"
	case addr != "":
		out["state"], out["address"], out["published"] = "ready", addr, pub
	case err != nil:
		switch step := funnelStep(err); {
		case step != "" && enable != "":
			// One page of Tailscale's turns on HTTPS and the funnel attribute together
			out["state"], out["url"] = "funnel_off", enable
		case step != "":
			// No page offered (a member who may not change the account): the steps by hand
			out["state"] = step
		default:
			out["state"], out["message"] = "failed", err.Error()
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

// funnelStep names what ListenFunnel lacks in the account, or "" when its failure is another.
func funnelStep(err error) string {
	msg := err.Error()
	switch {
	case strings.Contains(msg, "HTTPS must be enabled"):
		return "https_missing"
	case strings.Contains(msg, `"funnel" node attribute not set`):
		return "funnel_missing"
	}
	return ""
}

// funnelEnableURL is the page of Tailscale's console that turns Funnel on for the node's account,
// as Tailscale's own command line offers it (QueryFeature); "" when Tailscale offers none.
func funnelEnableURL(s *tsnet.Server) string {
	lc, err := s.LocalClient()
	if err != nil {
		return ""
	}
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	resp, err := lc.QueryFeature(ctx, "funnel")
	if err != nil {
		technical.printf("funnel: query feature: %v", err)
		return ""
	}
	if resp.Text != "" {
		say("Tailscale: %s", strings.TrimSpace(resp.Text))
	}
	if resp.Complete {
		return ""
	}
	return resp.URL
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
	srv, address, lastErr, listening, published, enableURL = nil, "", nil, false, false, ""
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

// The address exists for clients only once Tailscale has published it in the public DNS: a few
// seconds to a few minutes after the node's first Funnel. Asked of ts.net's own name servers,
// never of a shared resolver: a "no such name" there is kept five minutes (the zone's negative
// TTL), and would delay the address for everyone who asks it meanwhile, the client included.
const publicationPoll = 10 * time.Second

func watchPublication(s *tsnet.Server, host string) {
	began := time.Now()
	for {
		ok := isPublished(host)
		mu.Lock()
		if s != srv {
			mu.Unlock()
			return
		}
		if ok {
			published = true
			sayLocked("address published in the public DNS after %s", time.Since(began).Round(time.Second))
			mu.Unlock()
			return
		}
		mu.Unlock()
		time.Sleep(publicationPoll)
	}
}

// isPublished asks ts.net's authoritative name servers whether host has an address.
func isPublished(host string) bool {
	servers, err := net.LookupNS("ts.net")
	if err != nil {
		technical.printf("publication: ts.net name servers: %v", err)
		return false
	}
	for _, ns := range servers {
		server := net.JoinHostPort(strings.TrimSuffix(ns.Host, "."), "53")
		r := &net.Resolver{PreferGo: true, Dial: func(ctx context.Context, _, _ string) (net.Conn, error) {
			d := net.Dialer{Timeout: 3 * time.Second}
			return d.DialContext(ctx, "udp", server)
		}}
		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		addrs, err := r.LookupHost(ctx, host)
		cancel()
		if err == nil && len(addrs) > 0 {
			return true
		}
		// One server answering "no such name" is enough: they serve the same zone
		var dnsErr *net.DNSError
		if errors.As(err, &dnsErr) && dnsErr.IsNotFound {
			return false
		}
	}
	return false
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
