<?php

declare(strict_types=1);

namespace App\Tests\Support;

use Studio\Gesso\Attribute\OpenApiSpec;
use Studio\Gesso\Spec\OpenApiSpecLoader;
use Studio\Gesso\Symfony\OpenApiAssertions;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;

/**
 * Asserts that a response matches api/openapi.yaml (FR-003). The whole contract is loaded from
 * OPENAPI_CONTRACT_PATH (the repository's api/ directory is mounted read-only in the test container).
 *
 * Validator: Gesso (see docs/quality/testing-strategy.md section 3).
 */
trait AssertsOpenApiResponse
{
    use OpenApiAssertions;

    private const CONTRACT_SPEC = 'openapi';

    protected function openApiSpec(): string
    {
        return self::CONTRACT_SPEC;
    }

    protected function assertResponseMatchesContract(Response $response, string $method, string $path): void
    {
        $contract = $_SERVER['OPENAPI_CONTRACT_PATH'] ?? $_ENV['OPENAPI_CONTRACT_PATH'] ?? getenv('OPENAPI_CONTRACT_PATH');
        if (!\is_string($contract) || !is_file($contract)) {
            self::fail('OPENAPI_CONTRACT_PATH must point to api/openapi.yaml.');
        }

        OpenApiSpecLoader::reset();
        OpenApiSpecLoader::configure(\dirname($contract));

        $this->assertOpenApiResponse($response, Request::create($path, $method));
    }
}
