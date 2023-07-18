package funcify.feature.schema.directive.alias

import arrow.core.Option
import arrow.core.filterIsInstance
import arrow.core.getOrElse
import arrow.core.toOption
import funcify.feature.directive.AliasDirective
import funcify.feature.directive.MaterializationDirective
import funcify.feature.error.ServiceError
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.OptionExtensions.toOption
import graphql.GraphQLError
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 * @author smccarron
 * @created 2023-07-17
 */
class AliasRegistryTest {

    companion object {
        /**
         * Example schema obtained from
         * [DGS examples repo](https://github.com/Netflix/dgs-examples-kotlin/blob/56e7371ffad312a9d59f1318d04ab5426515e842/src/main/resources/schema/schema.graphqls)
         */
        private val exampleDGSSchema: String =
            """
                    |type Query {
                    |    shows(titleFilter: String): [Show]
                    |}
                    |
                    |type Show {
                    |    id: Int! @alias(name: "showId")
                    |    title(format: TitleFormat): String!
                    |    releaseYear: Int
                    |    reviews(
                    |      minStarScore: Int = 0 @alias(name: "minimumStarScore")
                    |    ): [Review]
                    |    artwork: [Image]
                    |}
                    |
                    |input TitleFormat {
                    |    uppercase: Boolean @alias(name: "upper")
                    |}
                    |
                    |type Review {
                    |    username: String @alias(name: "reviewerName")
                    |    starScore: Int
                    |    submittedDate: DateTime
                    |}
                    |
                    |type Image {
                    |    url: String
                    |}
                    |
                    |scalar DateTime
                    """
                .trimMargin()
    }

    @Test
    fun createAliasRegistryTest() {
        val tdr: TypeDefinitionRegistry =
            Assertions.assertDoesNotThrow<TypeDefinitionRegistry> {
                SchemaParser().parse(exampleDGSSchema)
            }
        Assertions.assertDoesNotThrow<TypeDefinitionRegistry> {
            sequenceOf(AliasDirective)
                .fold(Try.success(tdr)) {
                    t: Try<TypeDefinitionRegistry>,
                    d: MaterializationDirective ->
                    t.flatMap { reg: TypeDefinitionRegistry ->
                        reg.add(d.directiveDefinition)
                            .toOption()
                            .fold(
                                { Try.success(reg) },
                                { e: GraphQLError ->
                                    Try.failure<TypeDefinitionRegistry>(
                                        e.toOption().filterIsInstance<Throwable>().getOrElse {
                                            ServiceError.of(
                                                "graphql_error: %s",
                                                e.toSpecification()
                                            )
                                        }
                                    )
                                }
                            )
                    }
                }
                .orElseThrow()
        }
        val aliasRegistryOpt: Option<AttributeAliasRegistry> =
            AliasRegistryComposer().createAliasRegistry(tdr)
        Assertions.assertTrue(aliasRegistryOpt.isDefined())
        println("registry: %s".format(aliasRegistryOpt.orNull()))
        """
            {
              "show_id": "mlfs:/shows/id",
              "id": "mlfs:/shows/id",
              "reviewer_name": "mlfs:/shows/reviews/username",
              "username": "mlfs:/shows/reviews/username",
              "minimum_star_score": [
                "mlfs:/shows/reviews?minStarScore"
              ],
              "min_star_score": [
                "mlfs:/shows/reviews?minStarScore"
              ],
              "upper": [
                "mlfs:/shows/title?format=/uppercase"
              ],
              "uppercase": [
                "mlfs:/shows/title?format=/uppercase"
              ]
            }
        """
            .trimIndent()
        val aliasRegistry: AttributeAliasRegistry = aliasRegistryOpt.orNull()!!
        Assertions.assertTrue(aliasRegistry.containsSimilarSourceAttributeNameOrAlias("username"))
        Assertions.assertTrue(
            aliasRegistry.containsSimilarParameterAttributeNameOrAlias("minimumStarScore")
        )
        Assertions.assertTrue(aliasRegistry.containsSimilarParameterAttributeNameOrAlias("upper"))
        Assertions.assertEquals(
            listOf(SchematicPath.parse("mlfs:/shows/reviews?minStarScore")),
            aliasRegistry.getParameterVertexPathsWithSimilarNameOrAlias("minStarScore").toList()
        )
    }
}
