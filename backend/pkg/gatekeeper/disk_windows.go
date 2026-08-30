//go:build windows

/*
 * Package: gatekeeper
 * File: disk_windows.go
 * Purpose: Windows implementation of free disk space inspection using Win32 API.
 * Subsystem: Storage & Intelligence Engine
 * Concurrency: Thread-safe syscall.
 */

package gatekeeper

import (
	"syscall"
	"unsafe"
)

var (
	kernel32                = syscall.NewLazyDLL("kernel32.dll")
	procGetDiskFreeSpaceExW = kernel32.NewProc("GetDiskFreeSpaceExW")
)

// getFreeDiskSpace queries free bytes available to the caller on the Windows drive.
func getFreeDiskSpace(path string) (int64, error) {
	var freeBytesAvailable, totalNumberOfBytes, totalNumberOfFreeBytes int64
	pathPtr, err := syscall.UTF16PtrFromString(path)
	if err != nil {
		return 0, err
	}

	r1, _, errSys := procGetDiskFreeSpaceExW.Call(
		uintptr(unsafe.Pointer(pathPtr)),
		uintptr(unsafe.Pointer(&freeBytesAvailable)),
		uintptr(unsafe.Pointer(&totalNumberOfBytes)),
		uintptr(unsafe.Pointer(&totalNumberOfFreeBytes)),
	)

	if r1 == 0 {
		return 0, errSys
	}

	return freeBytesAvailable, nil
}
