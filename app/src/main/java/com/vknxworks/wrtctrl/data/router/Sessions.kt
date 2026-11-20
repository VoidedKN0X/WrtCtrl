package com.vknxworks.wrtctrl.data.router

import com.vknxworks.wrtctrl.data.api.BoardInfo
import com.vknxworks.wrtctrl.data.api.RouterAcl
import com.vknxworks.wrtctrl.data.api.RouterError
import com.vknxworks.wrtctrl.data.api.UbusClient
import com.vknxworks.wrtctrl.data.api.board
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.UUID

/** The active router and its ubus client. */
class ActiveRouter(val router: Router, val client: UbusClient)

/** What the signed-in login may do on the active router, and what the router is. */
data class Access(
    val acl: RouterAcl = RouterAcl.NONE,
    val board: BoardInfo? = null,
    val loading: Boolean = true,
    val error: RouterError? = null,
)

/**
 * Keeps the list of routers, which one is active, a ubus client for it and what its login may do.
 * Switching routers rebuilds the client; any change to a router (certificate, read-only) does too.
 */
class Sessions(
    private val store: RouterStore,
    private val baseHttp: OkHttpClient,
    private val scope: CoroutineScope,
    initiallyLocked: Boolean = false,
) {
    val routers: StateFlow<List<Router>> = store.routers

    /** True while the app is locked and the logins are sealed: no client exists until the next unlock. */
    private val locked = MutableStateFlow(initiallyLocked)

    fun setLocked(value: Boolean) {
        locked.value = value
    }

    /** Bumped when secrets change outside the client (a new password), so the client is rebuilt. */
    private val revision = MutableStateFlow(0)

    /**
     * The client last made, with the router settings and revision it was made for. The value [active]
     * starts with is reused when the flow below reaches the same router, so the app starts with one
     * client and one sign-in instead of two. Dropped while locked, so unlocking always makes a new client.
     */
    @Volatile
    private var built: Triple<Router, Int, ActiveRouter>? = null

    private fun activeFor(router: Router, rev: Int): ActiveRouter {
        val last = built
        if (last != null && last.first == router && last.second == rev) return last.third
        return ActiveRouter(router, clientFor(router)).also { built = Triple(router, rev, it) }
    }

    val active: StateFlow<ActiveRouter?> = combine(store.routers, store.activeId, revision, locked) { list, id, rev, isLocked ->
        (if (isLocked) null else list.firstOrNull { it.id == id }) to rev
    }
        .distinctUntilChanged()
        .map { (router, rev) ->
            if (router == null) {
                built = null
                null
            } else {
                activeFor(router, rev)
            }
        }
        .stateIn(
            scope,
            SharingStarted.Eagerly,
            if (initiallyLocked) null else store.routers.value.firstOrNull { it.id == store.activeId.value }?.let { activeFor(it, revision.value) },
        )

    private val _access = MutableStateFlow(Access())
    val access: StateFlow<Access> = _access.asStateFlow()
    private var accessJob: Job? = null

    init {
        scope.launch {
            active.collect { refreshAccess() }
        }
    }

    fun clientFor(router: Router): UbusClient =
        UbusClient(
            router,
            baseHttp,
            loadSecrets = { store.secrets(router.id) },
            isReadOnly = { store.routers.value.firstOrNull { it.id == router.id }?.readOnly ?: router.readOnly },
            onSecretsChanged = { updated -> store.saveSecrets(router.id, updated) },
        )

    /**
     * Learns what the active router's login may do and reads what the router is. Keeps the saved model,
     * hostname and OpenWrt version up to date.
     *
     * A saved session that is still good is kept (see [UbusClient.refreshLogin]); otherwise, and always
     * with [force] (the user tapped Retry), this signs in with the saved password. A kept session is
     * proven by the `system board` call that follows: if rpcd ended it, that call signs in again and the
     * ACL is read after it. A login that may not read `system board` can't be proven that way, so it
     * signs in as before.
     */
    fun refreshAccess(force: Boolean = false) {
        accessJob?.cancel()
        val current = active.value
        if (current == null) {
            _access.value = Access(loading = false)
            return
        }
        _access.value = _access.value.copy(loading = true, error = null)
        accessJob = scope.launch {
            try {
                val signedIn = current.client.refreshLogin(force)
                if (!signedIn && current.client.acl?.canCall("system", "board") != true) current.client.refreshLogin(force = true)
                val board = if (current.client.acl?.canCall("system", "board") == true) current.client.board() else null
                // Read after the call: a kept session that had ended was replaced by it, with that sign-in's ACL.
                val acl = current.client.acl ?: RouterAcl.NONE
                if (board != null) {
                    val r = current.router
                    val version = board.release.version
                    if (board.hostname != r.hostname || board.model != r.model || version != r.openWrtVersion) {
                        store.update(r.id) { it.copy(hostname = board.hostname, model = board.model, openWrtVersion = version) }
                    }
                }
                _access.value = Access(acl, board, loading = false)
            } catch (e: RouterError) {
                _access.value = _access.value.copy(loading = false, error = e)
            }
        }
    }

    /** True when the active router isn't in read-only mode and its login may call [obj].[method]. */
    fun canCall(obj: String, method: String): Boolean {
        val router = active.value?.router ?: return false
        return !router.readOnly && access.value.acl.canCall(obj, method)
    }

    fun add(router: Router, secrets: RouterSecrets): Router {
        val saved = if (router.id.isBlank()) router.copy(id = UUID.randomUUID().toString()) else router
        store.saveSecrets(saved.id, secrets)
        store.save(saved)
        store.setActive(saved.id)
        // Editing a router can leave its settings unchanged but its password new: rebuild the client either way.
        revision.value++
        return saved
    }

    fun switchTo(id: String) = store.setActive(id)

    fun remove(id: String) = store.remove(id)

    fun setReadOnly(id: String, readOnly: Boolean) = store.update(id) { it.copy(readOnly = readOnly) }
}
