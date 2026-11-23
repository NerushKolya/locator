<?php

declare(strict_types=1);

namespace App\Platform\Health;

use Doctrine\DBAL\Connection;
use Doctrine\Migrations\DependencyFactory;
use Symfony\Component\DependencyInjection\Attribute\Autowire;

/**
 * Compares the migrations in the code with the ones recorded in the database. The record is read through
 * the application connection (role core_app, SELECT only); the migrations connection is used only by the migrate command.
 */
final class DoctrineMigrationStatus implements MigrationStatus
{
    public function __construct(
        #[Autowire(service: 'doctrine.migrations.dependency_factory')]
        private readonly DependencyFactory $migrations,
        #[Autowire(service: 'doctrine.dbal.default_connection')]
        private readonly Connection $connection,
    ) {
    }

    public function hasPending(): bool
    {
        $available = array_map(
            static fn ($migration): string => (string) $migration->getVersion(),
            $this->migrations->getMigrationRepository()->getMigrations()->getItems(),
        );

        $executed = $this->connection->transactional(function (Connection $connection): array {
            $connection->executeStatement("SET LOCAL statement_timeout = '1000ms'");

            return $connection->fetchFirstColumn('SELECT version FROM core.doctrine_migration_versions');
        });

        return [] !== array_diff($available, $executed);
    }
}
