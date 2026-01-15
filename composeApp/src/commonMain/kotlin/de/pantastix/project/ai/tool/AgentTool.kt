package de.pantastix.project.ai.tool

import de.pantastix.project.model.gemini.Schema

interface AgentTool {
    val name: String
    val description: String
    // We can define a simplified schema structure later, or use a library like Serialization
    // For now, simple string description is enough for the simulated prompt
    val parameterSchemaJson: String
    
    val schema: Schema? get() = null // Default to null for backward compatibility
    
    suspend fun execute(parameters: Map<String, Any?>): String
}
