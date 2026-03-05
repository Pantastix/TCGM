package de.pantastix.project.service

import de.pantastix.project.model.PokemonCard
import de.pantastix.project.ui.viewmodel.ExportAttribute

class AndroidExportService : ExportService {
    override suspend fun exportToPdf(cards: List<PokemonCard>, attributes: List<ExportAttribute>) {
        // TODO: Implement using android.graphics.pdf.PdfDocument
        println("Android PDF Export not yet implemented")
    }
}
