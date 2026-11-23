// Package health serves GET /health. In this feature the delivery service is ready while the
// process serves HTTP, so the answer is always 200 {"status":"ok"} (contract schema Health).
package health

import "net/http"

// Route is the pattern of the health operation, also used as the logged route.
const Route = "GET /health"

var body = []byte(`{"status":"ok"}`)

// Register adds the health operation to mux.
func Register(mux *http.ServeMux) {
	mux.HandleFunc(Route, func(w http.ResponseWriter, _ *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.Header().Set("Cache-Control", "no-store")
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write(body)
	})
}
