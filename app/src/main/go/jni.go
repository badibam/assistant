package main

/*
#include <jni.h>
#include <stdlib.h>

static const char* jstr_get(JNIEnv* env, jstring s) { return (*env)->GetStringUTFChars(env, s, NULL); }
static void jstr_release(JNIEnv* env, jstring s, const char* c) { (*env)->ReleaseStringUTFChars(env, s, c); }
static jstring jstr_new(JNIEnv* env, const char* c) { return (*env)->NewStringUTF(env, c); }
*/
import "C"

import "unsafe"

// The JNI side of app.treelune.core.mcp.FunnelNative: strings in, strings out, the work in node.go.

func goString(env *C.JNIEnv, s C.jstring) string {
	c := C.jstr_get(env, s)
	defer C.jstr_release(env, s, c)
	return C.GoString(c)
}

func javaString(env *C.JNIEnv, s string) C.jstring {
	c := C.CString(s)
	defer C.free(unsafe.Pointer(c))
	return C.jstr_new(env, c)
}

//export Java_app_treelune_core_mcp_FunnelNative_start
func Java_app_treelune_core_mcp_FunnelNative_start(env *C.JNIEnv, cls C.jclass, dir, hostname C.jstring) {
	start(goString(env, dir), goString(env, hostname))
}

//export Java_app_treelune_core_mcp_FunnelNative_state
func Java_app_treelune_core_mcp_FunnelNative_state(env *C.JNIEnv, cls C.jclass) C.jstring {
	return javaString(env, state())
}

//export Java_app_treelune_core_mcp_FunnelNative_stop
func Java_app_treelune_core_mcp_FunnelNative_stop(env *C.JNIEnv, cls C.jclass) {
	stop()
}

//export Java_app_treelune_core_mcp_FunnelNative_logout
func Java_app_treelune_core_mcp_FunnelNative_logout(env *C.JNIEnv, cls C.jclass, dir, hostname C.jstring) C.jstring {
	return javaString(env, logout(goString(env, dir), goString(env, hostname)))
}

//export Java_app_treelune_core_mcp_FunnelNative_networkChanged
func Java_app_treelune_core_mcp_FunnelNative_networkChanged(env *C.JNIEnv, cls C.jclass, ifName C.jstring) {
	networkChanged(goString(env, ifName))
}

//export Java_app_treelune_core_mcp_FunnelNative_next
func Java_app_treelune_core_mcp_FunnelNative_next(env *C.JNIEnv, cls C.jclass, waitSeconds C.jint) C.jstring {
	return javaString(env, next(int(waitSeconds)))
}

//export Java_app_treelune_core_mcp_FunnelNative_reply
func Java_app_treelune_core_mcp_FunnelNative_reply(env *C.JNIEnv, cls C.jclass, id, response C.jstring) {
	reply(goString(env, id), goString(env, response))
}

func main() {}
