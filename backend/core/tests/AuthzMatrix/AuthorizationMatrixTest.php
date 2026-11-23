<?php

declare(strict_types=1);

namespace App\Tests\AuthzMatrix;

use PHPUnit\Framework\Attributes\DataProvider;
use PHPUnit\Framework\Attributes\Group;
use Symfony\Bundle\FrameworkBundle\Test\WebTestCase;

/**
 * Authorization matrix of the core (testing strategy section 3): one row per endpoint and caller,
 * each row is one test. Required CI job `authz-matrix`.
 *
 * Row format: [method, path, caller, expected HTTP status]
 *   - method:   HTTP method, upper case
 *   - path:     request path; use a concrete path (fill identifiers with the fixtures of the feature)
 *   - caller:   a key of CALLERS describing who sends the request; add a caller together with the
 *               feature that introduces it (non-member, member not sharing, paused member, removed member,
 *               other group's admin, operator with OPERATOR_MODE on/off, `store` device)
 *   - status:   the status the contract requires for this caller (404/403 for a forbidden caller)
 *
 * A feature that adds an endpoint adds its rows here in the same change; an endpoint without rows fails review.
 */
#[Group('authz-matrix')]
final class AuthorizationMatrixTest extends WebTestCase
{
    /** Request headers per caller. */
    private const CALLERS = [
        'anonymous' => [],
    ];

    /** @return iterable<string, array{string, string, string, int}> */
    public static function rows(): iterable
    {
        yield 'GET /health anonymous' => ['GET', '/health', 'anonymous', 200];
        yield 'GET /v1/config anonymous' => ['GET', '/v1/config', 'anonymous', 200];
    }

    #[DataProvider('rows')]
    public function testRow(string $method, string $path, string $caller, int $expectedStatus): void
    {
        self::assertArrayHasKey($caller, self::CALLERS, "unknown caller $caller");

        $client = static::createClient();
        $client->request($method, $path, server: self::CALLERS[$caller]);

        self::assertSame($expectedStatus, $client->getResponse()->getStatusCode());
    }
}
