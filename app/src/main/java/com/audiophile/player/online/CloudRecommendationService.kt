package com.audiophile.player.online

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import uniffi.audiophile_core.TrackInfo
import java.util.concurrent.ConcurrentHashMap

data class RecommendationSection(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val language: String? = null,
    val items: List<OnlineSongItem>
)

data class LanguageOption(
    val code: String,
    val name: String,
    val nativeScript: String
)

data class PopularArtist(
    val name: String,
    val language: String,
    val imageUrl: String = "",
    val category: String = "Popular"
)

object CloudRecommendationService {
    private const val TAG = "CloudRecommendationService"

    // In-memory cache for fast, zero-lag loading across screen recompositions
    private val sectionCache = ConcurrentHashMap<String, List<OnlineSongItem>>()
    private val artistPortraitCache = ConcurrentHashMap<String, String>()
    private var lastCacheTimeMs = 0L
    private const val CACHE_EXPIRY_MS = 30 * 60 * 1000L // 30 minutes

    val curatedLanguages = listOf(
        LanguageOption("Malayalam", "Malayalam", "മലയാളം"),
        LanguageOption("Tamil", "Tamil", "தமிழ்"),
        LanguageOption("Hindi", "Hindi", "हिन्दी"),
        LanguageOption("English", "English", "Global"),
        LanguageOption("Telugu", "Telugu", "తెలుగు"),
        LanguageOption("Punjabi", "Punjabi", "ਪੰਜਾਬੀ"),
        LanguageOption("Kannada", "Kannada", "ಕನ್ನಡ"),
        LanguageOption("Bengali", "Bengali", "বাংলা"),
        LanguageOption("Spanish", "Spanish", "Español"),
        LanguageOption("K-Pop", "K-Pop", "한국어")
    )

    val defaultLanguages = curatedLanguages.map { it.code }

    val popularArtists = listOf(
        // Malayalam
        PopularArtist("Sushin Shyam", "Malayalam"),
        PopularArtist("Deepak Dev", "Malayalam"),
        PopularArtist("Shaan Rahman", "Malayalam"),
        PopularArtist("K. S. Chithra", "Malayalam"),
        PopularArtist("K. J. Yesudas", "Malayalam"),
        PopularArtist("Sanah Moidutty", "Malayalam"),
        PopularArtist("Job Kurian", "Malayalam"),
        // Tamil
        PopularArtist("A.R. Rahman", "Tamil"),
        PopularArtist("Anirudh Ravichander", "Tamil"),
        PopularArtist("Yuvan Shankar Raja", "Tamil"),
        PopularArtist("Harris Jayaraj", "Tamil"),
        PopularArtist("Sid Sriram", "Tamil"),
        PopularArtist("Pradeep Kumar", "Tamil"),
        PopularArtist("Ilaiyaraaja", "Tamil"),
        // Hindi
        PopularArtist("Arijit Singh", "Hindi"),
        PopularArtist("Pritam", "Hindi"),
        PopularArtist("Shreya Ghoshal", "Hindi"),
        PopularArtist("Vishal Mishra", "Hindi"),
        PopularArtist("Atif Aslam", "Hindi"),
        PopularArtist("Badshah", "Hindi"),
        // English
        PopularArtist("Taylor Swift", "English"),
        PopularArtist("The Weeknd", "English"),
        PopularArtist("Billie Eilish", "English"),
        PopularArtist("Ed Sheeran", "English"),
        PopularArtist("Bruno Mars", "English"),
        PopularArtist("Dua Lipa", "English"),
        PopularArtist("Drake", "English"),
        PopularArtist("Kendrick Lamar", "English"),
        // Telugu
        PopularArtist("Devi Sri Prasad", "Telugu"),
        PopularArtist("Thaman S", "Telugu"),
        PopularArtist("Ram Miriyala", "Telugu"),
        PopularArtist("M.M. Keeravaani", "Telugu"),
        // Punjabi
        PopularArtist("Diljit Dosanjh", "Punjabi"),
        PopularArtist("Karan Aujla", "Punjabi"),
        PopularArtist("AP Dhillon", "Punjabi"),
        PopularArtist("Sidhu Moose Wala", "Punjabi")
    )

