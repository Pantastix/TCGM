Projektkonzept: Poké-Agent (KMP + Supabase + Gemma 3 & Ollama)

1. Übersicht & Vision

Entwicklung eines intelligenten Inventar-Assistenten für eine Pokémon TCG Management App (Kotlin Multiplatform). Der User kann mittels natürlicher Sprache Fragen zu seiner Sammlung stellen (z.B. "Wie viel sind meine Gluraks wert?") oder allgemeines Wissen abfragen.

Kern-Features:

*   **KMP Architecture:** Code-Sharing zwischen Android, iOS & Desktop.
*   **Backend:** Supabase (PostgreSQL) für Datenhaltung.
*   **AI Engine (Hybrid):**
    *   **Cloud:** Google Gemini API (BYOK - Bring Your Own Key).
    *   **Local (Desktop):** Ollama (für Offline/Privacy-Focused Use Cases).
*   **Model Flexibility:** Dynamische Auswahl verfügbarer Modelle.
*   **Agentic Workflow:** Nutzung von "Function Calling" (Native API oder Text-Parsing) zur Datenbankabfrage.

2. Dynamisches Model-Management & Konfiguration

Da die Modelle je nach Provider variieren, benötigen wir eine flexible Struktur.

2.1 Abstraktion (Interfaces)

Wir führen ein `AiService` Interface ein, um die Implementierung (Cloud vs. Local) zu kapseln.

```kotlin
interface AiService {
    val providerType: AiProviderType
    suspend fun getAvailableModels(config: AiConfig): List<AiModel>
    suspend fun generateResponse(prompt: String, chatHistory: List<ChatMessage>, config: AiConfig, availableTools: List<AgentTool>): AiResponse
}
```

2.2 Provider 1: Gemini API (Cloud)

*   **Endpoint:** `GET /v1beta/models`
*   **Filter:** `generateContent` supported, exclude `embedding`, `imagen`, etc.
*   **Workflow:** Nutzt **Native Function Calling** der Gemini API.
*   **Modelle:** Gemini 1.5 Flash, Gemini 1.5 Pro, Gemini 2.0 Flash (sobald verfügbar).

2.3 Provider 2: Ollama (Local - Desktop Only)

*   **Endpoint:** `GET http://localhost:11434/api/tags`
*   **Modelle:**
    *   `gemma3` (via Ollama)
    *   `gpt-oss:20b`, `gpt-oss:120b` (und andere text-generierende Modelle).
*   **Workflow:** Nutzt **Workflow Strategies** (siehe Punkt 4).

3. Rate Limiting & Fehlerbehandlung

*   **Gemini API:** Exponential Backoff (429 handling).
*   **Ollama:** Timeout Handling (Local inference kann langsam sein), Connection Checks (läuft der Server?).

4. Der Agenten-Workflow (The Loop) & Strategies

Um verschiedene Modelle optimal zu unterstützen, führen wir das Konzept der **Workflow Strategies** ein. Ein Modell ist nicht starr, sondern nutzt eine zugewiesene Strategie, um Tools zu verwenden.

**Interface:** `AiWorkflowStrategy`

```kotlin
interface AiWorkflowStrategy {
    fun preparePrompt(systemPrompt: String, tools: List<AgentTool>): String
    fun parseResponse(content: String): AiResponse
}
```

4.1 Workflow A: Native Tool Support (Gemini Cloud)

Standard Gemini API Flow:
1.  `tools` Definition im API Request mitsenden (via Schema).
2.  Modell antwortet mit strukturiertem `functionCall`.
3.  Client führt Funktion aus.

4.2 Workflow B: Simulated Tool Support (Ollama / Gemma 3 / GPT-OSS)

Für Modelle ohne native Tool-API (oder wo wir sie erzwingen wollen, z.B. Gemma 3 on Ollama).

1.  **System Prompt Injection:** Wir hängen die Tool-Definitionen als Text an den System Prompt an.
    > "You have access to the following tools: ... To use a tool, output ONLY a JSON block wrapped in: ```json\n{ \"tool\": \"search_cards\", \"params\": {...} }\n```"
2.  **Parsing:** Der Client scannt die Antwort nach diesem JSON-Pattern.
3.  **Action:**
    *   *Gefunden:* Tool ausführen -> Ergebnis als "User Message" (formatiert als "Tool Result: ...") zurück in den Chatverlauf.
    *   *Nicht gefunden:* Text direkt anzeigen.

5. Tool Definitionen (Schnittstelle)

Interface für Tools im Code:

```kotlin
interface AgentTool {
    val name: String
    val description: String
    val parameterSchemaJson: String // Vereinfachtes Schema für Prompts
    suspend fun execute(params: Map<String, Any?>): String // Returns JSON or Text
}
```

*   **search_cards:** Query Supabase Inventory.
*   **get_stats:** Aggregierte Sammlungswerte.

6. UI/UX Konzept (Settings & Chat)

6.1 Settings Screen (Erweitert)

*   **AI Provider Switch:** Segmented Button [Google Gemini Cloud | Ollama Local]
*   **Gemini Mode:**
    *   API Key Input.
    *   Model Dropdown (Live-Fetch).
*   **Ollama Mode (Nur Desktop sichtbar):**
    *   Host URL Input (Default: `http://localhost:11434`).
    *   Model Dropdown (Live-Fetch von lokalem Server).
    *   **Workflow Selection (Optional/Advanced):** In Zukunft könnte man pro Modell die Strategie wählen, aktuell automatisch:
        *   Gemma 3 -> Simulated JSON Strategy
        *   GPT-OSS -> Simulated JSON Strategy

6.2 Chat Screen

*   Unterscheidung der "Thinking" States.
*   Visualisierung: Typing Indicators & Thought Process Expandables.

7. Roadmap zur Implementierung (Update)

1.  **Core AI Module:** Refactoring von `AiCore.kt` zur Einführung von `AiWorkflowStrategy`.
2.  **Strategies:** Implementierung von `SimulatedJsonStrategy` (aus `OllamaService` extrahieren) und `NativeGeminiStrategy` (Vorbereitung).
3.  **Ollama Service Refactoring:** `OllamaService` nutzt nun dynamische Strategien.
4.  **Settings Update:** Sicherstellen, dass neue Modelle (Gemma 3, GPT-OSS) korrekt geladen und angezeigt werden.