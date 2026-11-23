// Package logging writes structured JSON log records (one object per line) as defined by the
// log-records contract of the walking skeleton: fields ts, level, service, msg, requestId, and
// scrubbing of personal location data and secrets before anything is written.
package logging

import (
	"context"
	"io"
	"log/slog"
	"strings"
	"time"
)

type requestIDKey struct{}

// ContextWithRequestID returns a context whose log records carry the given request id.
func ContextWithRequestID(ctx context.Context, id string) context.Context {
	return context.WithValue(ctx, requestIDKey{}, id)
}

// RequestIDFromContext returns the request id stored by ContextWithRequestID.
func RequestIDFromContext(ctx context.Context) (string, bool) {
	id, ok := ctx.Value(requestIDKey{}).(string)
	return id, ok && id != ""
}

// ParseLevel maps LOG_LEVEL to a slog level; unknown or empty values mean info.
func ParseLevel(value string) slog.Level {
	switch strings.ToLower(strings.TrimSpace(value)) {
	case "debug":
		return slog.LevelDebug
	case "warning", "warn":
		return slog.LevelWarn
	case "error":
		return slog.LevelError
	default:
		return slog.LevelInfo
	}
}

// NewHandler returns a slog handler that writes contract-shaped JSON lines to w.
// Every record is scrubbed before it reaches the JSON encoder.
func NewHandler(w io.Writer, level slog.Leveler, service string) slog.Handler {
	inner := slog.NewJSONHandler(w, &slog.HandlerOptions{
		Level:       level,
		ReplaceAttr: renameBuiltins,
	})
	return &scrubHandler{inner: inner.WithAttrs([]slog.Attr{slog.String("service", service)})}
}

// renameBuiltins gives the built-in fields the names and formats of the contract.
func renameBuiltins(groups []string, a slog.Attr) slog.Attr {
	if len(groups) > 0 {
		return a
	}
	switch a.Key {
	case slog.TimeKey:
		t, ok := a.Value.Any().(time.Time)
		if !ok {
			return a
		}
		return slog.String("ts", t.UTC().Format("2006-01-02T15:04:05.000Z07:00"))
	case slog.LevelKey:
		level, ok := a.Value.Any().(slog.Level)
		if !ok {
			return a
		}
		return slog.String(slog.LevelKey, levelName(level))
	}
	return a
}

func levelName(level slog.Level) string {
	switch {
	case level < slog.LevelInfo:
		return "debug"
	case level < slog.LevelWarn:
		return "info"
	case level < slog.LevelError:
		return "warning"
	default:
		return "error"
	}
}

// scrubHandler applies the scrubbing rules to the message and to every attribute.
type scrubHandler struct {
	inner slog.Handler
	// redactAll is set inside a group whose name is a sensitive key (for example WithGroup("location")):
	// every value logged inside it is replaced.
	redactAll bool
}

func (h *scrubHandler) Enabled(ctx context.Context, level slog.Level) bool {
	return h.inner.Enabled(ctx, level)
}

func (h *scrubHandler) Handle(ctx context.Context, r slog.Record) error {
	out := slog.NewRecord(r.Time, r.Level, scrubString(r.Message), r.PC)
	if id, ok := RequestIDFromContext(ctx); ok && !h.redactAll {
		out.AddAttrs(slog.String("requestId", id))
	}
	r.Attrs(func(a slog.Attr) bool {
		out.AddAttrs(h.scrubAttr(a))
		return true
	})
	return h.inner.Handle(ctx, out)
}

func (h *scrubHandler) WithAttrs(attrs []slog.Attr) slog.Handler {
	scrubbed := make([]slog.Attr, 0, len(attrs))
	for _, a := range attrs {
		scrubbed = append(scrubbed, h.scrubAttr(a))
	}
	return &scrubHandler{inner: h.inner.WithAttrs(scrubbed), redactAll: h.redactAll}
}

func (h *scrubHandler) WithGroup(name string) slog.Handler {
	return &scrubHandler{inner: h.inner.WithGroup(name), redactAll: h.redactAll || isSensitiveKey(name)}
}

func (h *scrubHandler) scrubAttr(a slog.Attr) slog.Attr {
	if h.redactAll {
		return slog.String(a.Key, redacted)
	}
	return scrubAttr(a, 0)
}
