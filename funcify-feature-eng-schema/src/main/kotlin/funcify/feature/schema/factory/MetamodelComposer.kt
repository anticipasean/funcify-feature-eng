package funcify.feature.schema.factory

import arrow.core.Either
import arrow.core.filterIsInstance
import arrow.core.identity
import arrow.core.left
import arrow.core.none
import arrow.core.right
import arrow.core.some
import arrow.core.toOption
import funcify.feature.error.ServiceError
import funcify.feature.schema.Metamodel
import funcify.feature.schema.dataelement.DataElementSource
import funcify.feature.schema.dataelement.DataElementSourceProvider
import funcify.feature.schema.context.MetamodelBuildContext
import funcify.feature.schema.feature.FeatureCalculator
import funcify.feature.schema.feature.FeatureCalculatorProvider
import funcify.feature.schema.transformer.TransformerSource
import funcify.feature.schema.transformer.TransformerSourceProvider
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.OptionExtensions.recurse
import funcify.feature.tools.extensions.OptionExtensions.toOption
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentMap
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tree.PersistentTree
import funcify.feature.tree.path.TreePath
import graphql.language.FieldDefinition
import graphql.language.ImplementingTypeDefinition
import graphql.language.ListType
import graphql.language.Node
import graphql.language.NonNullType
import graphql.language.ObjectTypeDefinition
import graphql.language.Type
import graphql.language.TypeName
import graphql.schema.idl.TypeDefinitionRegistry
import org.slf4j.Logger
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

internal object MetamodelComposer : (MetamodelBuildContext) -> Mono<out Metamodel> {

    private const val QUERY_OBJECT_TYPE_NAME = "Query"
    private val logger: Logger = loggerFor<MetamodelComposer>()

    override fun invoke(context: MetamodelBuildContext): Mono<out Metamodel> {
        logger.info(
            """invoke: [ context { 
                |transformerSourceProviders.size: {}, 
                |dataElementSourceProviders.size: {}, 
                |featureCalculatorProviders.size: {} } ]"""
                .flatten(),
            context.transformerSourceProviders.size,
            context.dataElementSourceProviders.size,
            context.featureCalculatorProviders.size
        )
        Mono.fromSupplier {
                DefaultMetamodel(
                    transformerSourceProvidersByName =
                        context.transformerSourceProviders
                            .asSequence()
                            .map { tsp: TransformerSourceProvider<*> -> tsp.name to tsp }
                            .reducePairsToPersistentMap(),
                    dataElementSourceProvidersByName =
                        context.dataElementSourceProviders
                            .asSequence()
                            .map { desp: DataElementSourceProvider<*> -> desp.name to desp }
                            .reducePairsToPersistentMap(),
                    featureCalculatorProvidersByName =
                        context.featureCalculatorProviders
                            .asSequence()
                            .map { fcp: FeatureCalculatorProvider<*> -> fcp.name to fcp }
                            .reducePairsToPersistentMap(),
                )
            }
            .flatMap(validateProviders(context))
            .flatMap { metamodel: DefaultMetamodel ->
                Flux.fromIterable(context.transformerSourceProviders)
                    .flatMap { tsp: TransformerSourceProvider<*> ->
                        tsp.getLatestTransformerSource()
                            .flatMap(validateTransformerSourceForProvider(tsp))
                    }
                    .reduce(metamodel) { mm: DefaultMetamodel, ts: TransformerSource ->
                        mm.copy(
                            transformerSourcesByName = mm.transformerSourcesByName.put(ts.name, ts)
                        )
                    }
            }
            .flatMap { metamodel: DefaultMetamodel ->
                Flux.fromIterable(context.dataElementSourceProviders)
                    .flatMap { desp: DataElementSourceProvider<*> ->
                        desp
                            .getLatestDataElementSource()
                            .flatMap(validateDataElementSourceForProvider(desp))
                    }
                    .reduce(metamodel) { mm: DefaultMetamodel, des: DataElementSource ->
                        mm.copy(
                            dataElementSourcesByName =
                                mm.dataElementSourcesByName.put(des.name, des)
                        )
                    }
            }
            .flatMap { metamodel: DefaultMetamodel ->
                Flux.fromIterable(context.featureCalculatorProviders)
                    .flatMap { fcp: FeatureCalculatorProvider<*> ->
                        fcp.getLatestFeatureCalculator()
                            .flatMap(validateFeatureCalculatorForProvider(fcp))
                    }
                    .reduce(metamodel) { mm: DefaultMetamodel, fc: FeatureCalculator ->
                        mm.copy(
                            featureCalculatorsByName = mm.featureCalculatorsByName.put(fc.name, fc)
                        )
                    }
            }

        TODO()
    }

