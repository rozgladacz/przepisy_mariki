package pl.local.przepisy.data.importer

import org.jsoup.nodes.Document
import pl.local.przepisy.domain.model.RecipeDraft

interface RecipeSourceAdapter {
    val supportedHosts: Set<String>
    fun parse(document: Document, canonicalUrl: String): RecipeDraft?

    fun supports(host: String): Boolean = host.lowercase().removePrefix("www.") in supportedHosts
}

class RecipeImportException(message: String) : Exception(message)
