<?php

declare(strict_types=1);

namespace App\Platform\RemoteConfig;

/**
 * Built-in defaults of the remote configuration document (data model sections 1 and 1.1).
 * Until the back office publishes a document (stage 9) these values are the document.
 */
final class BuiltInRemoteConfig
{
    private const VERSION = 1;
    private const MAP_STYLE_URL = 'https://tiles.openfreemap.org/styles/liberty';

    private readonly string $publicBaseUrl;

    public function __construct(string $publicBaseUrl, private readonly string $operatorMode)
    {
        $this->publicBaseUrl = rtrim($publicBaseUrl, '/');
    }

    /**
     * @return array{version: int, mapStyleUrl: string, engine: array<string, int>, limits: array<string, int>, legal: array<string, string>, operatorMode: bool}
     */
    public function toArray(): array
    {
        return [
            'version' => self::VERSION,
            'mapStyleUrl' => self::MAP_STYLE_URL,
            // Starting values of the Balanced profile (Android architecture 5.1); keys belong to the location-engine specification.
            'engine' => [
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
            ],
            'limits' => [
                'maxGroupMembers' => 20,
                'maxGroupsPerUser' => 10,
                'maxPlacesPerGroup' => 20,
                'placeRadiusMinM' => 100,
                'placeRadiusMaxM' => 1000,
                'invitationTtlH' => 72,
                'sosDurationS' => 900,
                'sosHoldMs' => 2000,
                'lowBatteryPct' => 15,
            ],
            'legal' => [
                'termsUrl' => $this->publicBaseUrl.'/terms',
                'privacyUrl' => $this->publicBaseUrl.'/privacy',
                'termsVersion' => '0',
            ],
            'operatorMode' => 'on' === $this->operatorMode,
        ];
    }
}
