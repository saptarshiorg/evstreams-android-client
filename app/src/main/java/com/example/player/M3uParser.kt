package com.example.player

data class M3uChannel(
    val name: String,
    val url: String,
    val group: String = "All",
    val logoUrl: String? = null
)

object M3uParser {
    fun parse(content: String): List<M3uChannel> {
        val lines = content.lines()
        val channels = mutableListOf<M3uChannel>()
        var currentName: String? = null
        var currentGroup = "General"
        var currentLogo: String? = null

        val nameRegex = Regex(""",([^,]+)$""")
        val groupRegex = Regex("""group-title="([^"]+)"""")
        val logoRegex = Regex("""tvg-logo="([^"]+)"""")

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            if (trimmed.startsWith("#EXTINF", ignoreCase = true)) {
                val groupMatch = groupRegex.find(trimmed)
                if (groupMatch != null) currentGroup = groupMatch.groupValues[1]

                val logoMatch = logoRegex.find(trimmed)
                if (logoMatch != null) currentLogo = logoMatch.groupValues[1]

                val nameMatch = nameRegex.find(trimmed)
                currentName = if (nameMatch != null) {
                    nameMatch.groupValues[1].trim()
                } else {
                    "Channel ${channels.size + 1}"
                }
            } else if (!trimmed.startsWith("#")) {
                if (trimmed.startsWith("http://") || trimmed.startsWith("https://") ||
                    trimmed.startsWith("rtmp://") || trimmed.startsWith("rtsp://") ||
                    trimmed.startsWith("udp://")
                ) {
                    val name = currentName ?: "Channel ${channels.size + 1}"
                    channels.add(
                        M3uChannel(
                            name = name,
                            url = trimmed,
                            group = currentGroup,
                            logoUrl = currentLogo
                        )
                    )
                    currentName = null
                    currentGroup = "General"
                    currentLogo = null
                }
            }
        }
        return channels
    }
}
