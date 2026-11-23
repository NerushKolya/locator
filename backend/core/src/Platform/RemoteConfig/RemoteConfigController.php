<?php

declare(strict_types=1);

namespace App\Platform\RemoteConfig;

use Psr\Log\LoggerInterface;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;

/**
 * GET /v1/config: public remote configuration with a strong ETag and conditional requests.
 */
final class RemoteConfigController
{
    public function __construct(
        private readonly BuiltInRemoteConfig $config,
        private readonly LoggerInterface $logger,
    ) {
    }

    #[Route('/v1/config', name: 'remote_config', methods: ['GET'])]
    public function __invoke(Request $request): Response
    {
        $document = $this->config->toArray();
        $body = json_encode($document, \JSON_THROW_ON_ERROR | \JSON_UNESCAPED_SLASHES);
        // Strong validator: the same document always gives the same tag.
        $etag = '"'.hash('sha256', $body).'"';

        $this->logger->debug('remote configuration served', [
            'event' => 'config.served',
            'requestId' => $request->attributes->get('requestId'),
            'configVersion' => $document['version'],
        ]);

        $response = new JsonResponse($body, Response::HTTP_OK, [], true);
        $response->headers->set('ETag', $etag);
        // Clients keep a copy but revalidate on every use.
        $response->headers->set('Cache-Control', 'no-cache');

        // HttpFoundation compares If-None-Match as RFC 9110 requires (weak comparison, "*", lists)
        // and turns the response into a 304 without a body.
        $response->isNotModified($request);

        return $response;
    }
}
