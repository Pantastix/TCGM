package de.pantastix.project.service

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlin.test.*
import kotlinx.coroutines.test.runTest


class KtorTcgDexApiServiceTest {

    private fun createMockClient(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): HttpClient {
        val mockEngine = MockEngine { request ->
            handler(this, request)
        }
        return HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }
    }

    @Test
    fun `test getAllSets should return combined list on success`() = runTest {
        val mockGermanSetsJson = """
            [
                {"id": "sv10", "name": "Ewige Rivalen", "logo": "logo.png", "cardCount": {"official": 182, "total": 244}},
                {"id": "base1", "name": "Grundset", "logo": "logo.png", "cardCount": {"official": 102, "total": 102}}
            ]
        """
        val mockEnglishSetsJson = """
            [
                {"id": "sv10", "name": "Destined Rivals", "logo": "logo.png", "cardCount": {"official": 182, "total": 244}},
                {"id": "base1", "name": "Base Set", "logo": "logo.png", "cardCount": {"official": 102, "total": 102}}
            ]
        """

        val mockClient = createMockClient { request ->
            when (request.url.encodedPath) {
                "/v2/de/sets" -> respond(mockGermanSetsJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                "/v2/en/sets" -> respond(mockEnglishSetsJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                else -> error("Unerwartete Anfrage an ${request.url.encodedPath}")
            }
        }
        val apiService = KtorTcgDexApiService(mockClient)
        val result = apiService.getAllSets()

        assertNotNull(result)
        assertEquals(2, result.size)
        val firstSet = result.find { it.setId == "sv10" }
        assertNotNull(firstSet)
        assertEquals("Ewige Rivalen", firstSet.nameDe)
        assertEquals("Destined Rivals", firstSet.nameEn)
    }

    @Test
    fun `test getGermanCardDetails should return card details on success`() = runTest {
        val mockCardJson = """
            {
                "id": "sv10-051", "localId": "051", "name": "Team Rockets Arktos", "hp": 120,
                "rarity": "Selten", "category": "Pokémon",
                "set": {"id": "sv10", "name": "Ewige Rivalen", "cardCount": {"official": 182, "total": 244}},
                "attacks": [{"name": "Dunkler Frost", "damage": "60+"}]
            }
        """

        val mockClient = createMockClient { respond(mockCardJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }
        val apiService = KtorTcgDexApiService(mockClient)
        val cardDetails = apiService.getGermanCardDetails("sv10", "051")

        assertNotNull(cardDetails)
        assertEquals("Team Rockets Arktos", cardDetails.name)
        assertEquals("sv10-051", cardDetails.id)
        assertEquals(120, cardDetails.hp)
        assertTrue(cardDetails.attacks?.isNotEmpty() ?: false)
        assertEquals("Dunkler Frost", cardDetails.attacks?.first()?.name)
    }

    @Test
    fun `test getEnglishCardDetails should return card details on success`() = runTest {
        val mockCardJson = """
            {
                "id": "sv10-051", "localId": "051", "name": "Team Rocket's Articuno", "hp": 120,
                "rarity": "Rare", "category": "Pokémon",
                "set": {"id": "sv10", "name": "Destined Rivals", "cardCount": {"official": 182, "total": 244}}
            }
        """

        val mockClient = createMockClient { respond(mockCardJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }
        val apiService = KtorTcgDexApiService(mockClient)
        val cardDetails = apiService.getEnglishCardDetails("sv10", "051")

        assertNotNull(cardDetails)
        assertEquals("Team Rocket's Articuno", cardDetails.name)
        assertEquals("sv10-051", cardDetails.id)
    }

    @Test
    fun `test getGermanCardDetails should return null on API error`() = runTest {
        val mockClient = createMockClient { respond("Not Found", HttpStatusCode.NotFound) }
        val apiService = KtorTcgDexApiService(mockClient)
        val cardDetails = apiService.getGermanCardDetails("invalid-set", "999")
        assertNull(cardDetails)
    }

    @Test
    fun `test getAllSets should return empty list on network error`() = runTest {
        val mockClient = createMockClient { throw Exception("Network connection failed") }
        val apiService = KtorTcgDexApiService(mockClient)
        val sets = apiService.getAllSets()
        assertNotNull(sets)
        assertTrue(sets.isEmpty())
    }
}
