# AI Agent Tool Concepts (Poké-Agent)

This document outlines the roadmap for AI tool integration. The focus is on high-utility tools that provide clear value to the user while keeping the LLM context efficient.

## 1. Planned Implementations (Priority)

These tools are approved for the next development phase.

### A. Set Search Tool (`search_sets`)
*   **Purpose:** Allows the AI to resolve vague set names (e.g., "151", "Obsidian") into concrete Set IDs used by the database. Crucial for linking user intent to database queries.
*   **Parameters:**
    *   `query` (String): Search term matching set name (English/Local), abbreviation, or ID.
*   **Returns:** A concise list of matching sets.
    *   `[{ "id": "sv3pt5", "name": "151", "abbreviation": "MEW", "card_count": 165 }]`
*   **Why:** Enables the AI to perform subsequent queries like "Show me cards from 151" by first finding the ID `sv3pt5`.

### B. Enhanced Card Search (`search_cards` v2)
*   **Purpose:** Refine the existing search to support Set-based filtering.
*   **New Optional Parameters:**
    *   `set_id` (String): The exact API ID of the set (e.g., "sv1", "swsh12").
    *   `set_name` (String): Fallback if ID is unknown (less precise, prone to language issues).
*   **Existing Parameters:** `query` (name), `type`, `sort`.
*   **Logic:** If `set_id` is provided, the query is strictly scoped to that set.

### C. Enhanced Stats (`get_inventory_stats` v2)
*   **Purpose:** Get statistics for a specific subset of the collection or the global total.
*   **New Optional Parameters:**
    *   `set_id` (String): Calculates value and counts ONLY for the specified set.
*   **New Metrics:**
    *   `unique_cards_count`: Number of distinct card entries (ignoring duplicates).
    *   `total_cards_count`: Total number of physical cards (sum of owned copies).
*   **Anti-Hallucination/Misuse Rule:** The tool description must explicitly state: "DO NOT use this tool to find information about a single specific card. Use `search_cards` for that."
*   **Use Case:** "How much is my Paldea Evolved collection worth?" vs "How much is my whole collection worth?"

### D. Update Quantity (`update_card_quantity`)
*   **Purpose:** Hands-free inventory management.
*   **Parameters:**
    *   `card_id` (String): The unique internal ID of the card.
    *   `change` (Int): The amount to add or subtract (e.g., `+1`, `-2`).
*   **Safety:** Should return the new total for confirmation. The Agent logic should likely ask for confirmation before executing if the change is large.

---

## 2. Future Integrations (Menu-Based)

### E. Set Progress (`get_set_progress`)
*   **Status:** **DEFERRED**. Will be part of a dedicated visual menu/dashboard later.
*   **Concept:**
    *   Input: `set_id`.
    *   Output: Completion status (e.g., "150/165 cards owned", "91% complete").
    *   Advanced: Could list specific missing "Chase Cards" (high rarity) to give context without dumping the whole missing list.

---

## 3. Additional Ideas (Brainstorming)

Here are further safe, high-value tool ideas:

### F. Find Duplicates (`find_duplicates`)
*   **Purpose:** Helping users identify cards to trade or sell.
*   **Parameters:**
    *   `min_copies` (Int, default 4): Show cards where `owned > min_copies`.
    *   `rarity` (String, optional): E.g., only show duplicate "Rare" cards.
*   **Value:** "Which cards do I have more than 4 times?" is a very common collector question.
*   **Safety:** Read-only.

### G. Live Price Check (`fetch_market_price`)
*   **Purpose:** The local database might have old prices. This tool forces a fresh API lookup for a specific card.
*   **Parameters:**
    *   `card_id` (String).
*   **Value:** "Is my Moonbreon worth more today?" -> Triggers a TcgDex/Cardmarket API call and updates the local record.
*   **Safety:** Updates only one record's price field. Safe.

### H. Missing Cards by Rarity (`get_missing_cards`)
*   **Purpose:** Help collectors fill gaps without listing *every* common card.
*   **Parameters:**
    *   `set_id` (String).
    *   `rarity` (String): e.g., "Illustration Rare".
*   **Value:** "Which Full Arts am I missing from Silver Tempest?"
*   **Context Efficiency:** Returns a filtered list, not the whole set.

---

## 4. Response Enrichment (UX)

### I. Image Embedding (Markdown)
*   **Concept:** Instead of a dedicated tool call, the LLM is instructed to embed card images directly into its text response using Markdown.
*   **Mechanism:** `![Card Name](image_url)`
*   **Prerequisite:** The `search_cards` tool MUST return the `imageUrl` field in its JSON output.
*   **System Prompt Instruction:** "If you discuss a specific card and have its image URL, embed it using Markdown syntax at a relevant place in your answer."
*   **Benefit:** Visually rich chat without the overhead/latency of extra tool round-trips.

