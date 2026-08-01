package app.amisles.hanime.data.parser

import app.amisles.hanime.domain.model.DownloadQuality
import app.amisles.hanime.core.common.util.AppLogger
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * 下载页解析器
 */
class DownloadPageParser {

    fun parse(html: String, baseUrl: String): List<DownloadQuality> {
        AppLogger.log("DownloadPageParser", "parse called")
        val qualities = mutableListOf<DownloadQuality>()
        try {
            val doc = Jsoup.parse(html, baseUrl)
            val table = doc.selectFirst("table.download-table")
            if (table == null) {
                AppLogger.log("DownloadPageParser", "No download-table found, trying alternative selectors")
                val altTables = doc.select("table")
                for (t in altTables) {
                    if (t.text().contains("下載") || t.text().contains("畫質")) {
                        parseDownloadTable(t, qualities)
                        if (qualities.isNotEmpty()) break
                    }
                }
            } else {
                parseDownloadTable(table, qualities)
            }
            AppLogger.log("DownloadPageParser", "Found ${qualities.size} download qualities")
            return qualities
        } catch (e: Exception) {
            AppLogger.logError("DownloadPageParser", "Error parsing download page: ${e.message}", e)
            return emptyList()
        }
    }

    private fun parseDownloadTable(table: Element, qualities: MutableList<DownloadQuality>) {
        val rows = table.select("tbody tr")
        AppLogger.log("DownloadPageParser", "Download table has ${rows.size} rows")
        for (row in rows) {
            try {
                val cells = row.select("td")
                if (cells.size < 4) continue

                val qualityText = cells.getOrNull(1)?.text()?.trim() ?: continue
                val fileType = cells.getOrNull(2)?.text()?.trim() ?: "mp4"
                val fileSize = cells.getOrNull(3)?.text()?.trim() ?: "N/A"

                val downloadLink = cells.getOrNull(cells.size - 1)?.selectFirst("a[data-url]")
                val downloadUrl = downloadLink?.attr("data-url")?.trim() ?: continue

                val resolution = Regex("\\((\\d+p)\\)").find(qualityText)?.groupValues?.get(1)
                    ?: if (qualityText.contains("1080")) "1080p"
                    else if (qualityText.contains("720")) "720p"
                    else if (qualityText.contains("480")) "480p"
                    else ""

                qualities.add(
                    DownloadQuality(
                        quality = qualityText,
                        resolution = resolution,
                        fileType = fileType,
                        fileSize = fileSize,
                        downloadUrl = downloadUrl
                    )
                )
                AppLogger.log("DownloadPageParser", "Quality: $qualityText, URL: ${downloadUrl.take(50)}...")
            } catch (e: Exception) {
                AppLogger.logError("DownloadPageParser", "Error parsing download row: ${e.message}", e)
                continue
            }
        }
    }
}
