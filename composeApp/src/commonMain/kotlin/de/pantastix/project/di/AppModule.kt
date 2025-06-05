package de.pantastix.project.di

import CardRepositoryImpl
import de.pantastix.project.data.local.DatabaseDriverFactory
import de.pantastix.project.db.CardDatabase
import de.pantastix.project.repository.CardRepository
import de.pantastix.project.ui.viewmodel.CardListViewModel
import org.koin.dsl.module

val commonModule = module {
    // DatabaseDriverFactory wird plattformspezifisch bereitgestellt (siehe nächster Schritt Koin-Initialisierung)

    // Stellt die CardDatabase-Instanz bereit. Benötigt eine DatabaseDriverFactory.
    single {
        val driverFactory: DatabaseDriverFactory = get() // Koin holt die plattformspezifische Factory
        CardDatabase(driver = driverFactory.createDriver("cards.db")) // "cards.db" ist der Dateiname deiner Datenbank
    }

    // Stellt die von SQLDelight generierten Queries bereit. Benötigt CardDatabase.
    single { get<CardDatabase>().cardDatabaseQueries }

    // Stellt die Implementierung des CardRepository bereit. Benötigt CardDatabaseQueries.
    single<CardRepository> { CardRepositoryImpl(queries = get()) }

    factory { CardListViewModel(cardRepository = get()) }
}