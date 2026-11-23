package server

import (
	"bytes"
	"encoding/json"
	"io"
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

func TestUnknownPathAnswersProblemJSON(t *testing.T) {
	handler := NewHandler(slog.New(slog.NewJSONHandler(io.Discard, nil)))

	for _, tc := range []struct{ method, path string }{
		{http.MethodGet, "/v1/groups/secret-id"},
		{http.MethodPost, "/health"},
	} {
		rec := httptest.NewRecorder()
		req := httptest.NewRequest(tc.method, tc.path, nil)
		req.Header.Set("X-Request-Id", "req-12345678")
		handler.ServeHTTP(rec, req)

		if rec.Code != http.StatusNotFound {
			t.Fatalf("%s %s: status %d, want 404", tc.method, tc.path, rec.Code)
		}
		if ct := rec.Header().Get("Content-Type"); ct != "application/problem+json" {
			t.Errorf("%s %s: Content-Type %q", tc.method, tc.path, ct)
		}
		var body map[string]any
		if err := json.Unmarshal(rec.Body.Bytes(), &body); err != nil {
			t.Fatalf("%s %s: body is not JSON: %v", tc.method, tc.path, err)
		}
		want := map[string]any{
			"type": "about:blank", "title": "Not Found", "status": float64(404),
			"code": "not_found", "requestId": "req-12345678",
		}
		for k, v := range want {
			if body[k] != v {
				t.Errorf("%s %s: body[%q] = %v, want %v", tc.method, tc.path, k, body[k], v)
			}
		}
		if strings.Contains(rec.Body.String(), "secret-id") {
			t.Errorf("request path echoed in the body: %s", rec.Body.String())
		}
	}
}
