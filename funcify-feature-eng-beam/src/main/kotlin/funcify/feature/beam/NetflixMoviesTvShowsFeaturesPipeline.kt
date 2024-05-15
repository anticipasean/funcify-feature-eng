package funcify.feature.beam

import arrow.core.filterIsInstance
import arrow.core.getOrElse
import arrow.core.toOption
import funcify.feature.scalar.registry.ScalarTypeRegistry
import funcify.feature.tools.container.attempt.Try
import graphql.ExceptionWhileDataFetching
import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.GraphQLError
import graphql.Scalars
import graphql.TypeResolutionEnvironment
import graphql.execution.DataFetcherResult
import graphql.language.ScalarTypeDefinition
import graphql.scalars.ExtendedScalars
import graphql.schema.*
import graphql.schema.idl.FieldWiringEnvironment
import graphql.schema.idl.InterfaceWiringEnvironment
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.ScalarWiringEnvironment
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import graphql.schema.idl.WiringFactory
import java.io.File
import java.nio.channels.Channels
import java.nio.charset.Charset
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import java.util.stream.Stream
import kotlin.streams.asStream
import org.apache.beam.sdk.Pipeline
import org.apache.beam.sdk.coders.RowCoder
import org.apache.beam.sdk.io.FileIO
import org.apache.beam.sdk.io.FileIO.ReadableFile
import org.apache.beam.sdk.io.TextIO
import org.apache.beam.sdk.options.Default
import org.apache.beam.sdk.options.Description
import org.apache.beam.sdk.options.PipelineOptions
import org.apache.beam.sdk.options.PipelineOptionsFactory
import org.apache.beam.sdk.options.Validation
import org.apache.beam.sdk.schemas.Schema
import org.apache.beam.sdk.transforms.DoFn
import org.apache.beam.sdk.transforms.MapElements
import org.apache.beam.sdk.transforms.ParDo
import org.apache.beam.sdk.transforms.Sample
import org.apache.beam.sdk.transforms.SerializableFunction
import org.apache.beam.sdk.values.PDone
import org.apache.beam.sdk.values.Row
import org.apache.beam.sdk.values.TypeDescriptors
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object NetflixMoviesTvShowsFeaturesPipeline {

    class ConvertIntoCsvRecordsFn(
        // @Transient private val charset: Charset,
        // @Transient private val csvFormat: CSVFormat,
        private val schema: Schema,
    ) : DoFn<ReadableFile, Row>() {

        companion object {
            private val logger: Logger =
                LoggerFactory.getLogger(ConvertIntoCsvRecordsFn::class.java)
        }

        @ProcessElement
        fun processElement(@Element element: ReadableFile, receiver: OutputReceiver<Row>): Unit {
            logger.info(
                "process_element: [ readable_file.metadata.resource_id.file_name: {} ]",
                element.metadata.resourceId().filename
            )
            try {

                CSVParser.parse(
                        Channels.newReader(element.open(), Charsets.UTF_8),
                        CSVFormat.DEFAULT
                    )
                    .use { p: CSVParser ->
                        var header: Map<String, Int> = mapOf()
                        p.stream()
                            .flatMap { c: CSVRecord ->
                                if (c.recordNumber == 1L) {
                                    header =
                                        c.values().asSequence().withIndex().associateBy({
                                            (_: Int, key: String) ->
                                            key
                                        }) { (idx: Int, _: String) ->
                                            idx
                                        }
                                    Stream.empty<Row>()
                                } else {
                                    val rb: Row.Builder = Row.withSchema(schema)
                                    var rfb: Row.FieldValueBuilder? = null
                                    header.entries.asSequence().forEach { (k: String, idx: Int) ->
                                        if (rfb == null) {
                                            rfb = rb.withFieldValue(k, c[idx])
                                        } else {
                                            rfb = rfb?.withFieldValue(k, c[idx])
                                        }
                                    }
                                    Stream.of(rfb?.build() ?: rb.build())
                                }
                            }
                            .forEach { row: Row -> receiver.output(row) }
                    }
            } catch (t: Throwable) {
                logger.error(
                    "process_element: [ error: { type: {}, message: {} } ]",
                    t::class.java,
                    t.message
                )
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
                            ScalarTypeRegistry.materializationRegistry()
                                .getAllScalarDefinitions()
                                .asSequence()
                                .filter { std: ScalarTypeDefinition -> std.name !in tdr.scalars() }
                                .toList()
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

    object GraphQLToAvroRowSchemaConverter : (GraphQLSchema) -> Schema {

        private val logger: Logger =
            LoggerFactory.getLogger(GraphQLToAvroRowSchemaConverter::class.java)

        override fun invoke(graphQLSchema: GraphQLSchema): Schema {
            return graphQLSchema
                .getType("Show")
                .toOption()
                .filterIsInstance<GraphQLFieldsContainer>()
                .map { gfc: GraphQLFieldsContainer -> gfc.fields.asSequence() }
                .getOrElse { emptySequence() }
                .mapNotNull { fd: GraphQLFieldDefinition ->
                    logger.info("field_definition: [ name: {}, output_type: {} ]", fd.name, fd.type)
                    when (val gt: GraphQLOutputType = fd.type) {
                        is GraphQLScalarType -> {
                            when (gt.name) {
                                Scalars.GraphQLString.name -> {
                                    Schema.Field.nullable(fd.name, Schema.FieldType.STRING)
                                }
                                Scalars.GraphQLInt.name -> {
                                    Schema.Field.nullable(fd.name, Schema.FieldType.INT32)
                                }
                                Scalars.GraphQLID.name -> {
                                    Schema.Field.nullable(fd.name, Schema.FieldType.STRING)
                                }
                                else -> {
                                    null
                                }
                            }
                        }
                        is GraphQLNonNull -> {
                            when (val wt: GraphQLType = gt.wrappedType) {
                                is GraphQLScalarType -> {
                                    when (wt.name) {
                                        Scalars.GraphQLString.name -> {
                                            Schema.Field.of(fd.name, Schema.FieldType.STRING)
                                        }
                                        Scalars.GraphQLInt.name -> {
                                            Schema.Field.of(fd.name, Schema.FieldType.INT32)
                                        }
                                        Scalars.GraphQLID.name -> {
                                            Schema.Field.of(fd.name, Schema.FieldType.STRING)
                                        }
                                        ExtendedScalars.Date.name -> {
                                            Schema.Field.of(fd.name, Schema.FieldType.DATETIME)
                                        }
                                        else -> {
                                            null
                                        }
                                    }
                                }
                                is GraphQLList -> {
                                    when (val wt1: GraphQLType = wt.wrappedType) {
                                        is GraphQLNonNull -> {
                                            when (val wt2: GraphQLType = wt1.wrappedType) {
                                                is GraphQLScalarType -> {
                                                    Schema.Field.of(
                                                        fd.name,
                                                        Schema.FieldType.array(
                                                            Schema.FieldType.STRING
                                                        )
                                                    )
                                                }
                                                is GraphQLObjectType -> {
                                                    Schema.Field.of(
                                                        fd.name,
                                                        Schema.FieldType.array(
                                                            Schema.FieldType.row(
                                                                Schema.builder()
                                                                    .addStringField("name")
                                                                    .build()
                                                            )
                                                        )
                                                    )
                                                }
                                                is GraphQLTypeReference -> {
                                                    Schema.Field.of(
                                                        fd.name,
                                                        Schema.FieldType.array(
                                                            Schema.FieldType.row(
                                                                Schema.builder()
                                                                    .addStringField("name")
                                                                    .build()
                                                            )
                                                        )
                                                    )
                                                }
                                                else -> {
                                                    null
                                                }
                                            }
                                        }
                                        else -> {
                                            null
                                        }
                                    }
                                }
                                is GraphQLTypeReference -> {
                                    Schema.Field.of(
                                        fd.name,
                                        Schema.FieldType.row(
                                            Schema.builder().addStringField("name").build()
                                        )
                                    )
                                }
                                else -> {
                                    null
                                }
                            }
                        }
                        is GraphQLObjectType -> {
                            Schema.Field.of(
                                fd.name,
                                Schema.FieldType.array(
                                    Schema.FieldType.row(
                                        Schema.builder().addStringField("name").build()
                                    )
                                )
                            )
                        }
                        else -> {
                            null
                        }
                    }
                }
                .asStream()
                .collect(Schema.toSchema())
        }
    }

    data class CSVRecordContext(
        val csvRecord: Row,
        val schema: Schema,
        val rowBuilder: Row.Builder
    )

    class ConvertIntoRowFn(private val graphQL: GraphQL, private val avroOutputSchema: Schema) :
        DoFn<Row, Row>() {

        companion object {
            val QUERY: String =
                """
                |query showFeatures(${'$'}showId: ID!){
                |    show(showId: ${'$'}{showId}) {
                |        showId
                |        title
                |        releaseYear
                |        director {
                |            name
                |        }
                |        cast {
                |            name
                |        }
                |        audienceSuitabilityRating
                |        productionCountry
                |        genres
                |        dateAdded
                |    }
                |}
            """
                    .trimMargin()
            private val logger: Logger = LoggerFactory.getLogger(ConvertIntoRowFn::class.java)
        }

        @Throws(Exception::class)
        @ProcessElement
        fun processElement(context: ProcessContext): Unit {
            logger.info("process_element: [ row: {} ]", context.element())
            graphQL
                .execute(
                    ExecutionInput.newExecutionInput()
                        .localContext(
                            CSVRecordContext(
                                context.element(),
                                avroOutputSchema,
                                Row.withSchema(avroOutputSchema)
                            )
                        )
                        .query(QUERY)
                        .variables(mapOf("showId" to context.element().getString("show_id")))
                )
                .let { er: ExecutionResult ->
                    if (er.isDataPresent) {
                        context.output(er.getData<Row.Builder>().build())
                    } else {
                        context.output(
                            Row.withSchema(
                                    Schema.builder()
                                        .addNullableStringField("errorType")
                                        .addNullableStringField("message")
                                        .build()
                                )
                                .withFieldValue(
                                    "errorType",
                                    er.errors.asSequence().firstOrNull()?.errorType ?: "<NA>"
                                )
                                .withFieldValue(
                                    "message",
                                    er.errors.asSequence().firstOrNull()?.message ?: "<NA>"
                                )
                                .build()
                        )
                    }
                }
        }
    }

    public interface NetflixShowsFeaturesPipelineOptions : PipelineOptions {

        @get:Description("Path of the file to read from")
        @get:Default.String(
            "funcify-feature-eng-beam/src/main/resources/netflix_movies_and_tv_shows_202306091725/netflix_titles.csv"
        )
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
        @get:Default.String(
            "funcify-feature-eng-beam/src/main/resources/netflix_movies_and_tv_shows.graphqls"
        )
        var graphQLSchemaFile: String

        @get:Description("Path of the file to write to")
        @get:Validation.Required //
        var output: String
    }

    private val logger: Logger =
        LoggerFactory.getLogger(NetflixMoviesTvShowsFeaturesPipeline::class.java)

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
        val csvRowSchema: Schema =
            sequenceOf(
                    "show_id",
                    "type",
                    "title",
                    "director",
                    "cast",
                    "country",
                    "date_added",
                    "release_year",
                    "rating",
                    "duration",
                    "listed_in",
                    "description"
                )
                .fold(Schema.builder()) { sb: Schema.Builder, f: String ->
                    sb.addNullableStringField(f)
                }
                .build()

        val graphQLSchema: GraphQLSchema = GraphQLResource().invoke(File(options.graphQLSchemaFile))
        val graphQL: GraphQL = GraphQL.newGraphQL(graphQLSchema).build()
        val avroOutputSchema: Schema = GraphQLToAvroRowSchemaConverter.invoke(graphQLSchema)
        val p: Pipeline = Pipeline.create(options)

        logger.info("avro_output_schema: {}", avroOutputSchema)

        p.apply("MatchFilePattern", FileIO.match().filepattern(options.inputFile))
            .apply("ReadMatchedFile", FileIO.readMatches())
            .apply(
                "ConvertIntoCsvRecords",
                ParDo.of(ConvertIntoCsvRecordsFn(schema = csvRowSchema))
            )
            .setCoder(RowCoder.of(csvRowSchema))
            .apply(Sample.any(1))
            .apply("ConvertIntoRow", ParDo.of(ConvertIntoRowFn(graphQL, avroOutputSchema)))
            .setCoder(RowCoder.of(avroOutputSchema))
            .apply(
                MapElements.into(TypeDescriptors.strings())
                    .via(SerializableFunction { r: Row -> r.toString(true) })
            )
            .apply<PDone>(
                "WriteRow",
                TextIO.write().withoutSharding().withSuffix(".json").to(options.output)
            )

        p.run().waitUntilFinish()
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val options: NetflixShowsFeaturesPipelineOptions =
            PipelineOptionsFactory.fromArgs(*args)
                .withValidation()
                .`as`(NetflixShowsFeaturesPipelineOptions::class.java)
        runCSV(options)
    }
}
