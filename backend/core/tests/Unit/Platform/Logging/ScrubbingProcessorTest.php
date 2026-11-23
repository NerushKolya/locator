<?php

declare(strict_types=1);

namespace App\Tests\Unit\Platform\Logging;

use App\Platform\Logging\JsonLineFormatter;
use App\Platform\Logging\ScrubbingProcessor;
use Monolog\Handler\StreamHandler;
use Monolog\Level;
use Monolog\Logger;
use PHPUnit\Framework\Attributes\DataProvider;
use PHPUnit\Framework\TestCase;

/**
 * Log-record contract (specs/001-walking-skeleton/contracts/log-records.md): record format (section 1)
 * and the nine scrubbing vectors (section 4).
 */
final class ScrubbingProcessorTest extends TestCase
{
    /** @var resource */
    private $stream;
    private Logger $logger;

    protected function setUp(): void
    {
        $this->stream = fopen('php://memory', 'w+');
        $handler = new StreamHandler($this->stream, Level::Debug);
        $handler->setFormatter(new JsonLineFormatter('core'));
        $this->logger = new Logger('app', [$handler], [new ScrubbingProcessor()]);
    }

    /** @return list<array<string, mixed>> */
    private function written(): array
    {
        rewind($this->stream);
        $lines = array_filter(explode("\n", (string) stream_get_contents($this->stream)));

        return array_values(array_map(static fn (string $l): array => json_decode($l, true, 512, JSON_THROW_ON_ERROR), $lines));
    }

    private function raw(): string
    {
        rewind($this->stream);

        return (string) stream_get_contents($this->stream);
    }

    /**
     * @param array<string, mixed> $context
     * @param list<string>         $forbidden
     * @param list<string>         $kept
     */
    #[DataProvider('vectors')]
    public function testVector(string $message, array $context, array $forbidden, array $kept): void
    {
        $this->logger->info($message, $context);

        $raw = $this->raw();
        foreach ($forbidden as $value) {
            self::assertStringNotContainsString($value, $raw, "forbidden value leaked: $value");
        }
        foreach ($kept as $value) {
            self::assertStringContainsString($value, $raw, "value should stay: $value");
        }
    }

    /** @return iterable<string, array{string, array<string, mixed>, list<string>, list<string>}> */
    public static function vectors(): iterable
    {
        yield '1 coordinates by key' => ['moved', ['lat' => 50.4501, 'lon' => 30.5234], ['50.4501', '30.5234'], []];
        yield '2 coordinates in message' => ['position 50.4501, 30.5234 accepted', [], ['50.4501', '30.5234'], ['[redacted:coords]']];
        yield '3 place name by key' => ['saved', ['placeName' => 'Home'], ['Home'], []];
        yield '4 token by key' => ['signed in', ['accessToken' => 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.c2lnbmF0dXJl'], ['eyJhbGciOiJIUzI1NiJ9', 'c2lnbmF0dXJl'], []];
        yield '5 bearer in message' => ['Authorization: Bearer abc.def.ghi failed', [], ['abc.def.ghi'], ['[redacted:token]', 'failed']];
        yield '6 email by key and in message' => ['sent to person@example.org', ['email' => 'person@example.org'], ['person@example.org'], ['[redacted:email]']];
        yield '7 invitation code by key and in message' => ['invite code K7P2M9QX expired', ['invitationCode' => 'K7P2M9QX'], ['K7P2M9QX'], ['[redacted:invite]', 'expired']];
        yield '8 nested coordinates' => ['reported', ['device' => ['location' => ['lat' => 50.45, 'lon' => 30.52]]], ['50.45', '30.52'], []];
        yield '9 ids and status stay' => ['request done', ['userId' => '0192f3e4-7a55-7c1e-9d2b-3f4a5b6c7d8e', 'status' => 200], [], ['0192f3e4-7a55-7c1e-9d2b-3f4a5b6c7d8e', '200']];
    }

    public function testKeyMatchingIgnoresCaseAndSeparators(): void
    {
        $this->logger->info('keys', ['Access_Token' => 'secret-value-1', 'E-Mail' => 'secret-value-2', 'device.lat' => 'secret-value-3']);

        $raw = $this->raw();
        self::assertStringNotContainsString('secret-value', $raw);
    }

    public function testOrdinaryWordsAfterInvitationKeywordsStay(): void
    {
        // The invitation alphabet is upper case only; lower-case words of 8 letters are not codes.
        $this->logger->info('invitation accepted by member, invite expected soon');

        $raw = $this->raw();
        self::assertStringContainsString('invitation accepted by member, invite expected soon', $raw);
        self::assertStringNotContainsString('[redacted:invite]', $raw);
    }

    public function testInvitationKeywordIsCaseInsensitive(): void
    {
        $this->logger->info('INVITE: K7P2M9QX, Invitation=K7P2M9QX');

        self::assertStringNotContainsString('K7P2M9QX', $this->raw());
    }

    public function testNumbersThatAreNotCoordinatePairsStay(): void
    {
        $this->logger->info('durations 12.500, 300.250 and version 1.2.3', ['count' => 3]);

        $raw = $this->raw();
        self::assertStringContainsString('12.500, 300.250', $raw);
        self::assertStringContainsString('1.2.3', $raw);
    }

    public function testRecordHasContractFields(): void
    {
        $this->logger->warning('config.served', ['requestId' => '0192f3e4-7a55-7c1e-9d2b-3f4a5b6c7d8e', 'event' => 'config.served', 'status' => 200]);

        $records = $this->written();
        self::assertCount(1, $records);
        $record = $records[0];

        self::assertSame('warning', $record['level']);
        self::assertSame('core', $record['service']);
        self::assertSame('config.served', $record['msg']);
        self::assertSame('0192f3e4-7a55-7c1e-9d2b-3f4a5b6c7d8e', $record['requestId']);
        self::assertSame('config.served', $record['event']);
        self::assertSame(200, $record['status']);
        self::assertMatchesRegularExpression('/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/', $record['ts']);
        self::assertSame(['ts', 'level', 'service', 'msg', 'requestId', 'event', 'status'], array_keys($record));
    }

    public function testRequestIdIsOmittedOutsideRequests(): void
    {
        $this->logger->info('migrations done');

        self::assertArrayNotHasKey('requestId', $this->written()[0]);
    }

    public function testMonologLevelsMapToContractLevels(): void
    {
        $this->logger->debug('d');
        $this->logger->notice('n');
        $this->logger->error('e');
        $this->logger->critical('c');

        self::assertSame(['debug', 'info', 'error', 'error'], array_column($this->written(), 'level'));
    }

    public function testInvalidUtf8DoesNotEraseTheRecord(): void
    {
        $this->logger->info("bad \xB1 byte", ['userAgent' => "x\xFFy"]);

        $record = $this->written()[0];
        self::assertStringStartsWith('bad ', $record['msg']);
        self::assertStringStartsWith('x', $record['userAgent']);
    }

    public function testEveryLineIsOneJsonObject(): void
    {
        $this->logger->info("multi\nline message");
        $this->logger->info('second');

        self::assertCount(2, explode("\n", trim($this->raw())));
    }
}
