package main

/*
#if defined(__ANDROID__) || defined(ANDROID)
#include <jni.h>
#else
typedef struct _JNIEnv JNIEnv;
typedef void* jobject;
typedef void* jstring;
typedef int   jint;
#endif
#include <stdlib.h>

#if defined(__ANDROID__) || defined(ANDROID)
static const char* get_string_utf(JNIEnv *env, jstring str) {
    if (str == NULL || env == NULL) return NULL;
    return (*env)->GetStringUTFChars(env, str, NULL);
}

static void release_string_utf(JNIEnv *env, jstring str, const char* chars) {
    if (str != NULL && chars != NULL && env != NULL) {
        (*env)->ReleaseStringUTFChars(env, str, chars);
    }
}
#else
static const char* get_string_utf(JNIEnv *env, jstring str) { return NULL; }
static void release_string_utf(JNIEnv *env, jstring str, const char* chars) {}
#endif
*/
import "C"

import (
	"context"
	"fmt"
	"sync"

	"github.com/cubicreates/unbound-engine/pkg/server"
)

var (
	activeServer *server.Server
	serverMutex  sync.Mutex
)

func startEngineInternal(env *C.JNIEnv, jAppStoragePath C.jstring, jPort C.jint) C.jint {
	serverMutex.Lock()
	defer serverMutex.Unlock()

	if activeServer != nil {
		return 0 // Already running
	}

	// Convert Java jstring to Go string
	var appStoragePath string
	cStr := C.get_string_utf(env, jAppStoragePath)
	if cStr != nil {
		appStoragePath = C.GoString(cStr)
		C.release_string_utf(env, jAppStoragePath, cStr)
	}

	port := int(jPort)
	if port <= 0 {
		port = 45731
	}

	cfg := server.Config{
		Port:           port,
		AppStorageRoot: appStoragePath,
		LibraryRoot:    appStoragePath,
	}

	srv, err := server.NewServer(cfg)
	if err != nil {
		fmt.Printf("[UNBOUND JNI] Server init error: %v\n", err)
		return -1
	}

	activeServer = srv

	go func() {
		fmt.Printf("[UNBOUND JNI] Embedded Go Engine listening on 127.0.0.1:%d (Storage: %s)\n", port, appStoragePath)
		if err := srv.Start(); err != nil {
			fmt.Printf("[UNBOUND JNI] Server exited: %v\n", err)
		}
	}()

	return 1
}

func stopEngineInternal() C.jint {
	serverMutex.Lock()
	defer serverMutex.Unlock()

	if activeServer == nil {
		return 0
	}

	_ = activeServer.Shutdown(context.Background())
	activeServer = nil
	return 1
}

//export Java_com_example_unboundtestfrontend_DaemonManager_startEngineNative
func Java_com_example_unboundtestfrontend_DaemonManager_startEngineNative(
	env *C.JNIEnv,
	clazz C.jobject,
	jAppStoragePath C.jstring,
	jPort C.jint,
) C.jint {
	return startEngineInternal(env, jAppStoragePath, jPort)
}

//export Java_com_example_unboundtestfrontend_DaemonManager_stopEngineNative
func Java_com_example_unboundtestfrontend_DaemonManager_stopEngineNative(
	env *C.JNIEnv,
	clazz C.jobject,
) C.jint {
	return stopEngineInternal()
}

//export Java_com_cubicreates_unboundmusic_daemon_DaemonManager_startEngineNative
func Java_com_cubicreates_unboundmusic_daemon_DaemonManager_startEngineNative(
	env *C.JNIEnv,
	clazz C.jobject,
	jAppStoragePath C.jstring,
	jPort C.jint,
) C.jint {
	return startEngineInternal(env, jAppStoragePath, jPort)
}

//export Java_com_cubicreates_unboundmusic_daemon_DaemonManager_stopEngineNative
func Java_com_cubicreates_unboundmusic_daemon_DaemonManager_stopEngineNative(
	env *C.JNIEnv,
	clazz C.jobject,
) C.jint {
	return stopEngineInternal()
}

func main() {
	// Required for buildmode=c-shared
}
