<?php

declare(strict_types=1);

namespace App\Tests\Api;

use App\Platform\Http\ProblemJsonExceptionListener;
use Monolog\Handler\TestHandler;
use PHPUnit\Framework\Attributes\DataProvider;
use Symfony\Bundle\FrameworkBundle\Test\WebTestCase;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\HttpKernel\Exception\HttpException;
use Symfony\Component\HttpKernel\Event\ExceptionEvent;
use Symfony\Component\HttpKernel\HttpKernelInterface;

/**
 * Request id and error format (log-records contract section 1, API guide section 4).
 */
final class RequestIdAndErrorsTest extends WebTestCase
{
    private const UUID_V7 = '/^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/';

    public function testValidRequestIdIsEchoed(): void
    {
        $client = static::createClient();
        $client->request('GET', '/nothing-here', server: ['HTTP_X_REQUEST_ID' => 'client-req-12345']);

        self::assertSame('client-req-12345', $client->getResponse()->headers->get('X-Request-Id'));
    }

    public function testMalformedOrMissingRequestIdIsReplacedByUuidV7(): void
    {
        $client = static::createClient();
        foreach (['short', str_repeat('a', 65), 'has space in it 123', 'bad_chars_12345678', "new\nline12345678", null] as $sent) {
            $server = null === $sent ? [] : ['HTTP_X_REQUEST_ID' => $sent];
            $client->request('GET', '/nothing-here', server: $server);

            $id = $client->getResponse()->headers->get('X-Request-Id');
            self::assertMatchesRegularExpression(self::UUID_V7, (string) $id, 'sent: '.json_encode($sent));
            self::assertNotSame($sent, $id);
        }
    }

    public function testRequestIdAppearsInTheRequestLogRecord(): void
    {
        $client = static::createClient();
        $client->request('GET', '/nothing-here', server: ['HTTP_X_REQUEST_ID' => 'client-req-67890']);

        /** @var TestHandler $handler */
        $handler = $client->getContainer()->get('monolog.handler.main');
        $records = array_values(array_filter(
            $handler->getRecords(),
            static fn ($r): bool => 'http.request' === ($r->context['event'] ?? null),
        ));

        self::assertCount(1, $records);
        self::assertSame('client-req-67890', $records[0]->context['requestId']);
        self::assertSame(404, $records[0]->context['status']);
        self::assertSame('GET', $records[0]->context['method']);
        self::assertArrayHasKey('durationMs', $records[0]->context);
    }

    public function testUnknownPathReturnsProblemJson(): void
    {
        $client = static::createClient();
        $client->request('GET', '/nothing-here', server: ['HTTP_X_REQUEST_ID' => 'client-req-13579']);

        $response = $client->getResponse();
        self::assertSame(404, $response->getStatusCode());
        self::assertStringStartsWith('application/problem+json', (string) $response->headers->get('Content-Type'));

        $body = json_decode((string) $response->getContent(), true, 512, \JSON_THROW_ON_ERROR);
        self::assertSame('not_found', $body['code']);
        self::assertSame(404, $body['status']);
        self::assertSame('client-req-13579', $body['requestId']);
        self::assertArrayHasKey('title', $body);
        // Built from LOCATOR_PUBLIC_BASE_URL, never from the client's Host header.
        self::assertSame('http://localhost:8080/problems/not-found', $body['type']);
    }

    public function testProblemTypeIgnoresTheHostHeader(): void
    {
        $client = static::createClient();
        $client->request('GET', '/nothing-here', server: ['HTTP_HOST' => 'attacker.example']);

        $body = json_decode((string) $client->getResponse()->getContent(), true, 512, \JSON_THROW_ON_ERROR);
        self::assertStringNotContainsString('attacker.example', $body['type']);
    }

    /**
     * @param array<string, string> $headers
     */
    #[DataProvider('httpExceptions')]
    public function testHttpExceptionKeepsItsStatus(HttpException $exception, int $status, string $code, array $headers): void
    {
        $response = $this->handle($exception);

        self::assertSame($status, $response->getStatusCode());
        $body = json_decode((string) $response->getContent(), true, 512, \JSON_THROW_ON_ERROR);
        self::assertSame($code, $body['code']);
        self::assertSame($status, $body['status']);
        foreach ($headers as $name => $value) {
            self::assertSame($value, $response->headers->get($name));
        }
    }

    /** @return iterable<string, array{HttpException, int, string, array<string, string>}> */
    public static function httpExceptions(): iterable
    {
        // Codes of the API guide, section 4.
        yield '401' => [new HttpException(401), 401, 'unauthorized', []];
        yield '403' => [new HttpException(403), 403, 'forbidden', []];
        yield '413' => [new HttpException(413), 413, 'payload_too_large', []];
        yield '422' => [new HttpException(422), 422, 'validation_failed', []];
        yield '429 keeps Retry-After' => [new HttpException(429, '', null, ['Retry-After' => '30']), 429, 'rate_limited', ['Retry-After' => '30']];
        yield '503' => [new HttpException(503), 503, 'unavailable', []];
        // Statuses without a code in the guide keep their status and get a code from the status text.
        yield '415' => [new HttpException(415), 415, 'unsupported_media_type', []];
        yield '502 is an internal error' => [new HttpException(502), 500, 'internal_error', []];
    }

    private function handle(\Throwable $exception): Response
    {
        $client = static::createClient();
        /** @var ProblemJsonExceptionListener $listener */
        $listener = $client->getContainer()->get(ProblemJsonExceptionListener::class);

        $event = new ExceptionEvent($client->getKernel(), Request::create('/x'), HttpKernelInterface::MAIN_REQUEST, $exception);
        $listener($event);

        $response = $event->getResponse();
        self::assertNotNull($response);

        return $response;
    }

    public function testWrongMethodReturnsProblemJson(): void
    {
        $client = static::createClient();
        $client->request('POST', '/health');

        $response = $client->getResponse();
        self::assertSame(405, $response->getStatusCode());
        self::assertSame('method_not_allowed', json_decode((string) $response->getContent(), true)['code']);
    }

    public function testUnhandledExceptionReturnsInternalErrorWithoutDetails(): void
    {
        $client = static::createClient();
        /** @var ProblemJsonExceptionListener $listener */
        $listener = $client->getContainer()->get(ProblemJsonExceptionListener::class);

        $request = Request::create('/boom');
        $request->attributes->set('requestId', 'client-req-11111');
        $event = new ExceptionEvent(
            $client->getKernel(),
            $request,
            HttpKernelInterface::MAIN_REQUEST,
            new \RuntimeException('database password is hunter2'),
        );
        $listener($event);

        $response = $event->getResponse();
        self::assertNotNull($response);
        self::assertSame(500, $response->getStatusCode());
        self::assertStringStartsWith('application/problem+json', (string) $response->headers->get('Content-Type'));
        self::assertStringNotContainsString('hunter2', (string) $response->getContent());
        self::assertStringNotContainsString('RuntimeException', (string) $response->getContent());

        $body = json_decode((string) $response->getContent(), true, 512, \JSON_THROW_ON_ERROR);
        self::assertSame('internal_error', $body['code']);
        self::assertSame(500, $body['status']);
        self::assertSame('client-req-11111', $body['requestId']);
        self::assertArrayNotHasKey('detail', $body);
    }
}
