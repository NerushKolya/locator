package io.github.nerushkolya.locator.internal

import android.os.Build
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.nerushkolya.locator.BuildConfig
import io.github.nerushkolya.locator.core.model.ServerAddress
import io.github.nerushkolya.locator.core.model.ServerAddressProvider
import javax.inject.Inject

/** Build properties that tell an emulator from a phone, behind an interface so tests can fake them. */
interface DeviceInfo {
    val fingerprint: String
    val hardware: String
    val product: String
}

class AndroidDeviceInfo @Inject constructor() : DeviceInfo {
    override val fingerprint: String get() = Build.FINGERPRINT
    override val hardware: String get() = Build.HARDWARE
    override val product: String get() = Build.PRODUCT
}

fun DeviceInfo.isEmulator(): Boolean =
    fingerprint.startsWith("generic") ||
        fingerprint.contains("emulator") ||
        hardware in setOf("goldfish", "ranchu") ||
        hardware.contains("emu") ||
        product.contains("sdk_gphone") ||
        product.contains("emulator") ||
        product.contains("simulator")

/**
 * Emulator: the host machine is `10.0.2.2`. Phone: `localhost`, reached through `adb reverse`
 * (install-phone.ps1 sets it up).
 */
class InternalServerAddressProvider(
    private val port: Int,
    private val device: DeviceInfo,
) : ServerAddressProvider {
    @Inject
    constructor(device: DeviceInfo) : this(BuildConfig.SERVER_PORT, device)

    override fun address(): ServerAddress {
        val host = if (device.isEmulator()) "10.0.2.2" else "localhost"
        return ServerAddress(baseUrl = "http://$host:$port", configurable = true)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class InternalAddressModule {
    @Binds
    abstract fun deviceInfo(impl: AndroidDeviceInfo): DeviceInfo

    @Binds
    abstract fun address(impl: InternalServerAddressProvider): ServerAddressProvider
}
