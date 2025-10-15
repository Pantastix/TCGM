package de.pantastix.project.platform

import android.content.pm.PackageManager
import org.koin.core.context.GlobalContext
import org.koin.java.KoinJavaComponent.getKoin

actual fun getAppVersion(): String {
    return try {
        val context = GlobalContext.get().get<android.content.Context>()

        @Suppress("DEPRECATION")
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)

        val versionName = packageInfo.versionName ?: "1.0.0"

        versionName
    } catch (e: Exception) {
        "Unknown (Android)"
    }
}