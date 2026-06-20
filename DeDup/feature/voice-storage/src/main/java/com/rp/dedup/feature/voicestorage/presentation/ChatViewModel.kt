package com.rp.dedup.feature.voicestorage.presentation

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.google.firebase.analytics.FirebaseAnalytics
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.rp.dedup.feature.voicestorage.data.model.MediaType
import com.rp.dedup.feature.voicestorage.data.model.SpeechResult
import com.rp.dedup.feature.voicestorage.data.model.StorageItem
import com.rp.dedup.feature.voicestorage.data.repository.ChatHistoryRepository
import com.rp.dedup.feature.voicestorage.data.repository.LocalStorageRepository
import com.rp.dedup.feature.voicestorage.data.source.VoiceCaptureDataSource
import com.rp.dedup.feature.voicestorage.domain.FilterConfig
import com.rp.dedup.feature.voicestorage.domain.SortBy
import com.rp.dedup.feature.voicestorage.domain.SortOrder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

val DEFAULT_SUGGESTIONS = listOf(
    "Find duplicate photos",
    "What's taking up space?",
    "How does this work?",
    "Is my data safe?"
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val streamingText: String = "",
    val isStreaming: Boolean = false,
    val suggestions: List<String> = DEFAULT_SUGGESTIONS,
    val isListening: Boolean = false,
    val partialTranscript: String = "",
    val micError: String? = null,
    val isTtsEnabled: Boolean = false,
    val isTtsSpeaking: Boolean = false,
    val isVibrationEnabled: Boolean = true,
    val previewItems: List<StorageItem> = emptyList(),
    val isPreviewExpanded: Boolean = false,
    val selectedUris: Set<Uri> = emptySet(),
    val showDeleteConfirmation: Boolean = false,
)

private data class StorageQuery(
    val config: FilterConfig,
    val limit: Int,
    val mode: String,   // "list" | "count" | "summary"
    val label: String,
    val suggestions: List<String>,
)

