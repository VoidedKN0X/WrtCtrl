package com.vknxworks.wrtctrl.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.vknxworks.wrtctrl.R
import com.vknxworks.wrtctrl.container
import com.vknxworks.wrtctrl.data.api.BoardInfo
import com.vknxworks.wrtctrl.data.api.RouterAcl
import com.vknxworks.wrtctrl.data.api.RouterError
import com.vknxworks.wrtctrl.data.api.UbusClient
import com.vknxworks.wrtctrl.data.api.board
import com.vknxworks.wrtctrl.data.api.httpFor
import com.vknxworks.wrtctrl.data.router.CleartextPolicy
import com.vknxworks.wrtctrl.data.router.Router
import com.vknxworks.wrtctrl.data.router.RouterAddress
import com.vknxworks.wrtctrl.data.router.RouterSecrets
import com.vknxworks.wrtctrl.ui.components.CtrlTextField
import com.vknxworks.wrtctrl.ui.components.Group
import com.vknxworks.wrtctrl.ui.components.GroupSwitch
import com.vknxworks.wrtctrl.ui.components.PillButton
import com.vknxworks.wrtctrl.ui.components.ScreenTopBar
import com.vknxworks.wrtctrl.ui.components.SecureScreen
import com.vknxworks.wrtctrl.ui.components.errorMessage
import com.vknxworks.wrtctrl.ui.theme.CtrlColors
import com.vknxworks.wrtctrl.ui.theme.Gutter
import kotlinx.coroutines.launch
import okhttp3.HttpUrl

/** Everything learned from a successful sign-in, shown before the router is saved. */
private class Connected(val router: Router, val secrets: RouterSecrets, val board: BoardInfo?, val acl: RouterAcl)

private class CertPrompt(val cert: CertInfo, val changed: Boolean)

/**
 * Adds an OpenWrt router, or changes the address or sign-in of an existing one ([editId]).
 *
 * 1. An http:// address is only contacted after the "unencrypted connection" warning. An https://
 *    address with a certificate the phone doesn't trust shows its SHA-256 fingerprint to confirm and
 *    pin (nothing has been sent to the router at that point).
 * 2. Then it signs in with `session.login`, which also says what the login may do (its rpcd ACL).
 * 3. The model, hostname and OpenWrt version are read, and the router is saved on Continue.
 */
