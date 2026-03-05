package de.pantastix.project.service

import com.lowagie.text.Document
import com.lowagie.text.Element
import com.lowagie.text.Image
import com.lowagie.text.Paragraph
import com.lowagie.text.pdf.PdfPCell
import com.lowagie.text.pdf.PdfPTable
import com.lowagie.text.pdf.PdfWriter
import de.pantastix.project.model.PokemonCard
import de.pantastix.project.ui.viewmodel.ExportAttribute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.FileOutputStream
import java.net.URL

class DesktopExportService : ExportService {
    override suspend fun exportToPdf(cards: List<PokemonCard>, attributes: List<ExportAttribute>) {
        if (attributes.isEmpty()) return

        withContext(Dispatchers.IO) {
            val fileDialog = FileDialog(null as Frame?, "PDF speichern", FileDialog.SAVE)
            fileDialog.file = "pokemon_cards_export.pdf"
            fileDialog.isVisible = true
            val directory = fileDialog.directory
            val fileName = fileDialog.file ?: return@withContext
            val filePath = directory + fileName

            val document = Document()
            try {
                PdfWriter.getInstance(document, FileOutputStream(filePath))
                document.open()
                document.add(Paragraph("Pokémon Karten Export"))
                document.add(Paragraph(" "))

                val columns = attributes.size
                val table = PdfPTable(columns)
                table.widthPercentage = 100f

                // Calculate relative widths: Image needs more space, others standard
                val relativeWidths = FloatArray(columns) { 1f }
                attributes.forEachIndexed { index, attr ->
                    if (attr == ExportAttribute.IMAGE) relativeWidths[index] = 1.5f
                }
                table.setWidths(relativeWidths)

                // Header
                attributes.forEach { attr ->
                    val cell = PdfPCell(Paragraph(getAttributeLabel(attr)))
                    cell.horizontalAlignment = Element.ALIGN_CENTER
                    table.addCell(cell)
                }

                // Data
                cards.forEach { card ->
                    attributes.forEach { attr ->
                        when (attr) {
                            ExportAttribute.NAME -> table.addCell(card.nameLocal)
                            ExportAttribute.TYPE -> table.addCell(card.types.joinToString(", "))
                            ExportAttribute.NUMBER -> table.addCell(card.localId)
                            ExportAttribute.SET -> table.addCell(card.setName)
                            ExportAttribute.PRICE -> {
                                val cell = PdfPCell(Paragraph("${card.currentPrice ?: 0.0} €"))
                                cell.horizontalAlignment = Element.ALIGN_RIGHT
                                table.addCell(cell)
                            }
                            ExportAttribute.QUANTITY -> {
                                val cell = PdfPCell(Paragraph(card.ownedCopies.toString()))
                                cell.horizontalAlignment = Element.ALIGN_CENTER
                                table.addCell(cell)
                            }
                            ExportAttribute.IMAGE -> {
                                try {
                                    val imageUrl = card.imageUrl
                                    if (imageUrl != null) {
                                        val image = Image.getInstance(URL(imageUrl))
                                        image.scaleToFit(60f, 60f)
                                        val cell = PdfPCell(image)
                                        cell.horizontalAlignment = Element.ALIGN_CENTER
                                        table.addCell(cell)
                                    } else {
                                        table.addCell("Kein Bild")
                                    }
                                } catch (e: Exception) {
                                    table.addCell("Fehler")
                                }
                            }
                        }
                    }
                }

                document.add(table)
            } finally {
                document.close()
            }
        }
    }

    private fun getAttributeLabel(attribute: ExportAttribute): String {
        return when (attribute) {
            ExportAttribute.NAME -> "Name"
            ExportAttribute.TYPE -> "Typ"
            ExportAttribute.NUMBER -> "Nummer"
            ExportAttribute.SET -> "Set"
            ExportAttribute.PRICE -> "Preis"
            ExportAttribute.QUANTITY -> "Menge"
            ExportAttribute.IMAGE -> "Bild"
        }
    }
}
