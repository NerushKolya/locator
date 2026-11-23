package io.github.nerushkolya.locator.store

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.nerushkolya.locator.BuildConfig
import io.github.nerushkolya.locator.core.model.ServerAddress
import io.github.nerushkolya.locator.core.model.ServerAddressProvider
import javax.inject.Inject

/** The store build talks to one fixed server; no code path changes it. */
class StoreServerAddressProvider(private val url: String) : ServerAddressProvider {
    @Inject
    constructor() : this(BuildConfig.SERVER_URL)

    override fun address() = ServerAddress(baseUrl = url, configurable = false)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class StoreAddressModule {
    @Binds
    abstract fun address(impl: StoreServerAddressProvider): ServerAddressProvider
}
