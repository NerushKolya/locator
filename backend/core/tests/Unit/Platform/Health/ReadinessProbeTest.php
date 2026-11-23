<?php

declare(strict_types=1);

namespace App\Tests\Unit\Platform\Health;

use App\Platform\Health\DatabaseProbe;
use App\Platform\Health\HealthStatus;
use App\Platform\Health\MigrationStatus;
use App\Platform\Health\ReadinessProbe;
use PHPUnit\Framework\TestCase;

/** Readiness of the core (research R-04): database answers and no migration is waiting. */
final class ReadinessProbeTest extends TestCase
{
    public function testDatabaseErrorMeansDown(): void
    {
        $probe = new ReadinessProbe(
            $this->database(new \RuntimeException('connection refused')),
            $this->migrations(false),
        );

        self::assertSame(HealthStatus::Down, $probe->check());
    }

    public function testDatabaseTimeoutMeansDown(): void
    {
        $probe = new ReadinessProbe(
            $this->database(new \RuntimeException('canceling statement due to statement timeout')),
            $this->migrations(false),
        );

        self::assertSame(HealthStatus::Down, $probe->check());
    }

    public function testUnexecutedMigrationMeansDown(): void
    {
        $probe = new ReadinessProbe($this->database(null), $this->migrations(true));

        self::assertSame(HealthStatus::Down, $probe->check());
    }

    public function testUnreadableMigrationStateMeansDown(): void
    {
        $probe = new ReadinessProbe($this->database(null), $this->migrations(new \RuntimeException('relation does not exist')));

        self::assertSame(HealthStatus::Down, $probe->check());
    }

    public function testDatabaseOkAndNothingPendingMeansOk(): void
    {
        $probe = new ReadinessProbe($this->database(null), $this->migrations(false));

        self::assertSame(HealthStatus::Ok, $probe->check());
    }

    public function testMigrationsAreNotCheckedWhenTheDatabaseIsDown(): void
    {
        $migrations = $this->migrations(new \LogicException('must not be called'));
        $probe = new ReadinessProbe($this->database(new \RuntimeException('down')), $migrations);

        self::assertSame(HealthStatus::Down, $probe->check());
    }

    public function testStatusValuesMatchTheContract(): void
    {
        self::assertSame('ok', HealthStatus::Ok->value);
        self::assertSame('down', HealthStatus::Down->value);
        self::assertSame(200, HealthStatus::Ok->httpStatus());
        self::assertSame(503, HealthStatus::Down->httpStatus());
    }

    private function database(?\Throwable $failure): DatabaseProbe
    {
        return new readonly class($failure) implements DatabaseProbe {
            public function __construct(private ?\Throwable $failure)
            {
            }

            public function ping(): void
            {
                if (null !== $this->failure) {
                    throw $this->failure;
                }
            }
        };
    }

    private function migrations(bool|\Throwable $pending): MigrationStatus
    {
        return new readonly class($pending) implements MigrationStatus {
            public function __construct(private bool|\Throwable $pending)
            {
            }

            public function hasPending(): bool
            {
                if ($this->pending instanceof \Throwable) {
                    throw $this->pending;
                }

                return $this->pending;
            }
        };
    }
}
