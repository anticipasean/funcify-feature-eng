package funcify.feature.transformer.jq

import funcify.feature.json.JsonObjectMappingConfiguration
import funcify.feature.tools.json.JsonMapper
import funcify.feature.transformer.jq.factory.DefaultJqTransformerFactory
import funcify.feature.transformer.jq.factory.DefaultJqTransformerSourceProviderFactory
import funcify.feature.transformer.jq.factory.JqTransformerFactory
import funcify.feature.transformer.jq.factory.JqTransformerSourceProviderFactory
import funcify.feature.transformer.jq.metadata.DefaultJqTransformerTypeDefinitionFactory
import funcify.feature.transformer.jq.metadata.JqTransformerTypeDefinitionFactory
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource

/**
 * @author smccarron
 * @created 2023-07-06
 */
class JqTransformerYamlTest {

    @Test
    fun createJqTransformerSourceTest() {
        val jsonMapper: JsonMapper = JsonObjectMappingConfiguration.jsonMapper()
        val jqTransformerFactory: JqTransformerFactory =
            DefaultJqTransformerFactory()
        val jqTransformerTypeDefinitionFactory: JqTransformerTypeDefinitionFactory =
            DefaultJqTransformerTypeDefinitionFactory
        val jqTransformerSourceProviderFactory: JqTransformerSourceProviderFactory =
            DefaultJqTransformerSourceProviderFactory(
                jsonMapper = jsonMapper,
                jqTransformerFactory = jqTransformerFactory,
                jqTransformerTypeDefinitionFactory = jqTransformerTypeDefinitionFactory
                                                     )
        val yamlClassPathResource: ClassPathResource = ClassPathResource("jq-transformers.yml")
        val jqTransformerSourceProvider: JqTransformerSourceProvider =
            Assertions.assertDoesNotThrow<JqTransformerSourceProvider> {
                jqTransformerSourceProviderFactory
                    .builder()
                    .name("jq")
                    .transformerYamlFile(yamlClassPathResource)
                    .build()
            }
    }
}
