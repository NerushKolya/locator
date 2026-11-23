package server

import (
	"encoding/json"
	"net/http"

	"github.com/NerushKolya/locator/backend/delivery/internal/requestid"
)

// catchAll is the pattern of the fallback route; the request log reports it as "unmatched".
const catchAll = "/"

// problem is the error body of the contract (schema Problem, RFC 9457). The service is not
// published to clients yet, so "type" is "about:blank", which RFC 9457 defines for problems
// that carry no more meaning than the HTTP status.
type problem struct {
	Type      string `json:"type"`
	Title     string `json:"title"`
	Status    int    `json:"status"`
	Code      string `json:"code"`
	RequestID string `json:"requestId,omitempty"`
}

// notFound answers every request no other route accepts. It never echoes the path or the method.
func notFound(w http.ResponseWriter, r *http.Request) {
	body, _ := json.Marshal(problem{
		Type:      "about:blank",
		Title:     http.StatusText(http.StatusNotFound),
		Status:    http.StatusNotFound,
		Code:      "not_found",
		RequestID: w.Header().Get(requestid.Header),
	})
	w.Header().Set("Content-Type", "application/problem+json")
	w.Header().Set("Cache-Control", "no-store")
	w.WriteHeader(http.StatusNotFound)
	_, _ = w.Write(body)
}