@Composable
fun SetupScreen(editId: String? = null, onDone: () -> Unit, onCancel: (() -> Unit)?) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val existing = remember(editId) { editId?.let { id -> c.sessions.routers.value.firstOrNull { it.id == id } } }
    val existingSecrets = remember(editId) { editId?.let { c.routerStore.secrets(it) } }

    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var address by remember { mutableStateOf(existing?.url.orEmpty()) }
    var username by remember { mutableStateOf(existing?.username.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var showSecret by remember { mutableStateOf(false) }

    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var certPrompt by remember { mutableStateOf<CertPrompt?>(null) }
    var httpPrompt by remember { mutableStateOf<HttpUrl?>(null) }
    var connected by remember { mutableStateOf<Connected?>(null) }
    var readOnly by remember { mutableStateOf(existing?.readOnly ?: false) }

    // A certificate the user trusted here, or the one already pinned for this router.
    var pinnedHost by remember { mutableStateOf(existing?.host) }
    var pin by remember { mutableStateOf(existing?.certPin.orEmpty()) }

    val resolved: HttpUrl? = RouterAddress.normalize(address)
    val addressError = if (address.isNotBlank() && resolved == null) stringResource(R.string.address_invalid) else null
    // The saved password is only ever sent to the address and username it was saved for. Changing
    // either needs it typed again, so an edited address can't receive the old password.
    val canReusePassword = existing != null && existingSecrets?.password?.isNotEmpty() == true &&
        resolved != null && RouterAddress.base(resolved) == existing.url && username.trim() == existing.username
    val canConnect = !busy && resolved != null && username.isNotBlank() && (password.isNotEmpty() || canReusePassword)

    // A certificate pin only means something over HTTPS; an http:// address never keeps one.
    fun pinFor(url: HttpUrl): String = if (url.isHttps && pinnedHost.equals(url.host, ignoreCase = true)) pin else ""

    fun connect() {
        val url = resolved ?: return
        val base = RouterAddress.base(url)
        if (!url.isHttps && !CleartextPolicy.isAllowed(url)) {
            error = null
            httpPrompt = url
            return
        }
        val user = username.trim()
        val pass = password.ifEmpty { if (canReusePassword) existingSecrets?.password.orEmpty() else "" }
        busy = true
        error = null
        scope.launch {
            try {
                val login = UbusClient.login(httpFor(c.http, url.host, pinFor(url)), base, user, pass)
                val draft = Router(
                    id = existing?.id.orEmpty(),
                    name = name.trim(),
                    url = base,
                    username = user,
                    certPin = pinFor(url),
                )
                val secrets = RouterSecrets(
                    password = pass,
                    session = login.session,
                    sessionIssuedAt = System.currentTimeMillis(),
                    sessionTimeout = login.timeout,
                )
                val board = if (login.acl.canCall("system", "board")) {
                    runCatching { UbusClient(draft, c.http, secrets).board() }.getOrNull()
                } else {
                    null
                }
                // Keep the user's read-only choice when editing; a login that can't change anything starts read-only.
                readOnly = existing?.readOnly ?: login.acl.isReadOnly
                connected = Connected(
                    router = draft.copy(
                        name = name.trim().ifBlank { board?.hostname?.ifBlank { null } ?: url.host },
                        hostname = board?.hostname.orEmpty(),
                        model = board?.model.orEmpty(),
                        openWrtVersion = board?.release?.version.orEmpty(),
                    ),
                    secrets = secrets,
                    board = board,
                    acl = login.acl,
                )
            } catch (e: RouterError.UntrustedCertificate) {
                askToTrust(context, url, changed = false, onCert = { certPrompt = it }, onFail = { error = it })
            } catch (e: RouterError.PinMismatch) {
                askToTrust(context, url, changed = true, onCert = { certPrompt = it }, onFail = { error = it })
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                error = errorMessage(context, e, url.host)
            } finally {
                busy = false
            }
        }
    }

    fun save() {
        val result = connected ?: return
        c.sessions.add(result.router.copy(readOnly = readOnly), result.secrets)
        onDone()
    }

    // The password is typed here: never in screenshots or Recents.
    SecureScreen()

    // An http:// address accepted here but not saved is forgotten on leaving; a saved one stays allowed.
    DisposableEffect(Unit) {
        onDispose { CleartextPolicy.clearPending() }
    }

    val result = connected
    Column(Modifier.fillMaxSize()) {
        if (onCancel != null) {
            ScreenTopBar(stringResource(if (editId != null) R.string.edit_router_title else R.string.add_router))
        } else {
            Spacer(Modifier.statusBarsPadding())
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            if (result == null) {
                if (onCancel == null) {
                    Column(Modifier.padding(horizontal = Gutter, vertical = 16.dp)) {
                        Icon(Icons.Outlined.Router, null, Modifier.size(48.dp), tint = CtrlColors.AccentText)
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.setup_intro), style = MaterialTheme.typography.bodyMedium, color = CtrlColors.Muted)
                    }
                }
                Column(Modifier.padding(horizontal = Gutter), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CtrlTextField(
                        value = address,
                        onValueChange = { address = it.trim() },
                        label = { Text(stringResource(R.string.field_address)) },
                        placeholder = { Text(stringResource(R.string.field_address_hint)) },
                        singleLine = true,
                        isError = addressError != null,
                        supportingText = {
                            when {
                                addressError != null -> Text(addressError)
                                resolved != null && !resolved.isHttps -> Text(
                                    stringResource(R.string.address_resolves, RouterAddress.base(resolved)) + "\n" +
                                        stringResource(R.string.address_http_warning),
                                    color = CtrlColors.Warn,
                                )
                                resolved != null -> Text(stringResource(R.string.address_resolves, RouterAddress.base(resolved)))
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    CtrlTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.field_name)) },
                        placeholder = { Text(stringResource(R.string.field_name_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    CtrlTextField(
                        value = username,
                        onValueChange = { username = it.trim() },
                        label = { Text(stringResource(R.string.field_username)) },
                        placeholder = { Text(stringResource(R.string.field_username_hint)) },
                        supportingText = { Text(stringResource(R.string.field_username_note)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next, autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    CtrlTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.field_password)) },
                        placeholder = if (canReusePassword) {
                            { Text("••••••••") }
                        } else {
                            null
                        },
                        supportingText = if (existing != null && !canReusePassword && password.isEmpty()) {
                            { Text(stringResource(R.string.password_reenter)) }
                        } else {
                            null
                        },
                        singleLine = true,
                        visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showSecret = !showSecret }) {
                                Icon(
                                    if (showSecret) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    stringResource(if (showSecret) R.string.hide_secret else R.string.show_secret),
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    error?.let {
                        Text(it, color = CtrlColors.Error, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(4.dp))
                    PillButton(
                        text = stringResource(if (busy) R.string.connecting else R.string.connect),
                        onClick = ::connect,
                        enabled = canConnect,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (busy) CircularProgressIndicator(Modifier.padding(top = 8.dp).size(24.dp), strokeWidth = 2.dp)
                }
            } else {
                ConnectedSummary(result, readOnly, onReadOnly = { readOnly = it })
                PillButton(
                    text = stringResource(if (editId != null) R.string.save_changes else R.string.continue_label),
                    onClick = ::save,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 12.dp),
                )
            }
        }
    }

    certPrompt?.let { prompt ->
        CertTrustDialog(
            cert = prompt.cert,
            changed = prompt.changed,
            onTrust = {
                certPrompt = null
                pinnedHost = prompt.cert.host
                pin = prompt.cert.fingerprint
                connect()
            },
            onCancel = { certPrompt = null },
        )
    }
    httpPrompt?.let { url ->
        HttpWarningDialog(
            host = url.host,
            onUseHttp = {
                httpPrompt = null
                CleartextPolicy.allow(url)
                connect()
            },
            onCancel = { httpPrompt = null },
        )
    }
}

/** Reads the certificate the router presents and asks the user to confirm it. */
private suspend fun askToTrust(
    context: android.content.Context,
    url: HttpUrl,
    changed: Boolean,
    onCert: (CertPrompt) -> Unit,
    onFail: (String) -> Unit,
) {
    try {
        onCert(CertPrompt(fetchCertInfo(url), changed))
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        // A plain connection problem reads like the connection test; anything else means the certificate
        // itself couldn't be read. Never the exception's own (English, technical) text.
        val unreachable = e is java.io.IOException && e !is javax.net.ssl.SSLException
        onFail(context.getString(if (unreachable) R.string.error_unreachable else R.string.error_cert_unreadable, url.host))
    }
}

@Composable
private fun ConnectedSummary(result: Connected, readOnly: Boolean, onReadOnly: (Boolean) -> Unit) {
    val router = result.router
    Column(Modifier.padding(horizontal = Gutter, vertical = 16.dp)) {
        Icon(Icons.Outlined.Router, null, Modifier.size(48.dp), tint = CtrlColors.AccentText)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.connected_to, router.name), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            listOfNotNull(
                router.model.ifBlank { null },
                result.board?.release?.description?.ifBlank { null }
                    ?: router.openWrtVersion.ifBlank { null }?.let { stringResource(R.string.openwrt_version, it) },
                stringResource(R.string.signed_in_as, router.username),
            ).joinToString("\n"),
            style = MaterialTheme.typography.bodyMedium,
            color = CtrlColors.Muted,
        )
    }
    Group(footer = if (result.acl.isReadOnly && readOnly) stringResource(R.string.read_only_suggest) else null) {
        GroupSwitch(
            stringResource(R.string.read_only_mode),
            stringResource(R.string.read_only_mode_subtitle),
            checked = readOnly,
            onChange = onReadOnly,
        )
    }
    AccessSummary(result.acl)
}
