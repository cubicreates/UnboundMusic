/*
 * Package: discord
 * File: rpc.go
 * Purpose: Pure Go Discord Rich Presence IPC connector broadcasting current track, artist, and timestamps to Discord desktop client.
 * Subsystem: Secondary Services & Desktop Integrations
 * Concurrency: Thread-safe state updates; non-blocking fail-soft execution when Discord is closed.
 */

package discord

import (
	"bytes"
	"encoding/binary"
	"encoding/json"
	"fmt"
	"net"
	"os"
	"runtime"
	"sync"
	"time"
)

const (
	defaultClientID = "123456789012345678" // Default public Unbound client application ID
	opHandshake     = 0
	opFrame         = 1
	opClose         = 2
	opPing          = 3
	opPong          = 4
)

// Activity represents the Discord Rich Presence activity payload.
type Activity struct {
	Details        string    `json:"details"`
	State          string    `json:"state"`
	LargeImageKey  string    `json:"large_image"`
	LargeImageText string    `json:"large_text"`
	SmallImageKey  string    `json:"small_image"`
	SmallImageText string    `json:"small_text"`
	StartTimestamp int64     `json:"start_timestamp,omitempty"`
	EndTimestamp   int64     `json:"end_timestamp,omitempty"`
}

// Client coordinates local IPC connection with Discord desktop.
type Client struct {
	mu        sync.Mutex
	conn      net.Conn
	clientID  string
	isConnected bool
}

// NewClient initializes a new Discord Rich Presence client.
func NewClient(clientID string) *Client {
	if clientID == "" {
		clientID = defaultClientID
	}
	return &Client{
		clientID: clientID,
	}
}

// Connect attempts to establish IPC connection with the running Discord desktop client.
func (c *Client) Connect() error {
	c.mu.Lock()
	defer c.mu.Unlock()

	if c.isConnected && c.conn != nil {
		return nil
	}

	socketPath := getSocketPath(0)
	conn, err := net.Dial("pipe", socketPath)
	if err != nil {
		// Try unix domain socket if on non-windows
		conn, err = net.Dial("unix", socketPath)
		if err != nil {
			return fmt.Errorf("discord client not running or unreachable: %w", err)
		}
	}

	c.conn = conn

	// Send Handshake
	handshakePayload, _ := json.Marshal(map[string]string{
		"v":         "1",
		"client_id": c.clientID,
	})

	if err := c.sendFrame(opHandshake, handshakePayload); err != nil {
		c.conn.Close()
		c.conn = nil
		return fmt.Errorf("failed to send discord handshake: %w", err)
	}

	c.isConnected = true
	return nil
}

// SetActivity updates the active listening status on Discord.
func (c *Client) SetActivity(activity Activity) error {
	c.mu.Lock()
	defer c.mu.Unlock()

	if !c.isConnected || c.conn == nil {
		return fmt.Errorf("discord rpc is not connected")
	}

	type Payload struct {
		Cmd   string `json:"cmd"`
		Args  struct {
			Pid      int       `json:"pid"`
			Activity Activity  `json:"activity"`
		} `json:"args"`
		Nonce string `json:"nonce"`
	}

	p := Payload{
		Cmd: "SET_ACTIVITY",
		Nonce: fmt.Sprintf("%d", time.Now().UnixNano()),
	}
	p.Args.Pid = os.Getpid()
	p.Args.Activity = activity

	data, err := json.Marshal(p)
	if err != nil {
		return fmt.Errorf("failed to encode discord activity: %w", err)
	}

	return c.sendFrame(opFrame, data)
}

// Close disconnects from the Discord IPC socket.
func (c *Client) Close() error {
	c.mu.Lock()
	defer c.mu.Unlock()

	if c.conn != nil {
		_ = c.sendFrame(opClose, []byte("{}"))
		err := c.conn.Close()
		c.conn = nil
		c.isConnected = false
		return err
	}
	return nil
}

// sendFrame writes an IPC frame with header [opcode:4][length:4][payload].
func (c *Client) sendFrame(opcode uint32, payload []byte) error {
	if c.conn == nil {
		return fmt.Errorf("connection is closed")
	}

	var buf bytes.Buffer
	_ = binary.Write(&buf, binary.LittleEndian, opcode)
	_ = binary.Write(&buf, binary.LittleEndian, uint32(len(payload)))
	buf.Write(payload)

	_, err := c.conn.Write(buf.Bytes())
	return err
}

// getSocketPath determines platform IPC endpoint for Discord.
func getSocketPath(index int) string {
	if runtime.GOOS == "windows" {
		return fmt.Sprintf(`\\.\pipe\discord-ipc-%d`, index)
	}

	tmp := os.Getenv("XDG_RUNTIME_DIR")
	if tmp == "" {
		tmp = os.Getenv("TMPDIR")
	}
	if tmp == "" {
		tmp = "/tmp"
	}
	return fmt.Sprintf("%s/discord-ipc-%d", tmp, index)
}
