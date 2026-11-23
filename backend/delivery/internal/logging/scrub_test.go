package logging

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"log/slog"
	"regexp"
	"strings"
	"testing"
	"time"
)

// newTestLogger returns a logger writing JSON lines into buf at debug level.
func newTestLogger(buf *bytes.Buffer) *slog.Logger {
	return slog.New(NewHandler(buf, slog.LevelDebug, "delivery"))
}

// decodeLine parses the single record written into buf.
func decodeLine(t *testing.T, buf *bytes.Buffer) map[string]any {
	t.Helper()
	lines := strings.Split(strings.TrimRight(buf.String(), "\n"), "\n")
	if len(lines) != 1 {
		t.Fatalf("want exactly one log line, got %d: %q", len(lines), buf.String())
	}
	var record map[string]any
	if err := json.Unmarshal([]byte(lines[0]), &record); err != nil {
		t.Fatalf("log line is not JSON: %v: %q", err, lines[0])
	}
	return record
}

// Test vectors of the log-records contract, section 4. Each case logs a message and attributes
// and asserts that none of the forbidden values appears in the written line.
func TestScrubbingVectors(t *testing.T) {
	jwt := "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.c2lnbmF0dXJl"

	cases := []struct {
		name      string
		msg       string
		attrs     []slog.Attr
		forbidden []string
		kept      []string
	}{
		{
			name:      "1 coordinate fields",
			msg:       "point stored",
			attrs:     []slog.Attr{slog.Float64("lat", 50.4501), slog.Float64("lon", 30.5234)},
			forbidden: []string{"50.4501", "30.5234"},
		},
		{
			name:      "2 coordinates in the message",
			msg:       "position 50.4501, 30.5234 accepted",
			forbidden: []string{"50.4501", "30.5234"},
			kept:      []string{"position", "accepted", "[redacted:coords]"},
		},
		{
			name:      "3 place name field",
			msg:       "place saved",
			attrs:     []slog.Attr{slog.String("placeName", "Home")},
			forbidden: []string{"Home"},
		},
		{
			name:      "4 access token field",
			msg:       "session refreshed",
			attrs:     []slog.Attr{slog.String("accessToken", jwt)},
			forbidden: []string{jwt},
		},
		{
			name:      "5 bearer token in the message",
			msg:       "Authorization: Bearer abc.def.ghi failed",
			forbidden: []string{"abc.def.ghi"},
			kept:      []string{"failed", "[redacted:token]"},
		},
		{
			name:      "6 e-mail field and message",
			msg:       "sent to person@example.org",
			attrs:     []slog.Attr{slog.String("email", "person@example.org")},
			forbidden: []string{"person@example.org"},
			kept:      []string{"sent to", "[redacted:email]"},
		},
		{
			name:      "7 invitation code field and message",
			msg:       "invite code K7P2M9QX expired",
			attrs:     []slog.Attr{slog.String("invitationCode", "K7P2M9QX")},
			forbidden: []string{"K7P2M9QX"},
			kept:      []string{"invite code", "expired", "[redacted:invite]"},
		},
		{
			name: "8 nested location",
			msg:  "device state",
			attrs: []slog.Attr{slog.Group("device",
				slog.Group("location", slog.Float64("lat", 50.45), slog.Float64("lon", 30.52)),
			)},
			forbidden: []string{"50.45", "30.52"},
		},
		{
			name:      "9 identifiers and status stay",
			msg:       "request handled",
			attrs:     []slog.Attr{slog.String("userId", "0192f3e4-7c1a-7b2e-9f00-1234567890ab"), slog.Int("status", 200)},
			forbidden: nil,
			kept:      []string{"0192f3e4-7c1a-7b2e-9f00-1234567890ab", `"status":200`},
		},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			var buf bytes.Buffer
			newTestLogger(&buf).LogAttrs(context.Background(), slog.LevelInfo, tc.msg, tc.attrs...)
			line := buf.String()
			t.Log(strings.TrimSpace(line))
			for _, value := range tc.forbidden {
				if strings.Contains(line, value) {
					t.Errorf("forbidden value %q found in %s", value, line)
				}
			}
			for _, value := range tc.kept {
				if !strings.Contains(line, value) {
					t.Errorf("expected %q in %s", value, line)
				}
			}
		})
	}
}

func TestRecordFields(t *testing.T) {
	var buf bytes.Buffer
	newTestLogger(&buf).Info("service started", slog.String("event", "service.started"))
	record := decodeLine(t, &buf)

	for _, field := range []string{"ts", "level", "service", "msg"} {
		if _, ok := record[field]; !ok {
			t.Errorf("field %q missing in %v", field, record)
		}
	}
	for _, field := range []string{"time"} {
		if _, ok := record[field]; ok {
			t.Errorf("field %q must be renamed, got %v", field, record)
		}
	}
	if record["service"] != "delivery" || record["msg"] != "service started" || record["level"] != "info" {
		t.Errorf("unexpected record %v", record)
	}
	ts, _ := record["ts"].(string)
	if !regexp.MustCompile(`^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$`).MatchString(ts) {
		t.Errorf("ts %q is not RFC 3339 UTC with milliseconds", ts)
	}
	if _, err := time.Parse(time.RFC3339Nano, ts); err != nil {
		t.Errorf("ts %q does not parse: %v", ts, err)
	}
}

