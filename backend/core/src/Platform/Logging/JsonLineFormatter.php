<?php

declare(strict_types=1);

namespace App\Platform\Logging;

use Monolog\Formatter\FormatterInterface;
use Monolog\Level;
use Monolog\LogRecord;

/**
 * One JSON object per line with the fields of the log-records contract, section 1:
 * ts, level, service, msg, requestId (in requests), event, then the remaining context fields.
 */
final class JsonLineFormatter implements FormatterInterface
{
    private const RESERVED = ['ts', 'level', 'service', 'msg'];

    public function __construct(private readonly string $service)
    {
    }

    public function format(LogRecord $record): string
    {
        $fields = [
            'ts' => $record->datetime->setTimezone(new \DateTimeZone('UTC'))->format('Y-m-d\TH:i:s.v\Z'),
            'level' => $this->level($record->level),
            'service' => $this->service,
            'msg' => $record->message,
        ];

        $values = $record->context + $record->extra;
        foreach (['requestId', 'event'] as $name) {
            if (isset($values[$name])) {
                $fields[$name] = $values[$name];
            }
            unset($values[$name]);
        }
        foreach ($values as $name => $value) {
            if (!\in_array($name, self::RESERVED, true)) {
                $fields[$name] = $value;
            }
        }

        // Invalid UTF-8 (for example from a header) is replaced by U+FFFD instead of erasing the value.
        return json_encode(
            $fields,
            \JSON_UNESCAPED_SLASHES | \JSON_UNESCAPED_UNICODE | \JSON_INVALID_UTF8_SUBSTITUTE | \JSON_PARTIAL_OUTPUT_ON_ERROR,
        )."\n";
    }

    public function formatBatch(array $records): string
    {
        return implode('', array_map($this->format(...), $records));
    }

    private function level(Level $level): string
    {
        return match (true) {
            $level->value >= Level::Error->value => 'error',
            $level->value >= Level::Warning->value => 'warning',
            $level->value >= Level::Info->value => 'info',
            default => 'debug',
        };
    }
}
