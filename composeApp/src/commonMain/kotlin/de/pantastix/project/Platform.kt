package de.pantastix.project

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform