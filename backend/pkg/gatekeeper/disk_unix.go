//go:build !windows

/*
 * Package: gatekeeper
 * File: disk_unix.go
 * Purpose: POSIX / Android / Linux implementation of free disk space inspection using statfs syscall.
 * Subsystem: Storage & Intelligence Engine
 * Concurrency: Thread-safe syscall.
 */

package gatekeeper

import (
	"syscall"
)

// getFreeDiskSpace queries free bytes available to the caller on UNIX/Android filesystems.
func getFreeDiskSpace(path string) (int64, error) {
	var stat syscall.Statfs_t
	err := syscall.Statfs(path, &stat)
	if err != nil {
		return 0, err
	}
	return int64(stat.Bavail) * int64(stat.Bsize), nil
}
