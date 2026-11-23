<?php

declare(strict_types=1);

namespace App\Tests\Integration\Platform\Health;

use App\Tests\Support\AssertsOpenApiResponse;
use Symfony\Bundle\FrameworkBundle\Test\WebTestCase;

/** GET /health against postgres-test (migrations are applied by the test container before PHPUnit starts). */
final class HealthIntegrationTest extends WebTestCase
{
    use AssertsOpenApiResponse;

    private ?string $originalPort = null;

    protected function tearDown(): void
    {
        if (null !== $this->originalPort) {
            $_SERVER['DB_PORT'] = $_ENV['DB_PORT'] = $this->originalPort;
            putenv('DB_PORT='.$this->originalPort);
        }
        parent::tearDown();
    }

    public function testReadyAfterMigrations(): void
    {
        $client = static::createClient();
        $client->request('GET', '/health');

        $response = $client->getResponse();
        self::assertSame(200, $response->getStatusCode());
        self::assertSame(['status' => 'ok'], json_decode((string) $response->getContent(), true, 512, \JSON_THROW_ON_ERROR));
        $this->assertResponseMatchesContract($response, 'GET', '/health');
    }

    public function testDownWhenTheDatabaseIsUnreachable(): void
    {
        $this->originalPort = (string) ($_SERVER['DB_PORT'] ?? getenv('DB_PORT'));
        $_SERVER['DB_PORT'] = $_ENV['DB_PORT'] = '1';
        putenv('DB_PORT=1');

        $client = static::createClient();
        $started = microtime(true);
        $client->request('GET', '/health');

        $response = $client->getResponse();
        self::assertSame(503, $response->getStatusCode());
        self::assertSame(['status' => 'down'], json_decode((string) $response->getContent(), true, 512, \JSON_THROW_ON_ERROR));
        $this->assertResponseMatchesContract($response, 'GET', '/health');
        self::assertLessThan(3.0, microtime(true) - $started, 'the probe must answer quickly');
    }
}
