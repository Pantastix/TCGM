package de.pantastix.project.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.font.FontWeight
import de.pantastix.project.ui.components.WorkInProgressScreen
import de.pantastix.project.ui.viewmodel.CardListViewModel

@Composable
fun ExportScreen (
    viewModel: CardListViewModel,
){
    WorkInProgressScreen()
}