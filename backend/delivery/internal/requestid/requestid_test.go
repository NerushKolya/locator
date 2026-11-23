package requestid

import (
	"net/http"
	"net/http/httptest"
	"regexp"
	"strings"
	"testing"

	"github.com/NerushKolya/locator/backend/delivery/internal/logging"
)

var uuidV7 = regexp.MustCompile(`^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$`)

// serve runs the middleware around a handler that records the id it sees in the request context.
func serve(t *testing.T, sent string, setHeader bool) (header, seen string) {
	t.Helper()
	inner := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		seen, _ = logging.RequestIDFromContext(r.Context())
		w.WriteHeader(http.StatusNoContent)
	})
	req := httptest.NewRequest(http.MethodGet, "/health", nil)
	if setHeader {
		req.Header.Set(Header, sent)
	}
	rec := httptest.NewRecorder()
	Middleware(inner).ServeHTTP(rec, req)
	return rec.Header().Get(Header), seen
}

func TestValidIDIsEchoed(t *testing.T) {
	for _, id := range []string{"abcdefgh", "0192f3e4-7c1a-7b2e-9f00-1234567890ab", strings.Repeat("A", 64), "client-req-42"} {
		header, seen := serve(t, id, true)
		if header != id || seen != id {
			t.Errorf("sent %q: header %q, context %q", id, header, seen)
		}
	}
}

func TestMalformedOrMissingIDIsReplaced(t *testing.T) {
	cases := map[string]string{
		"too short":       "abc",
		"too long":        strings.Repeat("a", 65),
		"forbidden chars": "abc_def/ghi",
		"spaces":          "abcd efgh",
		"newline":         "abcdefgh\nX",
		"empty":           "",
	}
	for name, sent := range cases {
		t.Run(name, func(t *testing.T) {
			header, seen := serve(t, sent, true)
			if header == sent || !uuidV7.MatchString(header) {
				t.Errorf("sent %q: header %q is not a new UUID v7", sent, header)
			}
			if seen != header {
				t.Errorf("context id %q differs from header %q", seen, header)
			}
		})
	}
	t.Run("missing", func(t *testing.T) {
		header, seen := serve(t, "", false)
		if !uuidV7.MatchString(header) || seen != header {
			t.Errorf("header %q, context %q", header, seen)
		}
	})
}

func TestGeneratedIDsAreUniqueAndOrdered(t *testing.T) {
	previous := ""
	seen := map[string]bool{}
	for i := 0; i < 200; i++ {
		id := NewV7()
		if !uuidV7.MatchString(id) {
			t.Fatalf("%q is not a UUID v7", id)
		}
		if seen[id] {
			t.Fatalf("duplicate id %q", id)
		}
		seen[id] = true
		// The first 48 bits are a millisecond timestamp, so ids never go back in time.
		if previous != "" && id[:13] < previous[:13] {
			t.Fatalf("timestamp went back: %q after %q", id, previous)
		}
		previous = id
	}
}
