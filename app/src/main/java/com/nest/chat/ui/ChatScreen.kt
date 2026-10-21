package com.nest.chat.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.nest.chat.AppViewModel
import com.nest.chat.data.Backend
import com.nest.chat.data.Chat
import com.nest.chat.data.ImageScanner
import com.nest.chat.data.ImageUtil
import com.nest.chat.data.ImageVerdict
import com.nest.chat.data.Message
import com.nest.chat.data.Moderation
import com.nest.chat.data.Penalty
import com.nest.chat.data.ReplyRef
import com.nest.chat.data.ReportResult
import com.nest.chat.data.Route
import com.nest.chat.data.UserCache
import com.nest.chat.data.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class ViolationInfo(val reason: String, val penalty: Penalty)

private val reportReasons = listOf(
    "Ngôn từ tục tĩu",
    "Hình ảnh nhạy cảm",
    "Quấy rối hoặc spam",
    "Lý do khác"
)

private val quickReactions = listOf("👍", "❤️", "😂", "😮", "😢", "🙏")

private val emojiList = listOf(
    "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "😇", "🙂", "🙃", "😉", "😌", "😍", "🥰",
    "😘", "😗", "😙", "😚", "😋", "😛", "😝", "😜", "🤪", "🤨", "🧐", "🤓", "😎", "🥳", "😏", "😒",
    "😞", "😔", "😟", "😕", "🙁", "😣", "😖", "😫", "😩", "🥺", "😢", "😭", "😤", "😠", "😡", "🤬",
    "🤯", "😳", "🥵", "🥶", "😱", "😨", "😰", "😥", "😓", "🤗", "🤔", "🤭", "🤫", "😶", "😐", "😑",
    "😬", "🙄", "😯", "😦", "😧", "😮", "😲", "🥱", "😴", "🤤", "😪", "😵", "🤐", "🥴", "🤢", "🤮",
    "😷", "🤒", "🤕", "🤑", "🤠", "😈", "👿", "👻", "💀", "👽", "🤖", "💩", "🙈", "🙉", "🙊", "😺",
    "👋", "🤚", "✋", "👌", "✌️", "🤞", "🤟", "🤘", "🤙", "👈", "👉", "👆", "👇", "👍", "👎", "✊",
    "👊", "👏", "🙌", "🤝", "🙏", "💪", "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "💔", "💕",
    "💖", "💗", "💘", "💝", "🔥", "✨", "⭐", "🌟", "💯", "🎉", "🎊", "🎂", "🎁", "🍕", "🍔", "🍟",
    "🍜", "🍰", "☕", "🍺", "⚽", "🏀", "🎮", "🎵", "🎧", "📷", "💡", "📌", "✅", "❌", "❓", "❗"
)

private val urlRegex = Regex("(https?://[^\\s]+|www\\.[^\\s]+)")
private val mentionRegex = Regex("@[\\p{L}\\p{N}_]+")

private fun AnnotatedString.Builder.appendMentions(segment: String, styleMentions: Boolean) {
    if (!styleMentions) {
        append(segment)
        return
    }
    var last = 0
    for (match in mentionRegex.findAll(segment)) {
        append(segment.substring(last, match.range.first))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(match.value) }
        last = match.range.last + 1
    }
    append(segment.substring(last))
}

private fun richText(text: String, linkColor: Color, styleMentions: Boolean): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (match in urlRegex.findAll(text)) {
        appendMentions(text.substring(last, match.range.first), styleMentions)
        val raw = match.value
        val clean = raw.trimEnd('.', ',', ';', ':', '!', '?', ')')
        val url = if (clean.startsWith("http")) clean else "https://$clean"
        val style = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
        withLink(LinkAnnotation.Url(url, style)) { append(clean) }
        append(raw.substring(clean.length))
        last = match.range.last + 1
    }
    appendMentions(text.substring(last), styleMentions)
}

private fun previewOf(message: Message): String = when {
    message.recalled -> "Tin nhắn đã thu hồi"
    message.image.isNotEmpty() && message.text.isEmpty() -> "[Hình ảnh]"
    else -> message.text.take(100)
}

