<?php

declare(strict_types=1);

namespace App\Tests\Support;

use PHPUnit\Framework\AssertionFailedError;
use PHPUnit\Framework\TestCase;
use Symfony\Component\HttpFoundation\JsonResponse;

/** Self-test of the contract assertion: it must accept right bodies and reject wrong ones. */
final class AssertsOpenApiResponseTest extends TestCase
{
    use AssertsOpenApiResponse;

    public function testAcceptsAValidHealthBody(): void
    {
        $this->assertResponseMatchesContract(new JsonResponse(['status' => 'ok']), 'GET', '/health');
        $this->assertResponseMatchesContract(new JsonResponse(['status' => 'down'], 503), 'GET', '/health');
    }

    public function testRejectsAWrongHealthBody(): void
    {
        $this->expectException(AssertionFailedError::class);

        $this->assertResponseMatchesContract(new JsonResponse(['status' => 'fine']), 'GET', '/health');
    }

    public function testRejectsAHealthBodyWithoutStatus(): void
    {
        $this->expectException(AssertionFailedError::class);

        $this->assertResponseMatchesContract(new JsonResponse([]), 'GET', '/health');
    }

    public function testRejectsAConfigWithoutRequiredField(): void
    {
        $this->expectException(AssertionFailedError::class);

        $this->assertResponseMatchesContract(new JsonResponse(['version' => 1]), 'GET', '/v1/config');
    }
}
