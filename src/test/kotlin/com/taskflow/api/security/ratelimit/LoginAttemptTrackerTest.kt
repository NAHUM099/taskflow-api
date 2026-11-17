package com.taskflow.api.security.ratelimit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LoginAttemptTrackerTest {

    private var nowMillis = 0L

    private fun tracker(
        windowMillis: Long = 60_000,
        maxAttemptsPerAccount: Int = 3,
        maxAttemptsPerIp: Int = 10
    ) = LoginAttemptTracker(
        windowMillis = windowMillis,
        maxAttemptsPerAccount = maxAttemptsPerAccount,
        maxAttemptsPerIp = maxAttemptsPerIp,
        now = { nowMillis }
    )

    @Test
    fun `permite los intentos mientras no se alcanza el limite por cuenta`() {
        val tracker = tracker()

        repeat(2) { tracker.recordFailure(IP, EMAIL) }

        assertEquals(0, tracker.secondsUntilAllowed(IP, EMAIL))
    }

    @Test
    fun `bloquea y publica los segundos restantes al alcanzar el limite por cuenta`() {
        val tracker = tracker()

        repeat(3) { tracker.recordFailure(IP, EMAIL) }

        assertEquals(60, tracker.secondsUntilAllowed(IP, EMAIL))

        nowMillis = 45_000

        assertEquals(15, tracker.secondsUntilAllowed(IP, EMAIL))
    }

    @Test
    fun `la ventana caduca y el acceso se recupera sin bloqueo permanente`() {
        val tracker = tracker()
        repeat(3) { tracker.recordFailure(IP, EMAIL) }

        nowMillis = 60_000

        assertEquals(0, tracker.secondsUntilAllowed(IP, EMAIL))
    }

    @Test
    fun `otra ip no queda bloqueada por los fallos contra una cuenta ajena`() {
        val tracker = tracker()
        repeat(3) { tracker.recordFailure(IP, EMAIL) }

        assertEquals(0, tracker.secondsUntilAllowed("10.0.0.99", EMAIL))
    }

    @Test
    fun `limita el total de intentos desde una misma ip aunque cambie el email`() {
        val tracker = tracker(maxAttemptsPerIp = 2)

        tracker.recordFailure(IP, "uno@taskflow.dev")
        tracker.recordFailure(IP, "dos@taskflow.dev")

        assertEquals(60, tracker.secondsUntilAllowed(IP, "tres@taskflow.dev"))
    }

    @Test
    fun `un login correcto libera la cuota de la cuenta para ese origen`() {
        val tracker = tracker()
        repeat(3) { tracker.recordFailure(IP, EMAIL) }
        assertEquals(60, tracker.secondsUntilAllowed(IP, EMAIL))

        tracker.recordSuccess(IP, EMAIL)

        assertEquals(0, tracker.secondsUntilAllowed(IP, EMAIL))
    }

    @Test
    fun `el email se normaliza para no eludir el limite cambiando mayusculas`() {
        val tracker = tracker()

        repeat(3) { tracker.recordFailure(IP, "Ana@TaskFlow.dev") }

        assertEquals(60, tracker.secondsUntilAllowed(IP, "ana@taskflow.dev"))
    }

    private companion object {
        const val IP = "192.168.1.10"
        const val EMAIL = "ana@taskflow.dev"
    }
}
