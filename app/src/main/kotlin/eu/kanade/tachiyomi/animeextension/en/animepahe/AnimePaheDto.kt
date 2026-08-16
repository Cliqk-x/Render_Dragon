package eu.kanade.tachiyomi.animeextension.en.animepahe

import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale

// ─── Search ───────────────────────────────────────────────────────────────────

@Serializable
data class AnimePaheSearchResponse(
    val data: List<AnimePaheSearchEntry>? = null,
    val total: Int = 0,
)

@Serializable
data class AnimePaheSearchEntry(
    val id: Int = 0,
    val slug: String = "",
    val title: String = "",
    val type: String = "",
    val episodes: Int = 0,
    val status: String = "",
    val season: String = "",
    val year: Int = 0,
    val score: Double = 0.0,
    val poster: String = "",
    val session: String = "",
) {
    fun toSAnime(baseUrl: String) = SAnime.create().apply {
        url = "/anime/$session"
        title = this@AnimePaheSearchEntry.title
        thumbnail_url = poster
        status = when (this@AnimePaheSearchEntry.status.lowercase()) {
            "currently airing" -> SAnime.ONGOING
            "finished airing" -> SAnime.COMPLETED
            else -> SAnime.UNKNOWN
        }
    }
}

// ─── Airing / Popular Page ────────────────────────────────────────────────────

@Serializable
data class AnimePahePageResponse(
    val total: Int = 0,
    val per_page: Int = 0,
    val current_page: Int = 1,
    val last_page: Int = 1,
    val data: List<AnimePahePageEntry> = emptyList(),
)

@Serializable
data class AnimePahePageEntry(
    val id: Int = 0,
    val title: String = "",
    val slug: String = "",
    val type: String = "",
    val episodes: Int = 0,
    val status: String = "",
    val season: String = "",
    val year: Int = 0,
    val score: Double = 0.0,
    val image: String = "",
    val session: String = "",
) {
    fun toSAnime(baseUrl: String) = SAnime.create().apply {
        url = "/anime/$session"
        title = this@AnimePahePageEntry.title
        thumbnail_url = image
        status = when (this@AnimePahePageEntry.status.lowercase()) {
            "currently airing" -> SAnime.ONGOING
            "finished airing" -> SAnime.COMPLETED
            else -> SAnime.UNKNOWN
        }
    }
}

// ─── Anime Detail ─────────────────────────────────────────────────────────────

@Serializable
data class AnimePaheAnimeDetail(
    val id: Int = 0,
    val title: String = "",
    val type: String = "",
    val episodes: Int = 0,
    val status: String = "",
    val season: String = "",
    val year: Int = 0,
    val score: Double = 0.0,
    val poster: String = "",
    val cover: String = "",
    val summary: String? = null,
    val genres: List<String>? = null,
    val session: String = "",
)

// ─── Episode List ─────────────────────────────────────────────────────────────

@Serializable
data class AnimePaheEpisodePage(
    val total: Int = 0,
    val per_page: Int = 0,
    val current_page: Int = 1,
    val last_page: Int = 1,
    val data: List<AnimePaheEpisodeEntry> = emptyList(),
)

@Serializable
data class AnimePaheEpisodeEntry(
    val id: Int = 0,
    val anime_id: Int = 0,
    val episode: Double = 0.0,
    val episode2: Double = 0.0,
    val edition: String = "",
    val title: String = "",
    val snapshot: String = "",
    val disc: String = "",
    val audio: String = "",
    val duration: String = "",
    val session: String = "",
    val created_at: String = "",
) {
    fun toSEpisode(baseUrl: String, animeSession: String): SEpisode {
        return SEpisode.create().apply {
            url = "/play/$animeSession/$session"
            // Format: "Episode 1" or "Episode 1.5" for decimals
            val epNum = if (episode2 > 0 && episode2 != episode) {
                "$episode-$episode2"
            } else {
                episode.let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() }
            }
            name = buildString {
                append("Episode $epNum")
                if (title.isNotBlank()) append(" – $title")
                if (audio.isNotBlank() && audio.lowercase() != "sub") append(" [$audio]")
            }
            episode_number = episode.toFloat()
            scanlator = disc.ifBlank { null }
            date_upload = parseDate(created_at)
        }
    }

    private fun parseDate(dateStr: String): Long {
        return runCatching {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(dateStr)?.time ?: 0L
        }.getOrDefault(0L)
    }
}