class ChatViewModel(
    private val repository: ChatHistoryRepository,
    private val voiceSource: VoiceCaptureDataSource,
    private val storageRepo: LocalStorageRepository,
    appContext: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var listenJob: Job? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val fa = FirebaseAnalytics.getInstance(appContext)

    private fun track(event: String, block: (Bundle.() -> Unit)? = null) {
        val bundle = if (block != null) Bundle().apply(block) else null
        fa.logEvent(event, bundle)
    }

    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        appContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private enum class Haptic { SEND, TYPING, MIC_ON, MIC_RESULT, ERROR }

    private fun haptic(h: Haptic) {
        if (!_state.value.isVibrationEnabled) return
        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = when (h) {
                // Crisp tap — message sent
                Haptic.SEND       -> VibrationEffect.createOneShot(28, 180)
                // Feather-light key-press feel — fires per character during AI response
                Haptic.TYPING     -> VibrationEffect.createOneShot(7, 48)
                // Medium pulse — mic activated
                Haptic.MIC_ON     -> VibrationEffect.createOneShot(35, 160)
                // Quick double-tap — voice captured
                Haptic.MIC_RESULT -> VibrationEffect.createWaveform(
                    longArrayOf(0, 22, 32, 22), intArrayOf(0, 150, 0, 150), -1)
                // Two firm beats — something went wrong
                Haptic.ERROR      -> VibrationEffect.createWaveform(
                    longArrayOf(0, 55, 75, 55), intArrayOf(0, 220, 0, 220), -1)
            }
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            when (h) {
                Haptic.SEND, Haptic.MIC_ON -> vibrator.vibrate(28)
                Haptic.TYPING              -> vibrator.vibrate(7)
                Haptic.MIC_RESULT          -> vibrator.vibrate(longArrayOf(0, 22, 32, 22), -1)
                Haptic.ERROR               -> vibrator.vibrate(longArrayOf(0, 55, 75, 55), -1)
            }
        }
    }

    fun toggleVibration() {
        val enabling = !_state.value.isVibrationEnabled
        _state.update { it.copy(isVibrationEnabled = enabling) }
        track("ai_vibration_toggled") { putString("enabled", enabling.toString()) }
        // Give tactile confirmation when turning on: simulate a few typing pulses
        if (enabling) viewModelScope.launch {
            repeat(4) { haptic(Haptic.TYPING); delay(60) }
        }
    }

    init {
        track("ai_chat_opened")
        val history = repository.load()
        _state.update { it.copy(messages = history.ifEmpty { listOf(WELCOME_MSG) }) }
        if (history.isEmpty()) repository.save(listOf(WELCOME_MSG))

        tts = TextToSpeech(appContext, ::onTtsInit)
    }

    private fun onTtsInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) {
            tts?.language = Locale.getDefault()
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) = _state.update { it.copy(isTtsSpeaking = true) }
                override fun onDone(id: String?)  = _state.update { it.copy(isTtsSpeaking = false) }
                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) = _state.update { it.copy(isTtsSpeaking = false) }
            })
        }
    }

    override fun onCleared() {
        tts?.shutdown()
        tts = null
        super.onCleared()
    }

    fun toggleTts() {
        val enabling = !_state.value.isTtsEnabled
        _state.update { it.copy(isTtsEnabled = enabling) }
        track("ai_tts_toggled") { putString("enabled", enabling.toString()) }
        if (!enabling) {
            tts?.stop()
            _state.update { it.copy(isTtsSpeaking = false) }
        }
    }

    fun send(text: String) {
        if (text.isBlank()) return
        tts?.stop()
        _state.update { it.copy(isTtsSpeaking = false) }
        val userMsg = ChatMessage(text = text.trim(), isUser = true)
        commit(userMsg)
        haptic(Haptic.SEND)
        _state.update { it.copy(suggestions = emptyList()) }

        viewModelScope.launch {
            delay(300)
            val storageQuery = detectStorageQuery(text.trim())
            val (response, nextSuggestions) = if (storageQuery != null) {
                track("ai_storage_query") {
                    putString("intent", storageQuery.mode)
                    putString("label", storageQuery.label)
                }
                val items = runCatching {
                    storageRepo.queryFiles("", storageQuery.config).first()
                }.getOrDefault(emptyList())
                val previewable = if (storageQuery.mode == "count") emptyList() else items.take(storageQuery.limit)
                _state.update { it.copy(previewItems = previewable, isPreviewExpanded = false, selectedUris = emptySet()) }
                formatStorageResult(items, storageQuery)
            } else {
                track("ai_chat_message_sent")
                _state.update { it.copy(previewItems = emptyList()) }
                buildResponse(text.trim(), _state.value.messages)
            }
            _state.update { it.copy(isStreaming = true, streamingText = "") }

            var streamed = ""
            var typingPulse = 0
            for (char in response) {
                streamed += char
                _state.update { it.copy(streamingText = streamed) }
                // Typing haptic: every 3rd letter/digit feels like keyboard key-presses
                if (char.isLetterOrDigit()) {
                    typingPulse++
                    if (typingPulse % 3 == 0) haptic(Haptic.TYPING)
                }
                delay(when (char) {
                    '.', '!', '?' -> 35L
                    ','           -> 18L
                    ' '           ->  7L
                    else          -> 11L
                })
            }
            commit(ChatMessage(text = response, isUser = false))
            _state.update { it.copy(isStreaming = false, streamingText = "", suggestions = nextSuggestions) }

            if (_state.value.isTtsEnabled && ttsReady) {
                tts?.speak(response, TextToSpeech.QUEUE_FLUSH, null, "bot_response")
            }
        }
    }

    fun startListening() {
        listenJob?.cancel()
        _state.update { it.copy(isListening = true, micError = null, partialTranscript = "") }
        haptic(Haptic.MIC_ON)
        listenJob = viewModelScope.launch {
            voiceSource.transcriptionFlow()
                .catch { e ->
                    haptic(Haptic.ERROR)
                    _state.update { it.copy(isListening = false, micError = e.message) }
                }
                .collect { result ->
                    when (result) {
                        is SpeechResult.Partial -> _state.update { it.copy(partialTranscript = result.text) }
                        is SpeechResult.Final -> {
                            haptic(Haptic.MIC_RESULT)
                            _state.update { it.copy(isListening = false, partialTranscript = "") }
                            send(result.text)
                        }
                        is SpeechResult.Error -> {
                            haptic(Haptic.ERROR)
                            _state.update { it.copy(isListening = false, partialTranscript = "", micError = "Could not understand — try again") }
                        }
                    }
                }
        }
    }

    fun stopListening() {
        listenJob?.cancel()
        listenJob = null
        _state.update { it.copy(isListening = false, partialTranscript = "") }
    }

    fun clearMicError() = _state.update { it.copy(micError = null) }

    fun clearHistory() {
        repository.clear()
        _state.update { it.copy(messages = listOf(WELCOME_MSG), suggestions = DEFAULT_SUGGESTIONS, previewItems = emptyList()) }
        repository.save(listOf(WELCOME_MSG))
    }

    fun expandPreview() {
        track("ai_file_preview_opened") { putInt("file_count", _state.value.previewItems.size) }
        _state.update { it.copy(isPreviewExpanded = true) }
    }
    fun collapsePreview() = _state.update { it.copy(isPreviewExpanded = false, selectedUris = emptySet()) }

    fun toggleSelectItem(uri: Uri) = _state.update { s ->
        val next = s.selectedUris.toMutableSet().apply { if (uri in this) remove(uri) else add(uri) }
        s.copy(selectedUris = next)
    }

    fun requestDeletion() = _state.update { it.copy(showDeleteConfirmation = true) }
    fun dismissDeletion() = _state.update { it.copy(showDeleteConfirmation = false) }

    fun onDeletionResult(granted: Boolean) {
        if (granted) {
            val deleted = _state.value.selectedUris.size
            val freedBytes = _state.value.previewItems
                .filter { it.uri in _state.value.selectedUris }
                .sumOf { it.sizeInBytes }
            track("ai_files_deleted") {
                putInt("deleted_count", deleted)
                putLong("freed_bytes", freedBytes)
            }
            _state.update { s ->
                s.copy(
                    previewItems           = s.previewItems.filterNot { it.uri in s.selectedUris },
                    selectedUris           = emptySet(),
                    showDeleteConfirmation = false,
                )
            }
        } else {
            _state.update { it.copy(showDeleteConfirmation = false) }
        }
    }

    private fun commit(msg: ChatMessage) {
        val next = _state.value.messages + msg
        _state.update { it.copy(messages = next) }
        repository.save(next)
    }

    // ── Storage query resolution ──────────────────────────────────────────────

    private fun String.has(vararg kw: String) = kw.any { this.contains(it) }

    private fun detectStorageQuery(q: String): StorageQuery? {
        val t = q.lowercase().trim()

        // ── Media type ──────────────────────────────────────────────────────
        val isVideo = t.has("video", "movie", "clip", "reel", "film", "recording")
        val isPhoto = t.has("photo", "image", "picture", "selfie", "screenshot", "burst", "gallery", "pic")
        val isAudio = t.has("audio", "music", "song", "podcast", "voice memo", "sound", "track", "mp3")
        val isDoc   = t.has("document", "pdf", "doc", "file", "download", "apk", "zip")

        // mp3/mp4 keywords sit in isDoc but belong to proper media types
        val isVideoStrict = isVideo || t.has("mp4", "mkv", "avi", "mov")
        val isAudioStrict = isAudio || t.has("mp3", "aac", "flac", "wav", "ogg")
        val types: Set<MediaType> = when {
            isVideoStrict && !isPhoto && !isAudioStrict           -> setOf(MediaType.VIDEO)
            isPhoto && !isVideoStrict && !isAudioStrict           -> setOf(MediaType.IMAGE)
            isAudioStrict && !isVideoStrict && !isPhoto           -> setOf(MediaType.AUDIO)
            isDoc   && !isVideoStrict && !isPhoto && !isAudioStrict -> setOf(MediaType.DOCUMENT)
            else                                                    -> setOf(MediaType.IMAGE, MediaType.VIDEO)
        }
        // ── Specific file-type MIME mapping ────────────────────────────────
        val specificMime: String? = when {
            t.has("pdf")                              -> "application/pdf"
            t.has("apk")                              -> "application/vnd.android.package-archive"
            t.has("zip")                              -> "application/zip"
            t.has("docx", "word")                     -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            t.has("xlsx", "excel", "spreadsheet")     -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            t.has("pptx", "powerpoint", "presentation") -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            t.has("txt", "text file", "plain text")   -> "text/plain"
            t.has("mp3")                              -> "audio/mpeg"
            t.has("mp4")                              -> "video/mp4"
            else                                      -> null
        }
        // For DOCUMENT type: use specific MIME if known, else broad wildcard
        val mimeFilter: String? = when {
            specificMime != null -> specificMime
            types == setOf(MediaType.DOCUMENT) -> "application/%"
            else -> null
        }
        // Refine label when user named a specific format
        val specificLabel: String? = when (specificMime) {
            "application/pdf"  -> "PDF files"
            "application/vnd.android.package-archive" -> "APK files"
            "application/zip"  -> "ZIP files"
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "Word documents"
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"       -> "Excel files"
            "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> "PowerPoint files"
            "text/plain"       -> "text files"
            "audio/mpeg"       -> "MP3 files"
            "video/mp4"        -> "MP4 files"
            else               -> null
        }
        val label = specificLabel ?: when (types) {
            setOf(MediaType.VIDEO)    -> "videos"
            setOf(MediaType.IMAGE)    -> "photos"
            setOf(MediaType.AUDIO)    -> "audio files"
            setOf(MediaType.DOCUMENT) -> "documents"
            else                      -> "files"
        }

        // ── Parse explicit count from query ("top 10", "5 largest", etc.) ─
        val explicitLimit = Regex("""\b(\d+)\b""").find(t)?.groupValues?.get(1)?.toIntOrNull()
            ?.coerceIn(1, 50)

        // ── Intent ─────────────────────────────────────────────────────────
        val wantsShow = t.has(
            "show", "list", "display", "find", "get me", "give me",
            "what are", "what do i have", "what's on", "whats on",
            "tell me about my", "look at", "see my", "view my", "browse"
        )
        val wantsCount = t.has(
            "how many", "count", "number of", "total number",
            "how much space", "how much storage", "how big", "total size", "total space"
        )
        val wantsLargest = t.has(
            "large", "largest", "biggest", "biggest", "heaviest", "heavy",
            "most space", "taking up", "taking space", "using space",
            "eating space", "eating up", "eating my", "using my storage",
            "using storage", "consuming", "big file", "size"
        ) || (t.has("space", "storage") && t.has("most", "top", "high", "which", "what"))
        val wantsSmall   = t.has("small", "tiny", "smallest", "least space", "lightest", "micro")
        val wantsRecent  = t.has(
            "recent", "latest", "newest", "last added", "new file", "added recently",
            "just added", "today", "this week", "new photo", "new video", "last photo", "last video"
        )
        val wantsOldest  = t.has("oldest", "old file", "earliest", "long ago", "old photo", "old video", "from old", "archive")

        // No storage intent at all — let conversational handler deal with it
        val hasIntent = wantsShow || wantsCount || wantsLargest || wantsSmall || wantsRecent || wantsOldest
        val hasType   = isVideo || isPhoto || isAudio || isDoc
        if (!hasIntent && !hasType) return null
        // Bare type mention with no action ("photos" alone) → conversational
        if (!hasIntent && hasType) return null

        val n = explicitLimit ?: 7
        fun cfg(sortBy: SortBy, sortOrder: SortOrder) =
            FilterConfig(mediaTypes = types, sortBy = sortBy, sortOrder = sortOrder, mimeTypeFilter = mimeFilter)

        return when {
            wantsCount -> StorageQuery(
                config      = cfg(SortBy.SIZE, SortOrder.DESC),
                limit       = Int.MAX_VALUE,
                mode        = "count",
                label       = label,
                suggestions = listOf("Show my largest $label", "Find duplicate $label", "Show recent $label"),
            )
            wantsSmall -> StorageQuery(
                config      = cfg(SortBy.SIZE, SortOrder.ASC),
                limit       = n,
                mode        = "list",
                label       = "smallest $label",
                suggestions = listOf("Show largest $label", "Find duplicates", "How many $label do I have?"),
            )
            wantsOldest -> StorageQuery(
                config      = cfg(SortBy.DATE_ADDED, SortOrder.ASC),
                limit       = n,
                mode        = "list",
                label       = "oldest $label",
                suggestions = listOf("Show newest $label", "Show largest $label", "Find duplicates"),
            )
            wantsLargest && !hasType -> StorageQuery(
                config      = FilterConfig(mediaTypes = setOf(MediaType.IMAGE, MediaType.VIDEO), sortBy = SortBy.SIZE, sortOrder = SortOrder.DESC),
                limit       = n,
                mode        = "list",
                label       = "largest files",
                suggestions = listOf("How many photos do I have?", "Find duplicates", "Show recent photos"),
            )
            wantsLargest -> StorageQuery(
                config      = cfg(SortBy.SIZE, SortOrder.DESC),
                limit       = n,
                mode        = "list",
                label       = "largest $label",
                suggestions = listOf("Find duplicate $label", "How many $label do I have?", "Show recent $label"),
            )
            else -> StorageQuery(
                config      = cfg(SortBy.DATE_ADDED, SortOrder.DESC),
                limit       = n,
                mode        = "list",
                label       = if (wantsRecent) "most recently added $label" else label,
                suggestions = listOf("Show largest $label", "Find duplicates", "How many $label do I have?"),
            )
        }
    }

    private fun formatStorageResult(items: List<StorageItem>, query: StorageQuery): Pair<String, List<String>> {
        if (items.isEmpty()) {
            return "I couldn't find any ${query.label} on your device. Storage permission may be needed, or there are no files of that type." to query.suggestions
        }

        return when (query.mode) {
            "count" -> {
                val total = items.sumOf { it.sizeInBytes }
                "You have ${"%,d".format(items.size)} ${query.label} on your device, taking up ${total.fmtBytes()} in total." to query.suggestions
            }
            else -> {
                val top = items.take(query.limit)
                val topTotal = top.sumOf { it.sizeInBytes }
                val rows = top.mapIndexed { i, item ->
                    val type = when (item.mediaType) {
                        MediaType.VIDEO    -> "Video"
                        MediaType.IMAGE    -> "Photo"
                        MediaType.AUDIO    -> "Audio"
                        MediaType.DOCUMENT -> "Doc"
                    }
                    "  ${i + 1}. ${item.displayName.cap(32)}  —  ${item.sizeInBytes.fmtBytes()}  ($type)"
                }.joinToString("\n")

                val footer = "\n\nCombined: ${topTotal.fmtBytes()}. Want me to scan these for duplicates?"
                "Here are your ${query.label}:\n\n$rows$footer" to query.suggestions
            }
        }
    }

    private fun Long.fmtBytes(): String = when {
        this >= 1_073_741_824L -> "%.1f GB".format(this / 1_073_741_824.0)
        this >= 1_048_576L     -> "%.1f MB".format(this / 1_048_576.0)
        this >= 1_024L         -> "%.0f KB".format(this / 1_024.0)
        else                   -> "$this B"
    }

    private fun String.cap(max: Int) = if (length > max) take(max - 1) + "…" else this

    // ── Conversational response engine ────────────────────────────────────────

    private fun buildResponse(query: String, context: List<ChatMessage>): Pair<String, List<String>> {
        val q = query.lowercase().trim()
        val lastBotText = context.lastOrNull { !it.isUser }?.text?.lowercase() ?: ""

        // Greetings
        if (q.matches(Regex("(hi|hello|hey|howdy|sup|what'?s up|yo|hiya)[!.]?.*")))
            return greetings.random() to listOf("Find duplicate photos", "What's taking up space?", "How does this work?")

        // Affirmations — context-driven
        if (q.matches(Regex("(yes|yeah|yep|sure|ok|okay|definitely|please|go ahead|do it|alright)[!.]?")))
            return affirmation(lastBotText) to listOf("Tell me more", "What else can you do?", "How long does it take?")

        // Negations
        if (q.matches(Regex("(no|nope|nah|not now|maybe later|never mind|nevermind|cancel)[!.]?")))
            return "No worries at all — I'm here whenever you're ready. Is there something else I can help you with?" to
                    listOf("What can you do?", "How does this work?", "Free up space")

        // Thank you
        if (q.contains("thank") || q == "thanks" || q == "ty" || q == "thx")
            return thanks.random() to listOf("What else can you do?", "Scan my photos", "Free up space")

        // How are you
        if (q.contains("how are you") || q.contains("you good") || q.contains("how r u"))
            return "Running at full speed — entirely on your device, no cloud needed. Ready when you are. What are we cleaning up?" to
                    listOf("Find duplicates", "Free up space", "What can you do?")

        // Capabilities
        if (q.contains("what can you do") || q.contains("help me") || q.contains("capabilit") || q.contains("feature"))
            return "Here's my full toolkit: I find duplicate photos using visual AI, catch duplicate videos, scan for redundant files and documents, clean up WhatsApp downloads, and deduplicate contacts. Everything runs locally on your device — no uploads, no cloud, no account needed. Where do you want to start?" to
                    listOf("Find duplicate photos", "Clean up WhatsApp", "Deduplicate contacts")

        // Duplicates
        if (q.contains("duplicate") || q.contains("dupl") || q.contains("dedup") || q.contains("same file") || q.contains("copies"))
            return duplicates.random() to listOf("Start a photo scan", "Scan for videos too", "How accurate is it?")

        // Photos / images
        if (q.contains("photo") || q.contains("image") || q.contains("picture") || q.contains("selfie") || q.contains("burst") || q.contains("screenshot"))
            return photos.random() to listOf("Start image scan", "What about videos?", "How does the AI work?")

        // Videos
        if (q.contains("video") || q.contains("movie") || q.contains("clip") || q.contains("reel"))
            return videos.random() to listOf("Start video scan", "What about photos?", "How much space will I recover?")

        // Storage / space
        if (q.contains("storage") || q.contains("space") || q.contains("free up") || q.contains("full") || q.contains(" gb") || q.contains(" mb") || q.contains("size") || q.contains("running out"))
            return storage.random() to listOf("Find duplicate photos", "Clean up WhatsApp", "Scan everything")

        // Delete / clean
        if (q.contains("delet") || q.contains("remov") || q.contains("clean") || q.contains("clear") || q.contains("wipe"))
            return cleanup.random() to listOf("Start a scan first", "Is it safe?", "How does it work?")

        // Privacy
        if (q.contains("privat") || q.contains("safe") || q.contains("internet") || q.contains("upload") || q.contains("cloud") || q.contains("server") || q.contains("secure") || q.contains("data"))
            return privacy.random() to listOf("How does the AI work?", "Find duplicates", "What can you do?")

        // How it works
        if ((q.contains("how") && (q.contains("work") || q.contains("does") || q.contains("function"))) || q.contains("explain") || q.contains("tell me about") || q.contains("accurate") || q.contains("technology"))
            return howItWorks.random() to listOf("Try it out", "Is my data safe?", "Start a scan")

        // WhatsApp
        if (q.contains("whatsapp") || q.contains("whats app") || q.contains("social") || q.contains("instagram") || q.contains("telegram"))
            return "WhatsApp downloads are one of the fastest-growing storage drains — photos, videos, voice notes, and documents from dozens of group chats pile up silently. The WhatsApp Cleaner on the dashboard gives you a full breakdown and lets you bulk-delete what you don't need. Want to give it a try?" to
                    listOf("Yes, open it", "What else can I clean?", "How much will I save?")

        // Contacts
        if (q.contains("contact") || q.contains("phonebook") || q.contains("address book"))
            return "Duplicate contacts happen more than you'd think — same person saved under different names, old and new numbers for the same contact, or imports that created doubles. The Contact Deduplication tool finds them and suggests merges. You approve each one, so nothing gets combined without your okay." to
                    listOf("Try contact dedup", "What else can you do?", "Back to storage")

        // Documents / files
        if (q.contains("document") || q.contains("pdf") || q.contains("doc") || q.contains("file") || q.contains("download"))
            return "Documents are easy to overlook, but duplicate PDFs, presentations, and downloads add up. Run a File Scanner from the dashboard — I'll surface redundant copies grouped by content similarity, not just file name." to
                    listOf("Start file scan", "What about photos?", "How much space will I recover?")

        // Speed / performance
        if (q.contains("fast") || q.contains("slow") || q.contains("speed") || q.contains("how long") || q.contains("time") || q.contains("quick"))
            return "A typical scan takes 1-3 minutes depending on how many files you have. I process everything locally on your device's chip, which is actually quite fast — no waiting for files to upload anywhere. Large video collections might take a bit longer." to
                    listOf("Start a scan", "What gets scanned?", "Is it safe?")

        return defaults.random() to DEFAULT_SUGGESTIONS
    }

    private fun affirmation(lastBotContext: String): String = when {
        lastBotContext.contains("photo") || lastBotContext.contains("image") ->
            "Great. Head to the dashboard and tap Image Scanner. I'll analyze your gallery using perceptual AI, group the near-duplicates, and let you review before anything is deleted. Should only take a minute or two."
        lastBotContext.contains("video") ->
            "Perfect. Open the dashboard and tap Video Scanner. I'll go through your videos and surface the duplicates — you'll see them grouped so you can compare before deciding."
        lastBotContext.contains("whatsapp") ->
            "Go to the dashboard and tap WhatsApp Cleaner. You'll see exactly what's been downloaded and how much space each category takes. Easy to bulk-select what you don't need."
        lastBotContext.contains("contact") ->
            "Head to the dashboard and tap Contact Dedup. It'll walk you through the merge suggestions one by one — you're in full control."
        else ->
            "Head to the dashboard and kick off a scan. I'll take it from there and surface the results right here. Come back if you have questions while it runs."
    }

    // ── Response pools ────────────────────────────────────────────────────────

    private val greetings = listOf(
        "Hey! Good to have you here. I work entirely on your device — no internet, no data uploads, just fast local AI. What's eating up your storage?",
        "Hi there! I'm your on-device storage companion. No cloud, no account, no data leaving your phone. What would you like to tackle — duplicate photos, clearing space, or something else?",
        "Hello! Ready to help you reclaim some storage. Ask me anything about duplicates, files, space — or just say what's bothering you and we'll figure it out together."
    )

    private val thanks = listOf(
        "Happy to help! Come back anytime.",
        "Of course — your storage, your rules. I'm just here to make it easier.",
        "Anytime! Let me know if anything else comes up."
    )

    private val duplicates = listOf(
        "Duplicates are the sneakiest space wasters. I use perceptual hashing for images — comparing visual content rather than filenames — and content fingerprinting for files. So I catch near-duplicates too: same photo saved twice, burst shots, edits of the same image. Want me to walk you through starting a scan?",
        "Most phones quietly accumulate hundreds of near-identical files — burst shots, WhatsApp forwards, screenshots, photos taken seconds apart. I group them by similarity so you can compare side-by-side and decide what to keep. The scan usually takes 1-2 minutes. Want to start?",
        "Here's what surprises people: two photos can look identical to the human eye but be different file sizes — an original and a compressed copy, or an HDR pair. I catch both exact duplicates and visual near-duplicates. Head to the Scan section when you're ready."
    )

    private val photos = listOf(
        "Photos are usually the single biggest culprit. Burst shots, near-identical selfies, HDR pairs, screenshots you forgot about, and WhatsApp forwards — they pile up silently. I scan for visual similarity, not just exact file matches, so I catch the ones that are almost identical too. Want to run an image scan?",
        "A typical gallery has 15-30% photos that are essentially duplicates — consecutive frames, same scene twice, or the same image received from different people. Run an Image Scanner from the dashboard and I'll group them so you can pick the best and clear the rest. Very satisfying.",
        "Something most people don't realize: two photos of the same scene can have completely different file sizes — original vs compressed, filtered vs unfiltered. I surface all of that, grouped visually, so you always keep the best quality copy. Should I help you get started?"
    )

    private val videos = listOf(
        "Videos are the most expensive storage items by far — even a 30-second clip can be several hundred megabytes. I scan for videos with matching content, not just the same filename, so I catch copies downloaded from different apps or saved multiple times. Run a Video Scanner from the dashboard.",
        "Duplicate videos are costly and easy to miss. I compare video content frame-by-frame (sampled, so it's fast) to find copies regardless of filename or resolution. Head to the dashboard and run a Video Scanner — the space recovered is usually significant."
    )

    private val storage = listOf(
        "Here's the usual breakdown on most devices: videos take the most space, then photos, then WhatsApp downloads, then apps. The fastest path to meaningful free space is a duplicate scan — most people recover 1-5 GB just from photos and videos. Want to start there?",
        "The most impactful thing you can do is scan for duplicates — they're often invisible but taking up a lot of room. After that, WhatsApp downloads are usually the next big win. I can help with both. Where do you want to start?",
        "Reclaiming storage feels great but the key is doing it safely. I show you what's a duplicate and what's unique before anything gets deleted — you approve everything. Most people recover 2-4 GB without touching anything they actually care about."
    )

    private val cleanup = listOf(
        "Safety first — I always scan and group before suggesting anything to delete. You see exactly what's a duplicate, which copy is higher quality, and what you'd be removing. Nothing gets deleted without your explicit approval.",
        "The safest cleanup flow is: scan, review grouped results, then delete with confidence. I highlight which copy is the best quality so you always keep the right one. Want to kick off a scan?",
        "I take a show-don't-delete approach. You see the groups, you compare, you decide. I just make the decision obvious by surfacing the duplicates and flagging the best version."
    )

    private val privacy = listOf(
        "Your privacy is the whole point. Every scan, every AI inference, every result — all of it runs directly on your device's chip. No file ever leaves your phone. No internet connection is needed. Ever. Not even to get the AI running.",
        "Zero data leaves your device. The AI model I use is stored locally, runs locally, and the results stay local. I couldn't send your files to a server even if I wanted to — there's no connection to one.",
        "On-device AI means your storage is nobody's business but yours. I process everything using your phone's own neural processor. No uploads, no accounts, no third party ever sees a single file."
    )

    private val howItWorks = listOf(
        "For photos, I use perceptual hashing — converting each image into a compact fingerprint that represents its visual content. Similar images produce similar fingerprints, so I find near-duplicates even if they're different sizes or lightly edited. For files, I use content fingerprinting. All of this runs on your device's AI chip — no internet required.",
        "Think of it like this: instead of comparing file names (which misses most duplicates), I compare the actual content. A photo taken twice in quick succession, a screenshot re-shared via WhatsApp, a PDF downloaded twice under a different name — I catch all of it. Fast, local, private.",
        "The core is on-device machine learning. For images: perceptual hashing for visual similarity. For videos: frame sampling to detect identical content. For files: byte-level and content fingerprinting. Results are grouped by similarity so you make smart decisions, not just see a flat list of suspects."
    )

    private val defaults = listOf(
        "That's a bit outside my lane — I'm specialized in storage management, duplicate detection, and file cleanup. Ask me something along those lines and I'll give you a much more useful answer. Or just say \"what can you do\" and I'll walk you through everything.",
        "I want to be upfront: I'm a storage AI, so my knowledge is deep in that space. For duplicates, space optimization, file cleanup, and privacy questions — ask away. What storage challenge are you dealing with?",
        "Not sure I can help with that specifically, but storage and file management? That's my expertise. Ask me about duplicate photos, freeing up space, or how the scanning works — I'll give you a real answer."
    )

    companion object {
        private val WELCOME_MSG = ChatMessage(
            text = "Hi! I'm DeDup AI — your on-device storage assistant. I can find duplicate photos and videos, clean up your files, and help you reclaim space. Everything runs locally on your device — no internet, no uploads, no data ever leaves your phone. What would you like to work on?",
            isUser = false
        )
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ChatViewModel(
            repository  = ChatHistoryRepository(context.applicationContext),
            voiceSource = VoiceCaptureDataSource(context.applicationContext),
            storageRepo = LocalStorageRepository(context.applicationContext),
            appContext  = context.applicationContext,
        ) as T
    }
}
