<?php

declare(strict_types=1);

namespace App\Platform\Health;

interface DatabaseProbe
{
    /** @throws \Throwable when the database does not answer correctly and in time */
    public function ping(): void;
}
