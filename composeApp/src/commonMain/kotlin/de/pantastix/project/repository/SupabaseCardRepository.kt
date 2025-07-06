package de.pantastix.project.repository

import de.pantastix.project.model.*
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class SupabasePokemonCard(
    val id: Long? = null,
    val setId: String,
    val tcgDexCardId: String,
    val nameLocal: String,
    val nameEn: String,
    val language: String,
    val localId: String,
    val imageUrl: String? = null,
    val cardMarketLink: String? = null,
    val ownedCopies: Int,
    val notes: String? = null,
    val rarity: String? = null,
    val hp: Int? = null,
    val types: String? = null,
    val illustrator: String? = null,
    val stage: String? = null,
    val retreatCost: Int? = null,
    val regulationMark: String? = null,
    val currentPrice: Double? = null,
    val lastPriceUpdate: String? = null,
    val variantsJson: String? = null,
    val abilitiesJson: String? = null,
    val attacksJson: String? = null,
    val legalJson: String? = null
)

class SupabaseCardRepository(
    private val postgrest: Postgrest
) : CardRepository {

    private val jsonParser = Json { ignoreUnknownKeys = true }
    private val cardsTable = "PokemonCardEntity" // Name deiner Tabelle in Supabase
    private val setsTable = "SetEntity"
    private val pokemonCardInfoView = "PokemonCardInfoView"

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
                    "*", // All columns from PokemonCardEntity
                    "SetEntity(nameLocal)" // Join to get the set name - Hier wird der gequotete Name verwendet
                )
            ){
                filter {
                    eq("id", cardId)
                }
            }

        println(result2.data)
        val result = result2.decodeSingleOrNull<Map<String, Any?>>()
        println("Fetched card details: $result")

        return result?.let { map ->
            // Hier wird auf den gequoteten Namen "SetEntity" zugegriffen
            val setInfoMap = map["SetEntity"] as? Map<String, Any?>
            // Hier wird auf den gequoteten Namen "nameLocal" zugegriffen
            val setName = setInfoMap?.get("nameLocal") as? String ?: "Unknown Set"

            // Manually create SupabasePokemonCard from the map
            val dbCard = SupabasePokemonCard(
                id = (map["id"] as? Number)?.toLong(),
                setId = map["setId"] as String,
                tcgDexCardId = map["tcgDexCardId"] as String,
                nameLocal = map["nameLocal"] as String,
                nameEn = map["nameEn"] as String,
                language = map["language"] as String,
                localId = map["localId"] as String,
                imageUrl = map["imageUrl"] as? String,
                cardMarketLink = map["cardMarketLink"] as? String,
                ownedCopies = (map["ownedCopies"] as? Number)?.toInt() ?: 0,
                notes = map["notes"] as? String,
                rarity = map["rarity"] as? String,
                hp = (map["hp"] as? Number)?.toInt(),
                types = map["types"] as? String,
                illustrator = map["illustrator"] as? String,
                stage = map["stage"] as? String,
                retreatCost = (map["retreatCost"] as? Number)?.toInt(),
                regulationMark = map["regulationMark"] as? String,
                currentPrice = (map["currentPrice"] as? Number)?.toDouble(),
                lastPriceUpdate = map["lastPriceUpdate"] as? String,
                variantsJson = map["variantsJson"] as? String,
                abilitiesJson = map["abilitiesJson"] as? String,
                attacksJson = map["attacksJson"] as? String,
                legalJson = map["legalJson"] as? String
            )

            // Convert SupabasePokemonCard to PokemonCard and set the set name
            dbCard.toPokemonCard(setName = setName)
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
        val supabaseCard = SupabasePokemonCard(
            setId = card.setName, // Annahme: setName enthält die ID
            tcgDexCardId = card.tcgDexCardId,
            nameLocal = card.nameLocal,
            nameEn = card.nameEn,
            language = card.language,
            localId = card.localId.split(" / ").firstOrNull() ?: "",
            imageUrl = card.imageUrl,
            cardMarketLink = card.cardMarketLink,
            ownedCopies = card.ownedCopies,
            notes = card.notes,
            rarity = card.rarity,
            hp = card.hp,
            types = card.types.joinToString(","),
            illustrator = card.illustrator,
            stage = card.stage,
            retreatCost = card.retreatCost,
            regulationMark = card.regulationMark,
            currentPrice = card.currentPrice,
            lastPriceUpdate = card.lastPriceUpdate,
            abilitiesJson = Json.encodeToString(ListSerializer(Ability.serializer()), card.abilities),
            attacksJson = Json.encodeToString(ListSerializer(Attack.serializer()), card.attacks)
        )
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