<?php

declare(strict_types=1);

namespace App\Tests\Api;

use App\Tests\Support\AssertsOpenApiResponse;
use Symfony\Bundle\FrameworkBundle\Test\WebTestCase;

/** GET /v1/config: public, contract-valid, cached with a strong ETag (data model section 1.2). */
final class RemoteConfigApiTest extends WebTestCase
{
    use AssertsOpenApiResponse;

    public function testAnonymousRequestReturnsTheDocument(): void
    {
        $client = static::createClient();
        $client->request('GET', '/v1/config');

        $response = $client->getResponse();
        self::assertSame(200, $response->getStatusCode());
        self::assertStringStartsWith('application/json', (string) $response->headers->get('Content-Type'));
        $this->assertResponseMatchesContract($response, 'GET', '/v1/config');

        $body = json_decode((string) $response->getContent(), true, 512, \JSON_THROW_ON_ERROR);
        self::assertSame(1, $body['version']);
        self::assertSame('http://localhost:8080/terms', $body['legal']['termsUrl']);
    }

    public function testEtagIsStrongQuotedHashOfTheBodyAndStable(): void
    {
        $client = static::createClient();
        $client->request('GET', '/v1/config');
        $first = $client->getResponse();
        $etag = (string) $first->headers->get('ETag');

        self::assertMatchesRegularExpression('/^"[0-9a-f]{16,}"$/', $etag, 'strong (no W/ prefix), quoted');
        self::assertSame('"'.hash('sha256', (string) $first->getContent()).'"', $etag);

        $client->request('GET', '/v1/config');
        self::assertSame($etag, $client->getResponse()->headers->get('ETag'));
    }

    public function testMatchingIfNoneMatchReturnsNotModifiedWithoutBody(): void
    {
        $client = static::createClient();
        $client->request('GET', '/v1/config');
        $etag = (string) $client->getResponse()->headers->get('ETag');

        $client->request('GET', '/v1/config', server: ['HTTP_IF_NONE_MATCH' => $etag]);

        $response = $client->getResponse();
        self::assertSame(304, $response->getStatusCode());
        self::assertSame('', (string) $response->getContent());
        self::assertSame($etag, $response->headers->get('ETag'));
    }

    /**
     * If-None-Match uses the weak comparison (RFC 9110, section 13.1.2): W/"x" matches "x";
     * "*" matches any current document; a list matches when one of its tags does.
     */
    public function testIfNoneMatchFollowsRfc9110(): void
    {
        $client = static::createClient();
        $client->request('GET', '/v1/config');
        $etag = (string) $client->getResponse()->headers->get('ETag');

        foreach (['W/'.$etag, '*', '"other", '.$etag] as $sent) {
            $client->request('GET', '/v1/config', server: ['HTTP_IF_NONE_MATCH' => $sent]);
            self::assertSame(304, $client->getResponse()->getStatusCode(), 'If-None-Match: '.$sent);
        }
    }

    public function testOtherIfNoneMatchReturnsTheDocument(): void
    {
        $client = static::createClient();
        $client->request('GET', '/v1/config', server: ['HTTP_IF_NONE_MATCH' => '"something-else"']);

        $response = $client->getResponse();
        self::assertSame(200, $response->getStatusCode());
        self::assertNotSame('', (string) $response->getContent());
    }

    public function testClientsMustRevalidate(): void
    {
        $client = static::createClient();
        $client->request('GET', '/v1/config');

        $cacheControl = (string) $client->getResponse()->headers->get('Cache-Control');
        self::assertStringContainsString('no-cache', $cacheControl);
    }

    public function testRequestIdHeaderIsPresent(): void
    {
        $client = static::createClient();
        $client->request('GET', '/v1/config');

        self::assertNotEmpty($client->getResponse()->headers->get('X-Request-Id'));
    }
}
