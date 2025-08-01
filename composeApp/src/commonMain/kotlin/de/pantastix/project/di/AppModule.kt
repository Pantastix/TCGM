package de.pantastix.project.di

import de.pantastix.project.data.local.DatabaseDriverFactory
import de.pantastix.project.repository.LocalCardRepositoryImpl
import de.pantastix.project.db.cards.CardDatabase
import de.pantastix.project.db.cards.CardDatabaseQueries
import de.pantastix.project.db.settings.SettingsDatabase
import de.pantastix.project.repository.CardRepository
import de.pantastix.project.repository.SettingsRepository
import de.pantastix.project.repository.SettingsRepositoryImpl
import de.pantastix.project.service.CombinedTcgApiService
import de.pantastix.project.service.TcgApiService
import de.pantastix.project.service.TcgDexApiService
import de.pantastix.project.service.TcgIoApiService
import de.pantastix.project.ui.viewmodel.CardListViewModel
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
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

    single {
        val driverFactory: DatabaseDriverFactory = get()
        SettingsDatabase(driver = driverFactory.createDriver("settings.db"))
    }
    single { get<SettingsDatabase>().settingsDatabaseQueries }


    // Repository
    single<CardRepository>{ LocalCardRepositoryImpl(queries = get<CardDatabaseQueries>()) }
    single<SettingsRepository> { SettingsRepositoryImpl(queries = get()) }

    // Einzelne API Service Implementierungen
    // Diese werden intern von CombinedTcgApiService verwendet
    single { TcgDexApiService(client = get()) }
    single { TcgIoApiService(client = get()) }

    // Der Haupt-API-Service, der an andere Komponenten injiziert wird.
    // Er kombiniert die Funktionalität der beiden anderen Services.
    single<TcgApiService> {
        CombinedTcgApiService(
            localApiService = get<TcgDexApiService>(),
            enApiService = get<TcgIoApiService>()
        )
    }

    // ViewModel
    single {
        CardListViewModel(
            localCardRepository = get(),
            settingsRepository = get(), // Das neue Repository wird hier injiziert
            apiService = get()
        )
    }
}