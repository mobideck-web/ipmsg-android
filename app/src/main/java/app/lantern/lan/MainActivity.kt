package app.lantern.lan

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    private val route = mutableStateOf<Intent?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        route.value = intent
        setContent {
            val dark = isSystemInDarkTheme()
            SideEffect {
                val background = if (dark) Color(0xFF101916) else Color(0xFFF7F8F4)
                window.decorView.setBackgroundColor(background.toArgb())
                window.navigationBarColor = background.toArgb()
                window.statusBarColor = background.toArgb()
                window.isNavigationBarContrastEnforced = false
                WindowCompat.getInsetsController(window, window.decorView).apply { isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark }
            }
            IPMsgTheme(dark) {
                Lantern(Model.get(this), route.value)
            }
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); route.value = intent }
    override fun onResume() { super.onResume(); Model.get(this).update { it.copy(foreground = true) } }
    override fun onPause() { Model.get(this).update { it.copy(foreground = false) }; super.onPause() }
}

@Composable private fun Lantern(model: Model, routeIntent: Intent?) {
    val state by model.state.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf("Nearby") }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedUser by rememberSaveable { mutableStateOf("") }
    val drafts = rememberSaveable(saver = mapSaver<SnapshotStateMap<String, String>>(save = { it.toMap() }, restore = { saved -> mutableStateMapOf<String, String>().apply { saved.forEach { (key, value) -> put(key, value as String) } } })) { mutableStateMapOf<String, String>() }
    val draftKey = "${selected.orEmpty()}|$selectedUser"
    val draft = drafts[draftKey].orEmpty()
    var search by rememberSaveable { mutableStateOf("") }
    var deviceSearch by rememberSaveable { mutableStateOf("") }
    var transferFilter by rememberSaveable { mutableStateOf("All") }
    var renameDevice by remember { mutableStateOf<KnownDevice?>(null) }
    var deleteTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var manual by remember { mutableStateOf(false) }
    var ip by remember { mutableStateOf("") }
    var name by remember { mutableStateOf(model.name) }
    var clear by remember { mutableStateOf(false) }
    var reset by remember { mutableStateOf(false) }
    var notices by remember { mutableStateOf(false) }
    var consent by remember { mutableStateOf<(() -> Unit)?>(null) }
    var consentText by remember { mutableStateOf("") }
    var filePeer by rememberSaveable { mutableStateOf<String?>(null) }
    var fileUser by rememberSaveable { mutableStateOf("") }
    var fileDraft by rememberSaveable { mutableStateOf("") }
    var sharedUris by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var sharedText by rememberSaveable { mutableStateOf("") }
    var actionChat by remember { mutableStateOf<Chat?>(null) }
    var reviewItem by remember { mutableStateOf<OutboxItem?>(null) }
    var attach by remember { mutableStateOf(false) }
    var pendingCaptureVideo by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var captureUri by rememberSaveable { mutableStateOf<String?>(null) }
    var connectTarget by remember { mutableStateOf<Peer?>(null) }
    var manageDevices by remember { mutableStateOf(false) }
    var removeTarget by remember { mutableStateOf<Peer?>(null) }
    val snackbar = remember { SnackbarHostState() }
    var pendingCamera by remember {mutableStateOf<Intent?>(null)}
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val camera=pendingCamera;pendingCamera=null
        if(camera!=null){if(granted && Events.cameraAlertsEnabled(context))context.startActivity(camera) else model.error(IllegalStateException("Enable camera activity notifications before sharing the live camera."))}
    }
    LaunchedEffect(Unit) {
        Events.popups.collectLatest { popup ->
            val result = snackbar.showSnackbar("${popup.title} · ${popup.text}", actionLabel = if (popup.ip != null) "Open" else null, withDismissAction = true, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed && popup.ip != null) { selected = popup.ip; selectedUser = popup.user; tab = "Chats" }
        }
    }
    LaunchedEffect(selected,selectedUser) {
        selected?.let { address -> val key="reply-draft:$address|$selectedUser"; model.preferences.getString(key,null)?.let {text->drafts["$address|$selectedUser"]=text;model.preferences.edit().remove(key).apply()} }
    }
    BackHandler(enabled = selected != null && tab == "Chats") { selected = null; search = "" }
    LaunchedEffect(tab, selected, selectedUser, state.foreground, state.chats) {
        val address = selected
        val user = selectedUser
        if (tab == "Chats" && address != null && state.foreground && state.chats.any { it.ip == address && it.peerUser == selectedUser && !it.outgoing && !it.seen }) {
            kotlinx.coroutines.withContext(Dispatchers.IO) { model.markSeen(address, user) }
            context.getSystemService(android.app.NotificationManager::class.java).cancel(address, 2)
        }
    }
    LaunchedEffect(routeIntent) {
        if(routeIntent?.action == Intent.ACTION_SEND || routeIntent?.action == Intent.ACTION_SEND_MULTIPLE) {
            runCatching { val received=ShareSupport.received(routeIntent!!);require(received.none {it.authority == context.packageName+".files"}) {"Choose a file directly inside IPMsg"};sharedUris = ArrayList(received.map { it.toString() }); sharedText=routeIntent.getStringExtra(Intent.EXTRA_TEXT).orEmpty().take(6000); tab="Nearby"; selected=null; routeIntent.action=null }.onFailure(model::error)
        }
        routeIntent?.getStringExtra("replyDraft")?.let { value ->
            val address=routeIntent.getStringExtra("openIp").orEmpty(); val user=routeIntent.getStringExtra("openUser").orEmpty(); drafts["$address|$user"]=value; routeIntent.removeExtra("replyDraft")
        }
        routeIntent?.getStringExtra("openIp")?.let { selected = it; selectedUser = routeIntent.getStringExtra("openUser").orEmpty(); tab = routeIntent.getStringExtra("openTab") ?: "Chats" }
        routeIntent?.removeExtra("openIp")
    }
    fun queueFiles(uris: List<Uri>, target: String, user: String, message: String) {
        consentText="Keep ${uris.size} files and this message locally for up to 24 hours? You must review them before sending when the device returns."
        consent={ scope.launch(Dispatchers.IO) { runCatching {
            val durable=uris.map { ShareSupport.durable(model,it).toString() }
            model.repository.enqueue(target,user,message,durable)
            scope.launch(Dispatchers.Main) { drafts.remove("$target|$user"); sharedUris=arrayListOf();sharedText="" }
        }.onFailure(model::error) }; Unit }
    }
    fun sendFiles(uris: List<Uri> = emptyList(), target: String? = selected, user: String = selectedUser, message: String = draft, outboxId: Long? = null) {
        val address=target ?: return
        if(uris.size > 30) {model.error(IllegalArgumentException("Choose at most 30 files"));return}
        val peer=state.peers.find { it.ip==address && it.user==user }
        if(!state.listening || peer==null) {queueFiles(uris,address,user,message);return}
        if(!model.devices.approved(address,user)) {sharedUris=ArrayList(uris.map {it.toString()});sharedText=message;connectTarget=peer;return}
        val encrypted=model.engine?.encrypted(address)==true
        if(!encrypted && (model.setting("strict-messages") || model.devices.record(address,user)?.fingerprint != null)) {model.error(IllegalStateException("Secure connection required. Refresh the device key."));return}
        scope.launch {
            runCatching {
                val info=kotlinx.coroutines.withContext(Dispatchers.IO) {uris.map {ShareSupport.metadata(model,it)}}
                val action: () -> Unit = {
                    scope.launch(Dispatchers.IO) {runCatching {
                        val stable=uris.map {ShareSupport.durable(model,it)}
                        model.engine?.sendBatch(address,message,stable,!encrypted,user) { queued ->
                            if(queued) scope.launch(Dispatchers.Main) { outboxId?.let {model.repository.removeOutbox(it)}; if(drafts["$address|$user"]==message) drafts.remove("$address|$user"); sharedUris=arrayListOf();sharedText="" }
                        } ?: model.error(IllegalStateException("Enable listening before sending"))
                    }.onFailure(model::error)}
                    Unit
                }
                if(uris.isNotEmpty() || !encrypted) {
                    consentText=if(uris.isNotEmpty()) "Share ${uris.size} files (${fileSize(info.sumOf {it.second})}) with ${model.devices.label(peer)}?\n${info.take(5).joinToString("\n") {it.first}}\n${if(model.setting("strict-files")) "Only secure file downloads are allowed." else "This peer may download files without encryption."}" else "Send without encryption to $address?"
                    consent=action
                } else action()
            }.onFailure(model::error)
        }
    }
    fun send(uri: Uri? = null, target: String? = selected, user: String = selectedUser, message: String = draft) = sendFiles(listOfNotNull(uri),target,user,message)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if(uris.isNotEmpty()) {uris.forEach { uri -> runCatching {context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)} }; sendFiles(uris,filePeer,fileUser,fileDraft)}
    }
    val storageBrowser = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { }
    val photoCamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        captureUri?.let { value -> runCatching { FileActions.finishCapture(context, Uri.parse(value), success); if (success) send(Uri.parse(value), filePeer, fileUser, fileDraft) }.onFailure(model::error) }; captureUri = null
    }
    val videoCamera = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { success ->
        captureUri?.let { value -> runCatching { FileActions.finishCapture(context, Uri.parse(value), success); if (success) send(Uri.parse(value), filePeer, fileUser, fileDraft) }.onFailure(model::error) }; captureUri = null
    }
    fun launchCapture(video: Boolean) {
        runCatching {
            val uri = FileActions.capture(context, video); captureUri = uri.toString()
            if (video) videoCamera.launch(uri) else photoCamera.launch(uri)
        }.onFailure { failure ->
            captureUri?.let { runCatching { FileActions.finishCapture(context, Uri.parse(it), false) } }
            captureUri = null; model.error(failure)
        }
    }
    val capturePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val video = pendingCaptureVideo; pendingCaptureVideo = null
        if (granted && video != null) launchCapture(video)
        else if (!granted) model.error(IllegalStateException("Camera permission is needed. Allow it in Android app settings to capture photos or videos."))
    }
    fun requestCapture(video: Boolean) {
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) launchCapture(video)
        else { pendingCaptureVideo = video; capturePermission.launch(Manifest.permission.CAMERA) }
    }
    fun accept(t: Transfer) {
        val peer = state.peers.find { it.ip == t.ip }
        val encrypted = model.engine?.encrypted(t.ip) == true && peer != null && peer.command and Wire.FILE_ENC != 0L
        val action: () -> Unit = {
            scope.launch(Dispatchers.IO) {
                runCatching {
                    val engine = model.engine ?: error("Enable listening before receiving")
                    val destination = DownloadStorage.create(model, t.file.name, t.file.size)
                    engine.download(t, destination, !encrypted)
                }.onFailure(model::error)
            }
        }
        if (!encrypted) { consentText = "This file will be transferred without encryption. Receive ${t.file.name} from ${t.ip}?"; consent = action }
        else action()
    }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
        NavigationBar(containerColor=MaterialTheme.colorScheme.surface,tonalElevation=0.dp) {
            listOf("Nearby" to "◎", "Chats" to "☷", "Transfers" to "⇄", "Settings" to "⚙").forEach { (label, icon) ->
                val badge = when (label) { "Chats" -> state.chats.count { !it.outgoing && !it.seen }; "Transfers" -> state.transfers.count { !it.outgoing && it.status == "Awaiting acceptance" }; else -> 0 }
                NavigationBarItem(selected = tab == label, onClick = { tab = label; if (label != "Chats") selected = null }, icon = { BadgedBox(badge = { if (badge > 0) Badge { Text(if (badge > 99) "99+" else badge.toString()) } }) { NavigationSymbol(label) } }, label = { Text(if (label == "Transfers") "Files" else label) })
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().padding(horizontal = 20.dp)) {
            if (tab != "Chats" || selected == null) {
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.ipmsg_logo), "IP Messenger logo", Modifier.size(42.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("IPMsg", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                }
                Text(if (state.listening) "● Online" else "○ Offline", color = if (state.listening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
            }
            Spacer(Modifier.height(20.dp))
            }
            if (state.error != null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(state.error ?: "", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { model.update { it.copy(error = null) } }) { Text("Dismiss") }
                    }
                }; Spacer(Modifier.height(8.dp))
            }
            when (tab) {
                "Nearby" -> {
                    Text("Nearby devices", style = MaterialTheme.typography.headlineMedium)
                    if(sharedUris.isNotEmpty() || sharedText.isNotBlank()) Text("Choose a device to share ${if(sharedUris.isEmpty()) "text" else "${sharedUris.size} files"}",style=MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            if (state.listening) context.stopService(Intent(context, LanService::class.java))
                            else {
                                if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                context.startForegroundService(Intent(context, LanService::class.java))
                            }
                        }) { Text(if (state.listening) "Stop listening" else "Start listening") }
                        OutlinedButton(onClick = { model.engine?.refresh() }, enabled = state.listening) { Text("Refresh") }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState())) { model.repository.devices().filter{it.favorite && !it.blocked && state.peers.none{p->model.devices.identity(p.ip,p.user)==it.id}}.forEach {device-> TextButton(onClick={selected=device.ip;selectedUser=device.user;tab="Chats"}) {Text("★ "+device.nickname.ifBlank{device.name})} } }
                    Row { TextButton(onClick = { manual = true }, enabled = state.listening) { Text("+ Add Windows IP address") }; TextButton(onClick={manageDevices=true}) { Text("Manage") } }
                    OutlinedTextField(deviceSearch, { deviceSearch = it }, label = { Text("Search devices") }, placeholder = { Text("Name, computer or IP address") }, trailingIcon = { if (deviceSearch.isNotEmpty()) TextButton(onClick = { deviceSearch = "" }) { Text("Clear search") } }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp))
                    if (state.peers.isEmpty()) {
                        Card(Modifier.fillMaxWidth().padding(top = 16.dp)) { Column(Modifier.padding(24.dp)) {
                            Text(if (state.listening) "Looking for devices…" else "Connect to start", style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.height(8.dp)); Text("Use the same Wi-Fi network as your PC.")
                        } }
                    }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(state.peers.filter {model.devices.record(it.ip,it.user)?.blocked != true}.sortedByDescending { model.devices.record(it.ip,it.user)?.favorite == true }.filter { deviceSearch.isBlank() || listOf(it.name, it.host, it.ip).any { value -> value.contains(deviceSearch.trim(), true) } }, key = { it.ip }) { peer ->
                            Card(Modifier.fillMaxWidth().clickable { if (model.devices.approved(peer.ip,peer.user)) { selected=peer.ip; selectedUser=peer.user; search=""; tab="Chats"; if(sharedUris.isNotEmpty() || sharedText.isNotBlank()) sendFiles(sharedUris.map {Uri.parse(it)},peer.ip,peer.user,sharedText) } else connectTarget=peer }) {
                                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Text(model.devices.label(peer).take(1).uppercase(), fontSize = 24.sp) }
                                    Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) {
                                        Text((if(model.devices.record(peer.ip,peer.user)?.favorite==true) "★ " else "") + model.devices.label(peer), fontWeight = FontWeight.Bold); Text(peer.ip, style = MaterialTheme.typography.bodySmall)
                                    }
                                    TextButton(onClick = { manageDevices = true }) { Text("Manage") }
                                }
                            }
                        }
                    }
                }
                "Chats" -> {
                    if (selected == null) {
                        Text("Conversations", style = MaterialTheme.typography.headlineMedium)
                        OutlinedTextField(search, { search = it }, label = { Text("Search history") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                        val databaseChats by produceState<List<Chat>>(emptyList(),search,state.chats,state.revision) { value=kotlinx.coroutines.withContext(Dispatchers.IO){model.repository.summaries(search)} }
                        OutboxPanel(model) { reviewItem=it }
                        val conversations = (databaseChats + state.chats.filter { search.isBlank() || it.text.contains(search, true) || it.ip.contains(search) }).distinctBy {it.id}.groupBy { it.ip to it.peerUser }.entries.sortedByDescending { it.value.last().time }
                        if (conversations.isEmpty()) Text(if (search.isBlank()) "Choose a nearby device to start a conversation." else "No matching messages.", Modifier.padding(vertical = 24.dp))
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding=PaddingValues(vertical=8.dp)) { items(conversations) { conversation ->
                            val (address, user) = conversation.key
                            val last = conversation.value.last()
                            val label = model.devices.label(address,user)
                            val unread = state.chats.count { it.ip == address && it.peerUser == user && !it.outgoing && !it.seen }
                            Card(Modifier.fillMaxWidth(), colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow)) { ListItem(colors=ListItemDefaults.colors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow), headlineContent = { Text(label, fontWeight = if (unread > 0) FontWeight.Bold else FontWeight.Normal) }, supportingContent = { Text(last.text.take(80)) }, overlineContent = { Text(if (user.isEmpty()) "Earlier version history" else address) }, trailingContent = { if (unread > 0) Badge { Text(unread.toString()) } }, modifier = Modifier.clickable { selected = address; selectedUser = user; search = "" }) }
                        } }
                    } else {
                        val address = selected!!
                        val peer = state.peers.find { it.ip == address && it.user == selectedUser }
                        val canSend = state.listening && peer != null
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { selected = null }) { Text("‹ Back") }
                            Column(Modifier.weight(1f).clickable {renameDevice=model.devices.record(address,selectedUser)}) { Text(model.devices.label(address,selectedUser), style = MaterialTheme.typography.titleLarge); Text(address, style = MaterialTheme.typography.bodySmall) }
                            TextButton(onClick = { deleteTarget = address to selectedUser }) { Text("Clear") }
                        }
                        if (peer == null) Text("Offline", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(search, { search = it }, label = { Text("Search this conversation") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
                        var loaded by remember(address,selectedUser,search) {mutableStateOf<List<Chat>>(emptyList())}
                        var hasOlder by remember(address,selectedUser,search) {mutableStateOf(true)}
                        var loadingOlder by remember {mutableStateOf(false)}
                        LaunchedEffect(address,selectedUser,search,state.chats,state.revision) {
                            val recent=kotlinx.coroutines.withContext(Dispatchers.IO){model.repository.search(search,address,selectedUser)}
                            if(loaded.isEmpty()) hasOlder=recent.size==100
                            loaded=(loaded+recent).distinctBy {it.id}.sortedBy {it.id}
                        }
                        LaunchedEffect(loaded) {
                            val files=kotlinx.coroutines.withContext(Dispatchers.IO){model.repository.pageFiles(address,selectedUser,loaded.map{it.packet})}
                            model.update {it.copy(transfers=(it.transfers+files.filter {f->it.transfers.none{old->old.id==f.id}}).takeLast(2000))}
                        }
                        val chats=(loaded + state.chats.filter { it.ip==address && it.peerUser==selectedUser && (search.isBlank() || it.text.contains(search,true)) }.takeLast(100)).distinctBy {it.id}.sortedBy {it.id}
                        if(hasOlder) TextButton(onClick={ loadingOlder=true; scope.launch {val older=kotlinx.coroutines.withContext(Dispatchers.IO) {model.repository.search(search,address,selectedUser,loaded.firstOrNull()?.id ?: Long.MAX_VALUE)}; hasOlder=older.size==100; loaded=(older+loaded).distinctBy {it.id}.sortedBy {it.id}; loadingOlder=false } },enabled=hasOlder && !loadingOlder) {Text(if(loadingOlder) "Loading…" else "Load older messages")}
                        val scroll = rememberLazyListState()
                        var firstView by remember(address, selectedUser, search) { mutableStateOf(true) }
                        LaunchedEffect(chats.size, search) {
                            if (chats.isNotEmpty()) {
                                if (firstView) { scroll.scrollToItem(chats.lastIndex); firstView = false }
                                else if (chats.last().outgoing || scroll.firstVisibleItemIndex >= chats.lastIndex - 3) scroll.animateScrollToItem(chats.lastIndex)
                            }
                        }
                        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = scroll, contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            itemsIndexed(chats, key = { _, chat -> chat.id }) { index, chat ->
                                val dateFormat = remember { java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault()) }
                                if (index == 0 || dateFormat.format(Date(chats[index - 1].time)) != dateFormat.format(Date(chat.time))) {
                                    Text(dateFormat.format(Date(chat.time)), Modifier.fillMaxWidth().padding(vertical = 8.dp), style = MaterialTheme.typography.labelSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (chat.outgoing) Arrangement.End else Arrangement.Start) {
                                    val darkBubble=androidx.compose.foundation.isSystemInDarkTheme()
                                    val bubbleColor=if(chat.outgoing) {if(darkBubble) Color(0xFF164B40) else Color(0xFFDDF2E9)} else {if(darkBubble) Color(0xFF252D38) else Color(0xFFFFFFFF)}
                                    val bubbleText=if(darkBubble) Color(0xFFF0F5F3) else Color(0xFF172C29)
                                    Card(Modifier.padding(start=if(chat.outgoing) 32.dp else 0.dp,end=if(chat.outgoing) 0.dp else 32.dp).widthIn(min=76.dp,max = 340.dp).combinedClickable(onClick={},onLongClick={actionChat=chat}), colors = CardDefaults.cardColors(containerColor=bubbleColor,contentColor=bubbleText), shape = RoundedCornerShape(topStart=18.dp,topEnd=18.dp,bottomEnd=if(chat.outgoing) 5.dp else 18.dp,bottomStart=if(chat.outgoing) 18.dp else 5.dp), border = androidx.compose.foundation.BorderStroke(1.dp, if(darkBubble) Color(0xFF52675F) else Color(0xFFB7CAC2)), elevation=CardDefaults.cardElevation(defaultElevation=1.dp)) {
                                        Column(Modifier.padding(12.dp)) {
                                            val files = state.transfers.filter { it.ip == address && it.peerUser == selectedUser && it.packet == chat.packet && it.outgoing == chat.outgoing }
                                            if (chat.text.isNotBlank() && !(files.size == 1 && chat.text == "File: ${files[0].file.name}")) SelectionContainer { ChatText(chat.text) }
                                            files.forEach { file -> FileCard(model, file, ::accept,onSettings={tab="Settings"}) }
                                            Spacer(Modifier.height(6.dp))
                                            Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(chat.time)) + if (chat.outgoing) " · ${chat.status}" else "", style = MaterialTheme.typography.labelSmall,color=bubbleText.copy(alpha=0.68f))
                                        }
                                    }
                                }
                            }
                        }
                        OutlinedTextField(value = draft, onValueChange = { if (it.toByteArray(Charsets.UTF_8).size <= 6000) drafts[draftKey] = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Write a message…") }, maxLines = 4, enabled = true)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = { filePeer = address; fileUser = selectedUser; fileDraft = draft; if(peer != null && !model.devices.approved(address,selectedUser)) connectTarget=peer else attach = true }, enabled = true) { Text("+ Attach") }
                            if(canSend) TextButton(onClick={queueFiles(emptyList(),address,selectedUser,draft)},enabled=draft.isNotBlank()) {Text("Queue")}
                            Button(onClick = { send() }, enabled = draft.isNotBlank()) { Text(if(canSend) "Send" else "Queue") }
                        }
                    }
                }
                "Transfers" -> {
                    Text("Files", style = MaterialTheme.typography.headlineMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("All", "Incoming", "Outgoing").forEach { filter -> FilterChip(selected = transferFilter == filter, onClick = { transferFilter = filter }, label = { Text(filter) }) }
                    }
                    if (state.transfers.isEmpty()) Text("No files yet.", Modifier.padding(vertical = 24.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(state.transfers.sortedByDescending { it.created }.filter { transferFilter == "All" || (transferFilter == "Outgoing") == it.outgoing }, key = { it.id }) { t ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { selected = t.ip; selectedUser = t.peerUser; tab = "Chats" }) { Text("${if (t.outgoing) "To" else "From"} ${model.devices.label(t.ip,t.peerUser)} · Open chat") }
                                FileCard(model, t, ::accept,onSettings={tab="Settings"})
                            }
                        }
                    }
                }
                "Settings" -> PreferencesScreen(model, onClear = { clear = true }, onReset = { reset = true }, onNotices = { notices = true }, onStorage = { storageBrowser.launch(arrayOf("*/*")) })
            }
        }
    }
    if (manual) AlertDialog(onDismissRequest = { manual = false }, title = { Text("Add a Windows PC") }, text = { OutlinedTextField(ip, { ip = it }, label = { Text("IPv4 address") }, placeholder = { Text("192.168.1.20") }) }, confirmButton = { TextButton(onClick = { model.engine?.addIp(ip.trim()); manual = false }) { Text("Discover") } }, dismissButton = { TextButton(onClick = { manual = false }) { Text("Cancel") } })
    connectTarget?.let { peer -> DeviceDialog(model,peer,{connectTarget=null}) {
        selected=peer.ip; selectedUser=peer.user;search="";tab="Chats";connectTarget=null
        if(sharedUris.isNotEmpty() || sharedText.isNotBlank()) sendFiles(sharedUris.map {Uri.parse(it)},peer.ip,peer.user,sharedText)
    } }
    if (attach) AlertDialog(onDismissRequest = { attach = false }, title = { Text("Attach") }, text = { Column {
        TextButton(enabled=state.listening && selected?.let {model.devices.approved(it,selectedUser)}==true,onClick = { attach = false; consentText = "Share your live camera with $filePeer while this screen stays open? The temporary link works only from that device on this network. Viewing uses HTTPS without audio. Verify the certificate fingerprint in your browser against this phone before allowing a site-specific exception. The receiver can start recording and capture photos directly. You receive activity alerts and can stop the camera at any time."; consent = {
            val camera=Intent(context,LiveCameraActivity::class.java).putExtra("peerIp",filePeer).putExtra("peerUser",fileUser)
            if(Build.VERSION.SDK_INT>=33 && androidx.core.content.ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED){pendingCamera=camera;permission.launch(Manifest.permission.POST_NOTIFICATIONS)}
            else if(Events.cameraAlertsEnabled(context))context.startActivity(camera)
            else model.error(IllegalStateException("Enable Live camera activity notifications in Android notification settings before sharing."))
        } }) { Text("Live camera") }
        TextButton(onClick={attach=false;val clipboard=context.getSystemService(ClipboardManager::class.java);val text=clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty(); if(text.isBlank()) model.error(IllegalStateException("Clipboard is empty")) else {drafts[draftKey]=text.take(6000);consentText="Preview clipboard text:\n${text.take(1000)}";consent={sendFiles(emptyList(),selected,selectedUser,text.take(6000))}}}) {Text("Send clipboard")}
        TextButton(onClick = { attach = false; picker.launch(arrayOf("*/*")) }) { Text("Browse files") }
        TextButton(onClick = { attach = false; picker.launch(arrayOf("image/*", "video/*")) }) { Text("Photos & videos") }
        TextButton(onClick = { attach = false; requestCapture(false) }) { Text("Take photo") }
        TextButton(onClick = { attach = false; requestCapture(true) }) { Text("Record video") }
    } }, confirmButton = { TextButton(onClick = { attach = false }) { Text("Cancel") } })
    reviewItem?.let { item ->
        val queuedInfo by produceState<List<Pair<String,Long>>?>(null,item.id) {value=kotlinx.coroutines.withContext(Dispatchers.IO){runCatching{item.uris.map{ShareSupport.metadata(model,Uri.parse(it))}}.getOrNull()}}
        AlertDialog(onDismissRequest={reviewItem=null},title={Text("Review pending items")},text={Text("To ${model.repository.devices().find{it.id==item.deviceId}?.let{it.nickname.ifBlank{it.name}} ?: item.ip} (${item.ip})\n${item.text.take(500)}\n${item.uris.size} files (${fileSize(queuedInfo?.sumOf{it.second} ?: 0)}): ${queuedInfo?.take(5)?.joinToString{it.first} ?: "Checking file access…"}. Recheck the recipient before sharing. Interrupted attempts may already have delivered some items. Files may be downloaded without encryption unless secure uploads are required.")},confirmButton={TextButton(enabled=item.uris.isEmpty() || queuedInfo != null,onClick={
        val peer=state.peers.find {model.devices.identity(it.ip,it.user)==item.deviceId && model.devices.approved(it.ip,it.user)}
        if(peer==null) model.error(IllegalStateException("The approved device is not online. Refresh and try again."))
        else if(System.currentTimeMillis()>item.expires) model.error(IllegalStateException("This queued item expired. Select it again."))
        else if(model.engine?.encrypted(peer.ip)!=true) {selected=peer.ip;selectedUser=peer.user;drafts["${peer.ip}|${peer.user}"]=item.text; sharedUris=ArrayList(item.uris);sharedText=item.text; sendFiles(item.uris.map{Uri.parse(it)},peer.ip,peer.user,item.text,outboxId=item.id)}
        else scope.launch(Dispatchers.IO) {runCatching {item.uris.forEach {context.contentResolver.openInputStream(Uri.parse(it))!!.close()};model.repository.outboxState(item.id,"SENDING"); model.engine?.sendBatch(peer.ip,item.text,item.uris.map{Uri.parse(it)},false,peer.user) {queued -> if(queued) model.repository.removeOutbox(item.id) else model.repository.outboxState(item.id,"REVIEW")} ?: model.repository.outboxState(item.id,"REVIEW")}.onFailure {model.repository.outboxState(item.id,"REVIEW");model.error(it)}}
        reviewItem=null
    }){Text("Confirm recipient & send")}},dismissButton={TextButton(onClick={reviewItem=null}){Text("Cancel")}}) }
    actionChat?.let { chat -> AlertDialog(onDismissRequest={actionChat=null},title={Text("Message")},text={Column {
        TextButton(onClick={context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("IPMsg",chat.text));actionChat=null}){Text("Copy")}
        TextButton(onClick={drafts[draftKey]="> ${chat.text.take(1000).replace("\n","\n> ")}\n"+draft;actionChat=null}){Text("Quote reply")}
        TextButton(onClick={scope.launch(Dispatchers.IO) {state.transfers.filter{it.ip==chat.ip && it.peerUser==chat.peerUser && it.packet==chat.packet && it.outgoing==chat.outgoing}.forEach{model.engine?.cancel(it.id)};model.repository.deleteMessage(chat)};actionChat=null}){Text("Delete locally")}
        TextButton(onClick={actionChat=null;consentText="Send this message again? It may already have been delivered.";consent={sendFiles(state.transfers.filter{it.ip==chat.ip && it.packet==chat.packet && it.outgoing}.mapNotNull{it.sourceUri?.let(Uri::parse)},chat.ip,chat.peerUser,chat.text)}}){Text("Resend")}
    }},confirmButton={TextButton(onClick={actionChat=null}){Text("Close")}}) }
    renameDevice?.let { device->
        var nickname by remember(device.id){mutableStateOf(device.nickname)}
        AlertDialog(onDismissRequest={renameDevice=null},title={Text("Conversation name")},text={OutlinedTextField(nickname,{nickname=it.take(40)},label={Text("Local nickname")},placeholder={Text(device.name)},singleLine=true)},confirmButton={TextButton(onClick={model.repository.save(device.copy(nickname=nickname.trim()));renameDevice=null}){Text("Save")}},dismissButton={TextButton(onClick={renameDevice=null}){Text("Cancel")}})
    }
    if(manageDevices) AlertDialog(
        onDismissRequest={manageDevices=false},
        title={Text("Manage devices")},
        text={
            Column(Modifier.verticalScroll(rememberScrollState())) {
                KnownDevicesSettings(model)
                model.preferences.getStringSet("blocked",emptySet()).orEmpty().sorted().forEach { address ->
                    Row {
                        Text(address,Modifier.weight(1f))
                        TextButton(onClick={model.restore(address)}) {Text("Restore")}
                    }
                }
            }
        },
        confirmButton={TextButton(onClick={manageDevices=false}){Text("Done")}}
    )
    removeTarget?.let { peer -> AlertDialog(onDismissRequest = { removeTarget = null }, title = { Text("Remove ${peer.name}?") }, text = { Text("It will stay hidden and blocked until restored in Nearby → Manage. Saved chats and files remain.") }, confirmButton = { TextButton(onClick = { model.engine?.removeDevice(peer.ip); removeTarget = null }) { Text("Remove device") } }, dismissButton = { TextButton(onClick = { removeTarget = null }) { Text("Cancel") } }) }
    deleteTarget?.let { (address, user) -> AlertDialog(onDismissRequest = { deleteTarget = null }, title = { Text("Clear this conversation?") }, text = { Text("Remove messages and file links for $address? Active file offers will stop. Saved files stay on this phone.") }, confirmButton = { TextButton(onClick = { scope.launch(Dispatchers.IO) { model.engine?.revokeConversation(address, user); model.deleteConversation(address, user) }; deleteTarget = null }) { Text("Clear messages") } }, dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }) }
    if (consent != null) AlertDialog(onDismissRequest = { consent = null }, title = { Text("Confirm sharing") }, text = { Text(consentText) }, confirmButton = { TextButton(onClick = { val action = consent; consent = null; action?.invoke() }) { Text("Continue") } }, dismissButton = { TextButton(onClick = { consent = null }) { Text("Cancel") } })
    if (clear) AlertDialog(onDismissRequest = { clear = false }, title = { Text("Clear all history?") }, text = { Text("Remove all messages and file links? Active file offers will stop. Saved files stay on this phone.") }, confirmButton = { TextButton(onClick = { scope.launch(Dispatchers.IO) { model.engine?.revokeConversation(); model.clearHistory() }; clear = false }) { Text("Delete history") } }, dismissButton = { TextButton(onClick = { clear = false }) { Text("Cancel") } })
    if (reset) AlertDialog(onDismissRequest = { reset = false }, title = { Text("Reset trusted peer keys?") }, text = { Text("New keys will be trusted on the next discovery. Verify the Windows key before resetting.") }, confirmButton = { TextButton(onClick = { model.engine?.resetTrust(); reset = false }) { Text("Reset") } }, dismissButton = { TextButton(onClick = { reset = false }) { Text("Cancel") } })
    if (notices) AlertDialog(onDismissRequest = { notices = false }, title = { Text("Open-source notices") }, text = {
        val text = remember { context.assets.open("third-party-notices.txt").bufferedReader().use { it.readText() } }
        Text(text, Modifier.verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall)
    }, confirmButton = { TextButton(onClick = { notices = false }) { Text("Close") } })
}

private fun size(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes bytes"
}

