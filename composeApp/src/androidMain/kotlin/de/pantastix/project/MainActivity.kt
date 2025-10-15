package de.pantastix.project

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import de.pantastix.project.ui.App
import de.pantastix.project.ui.viewmodel.CardListViewModel
import org.koin.androidx.viewmodel.ext.android.getViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(this) {
            val viewModel: CardListViewModel = getViewModel()
            if (viewModel.uiState.value.apiCardDetails != null) {
                viewModel.resetApiCardDetails()
            } else {
                if (isEnabled) {
                    isEnabled = false
                    onBackPressed()
                }
            }
        }

        setContent {
            App()
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}