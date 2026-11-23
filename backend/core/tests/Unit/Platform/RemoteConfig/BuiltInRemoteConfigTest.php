<?php

declare(strict_types=1);

namespace App\Tests\Unit\Platform\RemoteConfig;

use App\Platform\RemoteConfig\BuiltInRemoteConfig;
use PHPUnit\Framework\Attributes\DataProvider;
use PHPUnit\Framework\TestCase;

/** Built-in defaults of the remote configuration (data model sections 1 and 1.1). */
final class BuiltInRemoteConfigTest extends TestCase
{
    public function testDefaults(): void
    {
        $config = (new BuiltInRemoteConfig('http://localhost:8080', 'off'))->toArray();

        self::assertSame(1, $config['version']);
        self::assertSame('https://tiles.openfreemap.org/styles/liberty', $config['mapStyleUrl']);
        self::assertFalse($config['operatorMode']);
    }

    public function testLimits(): void
    {
        $limits = (new BuiltInRemoteConfig('http://localhost:8080', 'off'))->toArray()['limits'];

        self::assertSame([
            'maxGroupMembers' => 20,
            'maxGroupsPerUser' => 10,
            'maxPlacesPerGroup' => 20,
            'placeRadiusMinM' => 100,
            'placeRadiusMaxM' => 1000,
            'invitationTtlH' => 72,
            'sosDurationS' => 900,
            'sosHoldMs' => 2000,
            'lowBatteryPct' => 15,
        ], $limits);
    }

    public function testEngineKeys(): void
    {
        $engine = (new BuiltInRemoteConfig('http://localhost:8080', 'off'))->toArray()['engine'];

        self::assertSame([
            'stationaryAfterS' => 300,
            'stationaryHeartbeatS' => 900,
            'anchorRadiusM' => 150,
            'footIntervalS' => 60,
            'footMinDisplacementM' => 20,
            'vehicleSpeedMps' => 3,
            'vehicleIntervalS' => 30,
            'activeViewIntervalS' => 10,
            'activeViewLeaseS' => 120,
            'activeViewMaxS' => 900,
            'activeViewCooldownS' => 300,
            'sosIntervalS' => 5,
            'lowBatteryIntervalFactor' => 2,
        ], $engine);
        self::assertCount(13, $engine);
    }

    public function testLegalLinksAreBuiltFromThePublicBaseUrl(): void
    {
        $legal = (new BuiltInRemoteConfig('https://locator.example.org', 'off'))->toArray()['legal'];

        self::assertSame([
            'termsUrl' => 'https://locator.example.org/terms',
            'privacyUrl' => 'https://locator.example.org/privacy',
            'termsVersion' => '0',
        ], $legal);
    }

    public function testTrailingSlashOfTheBaseUrlIsIgnored(): void
    {
        $legal = (new BuiltInRemoteConfig('http://localhost:18080/', 'off'))->toArray()['legal'];

        self::assertSame('http://localhost:18080/terms', $legal['termsUrl']);
    }

    #[DataProvider('operatorModes')]
    public function testOperatorModeIsOnlyTrueForOn(string $value, bool $expected): void
    {
        self::assertSame($expected, (new BuiltInRemoteConfig('http://localhost:8080', $value))->toArray()['operatorMode']);
    }

    /** @return iterable<string, array{string, bool}> */
    public static function operatorModes(): iterable
    {
        yield 'on' => ['on', true];
        yield 'off' => ['off', false];
        yield 'empty' => ['', false];
        yield 'true is not on' => ['true', false];
        yield 'upper case is not on' => ['ON', false];
    }

    public function testKeysAreExactlyTheContractFields(): void
    {
        $config = (new BuiltInRemoteConfig('http://localhost:8080', 'off'))->toArray();

        self::assertSame(['version', 'mapStyleUrl', 'engine', 'limits', 'legal', 'operatorMode'], array_keys($config));
    }
}
