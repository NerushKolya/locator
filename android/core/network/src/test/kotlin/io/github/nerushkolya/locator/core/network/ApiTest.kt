package io.github.nerushkolya.locator.core.network

import io.github.nerushkolya.locator.core.model.RemoteConfig
import io.github.nerushkolya.locator.core.model.ServerAddress
import io.github.nerushkolya.locator.core.model.ServerStatus
import io.github.nerushkolya.locator.core.model.UnreachableReason
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import java.net.ConnectException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ApiTest {
    private val address = ServerAddress("http://10.0.2.2:8080", configurable = true)
    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    private fun health(engine: MockEngine, timeouts: Timeouts = Timeouts.Default) =
        HealthApi(createHttpClient(engine, timeouts), address)

    private fun config(engine: MockEngine) = ConfigApi(createHttpClient(engine), address)

    @Test
    fun `defaults are connect 3 s and request 5 s`() {
        assertEquals(3_000L, Timeouts.Default.connectMillis)
        assertEquals(5_000L, Timeouts.Default.requestMillis)
    }

    @Test
    fun `200 ok is reachable`() = runBlocking<Unit> {
        val engine = MockEngine { respond("""{"status":"ok"}""", HttpStatusCode.OK, json) }
        assertEquals(ServerStatus.Reachable, health(engine).check())
        assertEquals("http://10.0.2.2:8080/health", engine.requestHistory.single().url.toString())
    }

    @Test
    fun `200 degraded is not ready`() = runBlocking<Unit> {
        val engine = MockEngine { respond("""{"status":"degraded"}""", HttpStatusCode.OK, json) }
        assertEquals(ServerStatus.NotReady("degraded"), health(engine).check())
    }

    @Test
    fun `503 down is not ready`() = runBlocking<Unit> {
        val engine = MockEngine { respond("""{"status":"down"}""", HttpStatusCode.ServiceUnavailable, json) }
        assertEquals(ServerStatus.NotReady("down"), health(engine).check())
    }

    @Test
    fun `503 with an unreadable body is still not ready`() = runBlocking<Unit> {
        val engine = MockEngine { respond("oops", HttpStatusCode.ServiceUnavailable) }
        assertEquals(ServerStatus.NotReady("down"), health(engine).check())
    }

    @Test
    fun `unexpected status or body is an unexpected response`() = runBlocking<Unit> {
        val notFound = MockEngine { respond("", HttpStatusCode.NotFound) }
        val garbage = MockEngine { respond("<html>", HttpStatusCode.OK, json) }
        listOf(notFound, garbage).forEach { engine ->
            val status = health(engine).check()
            assertEquals(
                ServerStatus.Unreachable(address.baseUrl, UnreachableReason.UNEXPECTED_RESPONSE),
                status,
            )
        }
    }

    @Test
    fun `connection error is unreachable`() = runBlocking<Unit> {
        val engine = MockEngine { throw ConnectException("Connection refused: 10.0.2.2/10.0.2.2:8080") }
        val status = health(engine).check()
        assertEquals(ServerStatus.Unreachable(address.baseUrl, UnreachableReason.CONNECTION_REFUSED), status)
    }

    @Test
    fun `slow server is a timeout`() = runBlocking<Unit> {
        val engine = MockEngine {
            delay(2_000)
            respond("""{"status":"ok"}""", HttpStatusCode.OK, json)
        }
        val status = health(engine, Timeouts(connectMillis = 100, requestMillis = 200)).check()
        assertEquals(ServerStatus.Unreachable(address.baseUrl, UnreachableReason.TIMEOUT), status)
    }

    @Test
    fun `config decodes a contract shaped document and ignores unknown fields`() = runBlocking<Unit> {
        val body = """
            {"version":3,"mapStyleUrl":"https://tiles.openfreemap.org/styles/liberty",
             "engine":{"a":1},"limits":{"maxGroupMembers":20},"legal":{"termsUrl":"https://x.y","privacyUrl":"https://x.y","termsVersion":"1"}}
        """.trimIndent()
        val engine = MockEngine { respond(body, HttpStatusCode.OK, json) }
        val result = config(engine).fetch()
        assertEquals(
            RemoteConfig(3, "https://tiles.openfreemap.org/styles/liberty"),
            result.getOrThrow(),
        )
    }

    @Test
    fun `config without a required field fails`() = runBlocking<Unit> {
        val engine = MockEngine { respond("""{"version":3}""", HttpStatusCode.OK, json) }
        val result = config(engine).fetch()
        assertTrue(result.isFailure)
        assertIs<SerializationException>(result.exceptionOrNull())
    }

    @Test
    fun `config error status fails`() = runBlocking<Unit> {
        val engine = MockEngine { respond("", HttpStatusCode.InternalServerError) }
        assertTrue(config(engine).fetch().isFailure)
    }

    @Test
    fun `health dto ignores unknown fields and requires status`() {
        assertEquals("ok", NetworkJson.decodeFromString<HealthDto>("""{"status":"ok","extra":1}""").status)
        assertFailsWith<SerializationException> { NetworkJson.decodeFromString<HealthDto>("{}") }
    }
}
