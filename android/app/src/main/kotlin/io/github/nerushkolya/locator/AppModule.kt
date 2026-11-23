package io.github.nerushkolya.locator

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.nerushkolya.locator.core.common.Logger
import io.github.nerushkolya.locator.core.model.ServerAddress
import io.github.nerushkolya.locator.core.model.ServerAddressProvider
import io.github.nerushkolya.locator.core.network.ConfigApi
import io.github.nerushkolya.locator.core.network.ConfigSource
import io.github.nerushkolya.locator.core.network.HealthApi
import io.github.nerushkolya.locator.core.network.HealthSource
import io.github.nerushkolya.locator.core.network.createHttpClient
import io.ktor.client.HttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun logger(): Logger = Logger(AndroidLogSink())

    @Provides
    @Singleton
    fun serverAddress(provider: ServerAddressProvider): ServerAddress = provider.address()

    @Provides
    @Singleton
    fun httpClient(): HttpClient = createHttpClient()

    @Provides
    fun healthSource(client: HttpClient, address: ServerAddress): HealthSource = HealthApi(client, address)

    @Provides
    fun configSource(client: HttpClient, address: ServerAddress): ConfigSource = ConfigApi(client, address)
}
