// Package server assembles the HTTP handler of the delivery service: routes, request id and
// request log records.
package server

import (
	"log/slog"
	"net/http"
	"strings"
	"time"

	"github.com/NerushKolya/locator/backend/delivery/internal/health"
	"github.com/NerushKolya/locator/backend/delivery/internal/requestid"
)

// NewHandler returns the complete handler as it is served, so tests (including the authorization
// matrix) exercise the same routing and middleware as production.
func NewHandler(logger *slog.Logger) http.Handler {
	mux := http.NewServeMux()
	health.Register(mux)
	return requestid.Middleware(requestLog(logger, mux))
}

// statusRecorder remembers the status written by the handler.
type statusRecorder struct {
	http.ResponseWriter
	status int
}

func (s *statusRecorder) WriteHeader(code int) {
	if s.status == 0 {
		s.status = code
	}
	s.ResponseWriter.WriteHeader(code)
}

func (s *statusRecorder) Write(p []byte) (int, error) {
	if s.status == 0 {
		s.status = http.StatusOK
	}
	return s.ResponseWriter.Write(p)
}

func (s *statusRecorder) Unwrap() http.ResponseWriter { return s.ResponseWriter }

// requestLog writes one "http.request" record per request: method, route pattern (never the raw
// path), status and duration. Health checks run every few seconds and are logged at debug only.
func requestLog(logger *slog.Logger, mux *http.ServeMux) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		started := time.Now()
		rec := &statusRecorder{ResponseWriter: w}
		mux.ServeHTTP(rec, r)
		if rec.status == 0 {
			rec.status = http.StatusOK
		}

		// ServeMux stores the matched pattern ("GET /health") in the request it was given.
		_, route, _ := strings.Cut(r.Pattern, " ")
		if r.Pattern == "" {
			route = "unmatched"
		} else if route == "" {
			route = r.Pattern
		}
		level := slog.LevelInfo
		if r.Pattern == health.Route {
			level = slog.LevelDebug
		}

		logger.LogAttrs(r.Context(), level, "request handled",
			slog.String("event", "http.request"),
			slog.String("method", r.Method),
			slog.String("route", route),
			slog.Int("status", rec.status),
			slog.Int64("durationMs", time.Since(started).Milliseconds()),
		)
	})
}
