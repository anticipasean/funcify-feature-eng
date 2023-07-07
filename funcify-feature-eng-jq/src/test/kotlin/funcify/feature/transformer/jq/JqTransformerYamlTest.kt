package funcify.feature.transformer.jq

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import funcify.feature.error.ServiceError
import funcify.feature.json.JsonObjectMappingConfiguration
import funcify.feature.tools.container.attempt.Failure
import funcify.feature.tools.container.attempt.Success
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.MonoExtensions.toTry
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.json.JsonMapper
import funcify.feature.transformer.jq.factory.DefaultJqTransformerFactory
import funcify.feature.transformer.jq.factory.DefaultJqTransformerSourceProviderFactory
import funcify.feature.transformer.jq.metadata.DefaultJqTransformerTypeDefinitionFactory
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource

/**
 * @author smccarron
 * @created 2023-07-06
 */
class JqTransformerYamlTest {

    // @BeforeEach
    // internal fun setUp() {
    //    (LoggerFactory.getILoggerFactory() as? LoggerContext)?.let { lc: LoggerContext ->
    //        lc.getLogger(Logger.ROOT_LOGGER_NAME)?.let { l: ch.qos.logback.classic.Logger ->
    //            l.level = Level.DEBUG
    //        }
    //    }
    // }

    @Test
    fun createJqTransformerSourceTest() {
        val jsonMapper: JsonMapper = JsonObjectMappingConfiguration.jsonMapper()
        val jqTransformerFactory: JqTransformerFactory = DefaultJqTransformerFactory()
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
        val jqTransformerSource: JqTransformerSource =
            Assertions.assertDoesNotThrow<JqTransformerSource> {
                jqTransformerSourceProvider.getLatestTransformerSource().toFuture().join()
            }
        Assertions.assertEquals(1, jqTransformerSource.transformersByName.size) {
            "size unexpected"
        }
        // println(
        //    sequenceOf(
        //            jqTransformerSource.sourceTypeDefinitionRegistry.getTypes(
        //                TypeDefinition::class.java
        //            ),
        //            jqTransformerSource.sourceTypeDefinitionRegistry
        //                .objectTypeExtensions()
        //                .values
        //                .asSequence()
        //                .flatMap { l -> l }
        //                .asIterable()
        //        )
        //        .flatMap { i: Iterable<Node<*>> -> i }
        //        .asSequence()
        //        .joinToString("\n") { td -> AstPrinter.printAst(td) }
        // )
        Assertions.assertEquals(
            "negative_to_null",
            jqTransformerSource.transformersByName.asSequence().first().key
        ) {
            "transformersByName[0].key does not match"
        }
        val jqTransformer: JqTransformer =
            jqTransformerSource.transformersByName["negative_to_null"]!!
        val serviceError: ServiceError =
            Assertions.assertThrows(
                ServiceError::class.java,
                {
                    jqTransformer
                        .transform(JsonNodeFactory.instance.objectNode().put("input", -1))
                        .toTry()
                        .let { t: Try<JsonNode> ->
                            when (t) {
                                is Success<JsonNode> -> {
                                    t.result
                                }
                                is Failure<JsonNode> -> {
                                    throw t.throwable
                                }
                            }
                        }
                },
                {
                    """service_error expected since transformer input definition 
                        |expects number, not an object type as input"""
                        .flatten()
                }
            )
        // println(serviceError.message)
        Assertions.assertTrue(serviceError.message.contains(Regex("invalid"))) {
            "service_error did not match expected regular expression: [ actual: service_error.message: \"%s\" ]".format(
                serviceError.message
            )
        }
        val result: JsonNode =
            Assertions.assertDoesNotThrow<JsonNode> {
                jqTransformer.transform(JsonNodeFactory.instance.numberNode(-1)).toFuture().join()
            }
        Assertions.assertTrue(result.isNull)
    }
}
