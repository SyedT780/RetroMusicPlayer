package code.name.monkey.retromusic.util

/**
 * Utility for splitting multi-artist strings into individual artist names.
 * 
 * Handles common separators used in audio tags like "/", ";", ",", "&", "feat.", "ft.", "and", "with", "vs", "x"
 */
object ArtistSeparator {

    /**
     * Array of separators commonly used to delimit multiple artists in metadata.
     */
    val SEPARATORS: Array<String> = arrayOf(
        "/", ";", ",", "&",
        "feat.", "ft.", "feat", "ft",
        "and", "with", "vs.", "vs", "x"
    )

    private val regex = Regex(
        "(?:[,/;&]|\\b(?:feat|ft|and|with|vs|x)\\.?\\b)",
        RegexOption.IGNORE_CASE
    )

    /**
     * Splits artist names by common separators.
     * 
     * Examples:
     * - `"Artist1 / Artist2"` → `["Artist1", "Artist2"]`
     * - `"Artist1 feat. Artist2"` → `["Artist1", "Artist2"]`
     * - `"Artist1, Artist2, Artist3"` → `["Artist1", "Artist2", "Artist3"]`
     * - `"A. R. Rahman, Anuradha Sriram, Annupamaa & Febi Mani"` → `["A. R. Rahman", "Anuradha Sriram", "Annupamaa", "Febi Mani"]`
     * 
     * @param artistString The artist name(s) to split
     * @return List of individual artist names, trimmed and deduplicated. 
     *         Returns empty list if input is null or empty.
     */
    fun split(artistString: String?): List<String> {
        if (artistString.isNullOrEmpty()) {
            return emptyList()
        }

        return artistString.split(regex)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }
}