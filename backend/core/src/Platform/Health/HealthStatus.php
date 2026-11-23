<?php

declare(strict_types=1);

namespace App\Platform\Health;

/** Values of the `Health` schema in the contract that the core produces ("degraded" is reserved). */
enum HealthStatus: string
{
    case Ok = 'ok';
    case Down = 'down';

    public function httpStatus(): int
    {
        return match ($this) {
            self::Ok => 200,
            self::Down => 503,
        };
    }
}
