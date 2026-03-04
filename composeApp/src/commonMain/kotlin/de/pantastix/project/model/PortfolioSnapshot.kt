package de.pantastix.project.model

import kotlinx.serialization.Serializable

@Serializable
data class PortfolioSnapshot(
    val date: String, // "YYYY-MM-DD"
    val totalValue: Double,
    val totalRawValue: Double,
    val totalGradedValue: Double,
    val cardCount: Int,
    val updatedAt: String
)

@Serializable
data class PortfolioSnapshotItem(
    val date: String,
    val cardId: Long,
    val nameLocal: String,
    val setName: String,
    val imageUrl: String?,
    val rawPrice: Double?,
    val rowCount: Int, // Total copies (raw)
    val gradedCopiesJson: String? // JSON state of GradedCopy list at that time
)