@Composable
fun ChatScreen(vm: AppViewModel, me: UserProfile, chatId: String) {
    val chats by vm.chats.collectAsStateWithLifecycle()
    val chat = chats.firstOrNull { it.id == chatId }
    if (chat == null) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("Trò chuyện", onBack = { vm.back() })
            Loading()
        }
    } else {
        ChatContent(vm, me, chat)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatContent(vm: AppViewModel, me: UserProfile, chat: Chat) {
    val c = Nest.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val chatId = chat.id
    val meState = rememberUpdatedState(me)
    val chatState = rememberUpdatedState(chat)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val listState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current
    val prefsMap by vm.chatPrefs.collectAsStateWithLifecycle()
    val nicknames by vm.nicknames.collectAsStateWithLifecycle()
    val blockedSet by vm.blocked.collectAsStateWithLifecycle()
    val allChats by vm.chats.collectAsStateWithLifecycle()
    val pref = prefsMap[chatId]
    val clearedAt = pref?.clearedAt ?: 0L
    val hidden = pref?.hidden ?: emptySet()

    var limit by remember(chatId) { mutableIntStateOf(40) }
    var raw by remember(chatId) { mutableStateOf<List<Message>>(emptyList()) }
    var input by remember(chatId) { mutableStateOf(TextFieldValue(vm.drafts[chatId].orEmpty())) }
    var replyTo by remember(chatId) { mutableStateOf<Message?>(null) }
    var editing by remember(chatId) { mutableStateOf<Message?>(null) }
    var searching by remember(chatId) { mutableStateOf(false) }
    var query by remember(chatId) { mutableStateOf("") }
    var emojiOpen by remember(chatId) { mutableStateOf(false) }
    var processing by remember { mutableStateOf(false) }
    var violation by remember { mutableStateOf<ViolationInfo?>(null) }
    var actionFor by remember { mutableStateOf<Message?>(null) }
    var reportFor by remember { mutableStateOf<Message?>(null) }
    var forwardFor by remember { mutableStateOf<Message?>(null) }
    var viewer by remember { mutableStateOf<String?>(null) }
    var otherLive by remember(chatId) { mutableStateOf<UserProfile?>(null) }
    var typing by remember(chatId) { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var lastTypingSent by remember(chatId) { mutableLongStateOf(0L) }

    val otherId = if (chat.isGroup) "" else chat.members.firstOrNull { it != me.uid }.orEmpty()
    val other by rememberUser(otherId)
    val now by rememberNow(me.bannedUntil)
    val minute by rememberMinuteTicker()
    val banned = me.bannedUntil > now
    val iBlocked = otherId.isNotEmpty() && otherId in blockedSet
    val isAdmin = chat.isAdmin(me.uid)
    val canPost = !banned && !iBlocked && !chat.disbanded && (!chat.onlyAdminsPost || isAdmin)
    val canPin = !chat.disbanded && (!chat.isGroup || isAdmin)

    val memberProfiles by produceState<List<UserProfile>>(initialValue = emptyList(), key1 = chat.members, key2 = chat.isGroup) {
        if (chat.isGroup) {
            value = chat.members.map { id -> async { UserCache.get(id) } }.awaitAll().filterNotNull()
        }
    }

    val otherRead by produceState(initialValue = 0L, key1 = chatId, key2 = me.readReceipts) {
        if (!chat.isGroup && me.readReceipts && otherId.isNotEmpty()) {
            Backend.readsFlow(chatId, otherId).catch { }.collect { value = it }
        }
    }

    val typingTick by produceState(initialValue = System.currentTimeMillis(), key1 = typing) {
        while (typing.isNotEmpty()) {
            delay(2_000L)
            value = System.currentTimeMillis()
        }
    }

    val inputState = rememberUpdatedState(input)
    val editingState = rememberUpdatedState(editing)
    DisposableEffect(chatId) {
        onDispose {
            if (editingState.value == null) vm.setDraft(chatId, inputState.value.text)
        }
    }

    LaunchedEffect(otherId) {
        if (otherId.isNotEmpty()) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                Backend.profileFlow(otherId).catch { }.collect { otherLive = it }
            }
        }
    }

    LaunchedEffect(chatId) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            Backend.typingFlow(chatId, meState.value.uid).catch { }.collect { typing = it }
        }
    }

    LaunchedEffect(chatId) {
        if (pref?.markedUnread == true) vm.setPref(chatId, mapOf("markedUnread" to false))
        if ((chatState.value.unread[meState.value.uid] ?: 0) > 0) Backend.resetUnread(chatId, meState.value.uid)
    }

    LaunchedEffect(chatId, limit, clearedAt) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var lastMarked = ""
            Backend.messagesFlow(chatId, limit, clearedAt).catch { }.collect { list ->
                raw = list
                val newest = list.firstOrNull()
                val mine = meState.value
                if (newest != null && newest.sender != mine.uid && newest.id != lastMarked) {
                    lastMarked = newest.id
                    if (mine.readReceipts) Backend.markRead(chatId, mine.uid)
                }
                if ((chatState.value.unread[mine.uid] ?: 0) > 0) Backend.resetUnread(chatId, mine.uid)
            }
        }
    }

    val shown = remember(raw, hidden, query) {
        val visible = raw.filter { it.id !in hidden }
        if (query.isBlank()) {
            visible
        } else {
            val needle = foldVi(query.trim())
            visible.filter { !it.recalled && !it.flagged && foldVi(it.text).contains(needle) }
        }
    }

    val newestId = shown.firstOrNull()?.id
    LaunchedEffect(newestId) {
        if (listState.firstVisibleItemIndex <= 3) listState.animateScrollToItem(0)
    }

    BackHandler(enabled = emojiOpen || searching || replyTo != null || editing != null) {
        if (emojiOpen) {
            emojiOpen = false
        } else if (searching) {
            searching = false
            query = ""
        } else if (editing != null) {
            editing = null
            input = TextFieldValue("")
        } else {
            replyTo = null
        }
    }

    fun clearInput() {
        input = TextFieldValue("")
        vm.setDraft(chatId, "")
    }

    fun jumpTo(id: String) {
        val index = shown.indexOfFirst { it.id == id }
        if (index >= 0) {
            scope.launch { listState.animateScrollToItem(index) }
        } else {
            vm.toast("Tin nhắn nằm ở phần cũ hơn, hãy tải thêm tin cũ")
        }
    }

    fun mentionedIds(text: String): List<String> =
        memberProfiles.filter { it.uid != me.uid && text.contains("@" + it.name) }.map { it.uid }.take(20)

    fun failToast() {
        vm.toast("Không gửi được (mất mạng, đang bị cấm chat hoặc bị chặn)")
    }

    fun submit() {
        val text = input.text.trim()
        if (text.isEmpty() || !canPost) return
        val edit = editing
        val reason = Moderation.scanText(text)
        if (reason != null) {
            clearInput()
            replyTo = null
            editing = null
            scope.launch {
                val penalty = Backend.selfViolation(me, false)
                violation = ViolationInfo(reason, penalty)
            }
            return
        }
        if (edit != null) {
            Backend.editMessage(chatId, edit.id, text) { vm.toast("Không sửa được tin nhắn") }
            editing = null
            clearInput()
            return
        }
        val quote = replyTo
        val ref = if (quote == null) null else ReplyRef(quote.id, previewOf(quote), quote.sender)
        val mentions = if (chat.isGroup) mentionedIds(text) else emptyList()
        Backend.sendMessage(chat, me.uid, text, "", 0, 0, ref, false, mentions) { failToast() }
        replyTo = null
        clearInput()
    }

    fun insertEmoji(emoji: String) {
        val start = input.selection.min.coerceIn(0, input.text.length)
        val end = input.selection.max.coerceIn(start, input.text.length)
        val text = input.text.substring(0, start) + emoji + input.text.substring(end)
        input = TextFieldValue(text, TextRange(start + emoji.length))
    }

    fun insertMention(profile: UserProfile) {
        val cursor = input.selection.start.coerceIn(0, input.text.length)
        val before = input.text.substring(0, cursor)
        val at = before.lastIndexOf('@')
        if (at < 0) return
        val text = before.substring(0, at) + "@" + profile.name + " " + input.text.substring(cursor)
        input = TextFieldValue(text, TextRange(at + 1 + profile.name.length + 1))
    }

    fun startEdit(message: Message) {
        editing = message
        replyTo = null
        input = TextFieldValue(message.text, TextRange(message.text.length))
    }

    fun react(message: Message, emoji: String) {
        val current = message.reactions[me.uid]
        Backend.react(chatId, message.id, me.uid, if (current == emoji) null else emoji)
    }

    fun pin(message: Message) {
        if (chat.pinnedId == message.id) {
            Backend.setPinned(chatId, "", "", "")
        } else {
            Backend.setPinned(chatId, message.id, previewOf(message), me.uid)
        }
    }

    fun forward(message: Message, target: Chat) {
        forwardFor = null
        if (message.image.isEmpty() && Moderation.scanText(message.text) != null) {
            vm.toast("Nội dung này không thể chuyển tiếp")
            return
        }
        Backend.sendMessage(
            target, me.uid, message.text, message.image, message.width, message.height, null, true, emptyList()
        ) { vm.toast("Không chuyển tiếp được (mất mạng, bị cấm chat hoặc nhóm hạn chế)") }
        vm.toast("Đã chuyển tiếp")
    }

    fun report(message: Message, reason: String) {
        reportFor = null
        vm.toast("Đang gửi báo cáo...")
        scope.launch {
            val result = Backend.reportMessage(chatId, message, reason)
            vm.toast(
                when (result) {
                    ReportResult.CONFIRMED -> "Đã xác nhận vi phạm. Tin nhắn bị gỡ và người gửi bị xử lý."
                    ReportResult.QUEUED -> "Đã gửi báo cáo, hệ thống sẽ xem xét."
                    ReportResult.FAILED -> "Không gửi được báo cáo, thử lại sau."
                }
            )
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(6)) { uris ->
        if (uris.isNotEmpty() && canPost) {
            val quote = replyTo
            val ref = if (quote == null) null else ReplyRef(quote.id, previewOf(quote), quote.sender)
            replyTo = null
            scope.launch {
                processing = true
                for (uri in uris) {
                    val outcome = withContext(Dispatchers.IO) {
                        val prepared = ImageUtil.prepare(context, uri, 1080, 260_000)
                        if (prepared == null) null else Pair(prepared, ImageScanner.scan(prepared.bitmap))
                    }
                    if (outcome == null) {
                        vm.toast("Không đọc được một ảnh")
                        continue
                    }
                    val prepared = outcome.first
                    when (outcome.second) {
                        ImageVerdict.SAFE -> Backend.sendMessage(
                            chat, me.uid, "", prepared.b64, prepared.width, prepared.height, ref, false, emptyList()
                        ) { failToast() }
                        ImageVerdict.BLOCK -> vm.toast("Một ảnh có thể chứa nội dung nhạy cảm nên không được gửi")
                        ImageVerdict.VIOLATION -> {
                            val penalty = Backend.selfViolation(me, true)
                            violation = ViolationInfo("Hình ảnh nhạy cảm", penalty)
                        }
                    }
                    if (outcome.second == ImageVerdict.VIOLATION) break
                }
                processing = false
            }
        }
    }

    val typers = typing.filter { typingTick - it.value < 8_000L }.keys
    val title = if (chat.isGroup) chat.name else displayName(otherLive ?: other, nicknames[otherId])
    val subtitle = when {
        chat.isGroup && typers.isNotEmpty() ->
            typers.mapNotNull { UserCache.peek(it)?.name }.joinToString(", ").ifEmpty { "Có người" } + " đang nhập..."
        chat.isGroup -> "${chat.members.size} thành viên"
        typers.isNotEmpty() -> "đang nhập..."
        else -> presenceText(otherLive ?: other, me.hideOnline, minute)
    }

    val mentionQuery: String? = if (!chat.isGroup) {
        null
    } else {
        val text = input.text
        val cursor = input.selection.start.coerceIn(0, text.length)
        val before = text.substring(0, cursor)
        val at = before.lastIndexOf('@')
        if (at < 0 || (at > 0 && !before[at - 1].isWhitespace())) {
            null
        } else {
            val partial = before.substring(at + 1)
            if (partial.length > 20 || partial.any { it.isWhitespace() }) null else partial
        }
    }
    val suggestions = if (mentionQuery == null) {
        emptyList()
    } else {
        val needle = foldVi(mentionQuery)
        memberProfiles.filter { it.uid != me.uid && foldVi(it.name).contains(needle) }.take(5)
    }

    Column(Modifier.fillMaxSize()) {
        if (searching) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconBtn(Icons.AutoMirrored.Filled.ArrowBack, {
                    searching = false
                    query = ""
                })
                GlassField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Tìm trong cuộc trò chuyện",
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconBtn(Icons.AutoMirrored.Filled.ArrowBack, { vm.back() })
                Row(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            if (chat.isGroup) vm.push(Route.GroupInfo(chatId)) else vm.push(Route.User(otherId))
                        }
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (chat.isGroup) GroupAvatar(42.dp, chat.avatar) else Avatar(otherLive ?: other, "?", 42.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Label(title, size = 17, weight = FontWeight.Bold, maxLines = 1)
                        Label(subtitle, size = 12, color = c.textSoft, maxLines = 1)
                    }
                }
                IconBtn(Icons.Rounded.Search, { searching = true })
            }
        }

        if (chat.pinnedId.isNotEmpty() && !searching) {
            Glass(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                RoundedCornerShape(14.dp)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { jumpTo(chat.pinnedId) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.PushPin, null, Modifier.size(16.dp), tint = c.textSoft)
                    Spacer(Modifier.width(8.dp))
                    Label(chat.pinnedText.ifEmpty { "Tin nhắn được ghim" }, Modifier.weight(1f), size = 13, maxLines = 1)
                    if (canPin) {
                        IconBtn(Icons.Rounded.Close, { Backend.setPinned(chatId, "", "", "") }, size = 28.dp)
                    }
                }
            }
        }

        if (shown.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Label(
                    if (query.isNotBlank()) "Không có tin nhắn phù hợp" else "Hãy gửi lời chào đầu tiên",
                    color = c.textSoft
                )
            }
        } else {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                reverseLayout = true,
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                itemsIndexed(shown, key = { _, message -> message.id }) { index, message ->
                    val older = shown.getOrNull(index + 1)
                    val newer = shown.getOrNull(index - 1)
                    val mine = message.sender == me.uid
                    Column {
                        if (older == null || Fmt.dayKey(older.time) != Fmt.dayKey(message.time)) {
                            DateChip(Fmt.dayLabel(message.time))
                        }
                        MessageRow(
                            message = message,
                            mine = mine,
                            showName = chat.isGroup && !mine && older?.sender != message.sender,
                            showAvatar = !mine && newer?.sender != message.sender,
                            seen = index == 0 && mine && otherRead > 0L && otherRead >= message.time,
                            myUid = me.uid,
                            nicknames = nicknames,
                            onLong = { if (!message.flagged) actionFor = message },
                            onImage = { viewer = message.image },
                            onReact = { emoji -> react(message, emoji) },
                            onJump = { id -> jumpTo(id) }
                        )
                    }
                }
                if (shown.size >= limit && query.isBlank()) {
                    item(key = "load_more") {
                        Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                            NestButton("Tải tin nhắn cũ hơn", { limit += 40 }, primary = false)
                        }
                    }
                }
            }
        }

        when {
            banned -> Banner("Bạn đang bị cấm chat đến ${Fmt.full(me.bannedUntil)}.")
            chat.disbanded -> Banner("Nhóm này đã giải tán.", danger = false)
            iBlocked -> Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Label("Bạn đã chặn người này.", Modifier.weight(1f), color = c.textSoft, size = 14)
                NestButton("Bỏ chặn", { Backend.unblock(me.uid, otherId) }, primary = false)
            }
            chat.onlyAdminsPost && !isAdmin -> Banner("Chỉ quản trị viên được nhắn trong nhóm này.", danger = false)
            else -> Column(Modifier.fillMaxWidth()) {
                if (suggestions.isNotEmpty()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (profile in suggestions) {
                            Glass(shape = RoundedCornerShape(16.dp), strong = true) {
                                Row(
                                    Modifier
                                        .clickable { insertMention(profile) }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Avatar(profile, profile.handle, 24.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Label(profile.name, size = 14, maxLines = 1)
                                }
                            }
                        }
                    }
                }

                val replying = replyTo
                val editingMessage = editing
                if (replying != null || editingMessage != null) {
                    val target = editingMessage ?: replying
                    Glass(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min)
                                .padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .width(3.dp)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(c.badge)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Label(
                                    if (editingMessage != null) {
                                        "Đang sửa tin nhắn"
                                    } else {
                                        "Trả lời " + if (target?.sender == me.uid) "chính bạn" else displayName(
                                            UserCache.peek(target?.sender.orEmpty()),
                                            nicknames[target?.sender.orEmpty()]
                                        )
                                    },
                                    size = 12,
                                    weight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                                Label(target?.let { previewOf(it) }.orEmpty(), size = 13, color = c.textSoft, maxLines = 1)
                            }
                            IconBtn(Icons.Rounded.Close, {
                                replyTo = null
                                if (editingMessage != null) {
                                    editing = null
                                    input = TextFieldValue("")
                                }
                            }, size = 36.dp)
                        }
                    }
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    if (processing) {
                        Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(22.dp), color = c.text, strokeWidth = 2.dp)
                        }
                    } else {
                        IconBtn(
                            Icons.Rounded.PhotoLibrary,
                            { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            size = 46.dp
                        )
                    }
                    IconBtn(
                        Icons.Rounded.EmojiEmotions,
                        {
                            if (emojiOpen) {
                                emojiOpen = false
                            } else {
                                focusManager.clearFocus()
                                emojiOpen = true
                            }
                        },
                        size = 46.dp
                    )
                    Glass(Modifier.weight(1f), RoundedCornerShape(24.dp)) {
                        Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            if (input.text.isEmpty()) Label("Nhắn tin...", color = c.textSoft, size = 16)
                            BasicTextField(
                                value = input,
                                onValueChange = { value ->
                                    val changed = value.text != input.text
                                    if (value.text.length <= 2000) input = value
                                    if (changed && value.text.isNotEmpty() && !me.hideOnline) {
                                        val stamp = System.currentTimeMillis()
                                        if (stamp - lastTypingSent > 4_000L) {
                                            lastTypingSent = stamp
                                            Backend.sendTyping(chatId, me.uid)
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { if (it.isFocused) emojiOpen = false },
                                maxLines = 5,
                                textStyle = TextStyle(color = c.text, fontSize = 16.sp),
                                cursorBrush = SolidColor(c.text)
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    val canSend = input.text.isNotBlank()
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(if (canSend) c.accent else c.glassStrong)
                            .clickable(enabled = canSend) { submit() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            null,
                            Modifier.size(22.dp),
                            tint = if (canSend) c.onAccent else c.textSoft
                        )
                    }
                }

                if (emojiOpen) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(8),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .padding(horizontal = 8.dp)
                    ) {
                        gridItems(emojiList) { emoji ->
                            Box(
                                Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { insertEmoji(emoji) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(emoji, fontSize = 24.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    actionFor?.let { target ->
        val mineMessage = target.sender == me.uid
        GlassDialog(title = "Tin nhắn", onDismiss = { actionFor = null }) {
            if (!target.recalled) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    for (emoji in quickReactions) {
                        val active = target.reactions[me.uid] == emoji
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (active) c.glassStrong else Color.Transparent)
                                .clickable {
                                    react(target, emoji)
                                    actionFor = null
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(emoji, fontSize = 22.sp)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                if (canPost) {
                    ActionRow("Trả lời", {
                        replyTo = target
                        editing = null
                        actionFor = null
                    })
                }
                if (target.text.isNotEmpty()) {
                    ActionRow("Sao chép", {
                        clipboard.setText(AnnotatedString(target.text))
                        vm.toast("Đã sao chép")
                        actionFor = null
                    })
                }
                ActionRow("Chuyển tiếp", {
                    forwardFor = target
                    actionFor = null
                })
                if (canPin) {
                    ActionRow(if (chat.pinnedId == target.id) "Bỏ ghim" else "Ghim tin nhắn", {
                        pin(target)
                        actionFor = null
                    })
                }
                if (mineMessage && target.image.isEmpty() && canPost) {
                    ActionRow("Sửa tin nhắn", {
                        startEdit(target)
                        actionFor = null
                    })
                }
                if (mineMessage) {
                    ActionRow("Thu hồi với mọi người", {
                        Backend.recallMessage(chatId, target.id) { vm.toast("Không thu hồi được tin nhắn") }
                        actionFor = null
                    }, danger = true)
                }
            }
            ActionRow("Xóa ở phía tôi", {
                Backend.hideMessageForMe(chatId, target.id)
                actionFor = null
            }, danger = true)
            if (!mineMessage && !target.recalled) {
                ActionRow("Báo cáo tin nhắn", {
                    reportFor = target
                    actionFor = null
                }, danger = true)
            }
        }
    }

    reportFor?.let { target ->
        GlassDialog(title = "Lý do báo cáo", onDismiss = { reportFor = null }, dismissText = "Hủy") {
            for (reason in reportReasons) {
                ActionRow(reason, { report(target, reason) })
            }
        }
    }

    forwardFor?.let { target ->
        GlassDialog(title = "Chuyển tiếp tới", onDismiss = { forwardFor = null }, dismissText = "Hủy") {
            Column(
                Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                for (destination in allChats.filter { it.lastSender.isNotEmpty() && !it.disbanded }) {
                    ForwardRow(destination, me.uid, nicknames) { forward(target, destination) }
                }
            }
        }
    }

    violation?.let { info ->
        GlassDialog(title = "Nội dung vi phạm", onDismiss = { violation = null }, dismissText = "Đã hiểu") {
            Label("Lý do: ${info.reason}", weight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Label("Xử lý: ${info.penalty.label}", color = c.danger, weight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Label(
                "Tin nhắn đã bị chặn và không được gửi. Xem chi tiết trong Cài đặt > Tài khoản.",
                size = 13,
                color = c.textSoft
            )
        }
    }

    viewer?.let { b64 -> ImageViewer(b64) { viewer = null } }
}

@Composable
private fun ForwardRow(chat: Chat, myUid: String, nicknames: Map<String, String>, onClick: () -> Unit) {
    val otherId = if (chat.isGroup) "" else chat.members.firstOrNull { it != myUid }.orEmpty()
    val other by rememberUser(otherId)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (chat.isGroup) GroupAvatar(38.dp, chat.avatar) else Avatar(other, "?", 38.dp)
        Spacer(Modifier.width(12.dp))
        Label(
            if (chat.isGroup) chat.name else displayName(other, nicknames[otherId]),
            Modifier.weight(1f),
            maxLines = 1
        )
    }
}

@Composable
private fun DateChip(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Glass(shape = RoundedCornerShape(12.dp)) {
            Label(text, Modifier.padding(horizontal = 12.dp, vertical = 4.dp), size = 12, color = Nest.colors.textSoft)
        }
    }
}

@Composable
private fun RichLabel(text: AnnotatedString, color: Color) {
    Text(text = text, color = color, fontSize = 16.sp, lineHeight = 21.sp)
}

@Composable
private fun QuoteBlock(name: String, text: String, onClick: () -> Unit) {
    val c = Nest.colors
    Row(
        Modifier
            .widthIn(max = 260.dp)
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(12.dp))
            .background(c.glass)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(c.badge)
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Label(name, size = 12, weight = FontWeight.SemiBold, maxLines = 1)
            Label(text, size = 13, color = c.textSoft, maxLines = 2)
        }
    }
}

@Composable
private fun ReactionRow(reactions: Map<String, String>, myUid: String, onReact: (String) -> Unit) {
    val c = Nest.colors
    val counts = remember(reactions) {
        reactions.values.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
    }
    Row(Modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (entry in counts) {
            val active = reactions[myUid] == entry.key
            Box(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (active) c.glassStrong else c.glass)
                    .border(1.dp, if (active) c.badge else c.strokeLo, RoundedCornerShape(12.dp))
                    .clickable { onReact(entry.key) }
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Label("${entry.key} ${entry.value}", size = 12)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageRow(
    message: Message,
    mine: Boolean,
    showName: Boolean,
    showAvatar: Boolean,
    seen: Boolean,
    myUid: String,
    nicknames: Map<String, String>,
    onLong: () -> Unit,
    onImage: () -> Unit,
    onReact: (String) -> Unit,
    onJump: (String) -> Unit
) {
    val c = Nest.colors
    val sender by rememberUser(if (mine) "" else message.sender)
    val shape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (mine) 18.dp else 5.dp,
        bottomEnd = if (mine) 5.dp else 18.dp
    )

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!mine) {
            if (showAvatar) Avatar(sender, "?", 30.dp) else Spacer(Modifier.size(30.dp))
            Spacer(Modifier.width(8.dp))
        }
        Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
            if (showName) {
                Label(
                    displayName(sender, nicknames[message.sender]),
                    Modifier.padding(start = 6.dp, bottom = 2.dp),
                    size = 12,
                    color = c.textSoft,
                    maxLines = 1
                )
            }
            if (message.fwd && !message.recalled && !message.flagged) {
                Label("Đã chuyển tiếp", Modifier.padding(horizontal = 6.dp), size = 11, color = c.textSoft)
            }
            if (message.replyTo.isNotEmpty() && !message.recalled && !message.flagged) {
                QuoteBlock(
                    if (message.replySender == myUid) "Bạn" else displayName(
                        UserCache.peek(message.replySender),
                        nicknames[message.replySender]
                    ),
                    message.replyText,
                    { onJump(message.replyTo) }
                )
                Spacer(Modifier.height(3.dp))
            }
            val meta = if (mine) c.onMine.copy(alpha = 0.6f) else c.textSoft
            when {
                message.flagged -> Box(
                    Modifier
                        .widthIn(max = 280.dp)
                        .clip(shape)
                        .background(c.glass)
                        .border(1.dp, c.strokeLo, shape)
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                ) {
                    Label("Tin nhắn đã bị gỡ do vi phạm quy tắc cộng đồng", size = 14, color = c.textSoft)
                }
                message.recalled -> Box(
                    Modifier
                        .widthIn(max = 280.dp)
                        .clip(shape)
                        .background(c.glass)
                        .border(1.dp, c.strokeLo, shape)
                        .combinedClickable(onClick = { }, onLongClick = onLong)
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                ) {
                    Label("Tin nhắn đã được thu hồi", size = 14, color = c.textSoft)
                }
                message.image.isNotEmpty() -> {
                    val image by rememberB64Image(message.image, 640)
                    val ratio = if (message.width > 0 && message.height > 0) {
                        (message.width.toFloat() / message.height).coerceIn(0.6f, 1.7f)
                    } else 1f
                    Box(
                        Modifier
                            .width(230.dp)
                            .aspectRatio(ratio)
                            .clip(shape)
                            .background(c.glassStrong)
                            .combinedClickable(onClick = onImage, onLongClick = onLong)
                    ) {
                        val bitmap = image
                        if (bitmap != null) {
                            Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        } else {
                            CircularProgressIndicator(
                                Modifier.size(24.dp).align(Alignment.Center),
                                color = c.text,
                                strokeWidth = 2.dp
                            )
                        }
                        Box(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x66000000))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Label(
                                Fmt.clock(message.time) + if (mine) (if (message.pending) "  Đang gửi" else "  ✓") else "",
                                size = 10,
                                color = Color.White
                            )
                        }
                    }
                }
                else -> {
                    val fg = if (mine) c.onMine else c.text
                    val rich = remember(message.text, mine, message.mentions) {
                        richText(message.text, if (mine) c.onMine else c.badge, message.mentions.isNotEmpty())
                    }
                    Box(
                        Modifier
                            .widthIn(max = 280.dp)
                            .clip(shape)
                            .background(if (mine) c.mine else c.glassStrong)
                            .then(if (!mine) Modifier.border(1.dp, c.strokeLo, shape) else Modifier)
                            .combinedClickable(onClick = { }, onLongClick = onLong)
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Column {
                            RichLabel(rich, fg)
                            Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                                if (message.edited) Label("đã sửa  ", size = 10, color = meta)
                                Label(Fmt.clock(message.time), size = 10, color = meta)
                                if (mine) Label(if (message.pending) "  Đang gửi" else "  ✓", size = 10, color = meta)
                            }
                        }
                    }
                }
            }
            if (message.reactions.isNotEmpty() && !message.recalled && !message.flagged) {
                ReactionRow(message.reactions, myUid, onReact)
            }
            if (seen) {
                Label("Đã xem", Modifier.padding(top = 2.dp, end = 4.dp), size = 11, color = c.textSoft)
            }
        }
    }
}
