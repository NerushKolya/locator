<?php

declare(strict_types=1);

namespace App\Platform\Logging;

use Monolog\LogRecord;
use Monolog\Processor\ProcessorInterface;

/**
 * Removes personal location data and secrets from every log record before it is written
 * (log-records contract, section 2; constitution principle V).
 *
 * Layer 1 replaces the value of a sensitive key with "[redacted]". Layer 2 replaces sensitive
 * patterns inside any string with "[redacted:<kind>]".
 */
final class ScrubbingProcessor implements ProcessorInterface
{
    /** Keys lower-cased and without "_" and "-" (see normalizeKey()). */
    private const SENSITIVE_KEYS = [
        // coordinates
        'lat', 'lon', 'lng', 'latitude', 'longitude', 'coordinates', 'location', 'position', 'accuracym',
        // place names
        'placename', 'place', 'address',
        // tokens and secrets
        'token', 'accesstoken', 'refreshtoken', 'idtoken', 'pushtoken', 'ticket', 'authorization',
        'password', 'secret', 'apikey', 'nonce',
        // e-mail addresses
        'email', 'mail',
        // invitation codes
        'code', 'invitationcode', 'invitecode',
    ];

    private const REDACTED = '[redacted]';
    private const MAX_DEPTH = 8;

    // Invitation alphabet: 8 characters without 0, 1, I, L, O, W (design system, invitation codes).
    private const INVITE_CODE = '[ABCDEFGHJKMNPQRSTUVXYZ2-9]{8}';

    public function __invoke(LogRecord $record): LogRecord
    {
        return $record->with(
            message: $this->scrubString($record->message),
            context: $this->scrubArray($record->context, 0),
            extra: $this->scrubArray($record->extra, 0),
        );
    }

    /**
     * @param array<array-key, mixed> $values
     *
     * @return array<array-key, mixed>
     */
    private function scrubArray(array $values, int $depth): array
    {
        $result = [];
        foreach ($values as $key => $value) {
            $result[$key] = \is_string($key) && $this->isSensitiveKey($key)
                ? self::REDACTED
                : $this->scrubValue($value, $depth + 1);
        }

        return $result;
    }

    private function scrubValue(mixed $value, int $depth): mixed
    {
        if ($depth > self::MAX_DEPTH) {
            return self::REDACTED;
        }

        return match (true) {
            \is_string($value) => $this->scrubString($value),
            \is_array($value) => $this->scrubArray($value, $depth),
            $value instanceof \Throwable => ['class' => $value::class, 'message' => $this->scrubString($value->getMessage())],
            $value instanceof \Stringable => $this->scrubString((string) $value),
            \is_object($value) => $value::class,
            \is_resource($value) => 'resource',
            default => $value, // int, float, bool, null
        };
    }

    private function isSensitiveKey(string $key): bool
    {
        $dot = strrpos($key, '.');
        $last = false === $dot ? $key : substr($key, $dot + 1);

        return \in_array($this->normalizeKey($last), self::SENSITIVE_KEYS, true);
    }

    private function normalizeKey(string $key): string
    {
        return strtolower(str_replace(['_', '-'], '', $key));
    }

    private function scrubString(string $text): string
    {
        // token: JWT-like value with two dots, or "Bearer <value>"
        $text = (string) preg_replace('/eyJ[\w-]+\.[\w-]+\.[\w-]+/', '[redacted:token]', $text);
        $text = (string) preg_replace('/\bBearer\s+\S+/i', '[redacted:token]', $text);

        // email
        $text = (string) preg_replace('/[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}/', '[redacted:email]', $text);

        // invite: the code itself directly after "code", "invite" or "invitation" and ":", "=" or a space.
        // Only the keyword ignores case; the code alphabet is upper case, so "invitation accepted" stays.
        $text = (string) preg_replace(
            '/\b((?i:code|invite|invitation))([:=\s]\s*)'.self::INVITE_CODE.'\b/',
            '$1$2[redacted:invite]',
            $text,
        );

        // coords: two decimals with at least 3 fraction digits, both within +-180
        return (string) preg_replace_callback(
            '/(?<![\d.])(-?\d{1,3}\.\d{3,})\s*,\s*(-?\d{1,3}\.\d{3,})(?!\d)/',
            static fn (array $m): string => abs((float) $m[1]) <= 180.0 && abs((float) $m[2]) <= 180.0
                ? '[redacted:coords]'
                : $m[0],
            $text,
        );
    }
}
