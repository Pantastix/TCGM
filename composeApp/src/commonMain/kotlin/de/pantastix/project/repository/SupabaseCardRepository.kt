package de.pantastix.project.repository

import de.pantastix.project.model.*
import de.pantastix.project.model.supabase.FullPokemonCardResponse
import de.pantastix.project.model.supabase.SupabasePokemonCard
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class SupabaseCardRepository(
    private val postgrest: Postgrest
) : CardRepository {

    private val jsonParser = Json { ignoreUnknownKeys = true }
    private val cardsTable = "PokemonCardEntity" // Name deiner Tabelle in Supabase
    private val setsTable = "SetEntity"
    private val pokemonCardInfoView = "PokemonCardInfoView"

    private fun PokemonCard.toSupabasePokemonCard(): SupabasePokemonCard {
        return SupabasePokemonCard(
            id = this.id,
            setId = this.setId,
            tcgDexCardId = this.tcgDexCardId,
            nameLocal = this.nameLocal,
            nameEn = this.nameEn,
            language = this.language,
            localId = this.localId, // Speichert den kombinierten String "051 / 244"
            imageUrl = this.imageUrl,
            cardMarketLink = this.cardMarketLink,
            ownedCopies = this.ownedCopies,
            notes = this.notes,
            rarity = this.rarity,
            hp = this.hp,
            types = this.types.joinToString(","), // Konvertiert Liste zu kommasepariertem String
            illustrator = this.illustrator,
            stage = this.stage,
            retreatCost = this.retreatCost,
            regulationMark = this.regulationMark,
            currentPrice = this.currentPrice,
            lastPriceUpdate = this.lastPriceUpdate,
            variantsJson = null, // Nicht im PokemonCard Modell enthalten
            abilitiesJson = jsonParser.encodeToString(ListSerializer(Ability.serializer()), this.abilities),
            attacksJson = jsonParser.encodeToString(ListSerializer(Attack.serializer()), this.attacks),
            legalJson = null // Nicht im PokemonCard Modell enthalten
        )
    }

    private fun SupabasePokemonCard.toPokemonCard(setName: String): PokemonCard {
        return PokemonCard(
            id = this.id,
            tcgDexCardId = this.tcgDexCardId,
            nameLocal = this.nameLocal,
            nameEn = this.nameEn,
            language = this.language,
            imageUrl = this.imageUrl,
            cardMarketLink = this.cardMarketLink,
            ownedCopies = this.ownedCopies,
            notes = this.notes,
            setName = setName, // SetName is passed here
            localId = this.localId,
            currentPrice = this.currentPrice,
            lastPriceUpdate = this.lastPriceUpdate,
            rarity = this.rarity,
            hp = this.hp,
            types = this.types?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList(),
            illustrator = this.illustrator,
            stage = this.stage,
            retreatCost = this.retreatCost,
            regulationMark = this.regulationMark,
            abilities = this.abilitiesJson?.let { jsonParser.decodeFromString(ListSerializer(Ability.serializer()), it) } ?: emptyList(),
            attacks = this.attacksJson?.let { jsonParser.decodeFromString(ListSerializer(Attack.serializer()), it) } ?: emptyList(),
            setId = this.setId,
        )
    }

    override fun getCardInfos(): Flow<List<PokemonCardInfo>> = flow {
        println("Fetching card infos from Supabase...")
            val data = postgrest.from(pokemonCardInfoView)
                .select() // Select all columns from the view
                .decodeList<PokemonCardInfo>() // Directly decode into PokemonCardInfo
            emit(data)
    }

    override suspend fun getFullCardDetails(cardId: Long): PokemonCard? {
        println("Fetching full card details for card ID: $cardId from Supabase...")
        // Fetches the card and the associated set name via a join
        val result2 = postgrest.from(cardsTable)
            .select(
                columns = Columns.list(
                    "*",
                    "SetEntity(nameLocal)"
                )
            ){
                filter {
                    eq("id", cardId)
                }
            }

        println(result2.data)
        val fullCardData = result2.decodeSingleOrNull<FullPokemonCardResponse>()
        println("Fetched card details: $fullCardData")

        return fullCardData?.let { data ->
            val setName = data.setEntity?.nameLocal ?: "Unknown Set"
            // Convert FullPokemonCardResponse to PokemonCard
            PokemonCard(
                id = data.id,
                tcgDexCardId = data.tcgDexCardId,
                nameLocal = data.nameLocal,
                nameEn = data.nameEn,
                language = data.language,
                imageUrl = data.imageUrl,
                cardMarketLink = data.cardMarketLink,
                ownedCopies = data.ownedCopies,
                notes = data.notes,
                setId = data.setId, // Use setId from FullPokemonCardResponse for apiSetId
                setName = setName,
                localId = data.localId,
                currentPrice = data.currentPrice,
                lastPriceUpdate = data.lastPriceUpdate,
                rarity = data.rarity,
                hp = data.hp,
                types = data.types?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList(),
                illustrator = data.illustrator,
                stage = data.stage,
                retreatCost = data.retreatCost,
                regulationMark = data.regulationMark,
                abilities = data.abilitiesJson?.let { jsonParser.decodeFromString(ListSerializer(Ability.serializer()), it) } ?: emptyList(),
                attacks = data.attacksJson?.let { jsonParser.decodeFromString(ListSerializer(Attack.serializer()), it) } ?: emptyList()
            )
        }
    }

    override suspend fun findCardByTcgDexId(tcgDexId: String, language: String): PokemonCardInfo? {
        val data = postgrest.from(pokemonCardInfoView).select() {
            filter {
                eq("tcgDexCardId", tcgDexId)
                eq("language", language)
            }
        }.decodeSingleOrNull<PokemonCardInfo>()
        return data
    }

    override suspend fun insertFullPokemonCard(card: PokemonCard) {
        val supabaseCard = card.toSupabasePokemonCard()
        postgrest.from(cardsTable).insert(supabaseCard)
    }

    override suspend fun updateCardUserData(cardId: Long, ownedCopies: Int, notes: String?, currentPrice: Double?, lastPriceUpdate: String?) {
        postgrest.from(cardsTable).update({
            set("ownedCopies", ownedCopies)
            set("notes", notes)
            set("currentPrice", currentPrice)
            set("lastPriceUpdate", lastPriceUpdate)
        }) {
            filter { eq("id", cardId) }
        }
    }

    override suspend fun deleteCardById(cardId: Long) {
        postgrest.from(cardsTable).delete { filter { eq("id", cardId) } }
    }

    // --- SET-OPERATIONEN---
    override fun getAllSets(): Flow<List<SetInfo>> = flow {
        val data = postgrest.from(setsTable).select().decodeList<SetInfo>()
        emit(data)
    }

    override suspend fun syncSets(sets: List<SetInfo>) {
        println("Synchronizing sets with Supabase...")
        sets.forEach { setInfo ->
            postgrest.from(setsTable).upsert(
                value = setInfo,
                onConflict = "setId"
            )
        }
    }

    override suspend fun updateSetAbbreviation(setId: String, abbreviation: String) {
        postgrest.from(setsTable).update({
            set("abbreviation", abbreviation)
        }) {
            filter { eq("setId", setId) }
        }
    }

    override suspend fun findExistingCard(setId: String, localId: String, language: String): PokemonCardInfo? = null
}