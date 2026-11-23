// Package requestid gives every HTTP request an id (log-records contract, section 1): the client's
// X-Request-Id when it is well-formed, otherwise a new UUID v7. The id is stored in the request
// context for log records and returned in the response header.
package requestid

import (
	"crypto/rand"
	"encoding/binary"
	"encoding/hex"
	"net/http"
	"regexp"
	"sync"
	"time"

	"github.com/NerushKolya/locator/backend/delivery/internal/logging"
)

// Header is the request and response header that carries the id.
const Header = "X-Request-Id"

var valid = regexp.MustCompile(`^[A-Za-z0-9-]{8,64}$`)

// Middleware sets the request id before calling next.
func Middleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		id := r.Header.Get(Header)
		if !valid.MatchString(id) {
			id = NewV7()
		}
		w.Header().Set(Header, id)
		next.ServeHTTP(w, r.WithContext(logging.ContextWithRequestID(r.Context(), id)))
	})
}

var (
	mu     sync.Mutex
	lastMs int64
)

// NewV7 returns a random UUID version 7 (RFC 9562): a 48-bit Unix millisecond timestamp,
// then random bits. The timestamp never goes back within the process.
func NewV7() string {
	mu.Lock()
	ms := time.Now().UnixMilli()
	if ms < lastMs {
		ms = lastMs
	}
	lastMs = ms
	mu.Unlock()

	var u [16]byte
	if _, err := rand.Read(u[6:]); err != nil {
		panic("requestid: system random source failed: " + err.Error())
	}
	var ts [8]byte
	binary.BigEndian.PutUint64(ts[:], uint64(ms))
	copy(u[0:6], ts[2:8])
	u[6] = (u[6] & 0x0f) | 0x70 // version 7
	u[8] = (u[8] & 0x3f) | 0x80 // variant 10

	var out [36]byte
	hex.Encode(out[0:8], u[0:4])
	out[8] = '-'
	hex.Encode(out[9:13], u[4:6])
	out[13] = '-'
	hex.Encode(out[14:18], u[6:8])
	out[18] = '-'
	hex.Encode(out[19:23], u[8:10])
	out[23] = '-'
	hex.Encode(out[24:36], u[10:16])
	return string(out[:])
}
