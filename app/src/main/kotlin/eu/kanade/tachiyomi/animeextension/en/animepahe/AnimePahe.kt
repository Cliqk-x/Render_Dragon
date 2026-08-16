package eu.kanade.tachiyomi.animeextension.en.animepahe

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimePahe : AnimeHttpSource(), ConfigurableAnimeSource {

    override val name = "AnimePahe"
    override val baseUrl = "https://animepahe.ru"
    override val lang = "en"
    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    private val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>()
            .getSharedPreferences("source_$id", 0x0000)
    }

    private val kwikExtractor by lazy {
        KwikExtractor(client, headers, json)
    }

    // ─── Headers ──────────────────────────────────────────────────────────

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add(
            "Accept",
            "application/json, text/javascript, */*; q=0.01",
        )
        .add("X-Requested-With", "XMLHttpRequest")

    // ─── Popular / Latest ─────────────────────────────────────────────────

    override fun popularAnimeRequest(page: Int): Request =
        GET(
            "$baseUrl/api?m=airing&page=$page",
            headers,
        )

    override fun popularAnimeParse(response: Response): AnimesPage {
        val body = response.body?.string() ?: ""

        val page = json.decodeFromString<AnimePahePageResponse>(body)

        val animes = page.data.map {
            it.toSAnime(baseUrl)
        }

        return AnimesPage(
            animes,
            page.current_page < page.last_page,
        )
    }

    override fun latestUpdatesRequest(page: Int): Request =
        GET(
            "$baseUrl/api?m=airing&page=$page",
            headers,
        )

    override fun latestUpdatesParse(response: Response): AnimesPage =
        popularAnimeParse(response)

    // ─── Search ───────────────────────────────────────────────────────────

    override fun searchAnimeRequest(
        page: Int,
        query: String,
        filters: eu.kanade.tachiyomi.animesource.model.AnimeFilterList,
    ): Request {
        return if (query.isNotBlank()) {
            GET(
                "$baseUrl/api?m=search&q=${query.trim()}",
                headers,
            )
        } else {
            GET(
                "$baseUrl/api?m=airing&page=$page",
                headers,
            )
        }
    }

    override fun searchAnimeParse(response: Response): AnimesPage {
        val url = response.request.url.toString()
        val body = response.body?.string() ?: ""

        return if (url.contains("m=search")) {
            val result =
                json.decodeFromString<AnimePaheSearchResponse>(body)

            val animes = result.data
                ?.map { it.toSAnime(baseUrl) }
                ?: emptyList()

            AnimesPage(
                animes,
                false,
            )
        } else {
            val page =
                json.decodeFromString<AnimePahePageResponse>(body)

            val animes = page.data.map {
                it.toSAnime(baseUrl)
            }

            AnimesPage(
                animes,
                page.current_page < page.last_page,
            )
        }
    }

    // ─── Anime Details ────────────────────────────────────────────────────

    // anime.url = /anime/<session>
    override fun animeDetailsRequest(anime: SAnime): Request {
        val session = anime.url.removePrefix("/anime/")

        return GET(
            "$baseUrl/api?m=anime&id=$session",
            headers,
        )
    }

    override fun animeDetailsParse(response: Response): SAnime {
        val body = response.body?.string() ?: ""

        val d =
            json.decodeFromString<AnimePaheAnimeDetail>(body)

        return SAnime.create().apply {
            title = d.title

            thumbnail_url = d.cover.ifBlank {
                d.poster
            }

            description = buildString {
                if (!d.summary.isNullOrBlank()) {
                    append(
                        d.summary
                            .replace(
                                Regex("<br\\s*/?>"),
                                "\n",
                            )
                            .trim(),
                    )
                }

                appendLine()
                appendLine()

                if (d.type.isNotBlank()) {
                    appendLine("Type: ${d.type}")
                }

                if (d.episodes > 0) {
                    appendLine("Episodes: ${d.episodes}")
                }

                if (d.season.isNotBlank()) {
                    appendLine(
                        "Season: ${
                            d.season.replaceFirstChar {
                                it.uppercase()
                            }
                        }",
                    )
                }

                if (d.year > 0) {
                    appendLine("Year: ${d.year}")
                }

                if (d.score > 0) {
                    append(
                        "Score: ${
                            "%.2f".format(d.score)
                        }",
                    )
                }
            }.trim()

            genre = d.genres?.joinToString(", ")

            status = when (d.status.lowercase()) {
                "currently airing" ->
                    SAnime.ONGOING

                "finished airing" ->
                    SAnime.COMPLETED

                else ->
                    SAnime.UNKNOWN
            }

            initialized = true
        }
    }

    // ─── Episode List ─────────────────────────────────────────────────────

    override fun episodeListRequest(anime: SAnime): Request {
        val session = anime.url.removePrefix("/anime/")

        return GET(
            "$baseUrl/api?m=release&id=$session&sort=episode_asc&page=1",
            headers,
        )
    }

    override fun episodeListParse(response: Response): List<SEpisode> {
        val episodes = mutableListOf<SEpisode>()

        val session =
            response.request.url.queryParameter("id")
                ?: return episodes

        var page =
            json.decodeFromString<AnimePaheEpisodePage>(
                response.body?.string() ?: "",
            )

        episodes.addAll(
            page.data.map {
                it.toSEpisode(
                    baseUrl,
                    session,
                )
            },
        )

        // Fetch all remaining episode pages.
        while (page.current_page < page.last_page) {
            val nextPage = page.current_page + 1

            val nextResponse = client.newCall(
                GET(
                    "$baseUrl/api?m=release&id=$session&sort=episode_asc&page=$nextPage",
                    headers,
                ),
            ).execute()

            val nextBody =
                nextResponse.body?.string() ?: ""

            page =
                json.decodeFromString<AnimePaheEpisodePage>(
                    nextBody,
                )

            episodes.addAll(
                page.data.map {
                    it.toSEpisode(
                        baseUrl,
                        session,
                    )
                },
            )
        }

        // Newest first.
        return episodes.reversed()
    }

    // ─── Video List ───────────────────────────────────────────────────────

    override fun videoListRequest(episode: SEpisode): Request =
        GET(
            "$baseUrl${episode.url}",
            headers,
        )

    override fun videoListParse(response: Response): List<Video> {
        val html = response.body?.string() ?: ""

        val videos = mutableListOf<Video>()

        val kwikLinks =
            Regex(
                """data-src="(https://kwik\.cx/e/[^"]+)"""",
            )
                .findAll(html)
                .map {
                    it.groupValues[1]
                }
                .toList()

        val qualities =
            Regex(
                """<span[^>]*>\s*(\d{3,4}p)\s*</span>""",
            )
                .findAll(html)
                .map {
                    it.groupValues[1]
                }
                .toList()

        kwikLinks.forEachIndexed { index, kwikUrl ->
            val quality =
                qualities.getOrElse(index) {
                    "Unknown"
                }

            runCatching {
                videos.addAll(
                    kwikExtractor.videosFromUrl(
                        kwikUrl,
                        quality,
                    ),
                )
            }
        }

        return videos.sortedByDescending {
            it.quality
                .filter(Char::isDigit)
                .toIntOrNull()
                ?: 0
        }
    }

    // ─── Preferences ─────────────────────────────────────────────────────

    override fun setupPreferenceScreen(
        screen: PreferenceScreen,
    ) {
        ListPreference(screen.context).apply {
            key = PREF_QUALITY_KEY
            title = "Preferred quality"

            entries = arrayOf(
                "1080p",
                "720p",
                "480p",
                "360p",
            )

            entryValues = arrayOf(
                "1080",
                "720",
                "480",
                "360",
            )

            setDefaultValue("720")
            summary = "%s"
        }.also(screen::addPreference)
    }

    companion object {
        private const val PREF_QUALITY_KEY =
            "preferred_quality"
    }
}