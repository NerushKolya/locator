package io.github.nerushkolya.locator.store

import io.github.nerushkolya.locator.BuildConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class StoreServerAddressProviderTest {
    @Test
    fun `store address is the fixed one and not configurable`() {
        val address = StoreServerAddressProvider().address()
        assertEquals(BuildConfig.SERVER_URL, address.baseUrl)
        assertEquals("https://locator.example.org", address.baseUrl)
        assertFalse(address.configurable)
    }
}