func TestLevelNames(t *testing.T) {
	want := map[slog.Level]string{
		slog.LevelDebug: "debug",
		slog.LevelInfo:  "info",
		slog.LevelWarn:  "warning",
		slog.LevelError: "error",
	}
	for level, name := range want {
		var buf bytes.Buffer
		newTestLogger(&buf).Log(context.Background(), level, "x")
		if got := decodeLine(t, &buf)["level"]; got != name {
			t.Errorf("level %v written as %v, want %q", level, got, name)
		}
	}
}

func TestParseLevel(t *testing.T) {
	cases := map[string]slog.Level{
		"debug":   slog.LevelDebug,
		"INFO":    slog.LevelInfo,
		"warning": slog.LevelWarn,
		"warn":    slog.LevelWarn,
		"error":   slog.LevelError,
		"":        slog.LevelInfo,
		"verbose": slog.LevelInfo,
	}
	for in, want := range cases {
		if got := ParseLevel(in); got != want {
			t.Errorf("ParseLevel(%q) = %v, want %v", in, got, want)
		}
	}
}

func TestLevelFilter(t *testing.T) {
	var buf bytes.Buffer
	logger := slog.New(NewHandler(&buf, slog.LevelInfo, "delivery"))
	logger.Debug("hidden")
	if buf.Len() != 0 {
		t.Fatalf("debug record written at info level: %q", buf.String())
	}
}

func TestScrubbingKeepsOrdinaryWords(t *testing.T) {
	var buf bytes.Buffer
	newTestLogger(&buf).Info("invitation accepted by member", slog.String("note", "code review done"))
	line := buf.String()
	for _, value := range []string{"invitation accepted by member", "code review done"} {
		if !strings.Contains(line, value) {
			t.Errorf("ordinary text %q changed: %s", value, line)
		}
	}
}

func TestScrubbingKeysAreCaseAndSeparatorInsensitive(t *testing.T) {
	var buf bytes.Buffer
	newTestLogger(&buf).Info("x",
		slog.String("Access_Token", "secret-one"),
		slog.String("push-token", "secret-two"),
		slog.String("client.password", "secret-three"),
	)
	line := buf.String()
	for _, value := range []string{"secret-one", "secret-two", "secret-three"} {
		if strings.Contains(line, value) {
			t.Errorf("value %q not redacted: %s", value, line)
		}
	}
}

func TestScrubbingWithAttrsAndGroups(t *testing.T) {
	var buf bytes.Buffer
	logger := newTestLogger(&buf).With(slog.String("email", "person@example.org")).WithGroup("location")
	logger.Info("x", slog.Float64("anything", 50.4501), slog.String("note", "near 50.4501, 30.5234"))
	line := buf.String()
	for _, value := range []string{"person@example.org", "50.4501", "30.5234"} {
		if strings.Contains(line, value) {
			t.Errorf("value %q not redacted: %s", value, line)
		}
	}
}

func TestScrubbingAnyValues(t *testing.T) {
	var buf bytes.Buffer
	newTestLogger(&buf).Info("x",
		slog.Any("payload", map[string]any{"device": map[string]any{"lat": 50.4501, "note": "mail person@example.org"}}),
		slog.Any("list", []any{"Bearer abc.def.ghi", 3}),
		slog.Any("err", errors.New("token eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.c2lnbmF0dXJl rejected")),
		slog.Any("opaque", struct{ Lat float64 }{Lat: 50.4501}),
	)
	line := buf.String()
	for _, value := range []string{"50.4501", "person@example.org", "abc.def.ghi", "eyJhbGciOiJIUzI1NiJ9"} {
		if strings.Contains(line, value) {
			t.Errorf("value %q not redacted: %s", value, line)
		}
	}
	if !strings.Contains(line, "rejected") {
		t.Errorf("error message lost: %s", line)
	}
}

func TestCoordinatePatternBounds(t *testing.T) {
	cases := map[string]bool{
		"at 50.4501, 30.5234":       true,
		"at -33.8688,151.2093":      true,
		"ratio 12.5, 30.1":          false, // fewer than 3 fraction digits
		"values 250.123, 30.123":    false, // outside +-180
		"build 1234.567, 30.123":    false, // starts inside a longer number
		"ids 10.100.200.300, 1.234": false,
	}
	for in, redacted := range cases {
		got := scrubString(in)
		if strings.Contains(got, "[redacted:coords]") != redacted {
			t.Errorf("scrubString(%q) = %q, want redacted=%v", in, got, redacted)
		}
	}
}

func TestRequestIDFromContext(t *testing.T) {
	var buf bytes.Buffer
	ctx := ContextWithRequestID(context.Background(), "abcdef12-3456")
	newTestLogger(&buf).InfoContext(ctx, "x")
	if got := decodeLine(t, &buf)["requestId"]; got != "abcdef12-3456" {
		t.Errorf("requestId = %v, want abcdef12-3456", got)
	}
}
