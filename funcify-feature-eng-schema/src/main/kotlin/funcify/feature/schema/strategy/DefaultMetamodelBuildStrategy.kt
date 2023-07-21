package funcify.feature.schema.strategy

import arrow.core.*
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.directive.AliasDirective
import funcify.feature.directive.LastUpdatedDirective
import funcify.feature.directive.MaterializationDirective
import funcify.feature.error.ServiceError
import funcify.feature.naming.StandardNamingConventions
import funcify.feature.scalar.registry.ScalarTypeRegistry
import funcify.feature.schema.Metamodel
import funcify.feature.schema.MetamodelBuildStrategy
import funcify.feature.schema.Source
import funcify.feature.schema.context.MetamodelBuildContext
import funcify.feature.schema.dataelement.DataElementSource
import funcify.feature.schema.dataelement.DataElementSourceProvider
import funcify.feature.schema.directive.alias.AliasRegistryComposer
import funcify.feature.schema.directive.alias.AttributeAliasRegistry
import funcify.feature.schema.directive.temporal.LastUpdatedTemporalAttributeRegistry
import funcify.feature.schema.directive.temporal.LastUpdatedTemporalAttributeRegistryComposer
import funcify.feature.schema.feature.FeatureCalculator
import funcify.feature.schema.feature.FeatureCalculatorProvider
import funcify.feature.schema.metamodel.DefaultMetamodel
import funcify.feature.schema.transformer.TransformerSource
import funcify.feature.schema.transformer.TransformerSourceProvider
import funcify.feature.tools.container.attempt.Failure
import funcify.feature.tools.container.attempt.Success
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.MonoExtensions.widen
import funcify.feature.tools.extensions.OptionExtensions.toOption
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.TryExtensions.failure
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
import graphql.GraphQLError
import graphql.language.*
import graphql.schema.idl.TypeDefinitionRegistry
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.isSubclassOf
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentMap
import org.slf4j.Logger
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

