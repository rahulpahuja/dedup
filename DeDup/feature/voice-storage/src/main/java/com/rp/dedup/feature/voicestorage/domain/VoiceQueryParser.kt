package com.rp.dedup.feature.voicestorage.domain

import com.rp.dedup.feature.voicestorage.data.model.MediaType
import java.util.Locale

// Superlatives that express a sort intent rather than a size threshold:
//   "largest file"  → sort by SIZE DESC
//   "smallest file" → sort by SIZE ASC
//   "oldest photo"  → sort by DATE_ADDED ASC
//   "newest video"  → sort by DATE_ADDED DESC

/**
 * Converts a natural-language voice transcript into a structured [ParseResult].
 *
 * Handles expressions like:
 *   "show me videos larger than 100 MB from last month"
 *   "delete photos older than 6 months"
 *   "find screenshots smaller than 5 megabytes"
 *   "all images from last week"
 *   "videos bigger than 1 GB added this year"
 */
object VoiceQueryParser {

    data class ParseResult(
        val nameQuery: String,
        val filters: FilterConfig,
        val summary: String,
    )

    // ── Compiled regexes (compiled once, reused across calls) ────────────────

    // "larger than 10 MB", "bigger than 1.5 gigabytes", "less than 500 kb"
    private val SIZE_RE = Regex(
        """(larger|bigger|more|greater|over|above|smaller|less|under|below)\s+than\s+(\d+(?:[.,]\d+)?)\s*(gb|gigabyte|mb|megabyte|kb|kilobyte|byte)s?""",
        RegexOption.IGNORE_CASE,
    )

    // "older than 3 months", "newer than 2 weeks", "more than 1 year"
    private val RELATIVE_DATE_RE = Regex(
        """(older|newer|more|less)\s+than\s+(\d+)\s+(day|week|month|year)s?""",
        RegexOption.IGNORE_CASE,
    )

    // Superlative size: largest, biggest, heaviest, smallest, tiniest
    private val SUPERLATIVE_SIZE_LARGE = Regex(
        """(largest|biggest|heaviest|most\s+storage|taking\s+most\s+space|highest)""",
        RegexOption.IGNORE_CASE,
    )
    private val SUPERLATIVE_SIZE_SMALL = Regex(
        """(smallest|tiniest|lightest|least\s+storage)""",
        RegexOption.IGNORE_CASE,
    )

    // Superlative date: newest/latest/most recent vs oldest
    private val SUPERLATIVE_DATE_NEW = Regex(
        """(newest|latest|most\s+recent|recently\s+added)""",
        RegexOption.IGNORE_CASE,
    )
    private val SUPERLATIVE_DATE_OLD = Regex(
        """(oldest|earliest|first\s+added)""",
        RegexOption.IGNORE_CASE,
    )

    private val DAY_MS = 24 * 60 * 60 * 1_000L

    // ── Extension → MIME + MediaType lookup ───────────────────────────────────
    // Each entry is (mimeType, impliedMediaType). Checked against individual
    // whitespace-delimited tokens in the transcript.

