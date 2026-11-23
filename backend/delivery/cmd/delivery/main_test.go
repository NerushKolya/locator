package main

import (
	"bytes"
	"context"
	"net"
	"net/http"
	"sync"
	"testing"
	"time"
)

// freeAddr returns a loopback address on which nothing listens.
func freeAddr(t *testing.T) string {
	t.Helper()
	l, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	addr := l.Addr().String()
	if err := l.Close(); err != nil {
		t.Fatal(err)
	}
	return addr
}

func envWith(values map[string]string) func(string) string {
	return func(name string) string { return values[name] }
}

// syncBuffer is a bytes.Buffer safe for the server goroutine and the test.
type syncBuffer struct {
	mu  sync.Mutex
	buf bytes.Buffer
}

func (b *syncBuffer) Write(p []byte) (int, error) {
	b.mu.Lock()
	defer b.mu.Unlock()
	return b.buf.Write(p)
}

func (b *syncBuffer) String() string {
	b.mu.Lock()
	defer b.mu.Unlock()
	return b.buf.String()
}

// startServer runs the service in the background and waits until it answers.
func startServer(t *testing.T, addr string) (stop func() int) {
	t.Helper()
	ctx, cancel := context.WithCancel(context.Background())
	done := make(chan int, 1)
	logs := &syncBuffer{}
	go func() {
		done <- run(ctx, nil, envWith(map[string]string{"DELIVERY_HTTP_ADDR": addr}), logs)
	}()

	deadline := time.Now().Add(5 * time.Second)
	for {
		resp, err := http.Get("http://" + addr + "/health")
		if err == nil {
			_ = resp.Body.Close()
			break
		}
		if time.Now().After(deadline) {
			cancel()
			t.Fatalf("server did not start on %s: %v; logs: %s", addr, err, logs.String())
		}
		time.Sleep(20 * time.Millisecond)
	}

	return func() int {
		cancel()
		select {
		case code := <-done:
			return code
		case <-time.After(shutdownTimeout + 2*time.Second):
			t.Fatal("server did not stop after the context was cancelled")
			return -1
		}
	}
}

func TestHealthcheckFlagAgainstRunningServer(t *testing.T) {
	addr := freeAddr(t)
	stop := startServer(t, addr)
	defer stop()

	env := envWith(map[string]string{"DELIVERY_HTTP_ADDR": addr})
	if code := run(context.Background(), []string{"-healthcheck"}, env, &bytes.Buffer{}); code != 0 {
		t.Errorf("-healthcheck exit code %d, want 0", code)
	}
}

func TestHealthcheckFlagWhenNothingListens(t *testing.T) {
	env := envWith(map[string]string{"DELIVERY_HTTP_ADDR": freeAddr(t)})
	if code := run(context.Background(), []string{"-healthcheck"}, env, &bytes.Buffer{}); code != 1 {
		t.Errorf("-healthcheck exit code %d, want 1", code)
	}
}

func TestGracefulShutdown(t *testing.T) {
	stop := startServer(t, freeAddr(t))
	if code := stop(); code != 0 {
		t.Errorf("exit code after shutdown %d, want 0", code)
	}
}

func TestUnknownFlagFails(t *testing.T) {
	if code := run(context.Background(), []string{"-nope"}, envWith(nil), &bytes.Buffer{}); code != 2 {
		t.Errorf("exit code %d, want 2", code)
	}
}

func TestHealthcheckURL(t *testing.T) {
	cases := map[string]string{
		"":             "http://127.0.0.1:8081/health",
		":8081":        "http://127.0.0.1:8081/health",
		"0.0.0.0:9000": "http://127.0.0.1:9000/health",
		"[::]:9000":    "http://127.0.0.1:9000/health",
		"10.1.2.3:80":  "http://10.1.2.3:80/health",
	}
	for addr, want := range cases {
		got, err := healthcheckURL(addr)
		if err != nil || got != want {
			t.Errorf("healthcheckURL(%q) = %q, %v; want %q", addr, got, err, want)
		}
	}
}
