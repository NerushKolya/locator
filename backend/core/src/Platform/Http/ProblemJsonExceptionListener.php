<?php

declare(strict_types=1);

namespace App\Platform\Http;

use Psr\Log\LoggerInterface;
use Symfony\Component\DependencyInjection\Attribute\Autowire;
use Symfony\Component\EventDispatcher\Attribute\AsEventListener;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\HttpKernel\Event\ExceptionEvent;
use Symfony\Component\HttpKernel\Exception\HttpExceptionInterface;

/**
 * Turns every exception into an application/problem+json response (RFC 9457, API guide section 4).
 * An HTTP exception keeps its status; anything else is "internal_error" without details, and the
 * details go to the log.
 */
#[AsEventListener(event: 'kernel.exception', priority: 128)]
final class ProblemJsonExceptionListener
{
    /** Codes of the API guide, section 4, where they differ from the status text. */
    private const CODES = [
        413 => 'payload_too_large',
        422 => 'validation_failed',
        429 => 'rate_limited',
        500 => 'internal_error',
        503 => 'unavailable',
    ];

    private readonly string $problemBaseUrl;

    public function __construct(
        private readonly LoggerInterface $logger,
        #[Autowire(env: 'LOCATOR_PUBLIC_BASE_URL')]
        string $publicBaseUrl,
    ) {
        // The type URI never takes the client's Host header.
        $this->problemBaseUrl = rtrim($publicBaseUrl, '/').'/problems/';
    }

    public function __invoke(ExceptionEvent $event): void
    {
        $exception = $event->getThrowable();
        $requestId = $event->getRequest()->attributes->get(RequestIdSubscriber::ATTRIBUTE);

        $status = Response::HTTP_INTERNAL_SERVER_ERROR;
        $headers = [];
        if ($exception instanceof HttpExceptionInterface && $this->isPublicStatus($exception->getStatusCode())) {
            $status = $exception->getStatusCode();
            $headers = $exception->getHeaders();
        }

        if (Response::HTTP_INTERNAL_SERVER_ERROR === $status) {
            $this->logger->error('unhandled exception', [
                'event' => 'http.error',
                'requestId' => $requestId,
                'exception' => $exception,
            ]);
        }

        $title = Response::$statusTexts[$status];
        $code = self::CODES[$status] ?? strtolower((string) preg_replace('/\W+/', '_', $title));
        $body = [
            'type' => $this->problemBaseUrl.str_replace('_', '-', $code),
            'title' => $title,
            'status' => $status,
            'code' => $code,
        ];
        if (\is_string($requestId)) {
            $body['requestId'] = $requestId;
        }

        $response = new JsonResponse($body, $status, $headers);
        $response->headers->set('Content-Type', 'application/problem+json');
        $event->setResponse($response);
    }

    /** Client errors and "503 unavailable" are shown as they are; any other server error is an internal error. */
    private function isPublicStatus(int $status): bool
    {
        return isset(Response::$statusTexts[$status])
            && (($status >= 400 && $status < 500) || Response::HTTP_SERVICE_UNAVAILABLE === $status);
    }
}