    private val EXTENSION_TO_MIME: Map<String, Pair<String, MediaType>> = mapOf(
        // ── Images ────────────────────────────────────────────────────────────
        "jpg"   to ("image/jpeg"                              to MediaType.IMAGE),
        "jpeg"  to ("image/jpeg"                              to MediaType.IMAGE),
        "png"   to ("image/png"                               to MediaType.IMAGE),
        "gif"   to ("image/gif"                               to MediaType.IMAGE),
        "webp"  to ("image/webp"                              to MediaType.IMAGE),
        "heic"  to ("image/heic"                              to MediaType.IMAGE),
        "heif"  to ("image/heif"                              to MediaType.IMAGE),
        "bmp"   to ("image/bmp"                               to MediaType.IMAGE),
        "tiff"  to ("image/tiff"                              to MediaType.IMAGE),
        "tif"   to ("image/tiff"                              to MediaType.IMAGE),
        "svg"   to ("image/svg+xml"                           to MediaType.IMAGE),
        "ico"   to ("image/x-icon"                            to MediaType.IMAGE),
        "avif"  to ("image/avif"                              to MediaType.IMAGE),
        "jxl"   to ("image/jxl"                               to MediaType.IMAGE),
        // Camera RAW formats
        "raw"   to ("image/x-raw"                             to MediaType.IMAGE),
        "dng"   to ("image/x-adobe-dng"                       to MediaType.IMAGE),
        "cr2"   to ("image/x-canon-cr2"                       to MediaType.IMAGE),
        "cr3"   to ("image/x-canon-cr3"                       to MediaType.IMAGE),
        "nef"   to ("image/x-nikon-nef"                       to MediaType.IMAGE),
        "nrw"   to ("image/x-nikon-nrw"                       to MediaType.IMAGE),
        "arw"   to ("image/x-sony-arw"                        to MediaType.IMAGE),
        "orf"   to ("image/x-olympus-orf"                     to MediaType.IMAGE),
        "rw2"   to ("image/x-panasonic-rw2"                   to MediaType.IMAGE),
        "pef"   to ("image/x-pentax-pef"                      to MediaType.IMAGE),
        "srw"   to ("image/x-samsung-srw"                     to MediaType.IMAGE),
        "raf"   to ("image/x-fuji-raf"                        to MediaType.IMAGE),
        // Layered / design
        "psd"   to ("image/vnd.adobe.photoshop"               to MediaType.IMAGE),
        "xcf"   to ("image/x-xcf"                             to MediaType.IMAGE),
        // ── Videos ────────────────────────────────────────────────────────────
        "mp4"   to ("video/mp4"                               to MediaType.VIDEO),
        "m4v"   to ("video/x-m4v"                             to MediaType.VIDEO),
        "mov"   to ("video/quicktime"                          to MediaType.VIDEO),
        "avi"   to ("video/x-msvideo"                         to MediaType.VIDEO),
        "mkv"   to ("video/x-matroska"                        to MediaType.VIDEO),
        "webm"  to ("video/webm"                              to MediaType.VIDEO),
        "3gp"   to ("video/3gpp"                              to MediaType.VIDEO),
        "3g2"   to ("video/3gpp2"                             to MediaType.VIDEO),
        "wmv"   to ("video/x-ms-wmv"                          to MediaType.VIDEO),
        "flv"   to ("video/x-flv"                             to MediaType.VIDEO),
        "f4v"   to ("video/x-f4v"                             to MediaType.VIDEO),
        "mpg"   to ("video/mpeg"                              to MediaType.VIDEO),
        "mpeg"  to ("video/mpeg"                              to MediaType.VIDEO),
        "ts"    to ("video/mp2t"                              to MediaType.VIDEO),
        "mts"   to ("video/mp2t"                              to MediaType.VIDEO),
        "m2ts"  to ("video/mp2t"                              to MediaType.VIDEO),
        "vob"   to ("video/dvd"                               to MediaType.VIDEO),
        "divx"  to ("video/x-divx"                            to MediaType.VIDEO),
        "ogv"   to ("video/ogg"                               to MediaType.VIDEO),
        // ── Audio ─────────────────────────────────────────────────────────────
        "mp3"   to ("audio/mpeg"                              to MediaType.AUDIO),
        "aac"   to ("audio/aac"                               to MediaType.AUDIO),
        "wav"   to ("audio/wav"                               to MediaType.AUDIO),
        "flac"  to ("audio/flac"                              to MediaType.AUDIO),
        "ogg"   to ("audio/ogg"                               to MediaType.AUDIO),
        "oga"   to ("audio/ogg"                               to MediaType.AUDIO),
        "opus"  to ("audio/opus"                              to MediaType.AUDIO),
        "m4a"   to ("audio/mp4"                               to MediaType.AUDIO),
        "m4b"   to ("audio/mp4"                               to MediaType.AUDIO),   // audiobook
        "wma"   to ("audio/x-ms-wma"                          to MediaType.AUDIO),
        "aiff"  to ("audio/aiff"                              to MediaType.AUDIO),
        "aif"   to ("audio/aiff"                              to MediaType.AUDIO),
        "amr"   to ("audio/amr"                               to MediaType.AUDIO),
        "mid"   to ("audio/midi"                              to MediaType.AUDIO),
        "midi"  to ("audio/midi"                              to MediaType.AUDIO),
        "ape"   to ("audio/x-ape"                             to MediaType.AUDIO),
        "wv"    to ("audio/x-wavpack"                         to MediaType.AUDIO),
        "ra"    to ("audio/x-realaudio"                       to MediaType.AUDIO),
        "ac3"   to ("audio/ac3"                               to MediaType.AUDIO),
        "dts"   to ("audio/vnd.dts"                           to MediaType.AUDIO),
        // ── Documents ─────────────────────────────────────────────────────────
        // Office
        "pdf"   to ("application/pdf"                         to MediaType.DOCUMENT),
        "doc"   to ("application/msword"                      to MediaType.DOCUMENT),
        "docx"  to ("application/vnd.openxmlformats-officedocument.wordprocessingml.document"   to MediaType.DOCUMENT),
        "xls"   to ("application/vnd.ms-excel"                to MediaType.DOCUMENT),
        "xlsx"  to ("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"         to MediaType.DOCUMENT),
        "ppt"   to ("application/vnd.ms-powerpoint"           to MediaType.DOCUMENT),
        "pptx"  to ("application/vnd.openxmlformats-officedocument.presentationml.presentation" to MediaType.DOCUMENT),
        "rtf"   to ("application/rtf"                         to MediaType.DOCUMENT),
        // OpenDocument
        "odt"   to ("application/vnd.oasis.opendocument.text"         to MediaType.DOCUMENT),
        "ods"   to ("application/vnd.oasis.opendocument.spreadsheet"   to MediaType.DOCUMENT),
        "odp"   to ("application/vnd.oasis.opendocument.presentation"  to MediaType.DOCUMENT),
        // Plain text / markup
        "txt"   to ("text/plain"                              to MediaType.DOCUMENT),
        "csv"   to ("text/csv"                                to MediaType.DOCUMENT),
        "json"  to ("application/json"                        to MediaType.DOCUMENT),
        "xml"   to ("text/xml"                                to MediaType.DOCUMENT),
        "html"  to ("text/html"                               to MediaType.DOCUMENT),
        "htm"   to ("text/html"                               to MediaType.DOCUMENT),
        "md"    to ("text/markdown"                           to MediaType.DOCUMENT),
        // E-books
        "epub"  to ("application/epub+zip"                    to MediaType.DOCUMENT),
        "mobi"  to ("application/x-mobipocket-ebook"          to MediaType.DOCUMENT),
        "azw"   to ("application/vnd.amazon.ebook"            to MediaType.DOCUMENT),
        "azw3"  to ("application/vnd.amazon.ebook"            to MediaType.DOCUMENT),
        // Archives
        "zip"   to ("application/zip"                         to MediaType.DOCUMENT),
        "rar"   to ("application/x-rar-compressed"            to MediaType.DOCUMENT),
        "7z"    to ("application/x-7z-compressed"             to MediaType.DOCUMENT),
        "tar"   to ("application/x-tar"                       to MediaType.DOCUMENT),
        "gz"    to ("application/gzip"                        to MediaType.DOCUMENT),
        "bz2"   to ("application/x-bzip2"                     to MediaType.DOCUMENT),
        "xz"    to ("application/x-xz"                        to MediaType.DOCUMENT),
        // Android / system
        "apk"   to ("application/vnd.android.package-archive" to MediaType.DOCUMENT),
        "xapk"  to ("application/vnd.android.package-archive" to MediaType.DOCUMENT),
        "db"    to ("application/x-sqlite3"                   to MediaType.DOCUMENT),
        "sqlite" to ("application/x-sqlite3"                  to MediaType.DOCUMENT),
    )

