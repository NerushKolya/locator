<?php

declare(strict_types=1);

namespace App\Platform\Health;

use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\Routing\Attribute\Route;

/** GET /health: `200 {"status":"ok"}` when ready, `503 {"status":"down"}` otherwise; no other field. */
final class HealthController
{
    public function __construct(private readonly ReadinessProbe $probe)
    {
    }

    #[Route('/health', name: 'health', methods: ['GET'])]
    public function __invoke(): JsonResponse
    {
        $status = $this->probe->check();

        $response = new JsonResponse(['status' => $status->value], $status->httpStatus());
        $response->headers->set('Cache-Control', 'no-store');

        return $response;
    }
}
