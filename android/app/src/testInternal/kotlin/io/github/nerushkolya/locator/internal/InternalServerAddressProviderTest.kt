package io.github.nerushkolya.locator.internal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InternalServerAddressProviderTest {
    private fun device(fingerprint: String, hardware: String, product: String) = object : DeviceInfo {
        override val fingerprint = fingerprint
        override val hardware = hardware
        override val product = product
    }

    private val emulator = device(
        "google/sdk_gphone64_x86_64/emu64xa:16/BP22:eng/test-keys",
        "ranchu",
        "sdk_gphone64_x86_64",
    )
    private val phone = device("samsung/dm1qxxx/dm1q:16/BP2A:user/release-keys", "qcom", "dm1qxxx")

    @Test
    fun `emulator uses the host address and the configured port`() {
        val address = InternalServerAddressProvider(port = 18080, device = emulator).address()
        assertEquals("http://10.0.2.2:18080", address.baseUrl)
        assertTrue(address.configurable)
    }

    @Test
    fun `phone uses localhost for adb reverse`() {
        val address = InternalServerAddressProvider(port = 8080, device = phone).address()
        assertEquals("http://localhost:8080", address.baseUrl)
    }

    @Test
    fun `generic fingerprint is an emulator`() {
        assertTrue(device("generic/sdk/x", "unknown", "unknown").isEmulator())
    }
}