    // ── Public API ────────────────────────────────────────────────────────────

    fun parse(input: String): ParseResult {
        val lower = input.lowercase(Locale.getDefault()).trim()

        val (mimeFilter, impliedTypes) = parseMimeFilter(lower)
        // Extension keyword wins over generic media-type keyword for precision
        val mediaTypes      = impliedTypes ?: parseMediaTypes(lower)
        val (minSz, maxSz)  = parseSizeConstraints(lower)
        val (after, before) = parseDateConstraints(lower)
        val (sortBy, sortOrder) = parseSortIntent(lower)
        val nameQuery       = extractNameQuery(lower)

        val filters = FilterConfig(
            minSizeBytes    = minSz,
            maxSizeBytes    = maxSz,
            dateAddedAfter  = after,
            dateAddedBefore = before,
            mediaTypes      = mediaTypes,
            sortBy          = sortBy,
            sortOrder       = sortOrder,
            mimeTypeFilter  = mimeFilter,
        )

        return ParseResult(
            nameQuery = nameQuery,
            filters   = filters,
            summary   = buildSummary(filters, nameQuery, input),
        )
    }

    // ── MIME / extension filter ───────────────────────────────────────────────

    private fun parseMimeFilter(lower: String): Pair<String?, Set<MediaType>?> {
        val tokens = lower.split(Regex("[\\s.,!?]+"))
        for (token in tokens) {
            val (mime, type) = EXTENSION_TO_MIME[token] ?: continue
            return mime to setOf(type)
        }
        return null to null
    }

