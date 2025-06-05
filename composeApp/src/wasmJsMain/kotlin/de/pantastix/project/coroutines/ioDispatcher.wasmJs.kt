package de.pantastix.project.coroutines

import kotlinx.coroutines.Dispatchers // Für Default
import kotlin.coroutines.CoroutineContext

internal actual val ioDispatcher: CoroutineContext = Dispatchers.Default