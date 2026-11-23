package io.github.nerushkolya.locator.core.data

import io.github.nerushkolya.locator.core.common.Logger
import io.github.nerushkolya.locator.core.model.RemoteConfig
import io.github.nerushkolya.locator.core.model.ServerStatus
import io.github.nerushkolya.locator.core.network.ConfigSource
import io.github.nerushkolya.locator.core.network.HealthSource
import javax.inject.Inject

class ServerStatusRepository @Inject constructor(
    private val api: HealthSource,
    private val logger: Logger,
) {
    suspend fun status(): ServerStatus {
        val status = api.check()
        val fields = when (status) {
            ServerStatus.Reachable -> mapOf("result" to "reachable")
            is ServerStatus.NotReady -> mapOf("result" to "not_ready", "serverStatus" to status.status)
            is ServerStatus.Unreachable -> mapOf("result" to "unreachable", "reason" to status.reason.name)
        }
        logger.info("server health read", event = "server.health", fields = fields)
        return status
    }
}

class RemoteConfigRepository @Inject constructor(
    private val api: ConfigSource,
    private val logger: Logger,
) {
    suspend fun config(): Result<RemoteConfig> {
        val result = api.fetch()
        result.fold(
            onSuccess = { logger.info("config read", event = "config.read", fields = mapOf("version" to it.version)) },
            // Only the exception class name is logged, never its text.
            onFailure = {
                logger.warning(
                    "config not available",
                    event = "config.failed",
                    fields = mapOf("error" to it::class.simpleName),
                )
            },
        )
        return result
    }
}
