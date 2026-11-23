package server

import (
	"bytes"
	"encoding/json"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/NerushKolya/locator/backend/delivery/internal/logging"
	"github.com/NerushKolya/locator/backend/delivery/internal/requestid"
)

func records(t *testing.T, buf *bytes.Buffer) []map[string]any {
	t.Helper()
	var out []map[string]any
	for _, line := range strings.Split(strings.TrimSpace(buf.String()), "\n") {
		if line == "" {
			continue
		}
		var record map[string]any
		if err := json.Unmarshal([]byte(line), &record); err != nil {
			t.Fatalf("log line is not JSON: %v: %q", err, line)
		}
		out = append(out, record)
	}
	return out
}

func TestRequestRecordCarriesRequestID(t *testing.T) {
	var buf bytes.Buffer
	handler := NewHandler(slog.New(logging.NewHandler(&buf, slog.LevelDebug, "delivery")))

	req := httptest.NewRequest(http.MethodGet, "/health", nil)
	req.Header.Set(requestid.Header, "client-req-0001")
	rec := httptest.NewRecorder()
	handler.ServeHTTP(rec, req)

	if got := rec.Header().Get(requestid.Header); got != "client-req-0001" {
		t.Fatalf("response %s = %q", requestid.Header, got)
	}
	logged := records(t, &buf)
	if len(logged) != 1 {
		t.Fatalf("want one request record, got %d: %s", len(logged), buf.String())
	}
	r := logged[0]
	want := map[string]any{
		"event":     "http.request",
		"requestId": "client-req-0001",
		"method":    "GET",
		"route":     "/health",
		"status":    float64(200),
		"level":     "debug", // health checks run every few seconds
		"service":   "delivery",
	}
	for k, v := range want {
		if r[k] != v {
			t.Errorf("record[%q] = %v, want %v (record %v)", k, r[k], v, r)
		}
	}
	if _, ok := r["durationMs"].(float64); !ok {
		t.Errorf("durationMs missing or not a number: %v", r)
	}
}

func TestHealthRecordHiddenAtInfoLevel(t *testing.T) {
	var buf bytes.Buffer
	handler := NewHandler(slog.New(logging.NewHandler(&buf, slog.LevelInfo, "delivery")))
	handler.ServeHTTP(httptest.NewRecorder(), httptest.NewRequest(http.MethodGet, "/health", nil))
	if buf.Len() != 0 {
		t.Errorf("health request logged above debug: %s", buf.String())
	}
}

func TestUnknownPathIsLoggedWithoutRawPath(t *testing.T) {
	var buf bytes.Buffer
	handler := NewHandler(slog.New(logging.NewHandler(&buf, slog.LevelDebug, "delivery")))

	rec := httptest.NewRecorder()
	handler.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/v1/groups/secret-id", nil))

	if rec.Code != http.StatusNotFound {
		t.Fatalf("status %d, want 404", rec.Code)
	}
	logged := records(t, &buf)
	if len(logged) != 1 || logged[0]["level"] != "info" || logged[0]["route"] != "unmatched" {
		t.Errorf("unexpected records %v", logged)
	}
	if strings.Contains(buf.String(), "secret-id") {
		t.Errorf("raw path written to the log: %s", buf.String())
	}
}
