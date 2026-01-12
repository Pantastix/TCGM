package de.pantastix.project.ai.tool

interface AgentTool {
    val name: String
    val description: String
    // We can define a simplified schema structure later, or use a library like Serialization
    // For now, simple string description is enough for the simulated prompt
    val parameterSchemaJson: String 
    
    suspend fun execute(parameters: Map<String, Any?>): String
}
