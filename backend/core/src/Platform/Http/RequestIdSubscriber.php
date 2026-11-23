<?php

declare(strict_types=1);

namespace App\Platform\Http;

use Monolog\Attribute\WithMonologChannel;
use Psr\Log\LoggerInterface;
use Symfony\Component\EventDispatcher\EventSubscriberInterface;
use Symfony\Component\HttpKernel\Event\RequestEvent;
use Symfony\Component\HttpKernel\Event\ResponseEvent;
use Symfony\Component\HttpKernel\Event\TerminateEvent;
use Symfony\Component\HttpKernel\KernelEvents;
use Symfony\Component\Uid\Uuid;

/**
 * Gives every request an id: the client's X-Request-Id when it matches the contract pattern,
 * otherwise a new UUID v7. The id is stored as request attribute "requestId", returned in the
 * response header and written to the request log record (log-records contract, section 1).
 */
final class RequestIdSubscriber implements EventSubscriberInterface
{
    public const ATTRIBUTE = 'requestId';
    public const HEADER = 'X-Request-Id';

    private const PATTERN = '/^[A-Za-z0-9-]{8,64}$/D';
    private const START = '_request_started_at';

    public function __construct(private readonly LoggerInterface $logger)
    {
    }

    public static function getSubscribedEvents(): array
    {
        return [
            KernelEvents::REQUEST => ['onRequest', 4096],
            KernelEvents::RESPONSE => ['onResponse', -4096],
            KernelEvents::TERMINATE => ['onTerminate', 0],
        ];
    }

    public function onRequest(RequestEvent $event): void
    {
        if (!$event->isMainRequest()) {
            return;
        }

        $request = $event->getRequest();
        $sent = $request->headers->get(self::HEADER);
        $id = \is_string($sent) && 1 === preg_match(self::PATTERN, $sent) ? $sent : Uuid::v7()->toRfc4122();

        $request->attributes->set(self::ATTRIBUTE, $id);
        $request->attributes->set(self::START, hrtime(true));
    }

    public function onResponse(ResponseEvent $event): void
    {
        if (!$event->isMainRequest()) {
            return;
        }

        $id = $event->getRequest()->attributes->get(self::ATTRIBUTE);
        if (\is_string($id)) {
            $event->getResponse()->headers->set(self::HEADER, $id);
        }
    }

    public function onTerminate(TerminateEvent $event): void
    {
        $request = $event->getRequest();
        $started = $request->attributes->get(self::START);
        $route = $request->attributes->get('_route');

        // Health checks run every few seconds: debug level only.
        $level = 'health' === $route ? 'debug' : 'info';

        $this->logger->log($level, 'request handled', [
            'event' => 'http.request',
            'requestId' => $request->attributes->get(self::ATTRIBUTE),
            'method' => $request->getMethod(),
            'route' => \is_string($route) ? $route : 'unmatched',
            'status' => $event->getResponse()->getStatusCode(),
            'durationMs' => \is_int($started) ? (int) round((hrtime(true) - $started) / 1_000_000) : null,
        ]);
    }
}
