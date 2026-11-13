package com.vknxworks.wrtctrl.ui.setup

import com.vknxworks.wrtctrl.ui.components.CtrlDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.vknxworks.wrtctrl.R
import com.vknxworks.wrtctrl.data.router.Tls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl

/** What the user is asked to trust: the router's certificate as fetched (nothing was sent to it). */
data class CertInfo(val host: String, val subject: String, val fingerprint: String)

/** Fetches the certificate [url] presents, without trusting it or sending anything to it. */
suspend fun fetchCertInfo(url: HttpUrl): CertInfo = withContext(Dispatchers.IO) {
    val leaf = Tls.fetchLeaf(url.host, url.port)
    CertInfo(url.host, leaf.subjectX500Principal.name, Tls.fingerprint(leaf))
}

/** Asks the user to confirm a self-signed certificate by its SHA-256 fingerprint (trust on first use). */
@Composable
fun CertTrustDialog(cert: CertInfo, changed: Boolean, onTrust: () -> Unit, onCancel: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    CtrlDialog(
        onDismissRequest = onCancel,
        icon = { Icon(Icons.Outlined.VerifiedUser, null) },
        title = { Text(stringResource(if (changed) R.string.cert_changed_title else R.string.cert_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (changed) {
                    Text(stringResource(R.string.cert_changed_warning, cert.host), color = cs.error)
                } else {
                    Text(stringResource(R.string.cert_intro, cert.host))
                }
                Text(stringResource(R.string.cert_check))
                Text(
                    cert.fingerprint,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(cs.surfaceContainerHighest).padding(10.dp),
                )
                if (cert.subject.isNotBlank()) {
                    Text(stringResource(R.string.cert_issued_to, cert.subject), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
                Text(stringResource(R.string.cert_pin_note), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onTrust) { Text(stringResource(R.string.cert_trust)) } },
        dismissButton = { TextButton(onCancel) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * Asks before WrtCtrl talks to [host] over plain HTTP (how OpenWrt serves LuCI unless HTTPS was set up).
 * Nothing is sent to the address before the user taps **Use HTTP**.
 */
@Composable
fun HttpWarningDialog(host: String, onUseHttp: () -> Unit, onCancel: () -> Unit) {
    CtrlDialog(
        onDismissRequest = onCancel,
        icon = { Icon(Icons.Outlined.Warning, null) },
        title = { Text(stringResource(R.string.http_title)) },
        text = { Text(stringResource(R.string.http_body, host)) },
        confirmButton = { TextButton(onUseHttp) { Text(stringResource(R.string.http_use)) } },
        dismissButton = { TextButton(onCancel) { Text(stringResource(R.string.cancel)) } },
    )
}
