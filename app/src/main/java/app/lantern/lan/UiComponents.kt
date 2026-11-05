package app.lantern.lan

import android.net.Uri
import android.os.StatFs
import android.provider.Settings
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable fun ChatText(value: String) {
    val text = androidx.compose.ui.text.buildAnnotatedString {
        append(value)
        Regex("https?://[^\\s<>]+", RegexOption.IGNORE_CASE).findAll(value).forEach { match ->
            val url = match.value.trimEnd('.', ',', ';', ')')
            addLink(androidx.compose.ui.text.LinkAnnotation.Url(url), match.range.first, match.range.first + url.length)
        }
    }
    Text(text, style = MaterialTheme.typography.bodyLarge, lineHeight = 24.sp)
}

fun fileSize(bytes: Long): String = when {
    bytes >= 1073741824L -> "%.1f GB".format(bytes / 1073741824.0)
    bytes >= 1048576L -> "%.1f MB".format(bytes / 1048576.0)
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable fun FileCard(model: Model, t: Transfer, accept: (Transfer) -> Unit, onSettings:()->Unit = {}) {
    val state by model.state.collectAsState()
    val uri = t.savedUri ?: t.sourceUri
    val active = t.active
    var restart by remember {mutableStateOf(false)}
    val label = when {
        t.phase == TransferPhase.OFFERED -> if (t.outgoing) "Shared" else "Ready"
        t.phase == TransferPhase.FAILED -> "Failed"
        t.phase == TransferPhase.RECEIVING -> "Downloading"
        else -> t.status
    }
    val thumbnail by produceState<ImageBitmap?>(null, uri, t.status) {
        if (uri != null && t.file.name.substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg", "png", "webp")) {
            value = withContext(Dispatchers.IO) { runCatching { model.context.contentResolver.loadThumbnail(Uri.parse(uri), android.util.Size(320, 240), null).asImageBitmap() }.getOrNull() }
        }
    }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            thumbnail?.let { Image(it, t.file.name, Modifier.fillMaxWidth().heightIn(max = 180.dp), contentScale = ContentScale.Fit) }
            Text(t.file.name, fontWeight = FontWeight.SemiBold, maxLines = 2)
            Text("${if (t.outgoing) "↑" else "↓"} ${fileSize(t.file.size)} · $label", style = MaterialTheme.typography.labelSmall)
            if (active) {
                LinearProgressIndicator(progress = { if (t.file.size == 0L) 1f else (t.done.toDouble() / t.file.size).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                val percent=if(t.file.size==0L) 100 else (t.done.toDouble()/t.file.size*100).toInt().coerceIn(0,100)
                val eta=if(t.speed>0) " · ~${((t.file.size-t.done).coerceAtLeast(0)/t.speed)}s left" else ""
                Text("$percent% · ${fileSize(t.done)} / ${fileSize(t.file.size)}" + if(t.speed>0) " · ${fileSize(t.speed)}/s$eta" else "", style = MaterialTheme.typography.labelSmall)
            }
            if(t.phase==TransferPhase.EXPIRED)Text("Ask the sender to share this file again.",style=MaterialTheme.typography.bodySmall)
            if (t.phase == TransferPhase.FAILED || t.phase==TransferPhase.INTERRUPTED) {
                Text(TransferRecovery.message(t),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(t.outgoing)Text(if(model.engine?.retryAvailable(t)==true)"Offer still available · retry from the receiving PC" else "Offer unavailable · share the source file again",style=MaterialTheme.typography.labelSmall)
            }
            if(TransferRecovery.canRestart(t) && state.peers.none{it.ip==t.ip && it.user==t.peerUser})Text("Waiting for sender · refresh Nearby",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!t.outgoing && t.phase == TransferPhase.OFFERED) {
                    TextButton(onClick = { accept(t) }, enabled = state.listening) { Text("Save") }
                    TextButton(onClick = { model.transfer(t.copy(phase = TransferPhase.DECLINED)) }) { Text("Decline") }
                }
                if (TransferRecovery.canRestart(t)) TextButton(onClick = { restart=true }, enabled = state.listening && state.peers.any{it.ip==t.ip && it.user==t.peerUser}) { Text("Restart download") }
                if(t.phase==TransferPhase.FAILED && t.failure in setOf(FailureReason.PERMISSION,FailureReason.STORAGE))TextButton(onClick=onSettings){Text("Storage settings")}
                if (active || (t.outgoing && (t.phase == TransferPhase.OFFERED || model.engine?.retryAvailable(t)==true))) TextButton(onClick = { model.engine?.cancel(t.id) }) { Text("Cancel") }
                if(t.batchId != null && (active || t.phase == TransferPhase.OFFERED)) TextButton(onClick={model.engine?.cancelBatch(t.batchId)}){Text("Cancel batch")}
                if (uri != null && (t.outgoing || t.phase == TransferPhase.SAVED)) {
                    TextButton(onClick = { FileActions.open(model, uri, t.file.name) }) { Text("Open") }
                    TextButton(onClick = { FileActions.folder(model, uri) }) { Text("Folder") }
                }
            }
        }
    }
    if(restart)AlertDialog(onDismissRequest={restart=false},title={Text("Restart download?")},text={Text("Download ${t.file.name} again from zero? The incomplete copy was removed. Keep the sender online; an expired offer must be shared again.")},confirmButton={TextButton(onClick={restart=false;accept(t.copy(done=0,speed=0,failure=null,detail=null))}){Text("Restart")}},dismissButton={TextButton(onClick={restart=false}){Text("Cancel")}})
}

@Composable private fun Preference(model: Model, title: String, key: String, default: Boolean = false, note: String? = null) {
    val state by model.state.collectAsState()
    val checked = remember(state.revision, key, default) { model.setting(key, default) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) { Text(title); if (note != null) Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Switch(checked = checked, onCheckedChange = { model.setSetting(key, it) })
    }
}

@Composable fun PreferencesScreen(model: Model, onClear: () -> Unit, onReset: () -> Unit, onNotices: () -> Unit, onStorage: () -> Unit) {
    val state by model.state.collectAsState()
    var name by remember { mutableStateOf(model.name) }
    var manageStorage by remember { mutableStateOf(false) }
    if(manageStorage){StorageScreen(model,onBack={manageStorage=false});return}
    val folderPicker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) runCatching {
            model.context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            model.preferences.edit().putString("download-folder", uri.toString()).apply()
            model.update { it.copy(revision = it.revision + 1) }
        }.onFailure(model::error)
    }
    var details by remember { mutableStateOf(false) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
        item { Text("Settings",style=MaterialTheme.typography.headlineMedium) }
        item { SettingsSection { OutlinedTextField(name, { name = it.take(40) }, label = { Text("Your name") }, modifier = Modifier.fillMaxWidth()); TextButton(onClick = { model.name = name; name = model.name; model.engine?.refresh() }) { Text("Save name") } } }
        item { SettingsSection { Text("My IP", style = MaterialTheme.typography.titleMedium)
            val ips = remember(state.revision, state.listening) { LocalAddresses.list() }
            SelectionContainer { Text(ips.joinToString("\n").ifBlank { "No local network address" }) }
        } }
        item { SettingsSection { Text("Notifications", style = MaterialTheme.typography.titleMedium)
 Preference(model, "Pop-up alerts", "popups", true)
 Preference(model, "Device connections", "device-alerts", true)
 Preference(model, "Message previews", "previews", note = "Show message text in notifications")
 TextButton(onClick = { model.context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, model.context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text("Android notification settings") } } }
        item { SettingsSection { Text("Storage", style = MaterialTheme.typography.titleMedium)

            val space = remember { StatFs(model.context.filesDir.absolutePath) }
            Text("${fileSize(space.availableBytes)} free of ${fileSize(space.totalBytes)}", style = MaterialTheme.typography.bodyMedium)
            Text("Downloads: " + DownloadStorage.label(model), style = MaterialTheme.typography.bodyMedium)
            Row { TextButton(onClick = { folderPicker.launch(null) }) { Text("Change folder") }
                TextButton(onClick = { model.preferences.edit().remove("download-folder").apply(); model.update { it.copy(revision = it.revision + 1) } }) { Text("Use default") } }
            TextButton(onClick={manageStorage=true}) { Text("Manage storage") }
            TextButton(onClick = onStorage) { Text("Browse storage") }
        } }
        item { SettingsSection {
            Text("Security",style=MaterialTheme.typography.titleMedium)
            Preference(model,"Require secure messages","strict-messages",note="Block plaintext messages in both directions")
            Preference(model,"Require secure file uploads","strict-files",note="Some Windows peers may be unable to download.")
            TextButton(onClick={details=!details}){Text(if(details)"Hide security details" else "Security details")}
            if(details){
                Text("RSA-2048 / AES-256. Compare device fingerprints. Classic IPMsg files do not authenticate contents.",style=MaterialTheme.typography.bodySmall)
                state.peers.filter{model.engine?.fingerprint(it.ip)!=null}.forEach{peer->SelectionContainer{Text("${peer.name}\n${model.engine?.fingerprint(peer.ip)}",style=MaterialTheme.typography.bodySmall)}}
            }
        } }
        item { SettingsSection { Text("History", style = MaterialTheme.typography.titleMedium); TextButton(onClick = onClear) { Text("Clear history") } } }
        item { SettingsSection { Text("Connection", style = MaterialTheme.typography.titleMedium); Diagnostics(model); Text(if (state.listening) "Online · UDP / TCP 2425" else "Offline", style = MaterialTheme.typography.bodySmall)
            val ips = remember(state.listening, state.revision) { runCatching { java.net.NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }.flatMap { it.inetAddresses.toList() }.filterIsInstance<java.net.Inet4Address>().mapNotNull { it.hostAddress } }.getOrDefault(emptyList()) }
            SelectionContainer { Text(ips.joinToString("\n"), style = MaterialTheme.typography.bodySmall) }
        } }
        item { SettingsSection { Text("IPMsg " + remember { model.context.packageManager.getPackageInfo(model.context.packageName,0).versionName } + " · Unofficial Android client", style = MaterialTheme.typography.labelSmall); TextButton(onClick = onNotices) { Text("Licenses & credits") } } }
    }
}

@Composable private fun SettingsSection(content:@Composable ColumnScope.()->Unit) {
    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
    }
}
