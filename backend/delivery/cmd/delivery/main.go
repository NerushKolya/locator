// Command delivery is the real-time delivery service (ADR-002). In the walking skeleton it serves
// GET /health only.
//
// Settings come from the environment:
//
//	DELIVERY_HTTP_ADDR  listen address, default ":8081"
//	LOG_LEVEL           debug, info, warning or error; default info
//
// With -healthcheck the binary requests its own /health and exits 0 when it answered 200, 1
// otherwise. The runtime image has no shell or curl, so the Compose health check uses this flag.
package main

import (
	"context"
	"errors"
	"flag"
	"fmt"
	"io"
	"log/slog"
	"net"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/NerushKolya/locator/backend/delivery/internal/logging"
	"github.com/NerushKolya/locator/backend/delivery/internal/server"
)

const (
	defaultAddr     = ":8081"
	shutdownTimeout = 10 * time.Second
)

func main() {
	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGTERM, os.Interrupt)
	code := run(ctx, os.Args[1:], os.Getenv, os.Stdout)
	stop()
	os.Exit(code)
}

// run is the testable body of main: it returns the process exit code.
func run(ctx context.Context, args []string, getenv func(string) string, stdout io.Writer) int {
	flags := flag.NewFlagSet("delivery", flag.ContinueOnError)
	flags.SetOutput(stdout)
	healthcheck := flags.Bool("healthcheck", false, "request the own /health and exit 0 when it answers 200")
	if err := flags.Parse(args); err != nil {
		return 2
	}

	addr := getenv("DELIVERY_HTTP_ADDR")
	if addr == "" {
		addr = defaultAddr
	}

	if *healthcheck {
		return runHealthcheck(addr)
	}

	logger := slog.New(logging.NewHandler(stdout, logging.ParseLevel(getenv("LOG_LEVEL")), "delivery"))
	if err := serve(ctx, addr, logger); err != nil {
		logger.Error("service stopped with an error", slog.String("event", "service.failed"), slog.Any("error", err))
		return 1
	}
	return 0
}

// serve listens on addr until ctx is cancelled, then shuts down within shutdownTimeout.
func serve(ctx context.Context, addr string, logger *slog.Logger) error {
	srv := &http.Server{
		Addr:              addr,
		Handler:           server.NewHandler(logger),
		ReadHeaderTimeout: 5 * time.Second,
		ErrorLog:          slog.NewLogLogger(logger.Handler(), slog.LevelWarn),
	}

	listener, err := net.Listen("tcp", addr)
	if err != nil {
		return fmt.Errorf("listen on %s: %w", addr, err)
	}
	logger.Info("service started", slog.String("event", "service.started"), slog.String("addr", listener.Addr().String()))

	served := make(chan error, 1)
	go func() { served <- srv.Serve(listener) }()

	select {
	case err := <-served:
		return err
	case <-ctx.Done():
	}

	logger.Info("shutting down", slog.String("event", "service.stopping"))
	shutdownCtx, cancel := context.WithTimeout(context.Background(), shutdownTimeout)
	defer cancel()
	if err := srv.Shutdown(shutdownCtx); err != nil {
		return fmt.Errorf("graceful shutdown: %w", err)
	}
	if err := <-served; err != nil && !errors.Is(err, http.ErrServerClosed) {
		return err
	}
	logger.Info("service stopped", slog.String("event", "service.stopped"))
	return nil
}

func runHealthcheck(addr string) int {
	url, err := healthcheckURL(addr)
	if err != nil {
		return 1
	}
	client := &http.Client{Timeout: 2 * time.Second}
	resp, err := client.Get(url)
	if err != nil {
		return 1
	}
	_ = resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return 1
	}
	return 0
}

// healthcheckURL turns a listen address into the URL of the own /health; an empty or unspecified
// host means the loopback address.
func healthcheckURL(addr string) (string, error) {
	if addr == "" {
		addr = defaultAddr
	}
	host, port, err := net.SplitHostPort(addr)
	if err != nil {
		return "", err
	}
	if ip := net.ParseIP(host); host == "" || (ip != nil && ip.IsUnspecified()) {
		host = "127.0.0.1"
	}
	return "http://" + net.JoinHostPort(host, port) + "/health", nil
}
