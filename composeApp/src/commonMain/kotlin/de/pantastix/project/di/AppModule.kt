package de.pantastix.project.di

import de.pantastix.project.data.local.DatabaseDriverFactory
import de.pantastix.project.repository.CardRepositoryImpl
import de.pantastix.project.db.CardDatabase
import de.pantastix.project.db.CardDatabaseQueries
import de.pantastix.project.repository.CardRepository
import de.pantastix.project.service.KtorTcgDexApiService
import de.pantastix.project.service.TcgDexApiService
import de.pantastix.project.ui.viewmodel.CardListViewModel
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.core.scope.get
import org.koin.dsl.module

val commonModule = module {

    // Stellt den echten Ktor-Client für die App bereit
    single {
        HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }
    }

    // Datenbank
    single {
        val driverFactory: DatabaseDriverFactory = get()
        CardDatabase(driver = driverFactory.createDriver("cards.db"))
    }
    single { get<CardDatabase>().cardDatabaseQueries }

    // Repository
    single<CardRepository> { CardRepositoryImpl(queries = get<CardDatabaseQueries>()) }

    // API Service
    single<TcgDexApiService> { KtorTcgDexApiService(client = get()) }

    // ViewModel - KORRIGIERT: Beide Abhängigkeiten werden jetzt übergeben
    factory {
        CardListViewModel(
            cardRepository = get(),
            apiService = get()
        )
    }
}