    private fun validateProviders(
        environment: MetamodelBuildContext
    ): (DefaultMetamodel) -> Mono<DefaultMetamodel> {
        return { metamodel: DefaultMetamodel ->
            when {
                environment.transformerSourceProviders.size !=
                    metamodel.transformerSourceProvidersByName.size -> {
                    Mono.error<DefaultMetamodel> {
                        ServiceError.of(
                            "non-unique transformer_source_provider.name: [ duplicates for [ %s ] ]",
                            environment.transformerSourceProviders
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
                environment.dataElementSourceProviders.size !=
                    metamodel.dataElementSourceProvidersByName.size -> {
                    Mono.error<DefaultMetamodel> {
                        ServiceError.of(
                            "non-unique data_element_source_providers.name: [ duplicates for [ %s ] ]",
                            environment.dataElementSourceProviders
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
                environment.featureCalculatorProviders.size !=
                    metamodel.featureCalculatorProvidersByName.size -> {
                    Mono.error<DefaultMetamodel> {
                        ServiceError.of(
                            "non-unique feature_calculator_provider.name: [ duplicates for [ %s ] ]",
                            environment.featureCalculatorProviders
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
                    Mono.just(metamodel)
                }
            }
        }
    }

    private fun <TS : TransformerSource> validateTransformerSourceForProvider(
        provider: TransformerSourceProvider<TS>
    ): (TS) -> Mono<TS> {
        return { transformerSource: TS ->
            createTreeFromTypeDefinitionRegistryQueryNode(
                    transformerSource.sourceTypeDefinitionRegistry
                )
                .flatMap { pt: PersistentTree<Node<*>> ->
                    val transformerQueryPath: TreePath = TreePath.of { pathSegment("transformer") }
                    when {
                        transformerQueryPath !in pt -> {
                            Mono.error<TS> {
                                ServiceError.of(
                                    "transformer_query_path not found in transformer_source.source_type_definition_registry [ name: %s ]",
                                    transformerSource.name
                                )
                            }
                        }
                        pt[transformerQueryPath]
                            .filter { subTree: PersistentTree<Node<*>> -> subTree.size() == 0 }
                            .isDefined() -> {
                            Mono.error<TS> {
                                ServiceError.of(
                                    "at least one field_definition must exist on Transformer type definition corresponding to transformers of transformer_source [ name: %s ]",
                                    transformerSource.name
                                )
                            }
                        }
                        else -> {
                            Mono.just(transformerSource)
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
            createTreeFromTypeDefinitionRegistryQueryNode(
                    dataElementSource.sourceTypeDefinitionRegistry
                )
                .flatMap { pt: PersistentTree<Node<*>> ->
                    val dataElementQueryPath: TreePath = TreePath.of { pathSegment("dataElement") }
                    when {
                        dataElementQueryPath !in pt -> {
                            Mono.error<DES> {
                                ServiceError.of(
                                    "data_element_query_path not found in data_element_source.source_type_definition_registry [ name: %s ]",
                                    dataElementSource.name
                                )
                            }
                        }
                        pt[dataElementQueryPath]
                            .filter { subTree: PersistentTree<Node<*>> -> subTree.size() == 0 }
                            .isDefined() -> {
                            Mono.error<DES> {
                                ServiceError.of(
                                    "at least one field_definition must exist on DataElement type definition corresponding to data elements of data_element_source [ name: %s ]",
                                    dataElementSource.name
                                )
                            }
                        }
                        else -> {
                            Mono.just(dataElementSource)
                        }
                    }
                }
        }
    }

    private fun <FC : FeatureCalculator> validateFeatureCalculatorForProvider(
        provider: FeatureCalculatorProvider<FC>
    ): (FC) -> Mono<FC> {
        return { featureCalculator: FC -> Mono.just(featureCalculator) }
    }
}
