<?php

declare(strict_types=1);

namespace App\Platform\Health;

use Doctrine\DBAL\Connection;
use Symfony\Component\DependencyInjection\Attribute\Autowire;

/** `SELECT 1` with a statement timeout of about one second, on the application connection (role core_app). */
final class DbalDatabaseProbe implements DatabaseProbe
{
    public function __construct(
        #[Autowire(service: 'doctrine.dbal.default_connection')]
        private readonly Connection $connection,
    ) {
    }

    public function ping(): void
    {
        $this->connection->transactional(function (Connection $connection): void {
            $connection->executeStatement("SET LOCAL statement_timeout = '1000ms'");
            $connection->fetchOne('SELECT 1');
        });
    }
}
