package com.aihomecloud.ahcplayer.data.tmdb

data class ParsedTitle(val title: String, val year: Int?, val isTvShow: Boolean)

object TitleParser {
    // Matches: "Title.Name.2023" or "Title Name (2023)" or "Title.S01E01"
    private val yearRegex = Regex("""[.\s(](\d{4})[.\s)]""")
    private val tvRegex = Regex("""[Ss]\d{1,2}[Ee]\d{1,2}""")
    private val separatorRegex = Regex("""[._]""")
    // Junk tokens that indicate where the title ends
    private val junkTokens = setOf(
        "1080p", "720p", "4k", "2160p", "480p",
        "bluray", "bdrip", "webrip", "web-dl", "webdl", "hdrip", "dvdrip",
        "x264", "x265", "hevc", "avc", "xvid",
        "aac", "ac3", "dts", "mp3",
        "extended", "theatrical", "remastered",
        "yify", "yts", "rarbg"
    )

    fun parse(filename: String): ParsedTitle {
        val name = filename.substringBeforeLast('.')  // strip extension
        val isTv = tvRegex.containsMatchIn(name)
        val yearMatch = yearRegex.find(name)
        val year = yearMatch?.groupValues?.get(1)?.toIntOrNull()

        val stopAt = when {
            yearMatch != null -> yearMatch.range.first
            tvRegex.containsMatchIn(name) -> (tvRegex.find(name)?.range?.first ?: name.length)
            else -> name.length
        }

        val raw = name.substring(0, stopAt)
        val tokens = separatorRegex.split(raw)
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.lowercase() !in junkTokens }

        val title = tokens.joinToString(" ")
            .trim()
            .ifEmpty { name }

        return ParsedTitle(title = title, year = year, isTvShow = isTv)
    }
}
