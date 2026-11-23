package health_test

import (
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/NerushKolya/locator/backend/delivery/internal/server"
)

// TestAuthzMatrix is the authorization matrix of the delivery service (constitution principle III).
// It runs against the complete HTTP handler of the service, exactly as it is served.
//
// Every operation of the service has one row per caller kind. Columns:
//   - method, path: the request as a client sends it (identifiers already filled in);
//   - caller: who sends it; "anonymous" sends no credentials. Later features add callers such as
//     "stream ticket of a group member", "stream ticket of another group" or "expired ticket"
//     together with the code in callers below that builds their request;
//   - want: the expected HTTP status.
//
// A new endpoint is not done until its rows are here. Run only this suite with
// scripts/test-backend.ps1 -AuthzMatrix (go test -run AuthzMatrix ./...).
func TestAuthzMatrix(t *testing.T) {
	rows := []struct {
		method, path, caller string
		want                 int
	}{
		{http.MethodGet, "/health", "anonymous", http.StatusOK},
		{http.MethodGet, "/v1/stream", "anonymous", http.StatusNotFound},
		{http.MethodPost, "/health", "anonymous", http.StatusNotFound},
	}

	callers := map[string]func(*http.Request){
		"anonymous": func(*http.Request) {},
	}

	handler := server.NewHandler(slog.New(slog.NewJSONHandler(io.Discard, nil)))

	for _, row := range rows {
		t.Run(row.method+" "+row.path+" as "+row.caller, func(t *testing.T) {
			prepare, ok := callers[row.caller]
			if !ok {
				t.Fatalf("unknown caller %q: add it to callers", row.caller)
			}
			req := httptest.NewRequest(row.method, row.path, nil)
			prepare(req)

			rec := httptest.NewRecorder()
			handler.ServeHTTP(rec, req)

			if rec.Code != row.want {
				t.Errorf("status %d, want %d", rec.Code, row.want)
			}
		})
	}
}
