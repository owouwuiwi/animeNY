package eu.kanade.tachiyomi.ui.player.loader

private val heightLabel = Regex("""(?<!\d)([2-9]\d{2}|[1-4]\d{3})p\b""", RegexOption.IGNORE_CASE)
private val dimensionsLabel = Regex("""\b\d{3,4}[x×](\d{3,4})\b""", RegexOption.IGNORE_CASE)
private val ultraHighDefinition = Regex("""\b(?:4K|UHD)\b""", RegexOption.IGNORE_CASE)

internal fun videoQualityHeight(label: String): Int? {
    return heightLabel.find(label)?.groupValues?.get(1)?.toIntOrNull()
        ?: dimensionsLabel.find(label)?.groupValues?.get(1)?.toIntOrNull()
        ?: 2160.takeIf { ultraHighDefinition.containsMatchIn(label) }
}