internal class DefaultMetamodelBuildStrategy(private val scalarTypeRegistry: ScalarTypeRegistry) :
    MetamodelBuildStrategy {

    companion object {
        private const val MAIN_METHOD_TAG = "build_metamodel"
        private const val QUERY_OBJECT_TYPE_NAME = "Query"
        private const val MUTATION_OBJECT_TYPE_NAME = "Mutation"
        private const val TRANSFORMER_FIELD_NAME = "transformer"
        private const val TRANSFORMER_OBJECT_TYPE_NAME = "Transformer"
        private const val DATA_ELEMENT_FIELD_NAME = "dataElement"
        private const val DATA_ELEMENT_OBJECT_TYPE_NAME = "DataElement"
        private const val FEATURE_FIELD_NAME = "feature"
        private const val FEATURE_OBJECT_TYPE_NAME = "Feature"
        private val logger: Logger = loggerFor<DefaultMetamodelBuildStrategy>()
    }

    override fun buildMetamodel(context: MetamodelBuildContext): Mono<out Metamodel> {
        logger.info(
            """{}: [ context {
            |transformerSourceProviders.size: {}, 
            |dataElementSourceProviders.size: {}, 
            |featureCalculatorProviders.size: {} 
            |} ]"""
                .flatten(),
            MAIN_METHOD_TAG,
            context.transformerSourceProviders.size,
            context.dataElementSourceProviders.size,
            context.featureCalculatorProviders.size
        )
        return Mono.just(context)
            .flatMap(validateSourceProviders())
            .flatMap(extractSourcesFromSourceProviders())
            .flatMap(createTypeDefinitionRegistryFromSources())
            .flatMap(createAliasRegistryFromTypeDefinitionRegistry())
            .flatMap(createLastUpdatedTemporalAttributeRegistryFromTypeDefinitionRegistry())
            .map { ctx: MetamodelBuildContext ->
                DefaultMetamodel(
                    transformerSourceProvidersByName =
                        ctx.transformerSourceProvidersByName.toPersistentMap(),
                    dataElementSourceProvidersByName =
                        ctx.dataElementSourceProvidersByName.toPersistentMap(),
                    featureCalculatorProvidersByName =
                        ctx.featureCalculatorProvidersByName.toPersistentMap(),
                    featureJsonValueStoresByName =
                        ctx.featureJsonValueStoresByName.toPersistentMap(),
                    featureJsonValuePublishersByName =
                        ctx.featureJsonValuePublishersByName.toPersistentMap(),
                    transformerSourcesByName = ctx.transformerSourcesByName.toPersistentMap(),
                    dataElementSourcesByName = ctx.dataElementSourcesByName.toPersistentMap(),
                    featureCalculatorsByName = ctx.featureCalculatorsByName.toPersistentMap(),
                    typeDefinitionRegistry = ctx.typeDefinitionRegistry,
                    attributeAliasRegistry = ctx.attributeAliasRegistry,
                    entityRegistry = ctx.entityRegistry,
                    lastUpdatedTemporalAttributeRegistry = ctx.lastUpdatedTemporalAttributeRegistry
                )
            }
            .doOnSuccess(logSuccessfulStatus())
            .doOnError(logFailedStatus())
            .cache()
    }

    private fun validateSourceProviders(): (MetamodelBuildContext) -> Mono<MetamodelBuildContext> {
        return { context: MetamodelBuildContext ->
            when {
                context.transformerSourceProviders.size !=
                    context.transformerSourceProvidersByName.size -> {
                    Mono.error<MetamodelBuildContext> {
                        ServiceError.of(
                            "non-unique transformer_source_provider.name: [ duplicates for [ %s ] ]",
                            context.transformerSourceProviders
                                .asSequence()
                                .map { tsp: TransformerSourceProvider<*> -> tsp.name }
                                .groupBy { k -> k }
                                .asSequence()
                                .filter { (_, v) -> v.size > 1 }
                                .map { (k, _) -> k }
                                .joinToString(", ")
                        )
                    }
                }
                context.dataElementSourceProviders.size !=
                    context.dataElementSourceProvidersByName.size -> {
                    Mono.error<MetamodelBuildContext> {
                        ServiceError.of(
                            "non-unique data_element_source_providers.name: [ duplicates for [ %s ] ]",
                            context.dataElementSourceProviders
                                .asSequence()
                                .map { desp: DataElementSourceProvider<*> -> desp.name }
                                .groupBy { k -> k }
                                .asSequence()
                                .filter { (_, v) -> v.size > 1 }
                                .map { (k, _) -> k }
                                .joinToString(", ")
                        )
                    }
                }
                context.featureCalculatorProviders.size !=
                    context.featureCalculatorProvidersByName.size -> {
                    Mono.error<MetamodelBuildContext> {
                        ServiceError.of(
                            "non-unique feature_calculator_provider.name: [ duplicates for [ %s ] ]",
                            context.featureCalculatorProviders
                                .asSequence()
                                .map { fcp: FeatureCalculatorProvider<*> -> fcp.name }
                                .groupBy { k -> k }
                                .asSequence()
                                .filter { (_, v) -> v.size > 1 }
                                .map { (k, _) -> k }
                                .joinToString(", ")
                        )
                    }
                }
                sequenceOf(
                        context.transformerSourceProvidersByName.keys,
                        context.dataElementSourceProvidersByName.keys,
                        context.featureCalculatorProvidersByName.keys
                    )
                    .flatMap(ImmutableSet<String>::asSequence)
                    .groupBy { k -> k }
                    .any { (_: String, v: List<String>) -> v.size > 1 } -> {
                    Mono.error<MetamodelBuildContext> {
                        ServiceError.of(
                            "non-unique source_provider.name: [ duplicates for [ %s ] ]",
                            sequenceOf(
                                    context.transformerSourceProvidersByName.keys,
                                    context.dataElementSourceProvidersByName.keys,
                                    context.featureCalculatorProvidersByName.keys
                                )
                                .flatMap(ImmutableSet<String>::asSequence)
                                .groupBy { k -> k }
                                .filter { (_: String, v: List<String>) -> v.size > 1 }
                                .map { (k: String, _: List<String>) -> k }
                                .joinToString(", ")
                        )
                    }
                }
                else -> {
                    Mono.just(context)
                }
            }
        }
    }

    private fun extractSourcesFromSourceProviders():
        (MetamodelBuildContext) -> Mono<MetamodelBuildContext> {
        return { context: MetamodelBuildContext ->
            Mono.just(context)
                .flatMap { ctx: MetamodelBuildContext ->
                    Flux.fromIterable(ctx.transformerSourceProviders)
                        .flatMap { tsp: TransformerSourceProvider<*> ->
                            tsp.getLatestSource()
                                .flatMap(validateTransformerSourceForProvider(tsp))
                                .cache()
                        }
                        .reduce(ctx) { c: MetamodelBuildContext, ts: TransformerSource ->
                            c.update { addTransformerSource(ts) }
                        }
                }
                .flatMap { ctx: MetamodelBuildContext ->
                    Flux.fromIterable(ctx.dataElementSourceProviders)
                        .flatMap { desp: DataElementSourceProvider<*> ->
                            desp
                                .getLatestSource()
                                .flatMap(validateDataElementSourceForProvider(desp))
                                .cache()
                        }
                        .reduce(ctx) { c: MetamodelBuildContext, des: DataElementSource ->
                            c.update { addDataElementSource(des) }
                        }
                }
                .flatMap { ctx: MetamodelBuildContext ->
                    Flux.fromIterable(ctx.featureCalculatorProviders)
                        .flatMap { fcp: FeatureCalculatorProvider<*> ->
                            fcp.getLatestSource()
                                .flatMap(validateFeatureCalculatorForProvider(fcp, ctx))
                                .cache()
                        }
                        .reduce(ctx) { c: MetamodelBuildContext, fc: FeatureCalculator ->
                            c.update { addFeatureCalculator(fc) }
                        }
                }
        }
    }

    private fun <TS : TransformerSource> validateTransformerSourceForProvider(
        provider: TransformerSourceProvider<TS>
    ): (TS) -> Mono<TS> {
        return { transformerSource: TS ->
            when {
                transformerSource.name != provider.name -> {
                    Mono.error<TS> {
                        ServiceError.of(
                            "transformer_source.name does not match transformer_source_provider.name [ provider.name: %s, source.name: %s ]",
                            provider.name,
                            transformerSource.name
                        )
                    }
                }
                else -> {
                    Mono.just(transformerSource)
                }
            }.flatMap { ts: TS ->
                createTypeDefinitionRegistryFromSource(ts).flatMap { tdr: TypeDefinitionRegistry ->
                    when {
                        !tdr.hasType(TypeName.newTypeName(QUERY_OBJECT_TYPE_NAME).build()) -> {
                            Mono.error<TS> {
                                ServiceError.of(
                                    "transformer_source [ name: %s ] does not contain any %s object type definition/extension definition",
                                    ts.name,
                                    QUERY_OBJECT_TYPE_NAME
                                )
                            }
                        }
                        // TODO: Ensure only one query type def exists
                        tdr.hasType(TypeName.newTypeName(MUTATION_OBJECT_TYPE_NAME).build()) -> {
                            Mono.error<TS> {
                                ServiceError.of(
                                    "transformer_source [ name: %s ] should not contain any %s object type or extension",
                                    ts.name
                                )
                            }
                        }
                        else -> {
                            Mono.just(ts)
                        }
                    }
                }
            }
        }
    }

    private fun createTypeDefinitionRegistryFromSource(
        source: Source
    ): Mono<out TypeDefinitionRegistry> {
        val sourceTypeName: String by lazy {
            when (source) {
                is TransformerSource -> {
                    TransformerSource::class.simpleName
                }
                is DataElementSource -> {
                    DataElementSource::class.simpleName
                }
                is FeatureCalculator -> {
                    FeatureCalculator::class.simpleName
                }
                else -> {
                    source::class
                        .supertypes
                        .asSequence()
                        .mapNotNull { kt: KType -> kt.classifier }
                        .filterIsInstance<KClass<*>>()
                        .filter { kc: KClass<*> -> kc.isSubclassOf(Source::class) }
                        .map { kc: KClass<*> -> kc.simpleName }
                        .firstOrNull()
                }
            }.let { name: String? ->
                StandardNamingConventions.SNAKE_CASE.deriveName(name ?: "<NA>").qualifiedForm
            }
        }
        return source.sourceSDLDefinitions
            .asSequence()
            .fold(Try.success(TypeDefinitionRegistry())) {
                tdrAttempt: Try<TypeDefinitionRegistry>,
                sd: SDLDefinition<*>,
                ->
                tdrAttempt.flatMap { tdr: TypeDefinitionRegistry ->
                    when (val possibleError: Option<GraphQLError> = tdr.add(sd).toOption()) {
                        is Some<GraphQLError> -> {
                            val message: String =
                                "%s.source_sdl_definitions: [ validation: failed ]".format(
                                    sourceTypeName
                                )
                            when (val e: GraphQLError = possibleError.value) {
                                is Throwable -> {
                                    ServiceError.builder().message(message).cause(e).build()
                                }
                                else -> {
                                    ServiceError.of(
                                        "$message[ graphql_error: %s ]",
                                        Try.attempt { e.toSpecification() }
                                            .orElseGet {
                                                mapOf(
                                                    "errorType" to e.errorType,
                                                    "message" to e.message
                                                )
                                            }
                                            .asSequence()
                                            .joinToString(", ", "{ ", " }") { (k, v) -> "$k: $v" }
                                    )
                                }
                            }.failure<TypeDefinitionRegistry>()
                        }
                        else -> {
                            Try.success(tdr)
                        }
                    }
                }
            }
            .toMono()
    }

    private fun <DES : DataElementSource> validateDataElementSourceForProvider(
        provider: DataElementSourceProvider<DES>
    ): (DES) -> Mono<DES> {
        return { dataElementSource: DES ->
            when {
                dataElementSource.name != provider.name -> {
                    Mono.error<DES> {
                        ServiceError.of(
                            "data_element_source.name does not match provider.name: [ provider.name: %s, source.name: %s ]",
                            provider.name,
                            dataElementSource.name
                        )
                    }
                }
                else -> {
                    Mono.just(dataElementSource)
                }
            }.flatMap { des: DES ->
                createTypeDefinitionRegistryFromSource(des).flatMap { tdr: TypeDefinitionRegistry ->
                    when {
                        !tdr.hasType(TypeName.newTypeName(QUERY_OBJECT_TYPE_NAME).build()) -> {
                            Mono.error<DES> {
                                ServiceError.of(
                                    "data_element_source [ name: %s ] does not contain any %s object type or extension definition",
                                    des.name,
                                    QUERY_OBJECT_TYPE_NAME
                                )
                            }
                        }
                        tdr.hasType(TypeName.newTypeName(MUTATION_OBJECT_TYPE_NAME).build()) -> {
                            Mono.error<DES> {
                                ServiceError.of(
                                    "data_element_source [ name: %s ] should not contain any %s object type or extension",
                                    des.name
                                )
                            }
                        }
                        else -> {
                            Mono.just(des)
                        }
                    }
                }
            }
        }
    }

    private fun <FC : FeatureCalculator> validateFeatureCalculatorForProvider(
        provider: FeatureCalculatorProvider<*>,
        context: MetamodelBuildContext
    ): (FC) -> Mono<FC> {
        return { featureCalculator: FC ->
            when {
                    featureCalculator.name != provider.name -> {
                        Mono.error<FC> {
                            ServiceError.of(
                                "feature_calculator.name does not match provider.name: [ provider.name: %s, source.name: %s ]",
                                provider.name,
                                featureCalculator.name
                            )
                        }
                    }
                    else -> {
                        Mono.just(featureCalculator)
                    }
                }
                .flatMap { fc: FC ->
                    createTypeDefinitionRegistryFromSource(fc).flatMap { tdr: TypeDefinitionRegistry
                        ->
                        when {
                            !tdr.hasType(TypeName.newTypeName(QUERY_OBJECT_TYPE_NAME).build()) -> {
                                Mono.error<FC> {
                                    ServiceError.of(
                                        "feature_calculator [ name: %s ] does not contain any %s object type definition/extension definition",
                                        fc.name,
                                        QUERY_OBJECT_TYPE_NAME
                                    )
                                }
                            }
                            tdr.hasType(
                                TypeName.newTypeName(MUTATION_OBJECT_TYPE_NAME).build()
                            ) -> {
                                Mono.error<FC> {
                                    ServiceError.of(
                                        "feature_calculator [ name: %s ] should not contain any %s object type or extension",
                                        fc.name
                                    )
                                }
                            }
                            else -> {
                                Mono.just(fc)
                            }
                        }
                    }
                }
                .flatMap { fc: FC ->
                    when {
                        FeatureCalculator.FEATURE_STORE_NOT_PROVIDED != fc.featureStoreName &&
                            fc.featureStoreName !in context.featureJsonValueStoresByName -> {
                            Mono.error<FC> {
                                ServiceError.of(
                                    "feature_store [ name: %s ] not found in list of provided feature_stores: [ %s ]",
                                    fc.featureStoreName,
                                    context.featureJsonValueStoresByName.keys.joinToString(", ")
                                )
                            }
                        }
                        FeatureCalculator.FEATURE_PUBLISHER_NOT_PROVIDED !=
                            fc.featurePublisherName &&
                            fc.featurePublisherName !in
                                context.featureJsonValuePublishersByName -> {
                            Mono.error<FC> {
                                ServiceError.of(
                                    "feature_publisher [ name: %s ] not found in list of provided feature_publishers: [ %s ]",
                                    fc.featurePublisherName,
                                    context.featureJsonValuePublishersByName.keys.joinToString(", ")
                                )
                            }
                        }
                        else -> {
                            Mono.just(fc)
                        }
                    }
                }
        }
    }

    private fun createTypeDefinitionRegistryFromSources():
        (MetamodelBuildContext) -> Mono<MetamodelBuildContext> {
        return { context: MetamodelBuildContext ->
            createTypeDefinitionRegistriesForEachSourceType(context)
                .reduce(
                    addScalarTypeDefinitionsToContextTypeDefinitionRegistry(context).flatMap {
                        ctx: MetamodelBuildContext ->
                        addDirectiveDefinitionsToContextTypeDefinitionRegistry(ctx)
                    }
                ) { ctxResult: Try<MetamodelBuildContext>, tdr: TypeDefinitionRegistry ->
                    ctxResult.flatMap { c: MetamodelBuildContext ->
                        when (
                            val mergeAttempt: Try<TypeDefinitionRegistry> =
                                Try.attempt { c.typeDefinitionRegistry.merge(tdr) }
                        ) {
                            is Failure<TypeDefinitionRegistry> -> {
                                val message: String =
                                    """error [ type: %s ] occurred when merging 
                                        |source_type type_definition_registry with 
                                        |metamodel_build_context.type_definition_registry"""
                                        .flatten()
                                        .format(mergeAttempt.throwable::class.qualifiedName)
                                Try.failure<MetamodelBuildContext>(
                                    ServiceError.builder()
                                        .message(message)
                                        .cause(mergeAttempt.throwable)
                                        .build()
                                )
                            }
                            is Success<TypeDefinitionRegistry> -> {
                                Try.success(
                                    c.update { typeDefinitionRegistry(mergeAttempt.result) }
                                )
                            }
                        }
                    }
                }
                .flatMap(Try<MetamodelBuildContext>::toMono)
                .flatMap(createTopLevelQueryObjectTypeDefinitionBasedOnSourceTypes())
                .cache()
        }
    }

    private fun createTypeDefinitionRegistriesForEachSourceType(
        context: MetamodelBuildContext
    ): Flux<out TypeDefinitionRegistry> {
        return Flux.concat(
            createTypeDefinitionRegistryForSourceType(
                TransformerSource::class,
                context.transformerSourcesByName.values
            ),
            createTypeDefinitionRegistryForSourceType(
                DataElementSource::class,
                context.dataElementSourcesByName.values
            ),
            createTypeDefinitionRegistryForSourceType(
                FeatureCalculator::class,
                context.featureCalculatorsByName.values
            )
        )
    }

    private fun <S : Source> createTypeDefinitionRegistryForSourceType(
        sourceType: KClass<out S>,
        sources: Iterable<Source>
    ): Mono<out TypeDefinitionRegistry> {
        val sourceObjectTypeName: String =
            when (sourceType) {
                TransformerSource::class -> {
                    TRANSFORMER_OBJECT_TYPE_NAME
                }
                DataElementSource::class -> {
                    DATA_ELEMENT_OBJECT_TYPE_NAME
                }
                FeatureCalculator::class -> {
                    FEATURE_OBJECT_TYPE_NAME
                }
                else -> {
                    throw ServiceError.of(
                        "unsupported source_type: [ type: %s ]",
                        sourceType.qualifiedName
                    )
                }
            }
        return Flux.fromIterable(sources)
            .flatMap { s: Source ->
                s.sourceSDLDefinitions
                    .asSequence()
                    .partition { sd: SDLDefinition<*> ->
                        sd is ObjectTypeDefinition && sd.name == QUERY_OBJECT_TYPE_NAME
                    }
                    .let { (queryDefs: List<SDLDefinition<*>>, otherDefs: List<SDLDefinition<*>>) ->
                        Mono.defer {
                                if (queryDefs.isEmpty() || queryDefs.size > 1) {
                                    Mono.error<ObjectTypeDefinition> {
                                        ServiceError.of(
                                            """one and only one %s object_type_definition 
                                            |should be provided 
                                            |[ source.name: %s, source.type: %s ]"""
                                                .flatten(),
                                            QUERY_OBJECT_TYPE_NAME,
                                            s.name,
                                            s::class.qualifiedName
                                        )
                                    }
                                } else {
                                    Mono.just(queryDefs[0] as ObjectTypeDefinition)
                                }
                            }
                            .map { queryDef: ObjectTypeDefinition -> queryDef to otherDefs }
                    }
            }
            .reduce(
                ObjectTypeDefinition.newObjectTypeDefinition().name(sourceObjectTypeName) to
                    persistentSetOf<SDLDefinition<*>>()
            ) {
                (otdb: ObjectTypeDefinition.Builder, ps: PersistentSet<SDLDefinition<*>>),
                (otd: ObjectTypeDefinition, dl: List<SDLDefinition<*>>) ->
                otd.fieldDefinitions.fold(otdb) {
                    otb: ObjectTypeDefinition.Builder,
                    fd: FieldDefinition ->
                    otb.fieldDefinition(fd)
                } to ps.addAll(dl)
            }
            .map { (otdb: ObjectTypeDefinition.Builder, ps: PersistentSet<SDLDefinition<*>>) ->
                ps.add(otdb.build())
            }
            .flatMap { defSet: PersistentSet<SDLDefinition<*>> ->
                defSet
                    .fold(Try.success(TypeDefinitionRegistry())) {
                        tdrAttempt: Try<TypeDefinitionRegistry>,
                        def: SDLDefinition<*> ->
                        tdrAttempt.flatMap { tdr: TypeDefinitionRegistry ->
                            when (
                                val possibleError: Option<GraphQLError> = tdr.add(def).toOption()
                            ) {
                                is Some<GraphQLError> -> {
                                    val message: String =
                                        """schema error when adding definition [ name: %s ] 
                                            |to type_definition_registry for 
                                            |source_type: [ %s ]"""
                                            .flatten()
                                            .format(
                                                def.toOption()
                                                    .filterIsInstance<SDLNamedDefinition<*>>()
                                                    .map(SDLNamedDefinition<*>::getName)
                                                    .getOrElse { "<NA>" },
                                                sourceType.simpleName
                                            )
                                    when (val e: GraphQLError = possibleError.value) {
                                        is Throwable -> {
                                            ServiceError.builder().message(message).cause(e).build()
                                        }
                                        else -> {
                                            ServiceError.of(
                                                "$message[ graphql_error: %s ]",
                                                Try.attempt { e.toSpecification() }
                                                    .orElseGet {
                                                        mapOf(
                                                            "errorType" to e.errorType,
                                                            "message" to e.message
                                                        )
                                                    }
                                                    .asSequence()
                                                    .joinToString(", ", "{ ", " }") { (k, v) ->
                                                        "$k: $v"
                                                    }
                                            )
                                        }
                                    }.failure<TypeDefinitionRegistry>()
                                }
                                else -> {
                                    Try.success(tdr)
                                }
                            }
                        }
                    }
                    .toMono()
            }
    }

    private fun addScalarTypeDefinitionsToContextTypeDefinitionRegistry(
        context: MetamodelBuildContext
    ): Try<MetamodelBuildContext> {
        val tdr: TypeDefinitionRegistry = context.typeDefinitionRegistry
        return tdr.addAll(scalarTypeRegistry.getAllScalarDefinitions())
            .toOption()
            .map { e: GraphQLError ->
                when (e) {
                    is Throwable -> {
                        ServiceError.builder()
                            .message(
                                "error occurred when adding scalar definitions to context.type_definition_registry"
                            )
                            .cause(e)
                            .build()
                    }
                    else -> {
                        ServiceError.of(
                            "error occurred when adding scalar definitions to context.type_definition_registry: [ %s ]",
                            Try.attempt { e.toSpecification() }
                                .orElseGet {
                                    mapOf("errorType" to e.errorType, "message" to e.message)
                                }
                                .asSequence()
                                .joinToString(", ") { (k, v) -> "$k: $v" }
                        )
                    }
                }.failure<TypeDefinitionRegistry>()
            }
            .getOrElse { Try.success(tdr) }
            .map { updatedTdr: TypeDefinitionRegistry ->
                context.update { typeDefinitionRegistry(updatedTdr) }
            }
    }

    private fun addDirectiveDefinitionsToContextTypeDefinitionRegistry(
        context: MetamodelBuildContext
    ): Try<MetamodelBuildContext> {
        return sequenceOf<MaterializationDirective>(AliasDirective, LastUpdatedDirective).fold(
            Try.success(context)
        ) { ctxAttempt: Try<MetamodelBuildContext>, md: MaterializationDirective ->
            ctxAttempt.flatMap { c: MetamodelBuildContext ->
                when (
                    val possibleError: Option<GraphQLError> =
                        c.typeDefinitionRegistry
                            .addAll(
                                sequenceOf(md.directiveDefinition)
                                    .plus(md.referencedInputObjectTypeDefinitions)
                                    .plus(md.referencedEnumTypeDefinitions)
                                    .toList()
                            )
                            .toOption()
                ) {
                    is Some<GraphQLError> -> {
                        val message: String =
                            """error [ type: %s ]
                            |when adding directive definition [ name: %s ] 
                            |and its referenced input object types [ names: %s ]
                            |to context.type_definition_registry
                            """
                                .flatten()
                                .format(
                                    possibleError.value::class.simpleName,
                                    md.directiveDefinition.name,
                                    md.referencedInputObjectTypeDefinitions
                                        .asSequence()
                                        .plus(md.referencedEnumTypeDefinitions)
                                        .map(SDLNamedDefinition<*>::getName)
                                        .joinToString(", ")
                                )
                        when (val e: GraphQLError = possibleError.value) {
                            is Throwable -> {
                                ServiceError.builder().message(message).cause(e).build()
                            }
                            else -> {
                                ServiceError.of(
                                    "$message[ graphql_error: %s ]",
                                    Try.attempt { e.toSpecification() }
                                        .orElseGet {
                                            mapOf(
                                                "errorType" to e.errorType,
                                                "message" to e.message
                                            )
                                        }
                                        .asSequence()
                                        .joinToString(", ", "{ ", " }") { (k, v) -> "$k: $v" }
                                )
                            }
                        }.failure<MetamodelBuildContext>()
                    }
                    else -> {
                        Try.success(c.update { typeDefinitionRegistry(c.typeDefinitionRegistry) })
                    }
                }
            }
        }
    }

    private fun createTopLevelQueryObjectTypeDefinitionBasedOnSourceTypes():
        (MetamodelBuildContext) -> Mono<MetamodelBuildContext> {
        return { context: MetamodelBuildContext ->
            context.typeDefinitionRegistry
                .getType(TRANSFORMER_OBJECT_TYPE_NAME, ObjectTypeDefinition::class.java)
                .toOption()
                .map(ObjectTypeDefinition::getName)
                .map(TypeName.newTypeName()::name)
                .map(TypeName.Builder::build)
                .zip(
                    context.typeDefinitionRegistry
                        .getType(DATA_ELEMENT_OBJECT_TYPE_NAME, ObjectTypeDefinition::class.java)
                        .toOption()
                        .map(ObjectTypeDefinition::getName)
                        .map(TypeName.newTypeName()::name)
                        .map(TypeName.Builder::build),
                    context.typeDefinitionRegistry
                        .getType(FEATURE_OBJECT_TYPE_NAME, ObjectTypeDefinition::class.java)
                        .toOption()
                        .map(ObjectTypeDefinition::getName)
                        .map(TypeName.newTypeName()::name)
                        .map(TypeName.Builder::build)
                ) { tn1: TypeName, tn2: TypeName, tn3: TypeName ->
                    sequenceOf(
                            TRANSFORMER_FIELD_NAME to tn1,
                            DATA_ELEMENT_FIELD_NAME to tn2,
                            FEATURE_FIELD_NAME to tn3
                        )
                        .map { (fieldName: String, tn: TypeName) ->
                            FieldDefinition.newFieldDefinition().name(fieldName).type(tn).build()
                        }
                        .fold(
                            ObjectTypeDefinition.newObjectTypeDefinition()
                                .name(QUERY_OBJECT_TYPE_NAME)
                        ) { otdb: ObjectTypeDefinition.Builder, fd: FieldDefinition ->
                            otdb.fieldDefinition(fd)
                        }
                        .build()
                }
                .successIfDefined {
                    ServiceError.of(
                        """object_type_definitions have not been generated 
                        |in context.type_definition_registry 
                        |for all source_types: [ type_names: %s ]"""
                            .flatten(),
                        sequenceOf(
                                TRANSFORMER_OBJECT_TYPE_NAME,
                                DATA_ELEMENT_OBJECT_TYPE_NAME,
                                FEATURE_OBJECT_TYPE_NAME
                            )
                            .joinToString(", ")
                    )
                }
                .flatMap { otd: ObjectTypeDefinition ->
                    when (
                        val possibleError: Option<GraphQLError> =
                            context.typeDefinitionRegistry.add(otd).toOption()
                    ) {
                        is Some<GraphQLError> -> {
                            val message: String =
                                """error [ type: %s ] occurred when adding 
                                    |top level query object_type_definition to 
                                    |context.type_definition_registry"""
                                    .flatten()
                                    .format(possibleError.value::class.qualifiedName)
                            when (val e: GraphQLError = possibleError.value) {
                                is Throwable -> {
                                    ServiceError.builder().message(message).cause(e).build()
                                }
                                else -> {
                                    ServiceError.of(
                                        "$message[ graphql_error: %s ]",
                                        Try.attempt { e.toSpecification() }
                                            .orElseGet {
                                                mapOf(
                                                    "errorType" to e.errorType,
                                                    "message" to e.message
                                                )
                                            }
                                            .asSequence()
                                            .joinToString(", ", "{ ", " }") { (k, v) -> "$k: $v" }
                                    )
                                }
                            }.failure<TypeDefinitionRegistry>()
                        }
                        else -> {
                            Try.success(context.typeDefinitionRegistry)
                        }
                    }
                }
                .map { tdr: TypeDefinitionRegistry ->
                    context.update { typeDefinitionRegistry(tdr) }
                }
                .toMono()
                .widen()
        }
    }

    private fun createAliasRegistryFromTypeDefinitionRegistry():
        (MetamodelBuildContext) -> Mono<MetamodelBuildContext> {
        return { context: MetamodelBuildContext ->
            AliasRegistryComposer()
                .createAliasRegistry(context.typeDefinitionRegistry)
                .successIfDefined {
                    ServiceError.of(
                        """unable to create attribute_alias_registry 
                        |from type_definition_registry; 
                        |query_object_type_definition not found"""
                            .flatten()
                    )
                }
                .toMono()
                .map { aar: AttributeAliasRegistry ->
                    context.update { attributeAliasRegistry(aar) }
                }
        }
    }

    private fun createLastUpdatedTemporalAttributeRegistryFromTypeDefinitionRegistry():
        (MetamodelBuildContext) -> Mono<MetamodelBuildContext> {
        return { context: MetamodelBuildContext ->
            LastUpdatedTemporalAttributeRegistryComposer()
                .createLastUpdatedTemporalAttributeRegistry(context.typeDefinitionRegistry)
                .successIfDefined {
                    ServiceError.of(
                        """unable to create last_updated_temporal_attribute_registry 
                        |from type_definition_registry; 
                        |query_object_type_definition not found"""
                            .flatten()
                    )
                }
                .toMono()
                .map { lutapr: LastUpdatedTemporalAttributeRegistry ->
                    context.update { lastUpdatedTemporalAttributePathRegistry(lutapr) }
                }
        }
    }

    private fun logSuccessfulStatus(): (Metamodel) -> Unit {
        return { mm: Metamodel ->
            logger.debug(
                """{}: [ status: successful ]
                |[ metamodel
                |.type_definition_registry
                |.query_object_type
                |.field_definitions: {} ]"""
                    .flatten(),
                MAIN_METHOD_TAG,
                mm.typeDefinitionRegistry
                    .getType(QUERY_OBJECT_TYPE_NAME, ObjectTypeDefinition::class.java)
                    .toOption()
                    .mapNotNull(ObjectTypeDefinition::getFieldDefinitions)
                    .map { fds: List<FieldDefinition> ->
                        fds.asSequence()
                            .map { fd: FieldDefinition ->
                                fd.name to
                                    fd.type
                                        .toOption()
                                        .filterIsInstance<NamedNode<*>>()
                                        .map(NamedNode<*>::getName)
                                        .getOrElse { "<NA>" }
                            }
                            .map { (fn: String, ft: String) -> "$fn: $ft" }
                            .joinToString(", ", "[ ", " ]")
                    }
                    .getOrElse { "<NA>" }
            )
        }
    }

    private fun logFailedStatus(): (Throwable) -> Unit {
        return { t: Throwable ->
            logger.error(
                """{}: [ status: failed ]
                |[ type: {}, message/json: {} ]"""
                    .flatten(),
                MAIN_METHOD_TAG,
                t.toOption()
                    .filterIsInstance<ServiceError>()
                    .and(ServiceError::class.qualifiedName.toOption())
                    .getOrElse { t::class.qualifiedName },
                t.toOption()
                    .filterIsInstance<ServiceError>()
                    .mapNotNull(ServiceError::toJsonNode)
                    .map(JsonNode::toString)
                    .getOrElse { t.message }
            )
        }
    }
}
