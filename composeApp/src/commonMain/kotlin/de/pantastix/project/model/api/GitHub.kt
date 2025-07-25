package de.pantastix.project.model.api

import de.pantastix.project.platform.Platform
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    val assets: List<GitHubAsset>
)

@Serializable
data class GitHubAsset(
    @SerialName("browser_download_url") val downloadUrl: String,
    val name: String
)

data class UpdateInfo(
    val version: String,
    val downloadUrl: String,
    val platform: Platform
)