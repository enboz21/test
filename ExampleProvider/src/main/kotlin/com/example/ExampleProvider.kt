package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class ExampleProvider : MainAPI() {
    // 1. TEMEL AYARLAR
    override var mainUrl = "https://animecix.tv"
    override var name = "Animecit"
    override val hasMainPage = true
    override var lang = "tr"

    // Anime ve Filmleri desteklediğini belirtiyoruz
    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.Movie,
        TvType.TvSeries
    )

    // 2. ARAMA FONKSİYONU
    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/ara?q=$query"

        val document = app.get(url).document

        return document.select("title-portrait-item").mapNotNull {
            val titleElement = it.select("a.title")
            val title = titleElement.text().trim()
            val href = fixUrl(titleElement.attr("href"))
            val image = it.select("img").attr("src")

            newAnimeSearchResponse(title, href, TvType.Anime) {
                this.posterUrl = image
            }
        }
    }

    // 3. DETAY SAYFASI
    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("h1.title")?.text() ?: document.selectFirst("h1")?.text() ?: "Bilinmeyen Başlık"
        val description = document.select("div.synopsis, div.description").text()
        val poster = document.select("img.poster, div.poster img").attr("src")

        val episodes = document.select("div.episodes div.episode").mapNotNull {
            val titleElement = it.select("a.title")
            val name = titleElement.text()
            val href = fixUrl(titleElement.attr("href"))
            val image = it.select("media-image img").attr("src")
            val date = it.select("div.release_date").text()

            val seasonEpStr = it.select("season-episode-number span").text()
            val regex = Regex("S\\s*(\\d+)\\s*B\\s*(\\d+)")
            val match = regex.find(seasonEpStr)

            // toInt() hatasını önlemek için güvenli çevirme
            val seasonNum = match?.groupValues?.get(1)?.toIntOrNull()
            val episodeNum = match?.groupValues?.get(2)?.toIntOrNull()

            // Episode oluşturucu (Değişiklik burada yapıldı)
            newEpisode(href) {
                this.name = name
                this.season = seasonNum
                this.episode = episodeNum
                this.posterUrl = image
                this.addDate(date) // addDate fonksiyonunu kullanmayı dene, yoksa this.date = date yap
            }
        }

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            this.posterUrl = poster
            this.plot = description
            // HATA ÇÖZÜMÜ: DubStatus.Subbed parametresi eklendi
            addEpisodes(DubStatus.Subbed, episodes)
        }
    }

    // 4. VİDEO LİNKLERİNİ ÇEKME
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val document = app.get(data).document

        document.select("div.iframe-container iframe, iframe").forEach {
            var sourceUrl = it.attr("src")

            if (sourceUrl.startsWith("//")) {
                sourceUrl = "https:$sourceUrl"
            }

            loadExtractor(sourceUrl, data, subtitleCallback, callback)
        }

        return true
    }
}