    // ── Sort intent (superlatives) ────────────────────────────────────────────

    private fun parseSortIntent(lower: String): Pair<SortBy, SortOrder> {
        return when {
            SUPERLATIVE_SIZE_LARGE.containsMatchIn(lower) -> SortBy.SIZE to SortOrder.DESC
            SUPERLATIVE_SIZE_SMALL.containsMatchIn(lower) -> SortBy.SIZE to SortOrder.ASC
            SUPERLATIVE_DATE_NEW.containsMatchIn(lower)   -> SortBy.DATE_ADDED to SortOrder.DESC
            SUPERLATIVE_DATE_OLD.containsMatchIn(lower)   -> SortBy.DATE_ADDED to SortOrder.ASC
            else                                          -> SortBy.DATE_ADDED to SortOrder.DESC
        }
    }

    // ── Media type ────────────────────────────────────────────────────────────

    private fun parseMediaTypes(lower: String): Set<MediaType> {
        val wantsImages = lower.containsAny("photo", "image", "picture", "screenshot", "selfie")
        val wantsVideos = lower.containsAny("video", "movie", "recording", "clip", "reel")
        val wantsAudio  = lower.containsAny("audio", "music", "song", "songs", "podcast", "track", "sound")
        val types = mutableSetOf<MediaType>()
        if (wantsImages) types += MediaType.IMAGE
        if (wantsVideos) types += MediaType.VIDEO
        if (wantsAudio)  types += MediaType.AUDIO
        return if (types.isEmpty()) setOf(MediaType.IMAGE, MediaType.VIDEO) else types
    }

    // ── Size constraints ──────────────────────────────────────────────────────

    private fun parseSizeConstraints(lower: String): Pair<Long?, Long?> {
        val match = SIZE_RE.find(lower) ?: return null to null
        val comparator = match.groupValues[1].lowercase()
        val amount = match.groupValues[2].replace(',', '.').toDoubleOrNull() ?: return null to null
        val unit   = match.groupValues[3].lowercase()

        val bytes = when {
            unit.startsWith("g") -> (amount * 1_073_741_824).toLong()
            unit.startsWith("m") -> (amount * 1_048_576).toLong()
            unit.startsWith("k") -> (amount * 1_024).toLong()
            else                 -> amount.toLong()
        }

        return when (comparator) {
            in setOf("larger", "bigger", "more", "greater", "over", "above") -> bytes to null
            else -> null to bytes  // smaller / less / under / below
        }
    }

    // ── Date constraints ──────────────────────────────────────────────────────

