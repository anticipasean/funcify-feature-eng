package funcify.feature.transformer.jq.yml

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.jsonSchema.JsonSchema

@JsonIgnoreProperties(ignoreUnknown = true)
data class JqTransformerDefinition(
    @JsonProperty("name") //
    val name: String,
    @JsonProperty("input_schema") //
    val inputSchema: JsonSchema,
    @JsonProperty("output_schema") //
    val outputSchema: JsonSchema,
    @JsonProperty("expression") //
    val expression: String
)
