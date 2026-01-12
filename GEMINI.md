# TCGM - Trading Card Game Manager & Poké-Agent

## Project Overview
TCGM is a Kotlin Multiplatform (KMP) application designed for Pokémon card collectors to manage their inventory. It supports syncing data between local storage (SQLDelight) and a cloud database (Supabase).
**Current Focus:** Implementing "Poké-Agent", an AI-powered assistant that allows users to query their collection using natural language (e.g., "How much is my Charizard collection worth?").

## Tech Stack
*   **Core:** Kotlin Multiplatform (Android, Desktop currently active).
*   **UI:** Jetpack Compose Multiplatform (Material 3).
*   **Dependency Injection:** Koin.
*   **Networking:** Ktor Client.
*   **Database (Local):** SQLDelight (`CardDatabase`, `SettingsDatabase`).
*   **Database (Cloud):** Supabase (PostgreSQL) via `postgrest-kt`.
*   **Image Loading:** Coil 3.
*   **Resources:** Moko Resources.
*   **AI/LLM:** Google Gemini API (Gemma 3 models), BYOK (Bring Your Own Key).

## Key Directory Structure
*   `composeApp/src/commonMain/kotlin/...`: Shared business logic and UI.
    *   `di/`: Koin modules.
    *   `model/`: Data classes (`PokemonCard`, `SetInfo`).
    *   `ui/`: Compose UI screens and ViewModels.
    *   `platform/`: Platform-specific abstractions.
*   `composeApp/src/commonMain/sqldelight`: Local database schemas.
*   `composeApp/src/androidMain`: Android-specific implementations.
*   `composeApp/src/desktopMain`: Desktop (JVM) specific implementations.

## Data Model (Supabase)
The AI agent primarily interacts with the Supabase `inventory` (conceptually mapped to `PokemonCardEntity`).
**Table: `PokemonCardEntity`**
*   `nameEn`, `nameLocal`: Card names.
*   `setId`, `tcgDexCardId`: Identifiers.
*   `currentPrice`: Market value.
*   `ownedCopies`: Quantity owned.
*   `types`, `rarity`: Card attributes.

## AI Agent Implementation Guide (Poké-Agent)
**Goal:** Create a chat interface where users can ask questions about their cards.

### 1. Architecture
*   **Pattern:** Agentic Workflow (Tool-use).
*   **Flow:** User Query -> LLM Analysis -> Tool Call (if needed) -> Supabase Query -> Data returned to LLM -> Final Answer.
*   **Model Management:** Dynamically fetch available models (filter for `generateContent` support, exclude vision/audio only models). Default to `gemma-3-27b-it` or `gemini-2.0-flash`.

### 2. Tools (Function Calling)
The agent needs tools to query the `inventory` (Supabase).
*   `search_cards(query: String?, type: String?, sort: String?)`: detailed search.
*   `get_stats()`: aggregate data (total count, total value).

### 3. Implementation Steps
1.  **Network:** Configure Ktor with exponential backoff for API rate limits.
2.  **Settings:** UI for User to input Gemini API Key and select Model.
3.  **Agent Logic:** State machine handling the chat loop and tool execution.
4.  **UI:** Chat interface with typing indicators and "thought process" visibility.

## Development & Commands
*   **Run Desktop:** `./gradlew desktopRun`
*   **Run Android:** `./gradlew installDebug`
*   **Build:** `./gradlew build`

## Coding Conventions
*   **Style:** Kotlin official style guide.
*   **Architecture:** MVVM (Model-View-ViewModel).
*   **Async:** Coroutines & Flows.
*   **UI:** Compose foundation, Material 3.
*   **Strictness:** Use strict typing, handle nullable types safely.
