<?php

declare(strict_types=1);

namespace App\Platform\Health;

/**
 * Readiness of the core (research R-04): the database answers in time and no migration is waiting.
 * Any failure means "down"; the reason is never part of the response.
 */
final class ReadinessProbe
{
    public function __construct(
        private readonly DatabaseProbe $database,
        private readonly MigrationStatus $migrations,
    ) {
    }

    public function check(): HealthStatus
    {
        try {
            $this->database->ping();

            return $this->migrations->hasPending() ? HealthStatus::Down : HealthStatus::Ok;
        } catch (\Throwable) {
            return HealthStatus::Down;
        }
    }
}