    suspend fun getArtistPortrait(name: String): String {
        artistPortraitCache[name]?.let { return it }
        return try {
            val list = JioSaavnService.searchArtists(name)
            val img = list.firstOrNull()?.imageUrl ?: ""
            if (img.isNotBlank()) {
                artistPortraitCache[name] = img
            }
            img
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Clears cached recommendation data to force fresh network fetch.
     */
    fun clearCache() {
        sectionCache.clear()
        lastCacheTimeMs = 0L
    }

    private val languageChartListIds = mapOf(
        "Malayalam" to "1134705865",
        "Tamil" to "1134651042",
        "Hindi" to "1134543272",
        "English" to "1134595537",
        "Telugu" to "1134643225",
        "Punjabi" to "1134543511",
        "Kannada" to "1134676505"
    )

    /**
     * Smart title deduplication so identical tracks across languages or remixes
     * do not clutter the recommendation feed.
     */
    fun List<OnlineSongItem>.deduplicate(): List<OnlineSongItem> {
        val seen = mutableSetOf<String>()
        return filter { item ->
            val cleanTitle = item.title
                .replace(Regex("(?i)\\((from|telugu|tamil|malayalam|hindi|kannada|punjabi|remix|reprised|cover|slowed|lofi).*?\\)"), "")
                .replace(Regex("(?i)\\[(from|telugu|tamil|malayalam|hindi|kannada|punjabi|remix|reprised|cover|slowed|lofi).*?\\]"), "")
                .replace(Regex("(?i)-(telugu|tamil|malayalam|hindi|kannada|punjabi|remix|version)"), "")
                .trim()
            val normTitle = cleanTitle.replace(Regex("[^a-zA-Z0-9]"), "").lowercase()
            val cleanArtist = item.artist.split(",", "&", "feat", "ft", "-").firstOrNull()?.trim() ?: ""
            val normArtist = cleanArtist.replace(Regex("[^a-zA-Z0-9]"), "").take(6).lowercase()
            val key = if (normTitle.length >= 3) "${normTitle}_${normArtist}" else item.id
            seen.add(key)
        }
    }

    /**
     * Fetches personalized recommendation sections based on user's selected languages,
     * favorite artists, and recently played tracks.
     */
    suspend fun getPersonalizedFeed(
        selectedLanguages: Set<String>,
        selectedArtists: Set<String>,
        recentTracks: List<TrackInfo> = emptyList(),
        activeLanguageFilter: String? = null
    ): List<RecommendationSection> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (now - lastCacheTimeMs > CACHE_EXPIRY_MS) {
            sectionCache.clear()
            lastCacheTimeMs = now
        }

        val effectiveLanguages = if (selectedLanguages.isEmpty()) {
            setOf("Malayalam", "Tamil", "English", "Hindi")
        } else {
            selectedLanguages
        }

        val languagesToFetch = if (!activeLanguageFilter.isNullOrBlank() && activeLanguageFilter != "All") {
            listOf(activeLanguageFilter)
        } else {
            effectiveLanguages.take(4).toList()
        }

        val sections = mutableListOf<RecommendationSection>()

        coroutineScope {
            // 1. Official Curated Superhits Top 50 Chart per selected language
            val langTasks = languagesToFetch.map { lang ->
                async {
                    val chartId = languageChartListIds[lang]
                    val items = if (chartId != null) {
                        getCachedOrFetch("chart_$chartId") {
                            JioSaavnService.getPlaylistSongs(chartId).deduplicate()
                        }
                    } else {
                        getCachedOrFetch("lang_$lang") {
                            JioSaavnService.searchSongs(lang).deduplicate()
                        }
                    }

                    if (items.isNotEmpty()) {
                        RecommendationSection(
                            id = "trending_$lang",
                            title = "Trending in $lang",
                            subtitle = "Top 50 Superhits Chart",
                            language = lang,
                            items = items.take(15)
                        )
                    } else null
                }
            }

            // 2. Artist spotlights (deduplicated genuine artist hits)
            val artistsToFetch = if (selectedArtists.isNotEmpty()) {
                selectedArtists.take(4).toList()
            } else {
                listOf("A.R. Rahman", "Sushin Shyam", "Anirudh Ravichander", "Arijit Singh")
            }

            val artistTasks = artistsToFetch.map { artist ->
                async {
                    val items = getCachedOrFetch("artist_$artist") {
                        JioSaavnService.searchSongs(artist).deduplicate()
                    }
                    if (items.isNotEmpty()) {
                        RecommendationSection(
                            id = "artist_$artist",
                            title = "Best of $artist",
                            subtitle = "Essential hits & fan favorites",
                            language = null,
                            items = items.take(15)
                        )
                    } else null
                }
            }

            // 3. Mood / Vibe Mixes (Acoustic, Late Night, Chill)
            val moodTask = async {
                val query = if (languagesToFetch.contains("English")) "Top 50 Global" else "${languagesToFetch.first()} chill acoustic"
                val items = getCachedOrFetch("mood_$query") {
                    JioSaavnService.searchSongs(query).deduplicate()
                }
                if (items.isNotEmpty()) {
                    RecommendationSection(
                        id = "mood_mix",
                        title = "Daily Vibe & Chill",
                        subtitle = "Handpicked for uninterrupted listening",
                        language = null,
                        items = items.take(12)
                    )
                } else null
            }

            // 4. "Because you listened to [Recent Track Artist]"
            val recentTask = async {
                val candidateArtist = recentTracks.mapNotNull { it.artist }.firstOrNull { it.isNotBlank() && it != "Unknown Artist" }
                if (!candidateArtist.isNullOrBlank()) {
                    val items = getCachedOrFetch("rec_artist_$candidateArtist") {
                        JioSaavnService.searchSongs(candidateArtist).deduplicate()
                    }
                    if (items.isNotEmpty()) {
                        RecommendationSection(
                            id = "recent_rec",
                            title = "Because you played $candidateArtist",
                            subtitle = "Similar tracks you'll love",
                            language = null,
                            items = items.take(12)
                        )
                    } else null
                } else null
            }

            // Await all parallel fetches
            val langResults = langTasks.awaitAll().filterNotNull()
            val artistResults = artistTasks.awaitAll().filterNotNull()
            val moodResult = moodTask.await()
            val recentResult = recentTask.await()

            sections.addAll(langResults)
            if (recentResult != null) sections.add(recentResult)
            sections.addAll(artistResults)
            if (moodResult != null) sections.add(moodResult)
        }

        sections
    }

    /**
     * Fetches infinite radio/autoplay tracks when a queue finishes.
     */
    suspend fun getAutoplayRadioTracks(
        artist: String,
        currentTitle: String
    ): List<OnlineSongItem> = withContext(Dispatchers.IO) {
        val query = artist.ifBlank { "Top 50" }
        try {
            val list = JioSaavnService.searchSongs(query).deduplicate()
            list.filter { !it.title.equals(currentTitle, ignoreCase = true) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun getCachedOrFetch(key: String, fetcher: suspend () -> List<OnlineSongItem>): List<OnlineSongItem> {
        sectionCache[key]?.let { return it }
        return try {
            val results = fetcher()
            if (results.isNotEmpty()) {
                sectionCache[key] = results
            }
            results
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching recommendation for '$key'", e)
            emptyList()
        }
    }
}
