<?php

declare(strict_types=1);

namespace App\Tests\Unit\Platform\Http;

use App\Platform\Http\RequestIdProcessor;
use Monolog\Level;
use Monolog\LogRecord;
use PHPUnit\Framework\TestCase;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\RequestStack;

final class RequestIdProcessorTest extends TestCase
{
    private function record(array $context = []): LogRecord
    {
        return new LogRecord(new \DateTimeImmutable(), 'app', Level::Info, 'message', $context);
    }

    public function testAddsTheIdOfTheCurrentRequest(): void
    {
        $stack = new RequestStack();
        $request = new Request();
        $request->attributes->set('requestId', 'client-req-12345');
        $stack->push($request);

        $record = (new RequestIdProcessor($stack))($this->record());

        self::assertSame('client-req-12345', $record->extra['requestId']);
    }

    public function testDoesNothingOutsideARequest(): void
    {
        $record = (new RequestIdProcessor(new RequestStack()))($this->record());

        self::assertArrayNotHasKey('requestId', $record->extra);
    }

    public function testKeepsAnExplicitIdOfTheRecord(): void
    {
        $stack = new RequestStack();
        $request = new Request();
        $request->attributes->set('requestId', 'from-the-stack');
        $stack->push($request);

        $record = (new RequestIdProcessor($stack))($this->record(['requestId' => 'explicit-id-1']));

        self::assertArrayNotHasKey('requestId', $record->extra);
        self::assertSame('explicit-id-1', $record->context['requestId']);
    }
}
