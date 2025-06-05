package de.pantastix.project.coroutines

import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

internal actual val ioDispatcher: CoroutineContext = Dispatchers.IO