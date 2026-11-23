<?php

declare(strict_types=1);

namespace App\Platform\Http;

use Monolog\LogRecord;
use Monolog\Processor\ProcessorInterface;
use Symfony\Component\HttpFoundation\RequestStack;

/** Adds the id of the current request to every log record written while the request is handled. */
final class RequestIdProcessor implements ProcessorInterface
{
    public function __construct(private readonly RequestStack $requests)
    {
    }

    public function __invoke(LogRecord $record): LogRecord
    {
        $id = $this->requests->getMainRequest()?->attributes->get(RequestIdSubscriber::ATTRIBUTE);
        if (!\is_string($id) || isset($record->context['requestId'])) {
            return $record;
        }

        return $record->with(extra: $record->extra + ['requestId' => $id]);
    }
}
