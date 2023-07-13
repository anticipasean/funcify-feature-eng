package funcify.feature.schema.strategy

import arrow.core.Either
import arrow.core.filterIsInstance
import arrow.core.getOrElse
import arrow.core.identity
import arrow.core.left
import arrow.core.none
import arrow.core.right
import arrow.core.some
import arrow.core.toOption
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.error.ServiceError
import funcify.feature.scalar.registry.ScalarTypeRegistry
import funcify.feature.schema.Metamodel
import funcify.feature.schema.MetamodelBuildStrategy
import funcify.feature.schema.Source
import funcify.feature.schema.context.MetamodelBuildContext
import funcify.feature.schema.dataelement.DataElementSource
import funcify.feature.schema.dataelement.DataElementSourceProvider
import funcify.feature.schema.feature.FeatureCalculator
import funcify.feature.schema.feature.FeatureCalculatorProvider
import funcify.feature.schema.metamodel.DefaultMetamodel
import funcify.feature.schema.transformer.TransformerSource
import funcify.feature.schema.transformer.TransformerSourceProvider
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.OptionExtensions.recurse
import funcify.feature.tools.extensions.OptionExtensions.toOption
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.TryExtensions.failure
import funcify.feature.tree.PersistentTree
import funcify.feature.tree.path.TreePath
import graphql.GraphQLError
import graphql.language.FieldDefinition
import graphql.language.ImplementingTypeDefinition
import graphql.language.ListType
import graphql.language.Node
import graphql.language.NonNullType
import graphql.language.ObjectTypeDefinition
import graphql.language.Type
import graphql.language.TypeName
import graphql.schema.idl.TypeDefinitionRegistry
import kotlinx.collections.immutable.toPersistentMap
import org.slf4j.Logger
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