    private fun parseDateConstraints(lower: String): Pair<Long?, Long?> {
        val now = System.currentTimeMillis()

        // Fixed anchor phrases → dateAddedAfter (files from within that window)
        val after: Long? = when {
            "today"                                       in lower -> now - DAY_MS
            "yesterday"                                   in lower -> now - 2 * DAY_MS
            "this week"   in lower || "last week"         in lower -> now - 7 * DAY_MS
            "this month"  in lower || "last month"        in lower -> now - 30 * DAY_MS
            "last 3 months" in lower || "past 3 months"  in lower -> now - 90 * DAY_MS
            "last 6 months" in lower || "past 6 months"  in lower -> now - 180 * DAY_MS
            "this year"   in lower || "last year"         in lower -> now - 365 * DAY_MS
            else -> parseRelativeAfter(lower, now)
        }

        // "older than X" → dateAddedBefore
        val before: Long? = if (after == null) parseRelativeBefore(lower, now) else null

        return after to before
    }

    // "newer than 2 weeks" → dateAddedAfter = now - 2 weeks
    private fun parseRelativeAfter(lower: String, now: Long): Long? {
        val m = RELATIVE_DATE_RE.find(lower) ?: return null
        val comparator = m.groupValues[1].lowercase()
        if (comparator !in setOf("newer", "less")) return null
        return now - durationMs(m.groupValues[2].toLongOrNull() ?: return null, m.groupValues[3])
    }

    // "older than 3 months" → dateAddedBefore = now - 3 months
    private fun parseRelativeBefore(lower: String, now: Long): Long? {
        val m = RELATIVE_DATE_RE.find(lower) ?: return null
        val comparator = m.groupValues[1].lowercase()
        if (comparator !in setOf("older", "more")) return null
        return now - durationMs(m.groupValues[2].toLongOrNull() ?: return null, m.groupValues[3])
    }

    private fun durationMs(amount: Long, unit: String): Long = when {
        unit.startsWith("day")   -> amount * DAY_MS
        unit.startsWith("week")  -> amount * 7 * DAY_MS
        unit.startsWith("month") -> amount * 30 * DAY_MS
        unit.startsWith("year")  -> amount * 365 * DAY_MS
        else -> 0L
    }

    // ── Name query extraction ─────────────────────────────────────────────────

    // Strip recognised filter vocabulary; the remainder (if meaningful) is a filename search.
    private val STRIP_WORDS = setOf(
        "show", "me", "find", "get", "display", "delete", "remove", "search", "look", "for",
        "all", "the", "my", "any", "some", "give", "what's", "whats", "what", "is", "are",
        "which", "where", "tell", "in", "on", "system", "device", "phone", "storage",
        "photo", "photos", "image", "images", "picture", "pictures", "video", "videos",
        "file", "files", "media", "screenshot", "screenshots", "selfie", "selfies",
        "recording", "recordings", "clip", "clips", "movie", "movies",
        "audio", "music", "song", "songs", "podcast", "track", "sound",
        "document", "documents",
        // extension keywords — handled by parseMimeFilter
        // images
        "jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "tiff", "tif",
        "svg", "ico", "avif", "jxl", "raw", "dng", "cr2", "cr3", "nef", "nrw",
        "arw", "orf", "rw2", "pef", "srw", "raf", "psd", "xcf",
        // videos
        "mp4", "m4v", "mov", "avi", "mkv", "webm", "3gp", "3g2", "wmv",
        "flv", "f4v", "mpg", "mpeg", "ts", "mts", "m2ts", "vob", "divx", "ogv",
        // audio
        "mp3", "aac", "wav", "flac", "ogg", "oga", "opus", "m4a", "m4b",
        "wma", "aiff", "aif", "amr", "mid", "midi", "ape", "wv", "ra", "ac3", "dts",
        // documents
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "rtf",
        "odt", "ods", "odp", "txt", "csv", "json", "xml", "html", "htm", "md",
        "epub", "mobi", "azw", "azw3",
        "zip", "rar", "7z", "tar", "gz", "bz2", "xz",
        "apk", "xapk", "db", "sqlite",
        "larger", "bigger", "smaller", "less", "more", "greater", "over", "under",
        "above", "below", "than", "about", "around",
        // superlatives — handled by parseSortIntent
        "largest", "biggest", "heaviest", "highest", "smallest", "tiniest", "lightest",
        "newest", "latest", "oldest", "earliest",
        "older", "newer", "before", "after", "since", "until",
        "last", "this", "past", "recent",
        "today", "yesterday", "week", "month", "year",
        "day", "days", "weeks", "months", "years",
        "gb", "mb", "kb", "gigabyte", "megabyte", "kilobyte", "gigabytes", "megabytes", "kilobytes",
        "byte", "bytes", "and", "or", "from", "added", "taken",
    )

