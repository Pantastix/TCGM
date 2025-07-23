package de.pantastix.project.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.pantastix.project.shared.resources.MR
import de.pantastix.project.ui.viewmodel.CardListViewModel
import dev.icerock.moko.resources.compose.stringResource
import org.koin.compose.koinInject

@Composable
fun LoadingScreen(viewModel: CardListViewModel = koinInject()) {
    // Der `getValue`-Delegat wird durch die `runtime` imports bereitgestellt
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))

        // Animiert den Textwechsel für einen weicheren Übergang
        AnimatedContent(
            targetState = uiState.loadingMessage,
            transitionSpec = { fadeIn() togetherWith fadeOut() }
        ) { message ->
            Text(
                text = message ?: stringResource(MR.strings.loading_default),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
