package de.pantastix.project.service

import de.pantastix.project.model.PokemonCard
import de.pantastix.project.ui.viewmodel.ExportAttribute

interface ExportService {
    suspend fun exportToPdf(cards: List<PokemonCard>, attributes: List<ExportAttribute>)
}
