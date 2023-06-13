package funcify.feature.beam

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import funcify.feature.scalar.registry.ScalarTypeRegistry
import funcify.feature.tools.container.attempt.Try
import graphql.ExceptionWhileDataFetching
import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.GraphQLError
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLScalarType
import graphql.schema.GraphQLSchema
import graphql.schema.idl.FieldWiringEnvironment
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.ScalarWiringEnvironment
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import graphql.schema.idl.WiringFactory
import java.io.File
import java.io.Reader
import java.nio.channels.Channels
import java.nio.charset.Charset
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import org.apache.beam.sdk.Pipeline
import org.apache.beam.sdk.io.FileIO
import org.apache.beam.sdk.io.FileIO.ReadableFile
import org.apache.beam.sdk.io.TextIO
import org.apache.beam.sdk.options.Default
import org.apache.beam.sdk.options.Description
import org.apache.beam.sdk.options.PipelineOptions
import org.apache.beam.sdk.options.PipelineOptionsFactory
import org.apache.beam.sdk.options.Validation
import org.apache.beam.sdk.transforms.Contextful
import org.apache.beam.sdk.transforms.FlatMapElements
import org.apache.beam.sdk.transforms.InferableFunction
import org.apache.beam.sdk.transforms.MapElements
import org.apache.beam.sdk.transforms.SerializableFunction
import org.apache.beam.sdk.values.PDone
import org.apache.beam.sdk.values.TypeDescriptor
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object NetflixMoviesTvShowsFeaturesPipeline {

    class ConvertIntoCsvRecordsFn(
        private val charset: Charset = Charsets.UTF_8,
        private val csvFormat: CSVFormat = CSVFormat.DEFAULT,
    ) : InferableFunction<ReadableFile, Iterable<CSVRecord>>() {

        @Throws(Exception::class)
        override fun apply(input: ReadableFile): Iterable<CSVRecord> {
            return Channels.newReader(input.open(), charset).use { r: Reader ->
                CSVParser.parse(r, csvFormat)
            }
        }
    }

    class GraphQLResource(val charset: Charset = Charsets.UTF_8) : (File) -> GraphQLSchema {

        override fun invoke(schemaFile: File): GraphQLSchema {
            return Try.attempt {
                    SchemaParser().parse(Files.newBufferedReader(schemaFile.toPath(), charset))
                }
                .flatMap { tdr: TypeDefinitionRegistry ->
                    val errorHolder: Array<GraphQLError?> = arrayOfNulls<GraphQLError>(1)
                    tdr.addAll(
                            ScalarTypeRegistry.materializationRegistry().getAllScalarDefinitions()
                        )
                        .ifPresent { ge: GraphQLError -> errorHolder[0] = ge }
                    if (errorHolder[0] != null) {
                        Try.failure(
                            RuntimeException("graphql_error: [ %s ]".format(errorHolder[0]))
                        )
                    } else {
                        Try.success(tdr)
                    }
                }
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
            private val logger: Logger = LoggerFactory.getLogger(PipelineWiringFactory::class.java)
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
            private val logger: Logger = LoggerFactory.getLogger(PipelineDataFetcher::class.java)
        }

        override fun get(
            environment: DataFetchingEnvironment
        ): CompletableFuture<DataFetcherResult<Any?>> {
            logger.info(
                "get: [ env.field.name: {}, local_context: {} ]",
                environment.field.name,
                environment.getLocalContext()
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

    class ConvertIntoJSONFn(
        private val graphQL: GraphQL,
        private val objectMapper: ObjectMapper = ObjectMapper()
    ) : InferableFunction<CSVRecord, JsonNode>() {

        companion object {
            val QUERY: String =
                """
                |query showFeatures(${'$'}showId: ID!){
                |    show(showId: ${'$'}{showId}) {
                |        showId
                |        title
                |        releaseYear
                |        director
                |        cast
                |        audienceSuitabilityRating
                |        productionCountry
                |        genres
                |        dateAdded
                |    }
                |}
            """
                    .trimMargin()
        }

        @Throws(Exception::class)
        override fun apply(input: CSVRecord): JsonNode {
            return graphQL
                .executeAsync(
                    ExecutionInput.newExecutionInput()
                        .localContext(input)
                        .query(QUERY)
                        .variables(mapOf("showId" to input.get("showId")))
                )
                .join()
                .let { er: ExecutionResult -> objectMapper.valueToTree(er.toSpecification()) }
        }
    }

    interface NetflixShowsFeaturesPipelineOptions : PipelineOptions {

        @get:Description("Path of the file to read from")
        @get:Default.String("netflix_movies_and_tv_shows_202306091725/netflix_titles.csv")
        var inputFile: String

        @get:Description(
            "Character encoding in csv file to read from as defined in java.nio.charset.Charset.availableCharsets"
        )
        @get:Default.String("UTF-8")
        var charSet: String

        @get:Description("Format as defined in org.apache.commons.csv.CSVFormat.Predefined")
        @get:Default.String("DEFAULT")
        var csvFormat: String

        @get:Description("Path of the graphqls schema file")
        @get:Default.String("netflix_movies_and_tv_shows.graphqls")
        var graphQLSchemaFile: String

        @get:Description("Path of the file to write to")
        @get:Validation.Required //
        var output: String
    }

    @JvmStatic
    fun runCSV(options: NetflixShowsFeaturesPipelineOptions) {
        val charset: Charset =
            try {
                Charset.forName(options.charSet)
            } catch (t: Throwable) {
                Charsets.UTF_8
            }
        val csvFormat: CSVFormat =
            CSVFormat.Predefined.values()
                .asSequence()
                .firstOrNull { pd: CSVFormat.Predefined ->
                    pd.name.equals(options.csvFormat, ignoreCase = true)
                }
                ?.format
                ?: CSVFormat.DEFAULT

        val graphQLSchema: GraphQLSchema = GraphQLResource().invoke(File(options.graphQLSchemaFile))
        val p: Pipeline = Pipeline.create(options)

        p.apply("MatchFilePattern", FileIO.match().filepattern(options.inputFile))
            .apply("ReadMatchedFile", FileIO.readMatches())
            .apply(
                "ConvertIntoCsvRecords",
                FlatMapElements.into(TypeDescriptor.of(CSVRecord::class.java))
                    .via(Contextful.fn(ConvertIntoCsvRecordsFn(charset, csvFormat)))
            )
            .apply(
                "ConvertIntoJSON",
                MapElements.into(TypeDescriptor.of(JsonNode::class.java))
                    .via(ConvertIntoJSONFn(GraphQL.newGraphQL(graphQLSchema).build()))
            )
            .apply(
                MapElements.into(TypeDescriptor.of(String::class.java))
                    .via(SerializableFunction { jn: JsonNode -> jn.toString() })
            )
            .apply<PDone>("WriteJSON", TextIO.write().to(options.output))

        p.run().waitUntilFinish()
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val options: NetflixShowsFeaturesPipelineOptions =
            (PipelineOptionsFactory.fromArgs(*args).withValidation()
                as NetflixShowsFeaturesPipelineOptions)
        runCSV(options)
    }
}
