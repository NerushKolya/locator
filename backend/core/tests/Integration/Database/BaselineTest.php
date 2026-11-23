<?php

declare(strict_types=1);

namespace App\Tests\Integration\Database;

use Doctrine\DBAL\Connection;
use Doctrine\DBAL\Exception;
use Symfony\Bundle\FrameworkBundle\Test\KernelTestCase;

/** Database baseline (data model section 4) as seen by the application role. */
final class BaselineTest extends KernelTestCase
{
    private function appConnection(): Connection
    {
        self::bootKernel();

        /** @var Connection $connection */
        $connection = static::getContainer()->get('doctrine.dbal.default_connection');

        return $connection;
    }

    public function testPostgisExtensionExists(): void
    {
        $count = $this->appConnection()->fetchOne("SELECT count(*) FROM pg_extension WHERE extname = 'postgis'");

        self::assertSame(1, (int) $count);
    }

    public function testApplicationRoleIsCoreApp(): void
    {
        self::assertSame('core_app', $this->appConnection()->fetchOne('SELECT current_user'));
    }

    public function testApplicationRoleCanReadTheMigrationRecord(): void
    {
        $versions = $this->appConnection()->fetchFirstColumn('SELECT version FROM core.doctrine_migration_versions');

        self::assertNotEmpty($versions, 'the baseline migration must be recorded');
    }

    public function testApplicationRoleCannotCreateTablesInSchemaCore(): void
    {
        $connection = $this->appConnection();

        $this->expectException(Exception::class);
        $connection->executeStatement('CREATE TABLE core.created_by_app (id int)');
    }
}