    private fun extractNameQuery(lower: String): String {
        // Remove size and date sub-expressions first so their numbers don't leak through
        val cleaned = SIZE_RE.replace(RELATIVE_DATE_RE.replace(lower, ""), "")
        val remaining = cleaned.split(Regex("\\s+"))
            .filter { token ->
                token.isNotBlank()
                    && token !in STRIP_WORDS
                    && token.toLongOrNull() == null
                    && token.toDoubleOrNull() == null
            }
        return remaining.joinToString(" ").trim()
    }

    // ── Human-readable summary ────────────────────────────────────────────────

    private fun buildSummary(filters: FilterConfig, nameQuery: String, original: String): String {
        val parts = mutableListOf<String>()

        // If a specific extension was spoken, use it as the type label
        val extLabel = filters.mimeTypeFilter?.let { mime ->
            EXTENSION_TO_MIME.entries.firstOrNull { it.value.first == mime }?.key?.let { ".${it}" }
        }
        val typeLabel = if (extLabel != null) {
            "$extLabel files"
        } else {
            when (filters.mediaTypes) {
                setOf(MediaType.IMAGE)    -> "images"
                setOf(MediaType.VIDEO)    -> "videos"
                setOf(MediaType.AUDIO)    -> "audio files"
                setOf(MediaType.DOCUMENT) -> "documents"
                else                      -> "all media"
            }
        }
        parts += typeLabel

        val sortLabel = when {
            filters.sortBy == SortBy.SIZE && filters.sortOrder == SortOrder.DESC      -> "largest first"
            filters.sortBy == SortBy.SIZE && filters.sortOrder == SortOrder.ASC       -> "smallest first"
            filters.sortBy == SortBy.DATE_ADDED && filters.sortOrder == SortOrder.ASC -> "oldest first"
            filters.sortBy == SortBy.NAME                                             -> "by name"
            else -> null
        }
        sortLabel?.let { parts += it }

        filters.minSizeBytes?.let { parts += "larger than ${it.formatBytes()}" }
        filters.maxSizeBytes?.let { parts += "smaller than ${it.formatBytes()}" }
        filters.dateAddedAfter?.let  { parts += "added after ${formatEpoch(it)}" }
        filters.dateAddedBefore?.let { parts += "older than ${formatEpoch(it)}" }
        if (nameQuery.isNotBlank()) parts += "matching \"$nameQuery\""

        val hasNonDefaultFilter = extLabel != null || sortLabel != null
            || filters.minSizeBytes != null || filters.maxSizeBytes != null
            || filters.dateAddedAfter != null || filters.dateAddedBefore != null
            || nameQuery.isNotBlank()

        return if (!hasNonDefaultFilter && parts.size == 1)
            "Searching: $original"
        else
            "Showing ${parts.joinToString(", ")}"
    }

    private fun Long.formatBytes(): String = when {
        this >= 1_073_741_824L -> "%.1f GB".format(this / 1_073_741_824.0)
        this >= 1_048_576L     -> "%.1f MB".format(this / 1_048_576.0)
        this >= 1_024L         -> "%.0f KB".format(this / 1_024.0)
        else                   -> "$this B"
    }

    private fun formatEpoch(epochMs: Long): String {
        val diff = System.currentTimeMillis() - epochMs
        val days = diff / DAY_MS
        return when {
            days < 1   -> "today"
            days < 2   -> "yesterday"
            days < 8   -> "$days days ago"
            days < 32  -> "${days / 7} week${if (days / 7 > 1) "s" else ""} ago"
            days < 366 -> "${days / 30} month${if (days / 30 > 1) "s" else ""} ago"
            else        -> "${days / 365} year${if (days / 365 > 1) "s" else ""} ago"
        }
    }

    private fun String.containsAny(vararg tokens: String) = tokens.any { this.contains(it) }
}
