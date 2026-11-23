package io.github.nerushkolya.locator.core.model

/** Where the app talks to; `configurable` is always false in the store flavor. */
data class ServerAddress(val baseUrl: String, val configurable: Boolean)

/** Why the server could not be reached. A short category, never a raw exception text. */
enum class UnreachableReason { TIMEOUT, CONNECTION_REFUSED, UNEXPECTED_RESPONSE }

sealed interface ServerStatus {
    data object Reachable : ServerStatus

    /** `/health` answered `503`/`down` or `degraded`. */
    data class NotReady(val status: String) : ServerStatus

    data class Unreachable(val baseUrl: String, val reason: UnreachableReason) : ServerStatus
}

/** The part of `/v1/config` this feature uses. */
data class RemoteConfig(val version: Int, val mapStyleUrl: String)

/** Chooses the server address of the running build; implemented per flavor in `app`. */
fun interface ServerAddressProvider {
    fun address(): ServerAddress
}
