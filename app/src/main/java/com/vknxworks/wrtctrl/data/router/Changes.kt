package com.vknxworks.wrtctrl.data.router

import com.vknxworks.wrtctrl.data.api.RouterError
import com.vknxworks.wrtctrl.data.api.UbusClient
import com.vknxworks.wrtctrl.data.api.UbusStatus
import com.vknxworks.wrtctrl.data.api.uciApply
import com.vknxworks.wrtctrl.data.api.uciChanges
import com.vknxworks.wrtctrl.data.api.uciConfirm
import com.vknxworks.wrtctrl.data.api.uciRevert
import com.vknxworks.wrtctrl.data.api.uciRollback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Where a configuration change stands; shown by the apply dialog over every screen. */
sealed interface ApplyState {
    data object Idle : ApplyState

    /** Saving the changes on the router. [what] says what is being changed. */
    data class Applying(val what: String) : ApplyState

    /**
     * Applied with rollback: the router puts the old settings back at [deadline] (ms) unless WrtCtrl
     * can still reach it and confirms before then.
     */
    data class Confirming(val what: String, val deadline: Long, val timeoutSeconds: Int) : ApplyState

    data class Done(val what: String) : ApplyState

    /** Not confirmed in time (or undone): the router went back to its previous settings. */
    data class RolledBack(val what: String, val byUser: Boolean) : ApplyState

    /** Saving failed before anything was applied; nothing changed on the router. */
    data class Failed(val what: String, val error: Throwable) : ApplyState
}

/**
 * Every configuration change goes through here, the way LuCI does it: the changes are staged with uci,
 * applied with a rollback timeout, and confirmed once WrtCtrl can still reach the router afterwards.
 * If a change cut the phone off (Wi-Fi, an interface, the firewall), the confirmation never arrives and
 * OpenWrt restores the previous configuration by itself when the countdown runs out.
 */
class Changes {
    private val _state = MutableStateFlow<ApplyState>(ApplyState.Idle)
    val state: StateFlow<ApplyState> = _state.asStateFlow()
    private val lock = Mutex()

    @Volatile
    private var undoRequested = false

    fun dismiss() {
        if (_state.value !is ApplyState.Applying && _state.value !is ApplyState.Confirming) _state.value = ApplyState.Idle
    }

    /** Asks for the applied changes to be undone right away (instead of waiting for the countdown). */
    fun undo() {
        undoRequested = true
    }

    /**
     * Stages the changes with [stage], then applies them with rollback and confirms. Returns true when
     * the changes were kept. Never throws for a router error; the result is in [state].
     */
    suspend fun apply(client: UbusClient, what: String, timeoutSeconds: Int = TIMEOUT_SECONDS, stage: suspend UbusClient.() -> Unit): Boolean = lock.withLock {
        undoRequested = false
        _state.value = ApplyState.Applying(what)
        try {
            client.stage()
        } catch (e: CancellationException) {
            discardStaged(client)
            _state.value = ApplyState.Idle
            throw e
        } catch (e: Exception) {
            discardStaged(client)
            _state.value = if (e is RouterError.Cancelled) ApplyState.Idle else ApplyState.Failed(what, e)
            return@withLock false
        }
        try {
            client.uciApply(timeoutSeconds)
        } catch (e: RouterError.Ubus) {
            // "No data": nothing was staged, so there's nothing to apply.
            if (e.code == UbusStatus.NO_DATA) {
                _state.value = ApplyState.Done(what)
                return@withLock true
            }
            discardStaged(client)
            _state.value = ApplyState.Failed(what, e)
            return@withLock false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The router may have applied it anyway; without a confirmation it rolls back on its own.
            _state.value = ApplyState.Failed(what, e)
            return@withLock false
        }
        val deadline = System.currentTimeMillis() + timeoutSeconds * 1000L
        _state.value = ApplyState.Confirming(what, deadline, timeoutSeconds)
        // Give services a moment to restart with the new settings before checking that the router is
        // still reachable, as LuCI does; confirming at once would keep a change that cuts the phone off a
        // second later.
        delay(HOLDOFF_MS)
        while (System.currentTimeMillis() < deadline - SAFETY_MS) {
            if (undoRequested) {
                runCatching { client.uciRollback() }
                _state.value = ApplyState.RolledBack(what, byUser = true)
                return@withLock false
            }
            try {
                client.uciConfirm()
                _state.value = ApplyState.Done(what)
                return@withLock true
            } catch (e: CancellationException) {
                throw e
            } catch (e: RouterError.Ubus) {
                // No pending rollback any more: the router already went back.
                if (e.code == UbusStatus.NO_DATA || e.code == UbusStatus.NOT_FOUND) break
            } catch (e: Exception) {
                // Not reachable (yet): keep trying until the countdown runs out.
            }
            delay(RETRY_MS)
        }
        _state.value = ApplyState.RolledBack(what, byUser = false)
        false
    }

    /** Drops whatever this session staged, so a failed change doesn't linger as an unapplied change. */
    private suspend fun discardStaged(client: UbusClient) {
        runCatching { client.uciChanges().keys.forEach { client.uciRevert(it) } }
    }

    companion object {
        const val TIMEOUT_SECONDS = 30
        private const val HOLDOFF_MS = 4_000L
        private const val RETRY_MS = 1_500L
        private const val SAFETY_MS = 1_000L
    }
}
