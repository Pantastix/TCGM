package de.pantastix.project.model

import kotlinx.serialization.Serializable

@Serializable
data class TypeReference(
    val id: String,
    val name_de: String,
    val name_en: String,
    val name_fr: String? = null,
    val name_es: String? = null,
    val name_it: String? = null,
    val name_pt: String? = null,
    val name_jp: String? = null
) {
    fun getNameByCode(langCode: String): String {
        return when (langCode.lowercase()) {
            "de" -> name_de
            "en" -> name_en
            "fr" -> name_fr ?: name_en
            "es" -> name_es ?: name_en
            "it" -> name_it ?: name_en
            "pt" -> name_pt ?: name_en
            "jp" -> name_jp ?: name_en
            else -> name_en
        }
    }
    
    /**
     * Returns all known names for this type across all languages.
     * Useful for database searching.
     */
    fun getAllNames(): List<String> {
        return listOfNotNull(
            name_de, name_en, name_fr, name_es, name_it, name_pt, name_jp
        ).distinct()
    }
}
