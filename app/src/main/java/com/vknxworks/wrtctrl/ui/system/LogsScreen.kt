package com.vknxworks.wrtctrl.ui.system

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vknxworks.wrtctrl.R
import com.vknxworks.wrtctrl.data.api.LogLine
import com.vknxworks.wrtctrl.data.api.systemLog
import com.vknxworks.wrtctrl.ui.components.CtrlTextField
import com.vknxworks.wrtctrl.ui.components.ErrorCard
import com.vknxworks.wrtctrl.ui.components.ScreenTopBar
import com.vknxworks.wrtctrl.ui.components.activeRouter
import com.vknxworks.wrtctrl.ui.components.rememberRouterData
import com.vknxworks.wrtctrl.ui.theme.CtrlColors
import com.vknxworks.wrtctrl.ui.theme.CtrlType
import com.vknxworks.wrtctrl.ui.theme.Gutter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Quick filters: the log's source process (as LuCI shows it). */
private enum class LogFilter(val label: Int, val match: (LogLine) -> Boolean) {
    ALL(R.string.filter_all, { true }),
    KERNEL(R.string.log_kernel, { it.msg.startsWith("kernel:") || it.source.equals("kernel", true) }),
    DNSMASQ(R.string.log_dnsmasq, { it.msg.contains("dnsmasq") }),
    HOSTAPD(R.string.log_hostapd, { it.msg.contains("hostapd") }),
    ERRORS(R.string.log_errors, { it.priority in 0..3 }),
}

/** The system log (newest at the bottom), with search and source filters. Refreshes every 5 seconds. */
@Composable
fun LogsScreen() {
    val router = activeRouter()?.router
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(LogFilter.ALL) }
    val data = rememberRouterData(5_000) { client -> client.systemLog(1000) }
    val q = query.trim()
    val lines = data.value.orEmpty().filter { filter.match(it) && (q.isEmpty() || it.msg.contains(q, ignoreCase = true)) }
    val listState = rememberLazyListState()
    val fmt = SimpleDateFormat("MMM d HH:mm:ss", Locale.getDefault())

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.sys_logs))
        CtrlTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.log_search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 4.dp),
        )
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Gutter, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LogFilter.entries.forEach { f -> FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(stringResource(f.label)) }) }
        }
        data.error?.let { ErrorCard(it, router?.host.orEmpty(), onRetry = { data.refresh() }) }
        LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), state = listState, reverseLayout = true) {
            items(lines.asReversed()) { l ->
                Column(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 4.dp)) {
                    Text(
                        (if (l.time > 0) fmt.format(Date(l.time)) else "") + if (l.priority in 0..3) "  ·  " + stringResource(R.string.log_error_tag) else "",
                        style = CtrlType.Label,
                        color = if (l.priority in 0..3) CtrlColors.Error else CtrlColors.Muted,
                    )
                    Text(l.msg, style = CtrlType.Mono)
                }
            }
        }
    }
}