internal class DefaultMetamodelBuildStrategy(private val scalarTypeRegistry: ScalarTypeRegistry) :
    MetamodelBuildStrategy {

    companion object {
        private const val MAIN_METHOD_TAG = "build_metamodel"
        private const val QUERY_OBJECT_TYPE_NAME = "Query"
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
                createTreeFromTypeDefinitionRegistryQueryNode(ts.sourceTypeDefinitionRegistry)
                    .flatMap { pt: PersistentTree<Node<*>> ->
                        val transformerQueryPath: TreePath =
                            TreePath.of { pathSegment(TRANSFORMER_FIELD_NAME) }
                        when {
                            pt.children().count() != 1 -> {
                                Mono.error<TS> {
                                    ServiceError.of(
                                        "only one query with [ name: %s, type.name: %s ] should be provided by transformer_source.source_type_definition_registry [ query.field_definitions.size: %s ]",
                                        TRANSFORMER_FIELD_NAME,
                                        TRANSFORMER_OBJECT_TYPE_NAME,
                                        pt.children().count()
                                    )
                                }
                            }
                            transformerQueryPath !in pt -> {
                                Mono.error<TS> {
                                    ServiceError.of(
                                        "field_definition [ name: %s ] not found in transformer_source.source_type_definition_registry [ transformer_source.name: %s ]",
                                        TRANSFORMER_FIELD_NAME,
                                        ts.name
                                    )
                                }
                            }
                            pt[transformerQueryPath]
                                .filter { subTree: PersistentTree<Node<*>> -> subTree.size() == 0 }
                                .isDefined() -> {
                                Mono.error<TS> {
                                    ServiceError.of(
                                        "at least one field_definition must exist on [ type.name: %s ] type definition corresponding to transformers of transformer_source [ transformer_source.name: %s ]",
                                        TRANSFORMER_OBJECT_TYPE_NAME,
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

    private fun createTreeFromTypeDefinitionRegistryQueryNode(
        typeDefinitionRegistry: TypeDefinitionRegistry
    ): Mono<PersistentTree<Node<*>>> {
        return Mono.defer {
                when (
                    val queryObjectTypeDefinition: ObjectTypeDefinition? =
                        typeDefinitionRegistry
                            .getType(QUERY_OBJECT_TYPE_NAME, ObjectTypeDefinition::class.java)
                            .orElse(null)
                ) {
                    null -> {
                        Mono.error<Node<*>> {
                            ServiceError.of(
                                "root query type definition or extension definition not provided in type_definition_registry"
                            )
                        }
                    }
                    else -> {
                        Mono.just<Node<*>>(queryObjectTypeDefinition)
                    }
                }
            }
            .map { queryNode: Node<*> ->
                val visitedImplementingTypeNamesSet: MutableSet<String> = mutableSetOf()
                PersistentTree.fromSequenceTraversal<Node<*>>(queryNode) { n: Node<*> ->
                    when (n) {
                        is ImplementingTypeDefinition<*> -> {
                            if (n.name in visitedImplementingTypeNamesSet) {
                                emptySequence()
                            } else {
                                n.fieldDefinitions
                                    .asSequence()
                                    .map { fd: FieldDefinition -> (fd.name to fd).right() }
                                    .also { visitedImplementingTypeNamesSet.add(n.name) }
                            }
                        }
                        is FieldDefinition -> {
                            n.type
                                .toOption()
                                .recurse { t: Type<*> ->
                                    when (t) {
                                        is NonNullType -> t.type.left().some()
                                        is ListType -> t.type.left().some()
                                        is TypeName -> t.name.right().some()
                                        else -> none()
                                    }
                                }
                                .flatMap { typeName: String ->
                                    typeDefinitionRegistry
                                        .getType(typeName)
                                        .toOption()
                                        .filterIsInstance<ImplementingTypeDefinition<*>>()
                                }
                                .map { itd: ImplementingTypeDefinition<*> ->
                                    if (itd.name in visitedImplementingTypeNamesSet) {
                                        emptySequence()
                                    } else {
                                        itd.fieldDefinitions
                                            .asSequence()
                                            .map { fd: FieldDefinition -> (fd.name to fd).right() }
                                            .also { visitedImplementingTypeNamesSet.add(itd.name) }
                                    }
                                }
                                .fold(::emptySequence, ::identity)
                        }
                        else -> {
                            emptySequence<Either<Node<*>, Pair<String, Node<*>>>>()
                        }
                    }
                }
            }
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
                createTreeFromTypeDefinitionRegistryQueryNode(des.sourceTypeDefinitionRegistry)
                    .flatMap { pt: PersistentTree<Node<*>> ->
                        val dataElementQueryPath: TreePath =
                            TreePath.of { pathSegment(DATA_ELEMENT_FIELD_NAME) }
                        when {
                            pt.children().count() != 1 -> {
                                Mono.error<DES> {
                                    ServiceError.of(
                                        "only one query with [ name: %s, type.name: %s ] should be provided by data_element_source.source_type_definition_registry [ query.field_definitions.size: %s ]",
                                        DATA_ELEMENT_FIELD_NAME,
                                        DATA_ELEMENT_OBJECT_TYPE_NAME,
                                        pt.children().count()
                                    )
                                }
                            }
                            dataElementQueryPath !in pt -> {
                                Mono.error<DES> {
                                    ServiceError.of(
                                        "field_definition [ name: %s ] not found in data_element_source.source_type_definition_registry [ name: %s ]",
                                        DATA_ELEMENT_FIELD_NAME,
                                        des.name
                                    )
                                }
                            }
                            pt[dataElementQueryPath]
                                .filter { subTree: PersistentTree<Node<*>> -> subTree.size() == 0 }
                                .isDefined() -> {
                                Mono.error<DES> {
                                    ServiceError.of(
                                        "at least one field_definition must exist on [ type.name: %s ] implementing type definition corresponding to data elements of data_element_source [ name: %s ]",
                                        DATA_ELEMENT_OBJECT_TYPE_NAME,
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
                    createTreeFromTypeDefinitionRegistryQueryNode(fc.sourceTypeDefinitionRegistry)
                        .flatMap { pt: PersistentTree<Node<*>> ->
                            val featurePath: TreePath =
                                TreePath.of { pathSegment(FEATURE_FIELD_NAME) }
                            when {
                                pt.children().count() != 1 -> {
                                    Mono.error<FC> {
                                        ServiceError.of(
                                            "only one query with [ name: %s, type.name: %s ] should be provided by feature_calculator.source_type_definition_registry [ query.field_definitions.size: %s ]",
                                            FEATURE_FIELD_NAME,
                                            FEATURE_OBJECT_TYPE_NAME,
                                            pt.children().count()
                                        )
                                    }
                                }
                                featurePath !in pt -> {
                                    Mono.error<FC> {
                                        ServiceError.of(
                                            "field_definition [ name: %s ] not found in feature_calculator.source_type_definition_registry [ feature_calculator.name: %s ]",
                                            FEATURE_FIELD_NAME,
                                            fc.name
                                        )
                                    }
                                }
                                pt[featurePath]
                                    .filter { subTree: PersistentTree<Node<*>> ->
                                        subTree.size() == 0
                                    }
                                    .isDefined() -> {
                                    Mono.error<FC> {
                                        ServiceError.of(
                                            "at least one field_definition must exist on [ type.name: %s ] implementing type definition corresponding to features of feature_calculator [ name: %s ]",
                                            FEATURE_OBJECT_TYPE_NAME,
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
            Flux.fromIterable<Source>(context.transformerSourcesByName.values)
                .concatWith(Flux.fromIterable(context.dataElementSourcesByName.values))
                .concatWith(Flux.fromIterable(context.featureCalculatorsByName.values))
                .reduce(addScalarTypeDefinitionsToContextTypeDefinitionRegistry(context)) {
                    ctxResult: Try<MetamodelBuildContext>,
                    s: Source ->
                    ctxResult.flatMap { c: MetamodelBuildContext ->
                        Try.attempt {
                                c.typeDefinitionRegistry.merge(s.sourceTypeDefinitionRegistry)
                            }
                            .map { updatedTdr: TypeDefinitionRegistry ->
                                c.update { typeDefinitionRegistry(updatedTdr) }
                            }
                            .mapFailure { t: Throwable ->
                                ServiceError.builder()
                                    .message(
                                        "error [ type: %s ] occurred when merging [ source.name: %s ].source_type_definition_registry with metamodel_build_context.type_definition_registry",
                                        t::class.qualifiedName,
                                        s.name
                                    )
                                    .cause(t)
                                    .build()
                            }
                    }
                }
                .flatMap(Try<MetamodelBuildContext>::toMono)
                .cache()
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
                    is RuntimeException -> {
                        ServiceError.builder()
                            .message(
                                "error occurred when adding scalar definitions to context.type_definition_registry"
                            )
                            .cause(e)
                            .build()
                            .failure<TypeDefinitionRegistry>()
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
                            .failure<TypeDefinitionRegistry>()
                    }
                }
            }
            .getOrElse { Try.success(tdr) }
            .map { updatedTdr: TypeDefinitionRegistry ->
                context.update { typeDefinitionRegistry(updatedTdr) }
            }
    }

    private fun logSuccessfulStatus(): (Metamodel) -> Unit {
        return { mm: Metamodel ->
            logger.debug(
                """{}: [ status: successful ]
                |[ metamodel
                |.type_definition_registry
                |.query_object_type
                |.field_definitions.name: {} ]"""
                    .flatten(),
                MAIN_METHOD_TAG,
                mm.typeDefinitionRegistry
                    .getType(QUERY_OBJECT_TYPE_NAME, ObjectTypeDefinition::class.java)
                    .toOption()
                    .mapNotNull(ObjectTypeDefinition::getFieldDefinitions)
                    .map { fds: List<FieldDefinition> ->
                        fds.asSequence()
                            .map { fd: FieldDefinition -> fd.name }
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
