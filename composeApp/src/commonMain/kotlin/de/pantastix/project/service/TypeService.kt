package de.pantastix.project.service

import de.pantastix.project.model.TypeReference
import de.pantastix.project.repository.CardRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TypeService(private var repository: CardRepository) {
    private val _types = MutableStateFlow<List<TypeReference>>(emptyList())
    val types: StateFlow<List<TypeReference>> = _types.asStateFlow()

    fun setRepository(newRepository: CardRepository) {
        repository = newRepository
    }

    suspend fun refresh() {
        val refs = repository.getAllTypeReferences()
        _types.value = refs
    }

    /**
     * Translates a local type name (e.g., "Wasser") to the current app language.
     */
    fun translate(typeName: String, langCode: String): String {
        val refs = _types.value
        val ref = refs.find { it.getAllNames().any { name -> name.equals(typeName, ignoreCase = true) } }
        return ref?.getNameByCode(langCode) ?: typeName
    }

    /**
     * Returns all localized names for a given input (English or otherwise).
     * Useful for building database filters.
     */
    fun getSearchTerms(input: String): List<String> {
        val refs = _types.value
        val ref = refs.find { 
            it.id.equals(input, ignoreCase = true) || 
            it.getAllNames().any { name -> name.equals(input, ignoreCase = true) } 
        }
        return ref?.getAllNames() ?: listOf(input)
    }
}
