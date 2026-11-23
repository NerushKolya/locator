package io.github.nerushkolya.locator.core.network

import io.github.nerushkolya.locator.core.model.RemoteConfig
import io.github.nerushkolya.locator.core.model.ServerAddress
import io.github.nerushkolya.locator.core.model.ServerStatus
import io.github.nerushkolya.locator.core.model.UnreachableReason
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException

/** Timeouts of the contract: connect 3 s, request 5 s (SC-003). */
data class Timeouts(val connectMillis: Long, val requestMillis: Long) {
    companion object {
        val Default = Timeouts(connectMillis = 3_000, requestMillis = 5_000)
    }
}

internal val NetworkJson = Json { ignoreUnknownKeys = true }

/** `Health` of the contract. */
@Serializable
data class HealthDto(val status: String)

/** The part of `RemoteConfig` of the contract that the app reads; other fields are ignored. */
@Serializable
data class RemoteConfigDto(val version: Int, val mapStyleUrl: String) {
    fun toModel() = RemoteConfig(version = version, mapStyleUrl = mapStyleUrl)
}

fun createHttpClient(
    engine: HttpClientEngine = OkHttp.create(),
    timeouts: Timeouts = Timeouts.Default,
): HttpClient = HttpClient(engine) {
    expectSuccess = false
    install(HttpTimeout) {
        connectTimeoutMillis = timeouts.connectMillis
        requestTimeoutMillis = timeouts.requestMillis
        socketTimeoutMillis = timeouts.requestMillis
    }
    install(ContentNegotiation) { json(NetworkJson) }
}

fun interface HealthSource {
    suspend fun check(): ServerStatus
}

fun interface ConfigSource {
    /** The failure carries no raw server text; callers show "map unavailable". */
    suspend fun fetch(): Result<RemoteConfig>
}

class HealthApi(private val client: HttpClient, private val address: ServerAddress) : HealthSource {
    override suspend fun check(): ServerStatus = try {
        val response = client.get("${address.baseUrl}/health")
        val text = response.bodyAsText()
        when (response.status) {
            HttpStatusCode.OK -> {
                val status = NetworkJson.decodeFromString<HealthDto>(text).status
                if (status == "ok") ServerStatus.Reachable else ServerStatus.NotReady(status)
            }
            HttpStatusCode.ServiceUnavailable -> ServerStatus.NotReady(
                runCatching { NetworkJson.decodeFromString<HealthDto>(text).status }.getOrDefault("down"),
            )
            else -> unreachable(UnreachableReason.UNEXPECTED_RESPONSE)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: SerializationException) {
        unreachable(UnreachableReason.UNEXPECTED_RESPONSE)
    } catch (e: Exception) {
        unreachable(e.toReason())
    }

    private fun unreachable(reason: UnreachableReason) = ServerStatus.Unreachable(address.baseUrl, reason)
}

class ConfigApi(private val client: HttpClient, private val address: ServerAddress) : ConfigSource {
    override suspend fun fetch(): Result<RemoteConfig> = try {
        val response = client.get("${address.baseUrl}/v1/config")
        if (response.status != HttpStatusCode.OK) {
            Result.failure(IOException("Unexpected status ${response.status.value}"))
        } else {
            Result.success(NetworkJson.decodeFromString<RemoteConfigDto>(response.bodyAsText()).toModel())
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}

internal fun Throwable.toReason(): UnreachableReason = when (this) {
    is HttpRequestTimeoutException, is ConnectTimeoutException, is SocketTimeoutException,
    is java.net.SocketTimeoutException -> UnreachableReason.TIMEOUT
    is SerializationException -> UnreachableReason.UNEXPECTED_RESPONSE
    else -> UnreachableReason.CONNECTION_REFUSED
}
