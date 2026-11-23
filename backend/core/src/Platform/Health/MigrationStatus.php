<?php

declare(strict_types=1);

namespace App\Platform\Health;

interface MigrationStatus
{
    /**
     * @throws \Throwable when the state of the migrations cannot be read
     */
    public function hasPending(): bool;
}
