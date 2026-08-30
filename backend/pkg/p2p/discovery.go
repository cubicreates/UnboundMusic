/*
 * Package: p2p
 * File: discovery.go
 * Purpose: Local network P2P device discovery engine for zero-data peer-to-peer library synchronization.
 * Subsystem: P2P Wi-Fi Sync Engine
 * Concurrency: Thread-safe peer table with periodic UDP heartbeat broadcasting.
 */

package p2p

import (
	"context"
	"encoding/json"
	"fmt"
	"net"
	"sync"
	"time"
)

const (
	// DefaultDiscoveryPort is the UDP broadcast port for Unbound local peer announcements.
	DefaultDiscoveryPort = 45732
	// DiscoveryMagicHeader identifies Unbound P2P broadcast packets.
	DiscoveryMagicHeader = "UNBOUND_P2P_BEACON_V1"
)

// Peer represents another Unbound Music instance on the same Wi-Fi or Hotspot network.
type Peer struct {
	DeviceID   string    `json:"device_id"`
	DeviceName string    `json:"device_name"`
	IPAddress  string    `json:"ip_address"`
	APIPort    int       `json:"api_port"`
	TrackCount int       `json:"track_count"`
	LastSeen   time.Time `json:"last_seen"`
}

// BeaconPayload is the JSON packet broadcast over UDP.
type BeaconPayload struct {
	Header     string `json:"header"`
	DeviceID   string `json:"device_id"`
	DeviceName string `json:"device_name"`
	APIPort    int    `json:"api_port"`
	TrackCount int    `json:"track_count"`
}

// Discovery manages peer registry and local network beacons.
type Discovery struct {
	mu         sync.RWMutex
	deviceID   string
	deviceName string
	apiPort    int
	peers      map[string]Peer
}

// NewDiscovery creates a new P2P discovery manager.
func NewDiscovery(deviceID, deviceName string, apiPort int) *Discovery {
	if deviceID == "" {
		deviceID = "unbound_node_local"
	}
	if deviceName == "" {
		deviceName = "Unbound Music Device"
	}
	if apiPort <= 0 {
		apiPort = 45731
	}

	return &Discovery{
		deviceID:   deviceID,
		deviceName: deviceName,
		apiPort:    apiPort,
		peers:      make(map[string]Peer),
	}
}

// RegisterPeer adds or refreshes a peer in the local registry.
func (d *Discovery) RegisterPeer(p Peer) {
	d.mu.Lock()
	defer d.mu.Unlock()
	p.LastSeen = time.Now()
	d.peers[p.DeviceID] = p
}

// GetActivePeers returns a slice of all discovered peers seen within the last 30 seconds.
func (d *Discovery) GetActivePeers() []Peer {
	d.mu.RLock()
	defer d.mu.RUnlock()

	cutoff := time.Now().Add(-30 * time.Second)
	var active []Peer
	for _, p := range d.peers {
		if p.LastSeen.After(cutoff) {
			active = append(active, p)
		}
	}
	return active
}

// BroadcastBeacon sends a single UDP announcement packet to the local broadcast address.
func (d *Discovery) BroadcastBeacon() error {
	payload := BeaconPayload{
		Header:     DiscoveryMagicHeader,
		DeviceID:   d.deviceID,
		DeviceName: d.deviceName,
		APIPort:    d.apiPort,
		TrackCount: 0,
	}

	data, err := json.Marshal(payload)
	if err != nil {
		return err
	}

	addr, err := net.ResolveUDPAddr("udp4", fmt.Sprintf("255.255.255.255:%d", DefaultDiscoveryPort))
	if err != nil {
		return err
	}

	conn, err := net.DialUDP("udp4", nil, addr)
	if err != nil {
		return err
	}
	defer conn.Close()

	_, err = conn.Write(data)
	return err
}

// StartListening starts background UDP packet listening for incoming peer announcements.
func (d *Discovery) StartListening(ctx context.Context) error {
	addr, err := net.ResolveUDPAddr("udp4", fmt.Sprintf(":%d", DefaultDiscoveryPort))
	if err != nil {
		return err
	}

	conn, err := net.ListenUDP("udp4", addr)
	if err != nil {
		return err
	}
	defer conn.Close()

	buf := make([]byte, 2048)

	for {
		select {
		case <-ctx.Done():
			return nil
		default:
			_ = conn.SetReadDeadline(time.Now().Add(1 * time.Second))
			n, remoteAddr, err := conn.ReadFromUDP(buf)
			if err != nil {
				continue
			}

			var beacon BeaconPayload
			if err := json.Unmarshal(buf[:n], &beacon); err != nil {
				continue
			}

			if beacon.Header != DiscoveryMagicHeader || beacon.DeviceID == d.deviceID {
				continue // Skip invalid beacons or self-announcements
			}

			d.RegisterPeer(Peer{
				DeviceID:   beacon.DeviceID,
				DeviceName: beacon.DeviceName,
				IPAddress:  remoteAddr.IP.String(),
				APIPort:    beacon.APIPort,
				TrackCount: beacon.TrackCount,
			})
		}
	}
}
