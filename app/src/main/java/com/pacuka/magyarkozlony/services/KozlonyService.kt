package com.pacuka.magyarkozlony.services

import com.pacuka.magyarkozlony.models.Kozlony
import com.pacuka.magyarkozlony.models.KozlonyDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URLEncoder

class KozlonyService {
    private val baseUrl = "https://magyarkozlony.hu"

    suspend fun getIssues(page: Int = 1, query: String? = null): List<Kozlony> = withContext(Dispatchers.IO) {
        try {
            val isSearch = !query.isNullOrBlank()
            
            val url = if (isSearch) {
                val trimmedQuery = query!!.trim()
                val encodedQuery = URLEncoder.encode(trimmedQuery, "UTF-8")
                
                // Detect if it's a year search (4 digits)
                val yearRegex = "^\\d{4}$".toRegex()
                val yearParam = if (yearRegex.matches(trimmedQuery)) trimmedQuery else ""
                
                // Construct the official search URL
                buildString {
                    append("$baseUrl/lap-kereses?utf8=%E2%9C%93")
                    append("&filters%5Bquery%5D=$encodedQuery")
                    if (yearParam.isNotEmpty()) {
                        append("&filters%5Byear%5D=$yearParam")
                    }
                    append("&commit=Sz%C5%B1r%C3%A9s")
                    if (page > 1) {
                        append("&page=$page")
                    }
                }
            } else {
                if (page > 1) "$baseUrl/?page=$page" else baseUrl
            }
            
            val doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (X11; Linux x86_64; rv:150.0) Gecko/20100101 Firefox/150.0")
                .timeout(20000)
                .get()
            
            val issues = mutableListOf<Kozlony>()

            // On the home page, there's a .fresh-row
            if (page == 1 && !isSearch) {
                doc.select(".fresh-row").forEach { element ->
                    parseIssue(element)?.let { issues.add(it) }
                }
            }

            // Both lists use .journal-row for items
            doc.select(".journal-row").forEach { element ->
                parseIssue(element)?.let { issues.add(it) }
            }

            issues
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun parseIssue(element: Element): Kozlony? {
        val titleElement = element.selectFirst("b[itemprop=name]") ?: return null
        val title = titleElement.text()
        
        val dateElement = element.selectFirst("meta[itemprop=datePublished]")
        val date = dateElement?.attr("content") ?: ""
        
        val docLinks = element.select("a[href*=/letoltes]")
        val documents = docLinks.map { link ->
            val label = link.text().replace("»", "").trim().ifEmpty { 
                if (link.attr("href").contains("pdf")) "Közlöny" else "Letöltés" 
            }
            KozlonyDocument(label = label, url = link.attr("abs:href"))
        }

        val mainPdfUrl = documents.firstOrNull()?.url ?: ""

        // Extract year and issue from title
        val regex = "(\\d{4})\\. évi (\\d+)\\. szám".toRegex()
        val matchResult = regex.find(title)
        
        val year = matchResult?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val issue = matchResult?.groupValues?.get(2)?.toIntOrNull() ?: 0

        return Kozlony(
            year = year,
            issue = issue,
            date = date,
            title = title,
            pdfUrl = mainPdfUrl,
            documents = documents
        )
    }
}
