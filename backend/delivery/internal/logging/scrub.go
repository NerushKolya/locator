package logging

import (
	"fmt"
	"log/slog"
	"math"
	"reflect"
	"regexp"
	"strconv"
	"strings"
)

// Scrubbing rules of the log-records contract, section 2 (constitution principle V).
// Layer 1 replaces the value of a sensitive key with "[redacted]". Layer 2 replaces sensitive
// patterns inside any string with "[redacted:<kind>]". The same rules and test vectors are used
// by the core service and the app.

const (
	redacted = "[redacted]"
	maxDepth = 8
)

// sensitiveKeys are lower-cased and without "_" and "-" (see normalizeKey).
var sensitiveKeys = map[string]bool{
	// coordinates
	"lat": true, "lon": true, "lng": true, "latitude": true, "longitude": true,
	"coordinates": true, "location": true, "position": true, "accuracym": true,
	// place names
	"placename": true, "place": true, "address": true,
	// tokens and secrets
	"token": true, "accesstoken": true, "refreshtoken": true, "idtoken": true, "pushtoken": true,
	"ticket": true, "authorization": true, "password": true, "secret": true, "apikey": true, "nonce": true,
	// e-mail addresses
	"email": true, "mail": true,
	// invitation codes
	"code": true, "invitationcode": true, "invitecode": true,
}

var (
	jwtPattern    = regexp.MustCompile(`eyJ[\w-]+\.[\w-]+\.[\w-]+`)
	bearerPattern = regexp.MustCompile(`(?i)\bBearer\s+\S+`)
	emailPattern  = regexp.MustCompile(`[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}`)
	// Only the keyword ignores case; the invitation alphabet (8 characters without 0, 1, I, L, O, W)
	// is upper case, so ordinary words such as "invitation accepted" stay.
	invitePattern = regexp.MustCompile(`\b((?i:code|invite|invitation))([:=\s]\s*)[ABCDEFGHJKMNPQRSTUVXYZ2-9]{8}\b`)
	// Two decimals with at least 3 fraction digits. RE2 has no look-behind, so the character before the
	// first number is matched by group 1 and kept; the greedy fraction digits make a look-ahead unnecessary.
	coordsPattern = regexp.MustCompile(`(^|[^\d.])(-?\d{1,3}\.\d{3,})\s*,\s*(-?\d{1,3}\.\d{3,})`)
)

func normalizeKey(key string) string {
	return strings.ToLower(strings.NewReplacer("_", "", "-", "").Replace(key))
}

// isSensitiveKey matches the last segment of a dotted key, case- and separator-insensitive.
func isSensitiveKey(key string) bool {
	if dot := strings.LastIndexByte(key, '.'); dot >= 0 {
		key = key[dot+1:]
	}
	return sensitiveKeys[normalizeKey(key)]
}

func scrubAttr(a slog.Attr, depth int) slog.Attr {
	if isSensitiveKey(a.Key) {
		return slog.String(a.Key, redacted)
	}
	return slog.Attr{Key: a.Key, Value: scrubValue(a.Value, depth+1)}
}

func scrubValue(v slog.Value, depth int) slog.Value {
	if depth > maxDepth {
		return slog.StringValue(redacted)
	}
	v = v.Resolve()
	switch v.Kind() {
	case slog.KindString:
		return slog.StringValue(scrubString(v.String()))
	case slog.KindGroup:
		group := v.Group()
		out := make([]slog.Attr, 0, len(group))
		for _, a := range group {
			out = append(out, scrubAttr(a, depth))
		}
		return slog.GroupValue(out...)
	case slog.KindAny:
		return slog.AnyValue(scrubAny(v.Any(), depth))
	default: // numbers, bool, duration, time
		return v
	}
}

// scrubAny handles values passed with slog.Any. Maps and slices are walked; errors and Stringers
// become scrubbed strings; any other type is replaced by its type name so that unknown struct
// fields never reach the JSON encoder.
func scrubAny(value any, depth int) any {
	if depth > maxDepth {
		return redacted
	}
	switch x := value.(type) {
	case nil, bool, int, int8, int16, int32, int64, uint, uint8, uint16, uint32, uint64, float32, float64:
		return x
	case string:
		return scrubString(x)
	case error:
		return scrubString(x.Error())
	case fmt.Stringer:
		return scrubString(x.String())
	case map[string]any:
		out := make(map[string]any, len(x))
		for k, item := range x {
			if isSensitiveKey(k) {
				out[k] = redacted
			} else {
				out[k] = scrubAny(item, depth+1)
			}
		}
		return out
	case map[string]string:
		out := make(map[string]any, len(x))
		for k, item := range x {
			if isSensitiveKey(k) {
				out[k] = redacted
			} else {
				out[k] = scrubString(item)
			}
		}
		return out
	case []any:
		out := make([]any, 0, len(x))
		for _, item := range x {
			out = append(out, scrubAny(item, depth+1))
		}
		return out
	case []string:
		out := make([]any, 0, len(x))
		for _, item := range x {
			out = append(out, scrubString(item))
		}
		return out
	default:
		return reflect.TypeOf(value).String()
	}
}

func scrubString(text string) string {
	text = jwtPattern.ReplaceAllString(text, "[redacted:token]")
	text = bearerPattern.ReplaceAllString(text, "[redacted:token]")
	text = emailPattern.ReplaceAllString(text, "[redacted:email]")
	text = invitePattern.ReplaceAllString(text, "${1}${2}[redacted:invite]")
	return scrubCoords(text)
}

// scrubCoords replaces coordinate pairs whose numbers are both within +-180.
func scrubCoords(text string) string {
	matches := coordsPattern.FindAllStringSubmatchIndex(text, -1)
	if matches == nil {
		return text
	}
	var b strings.Builder
	last := 0
	for _, m := range matches {
		// m[2]:m[3] is the kept prefix character, m[4]:m[5] and m[6]:m[7] are the two numbers.
		if !withinRange(text[m[4]:m[5]]) || !withinRange(text[m[6]:m[7]]) {
			continue
		}
		b.WriteString(text[last:m[3]])
		b.WriteString("[redacted:coords]")
		last = m[1]
	}
	b.WriteString(text[last:])
	return b.String()
}

func withinRange(number string) bool {
	f, err := strconv.ParseFloat(number, 64)
	return err == nil && math.Abs(f) <= 180
}
