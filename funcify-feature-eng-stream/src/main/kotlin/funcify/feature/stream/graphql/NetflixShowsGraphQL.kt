package funcify.feature.stream.graphql

import funcify.feature.scalar.registry.ScalarTypeRegistry
import funcify.feature.tools.container.attempt.Try
import graphql.ExceptionWhileDataFetching
import graphql.GraphQLError
import graphql.TypeResolutionEnvironment
import graphql.execution.DataFetcherResult
import graphql.language.ScalarTypeDefinition
import graphql.schema.DataFetcher
import graphql.schema.DataFetcherFactory
import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLScalarType
import graphql.schema.GraphQLSchema
import graphql.schema.TypeResolver
import graphql.schema.idl.FieldWiringEnvironment
import graphql.schema.idl.InterfaceWiringEnvironment
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.ScalarWiringEnvironment
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import graphql.schema.idl.WiringFactory
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * @author smccarron
 * @created 2023-06-26
 */
class NetflixShowsGraphQL {

    companion object {

        fun createGraphQLSchemaFromFile(schemaFile: File): GraphQLSchema {
            return GraphQLResource().invoke(schemaFile)
        }

        class GraphQLResource(val charset: Charset = Charsets.UTF_8) : (File) -> GraphQLSchema {

            override fun invoke(schemaFile: File): GraphQLSchema {
                return Try.attempt {
                        SchemaParser().parse(Files.newBufferedReader(schemaFile.toPath(), charset))
                    }
                    //.flatMap { tdr: TypeDefinitionRegistry ->
                    //    val errorHolder: Array<GraphQLError?> = arrayOfNulls<GraphQLError>(1)
                    //    tdr.addAll(
                    //            ScalarTypeRegistry.materializationRegistry()
                    //                .getAllScalarDefinitions()
                    //                .asSequence()
                    //                .filter { std: ScalarTypeDefinition ->
                    //                    std.name !in tdr.scalars()
                    //                }
                    //                .toList()
                    //        )
                    //        .ifPresent { ge: GraphQLError -> errorHolder[0] = ge }
                    //    if (errorHolder[0] != null) {
                    //        Try.failure(
                    //            RuntimeException("graphql_error: [ %s ]".format(errorHolder[0]))
                    //        )
                    //    } else {
                    //        Try.success(tdr)
                    //    }
                    //}
                    .map { tdr: TypeDefinitionRegistry ->
                        SchemaGenerator()
                            .makeExecutableSchema(
                                tdr,
                                RuntimeWiring.newRuntimeWiring()
                                    .wiringFactory(PipelineWiringFactory())
                                    .build()
                            )
                    }
                    .orElseThrow()
            }


        }

        class PipelineWiringFactory() : WiringFactory {

            companion object {
                private val logger: Logger =
                    LoggerFactory.getLogger(PipelineWiringFactory::class.java)
                private val showTypeResolver: TypeResolver =
                    TypeResolver { env: TypeResolutionEnvironment ->
                        if (env.selectionSet.contains("duration")) {
                            env.schema.getTypeAs("Movie")
                        } else {
                            env.schema.getTypeAs("TVShow")
                        }
                    }
            }

            override fun providesTypeResolver(environment: InterfaceWiringEnvironment): Boolean {
                return environment.interfaceTypeDefinition.name == "Show"
            }

            override fun getTypeResolver(environment: InterfaceWiringEnvironment?): TypeResolver {
                return showTypeResolver
            }

            override fun providesScalar(environment: ScalarWiringEnvironment): Boolean {
                logger.info(
                    "provides_scalar: [ env.scalar_type_definition.name: {} ]",
                    environment.scalarTypeDefinition.name
                )
                return ScalarTypeRegistry.materializationRegistry()
                    .getScalarTypeDefinitionWithName(environment.scalarTypeDefinition.name) != null
            }

            override fun getScalar(environment: ScalarWiringEnvironment): GraphQLScalarType {
                return ScalarTypeRegistry.materializationRegistry()
                    .getGraphQLScalarTypeWithName(environment.scalarTypeDefinition.name)!!
            }

            override fun providesDataFetcherFactory(environment: FieldWiringEnvironment): Boolean {
                return super.providesDataFetcherFactory(environment)
            }

            override fun <T : Any?> getDataFetcherFactory(environment: FieldWiringEnvironment): DataFetcherFactory<T> {
                return super.getDataFetcherFactory(environment)
            }

            override fun providesDataFetcher(environment: FieldWiringEnvironment): Boolean {
                logger.info(
                    "provides_data_fetcher: [ env.field_definition.name: {} ]",
                    environment.fieldDefinition.name
                )
                return true
            }

            override fun getDataFetcher(environment: FieldWiringEnvironment): DataFetcher<*> {
                return PipelineDataFetcher()
            }
        }

        class PipelineDataFetcher() : DataFetcher<CompletableFuture<DataFetcherResult<Any?>>> {

            companion object {
                private val logger: Logger =
                    LoggerFactory.getLogger(PipelineDataFetcher::class.java)
            }

            override fun get(
                environment: DataFetchingEnvironment
            ): CompletableFuture<DataFetcherResult<Any?>> {
                logger.info(
                    "get: [ env.field.name: {}, env.source: {} ]",
                    environment.field.name,
                    environment.getSource()
                )
                return CompletableFuture.completedFuture(
                    DataFetcherResult.newResult<Any?>()
                        .error(
                            ExceptionWhileDataFetching(
                                environment.executionStepInfo.path,
                                IllegalArgumentException("logic not supported"),
                                environment.field.sourceLocation
                            )
                        )
                        .build()
                )
            }
        }
    }
}
