package eu.kanade.tachiyomi.animeextension.en.animepahe

import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.OkHttpClient

/**
 * KwikExtractor
 *
 * Kwik.cx is AnimePahe's video host. It protects streams with:
 *   1. A Referer check (must send Referer: https://animepahe.ru)
 *   2. Obfuscated JavaScript using the p,a,c,k,e,d packer
 *   3. A one-time POST token embedded in the packed script
 *
 * Flow:
 *   GET kwik.cx/e/<id> → parse packed JS → unpack → extract m3u8 URL
 *   If a form token is found, POST it first to get the final redirect.
 */
class KwikExtractor(
    private val client: OkHttpClient,
    private val baseHeaders: Headers,
    private val json: Json,
) {

    companion object {
        private const val KWIK_BASE = "https://kwik.cx"

        // Matches eval(function(p,a,c,k,e,d){...}('...',62,...))
        private val PACKED_REGEX = Regex(
            """eval\(function\(p,a,c,k,e,[dr]\).*?\('(.*?)',(\d+),(\d+),'(.*?)'""",
            RegexOption.DOT_MATCHES_ALL,
        )

        // After unpacking, the m3u8 source looks like:
        // source='https://...m3u8'
        private val SOURCE_REGEX =
            Regex("""source='(https[^']+\.m3u8[^']*)'""")

        // One-time POST token:
        // const _token = "abc123"
        private val TOKEN_REGEX =
            Regex("""const\s+_\d*[tT]oken\s*=\s*"([^"]+)"""")

        // Form action on the redirect page:
        // <form method="POST" action="...">
        private val FORM_ACTION_REGEX =
            Regex("""<form[^>]+action="([^"]+)"""")
    }

    /**
     * Entry point. Returns a list of [Video] objects for the given Kwik embed URL.
     *
     * [quality] is just a display label (e.g. "720p").
     */
    fun videosFromUrl(
        embedUrl: String,
        quality: String,
    ): List<Video> {

        val kwikHeaders = baseHeaders.newBuilder()
            .set("Referer", "https://animepahe.ru/")
            .set(
                "Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            )
            .removeAll("X-Requested-With")
            .build()

        val response = client.newCall(
            GET(embedUrl, kwikHeaders),
        ).execute()

        // OkHttp Response.body is nullable in newer versions.
        val html = response.body?.string() ?: ""

        // Step 1: check for the packed script.
        val packedMatch = PACKED_REGEX.find(html)
            ?: return extractDirectM3u8(
                html,
                embedUrl,
                quality,
                kwikHeaders,
            )

        val unpacked = unpack(
            encodedStr = packedMatch.groupValues[1],
            base = packedMatch.groupValues[2].toIntOrNull() ?: 62,
            count = packedMatch.groupValues[3].toIntOrNull() ?: 0,
            symbolTable = packedMatch.groupValues[4].split("|"),
        )

        // Step 2: look for the m3u8 URL directly in the unpacked code.
        val sourceMatch = SOURCE_REGEX.find(unpacked)

        if (sourceMatch != null) {
            val m3u8Url = sourceMatch.groupValues[1]

            return listOf(
                Video(
                    m3u8Url,
                    quality,
                    m3u8Url,
                    headers = kwikHeaders,
                ),
            )
        }

        // Step 3: some Kwik versions require a POST token
        // before revealing the stream.
        val token =
            TOKEN_REGEX.find(unpacked)?.groupValues?.get(1)
                ?: TOKEN_REGEX.find(html)?.groupValues?.get(1)

        if (token != null) {
            val formAction =
                FORM_ACTION_REGEX.find(html)?.groupValues?.get(1)
                    ?: embedUrl

            val postHeaders = kwikHeaders.newBuilder()
                .set("Referer", embedUrl)
                .set(
                    "Content-Type",
                    "application/x-www-form-urlencoded",
                )
                .build()

            val body = FormBody.Builder()
                .add("_token", token)
                .build()

            val postResponse = client.newCall(
                POST(
                    formAction,
                    postHeaders,
                    body,
                ),
            ).execute()

            // OkHttp Response.body is nullable in newer versions.
            val postHtml = postResponse.body?.string() ?: ""

            val postPacked = PACKED_REGEX.find(postHtml)

            if (postPacked != null) {
                val postUnpacked = unpack(
                    encodedStr = postPacked.groupValues[1],
                    base = postPacked.groupValues[2].toIntOrNull() ?: 62,
                    count = postPacked.groupValues[3].toIntOrNull() ?: 0,
                    symbolTable = postPacked.groupValues[4].split("|"),
                )

                val finalSource = SOURCE_REGEX.find(postUnpacked)

                if (finalSource != null) {
                    val m3u8Url = finalSource.groupValues[1]

                    return listOf(
                        Video(
                            m3u8Url,
                            quality,
                            m3u8Url,
                            headers = kwikHeaders,
                        ),
                    )
                }
            }

            // Direct source check in POST response.
            return extractDirectM3u8(
                postHtml,
                embedUrl,
                quality,
                kwikHeaders,
            )
        }

        return emptyList()
    }

    /**
     * Fallback: look for source='' directly in raw HTML.
     */
    private fun extractDirectM3u8(
        html: String,
        referer: String,
        quality: String,
        headers: Headers,
    ): List<Video> {

        val match = SOURCE_REGEX.find(html)
            ?: return emptyList()

        val m3u8Url = match.groupValues[1]

        return listOf(
            Video(
                m3u8Url,
                quality,
                m3u8Url,
                headers = headers,
            ),
        )
    }

    // ─── JavaScript p,a,c,k,e,d Unpacker ─────────────────────────────────

    /**
     * Kotlin port of the classic JS unpacker used by many sites.
     *
     * The packed string looks like:
     *
     * 'some+encoded+string',62,N,'a|b|c|...'
     */
    private fun unpack(
        encodedStr: String,
        base: Int,
        count: Int,
        symbolTable: List<String>,
    ): String {

        // Unescape the encoded string.
        val payload = encodedStr
            .replace("\\'", "'")
            .replace("\\\\", "\\")

        // Tokenize: split on non-alphanumeric boundaries.
        val tokenRegex = Regex("""\b\w+\b""")

        return tokenRegex.replace(payload) { match ->
            val word = match.value
            val index = word.toBaseN(base)
            val symbol = symbolTable.getOrElse(index) { "" }

            symbol.ifEmpty {
                word
            }
        }
    }

    /**
     * Convert a string from arbitrary base to a base-10 Int.
     */
    private fun String.toBaseN(base: Int): Int {

        if (base <= 36) {
            return this.toIntOrNull(base) ?: 0
        }

        // For bases > 36, Kwik uses:
        // 0-9, a-z, A-Z
        val alphabet =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"

        return this.fold(0) { acc, char ->
            val digit =
                alphabet.indexOf(char)
                    .takeIf { it >= 0 }
                    ?: 0

            acc * base + digit
        }
    }
}