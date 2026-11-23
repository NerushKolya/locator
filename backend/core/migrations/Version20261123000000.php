<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

/**
 * Baseline: asserts that the database image provides PostGIS (ADR-020). Creates no tables.
 */
final class Version20261123000000 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Baseline: PostGIS extension must exist';
    }

    public function up(Schema $schema): void
    {
        $this->abortIf(
            false === $this->connection->fetchOne("SELECT 1 FROM pg_extension WHERE extname = 'postgis'"),
            'PostGIS extension missing: use the project database image (infra/postgres).',
        );
    }

    public function down(Schema $schema): void
    {
        $this->throwIrreversibleMigrationException('The baseline cannot be reverted.');
    }
}
