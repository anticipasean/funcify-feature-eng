package funcify.feature.materializer.graph.context

import arrow.core.continuations.eagerEffect
import arrow.core.continuations.ensureNotNull
import arrow.core.identity
import funcify.feature.error.ServiceError
import funcify.feature.graph.DirectedPersistentGraph
import funcify.feature.materializer.graph.MaterializationEdge
import funcify.feature.materializer.graph.component.QueryComponentContext
import funcify.feature.materializer.graph.component.QueryComponentContextFactory
import funcify.feature.materializer.model.MaterializationMetamodel
import funcify.feature.naming.StandardNamingConventions
import funcify.feature.schema.dataelement.DataElementCallable
import funcify.feature.schema.document.GQLDocumentComposer
import funcify.feature.schema.document.GQLDocumentSpecFactory
import funcify.feature.schema.feature.FeatureCalculatorCallable
import funcify.feature.schema.feature.FeatureJsonValuePublisher
import funcify.feature.schema.feature.FeatureJsonValueStore
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.schema.transformer.TransformerCallable
import graphql.language.Document
import graphql.schema.FieldCoordinates
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.collections.immutable.toPersistentSet

internal object DefaultRequestMaterializationGraphContextFactory :
    RequestMaterializationGraphContextFactory {

    internal abstract class DefaultGraphContextBaseBuilder<
        B : RequestMaterializationGraphContext.Builder<B>
    >(
        protected open val existingGraphContext: RequestMaterializationGraphContext? = null,
        protected open var materializationMetamodel: MaterializationMetamodel? =
            existingGraphContext?.materializationMetamodel,
        protected open var requestGraph:
            DirectedPersistentGraph<GQLOperationPath, QueryComponentContext, MaterializationEdge>? =
            existingGraphContext?.requestGraph,
        protected open var queryComponentContextFactory: QueryComponentContextFactory? =
            existingGraphContext?.queryComponentContextFactory,
        protected open var gqlDocumentSpecFactory: GQLDocumentSpecFactory? =
            existingGraphContext?.gqlDocumentSpecFactory,
        protected open var gqlDocumentComposer: GQLDocumentComposer? =
            existingGraphContext?.gqlDocumentComposer,
        protected open var variableKeys: PersistentSet.Builder<String> =
            existingGraphContext?.variableKeys?.toPersistentSet()?.builder()
                ?: persistentSetOf<String>().builder(),
        protected open var rawInputContextKeys: PersistentSet.Builder<String> =
            existingGraphContext?.rawInputContextKeys?.toPersistentSet()?.builder()
                ?: persistentSetOf<String>().builder(),
        protected open val passThroughColumns: PersistentSet.Builder<String> =
            existingGraphContext?.passThroughColumns?.toPersistentSet()?.builder()
                ?: persistentSetOf<String>().builder(),
        protected open val connectedFieldPathsByCoordinates:
            PersistentMap.Builder<FieldCoordinates, ImmutableSet<GQLOperationPath>> =
            existingGraphContext?.connectedFieldPathsByCoordinates?.toPersistentMap()?.builder()
                ?: persistentMapOf<FieldCoordinates, ImmutableSet<GQLOperationPath>>().builder(),
        protected open val connectedPathsByCanonicalPath:
            PersistentMap.Builder<GQLOperationPath, ImmutableSet<GQLOperationPath>> =
            existingGraphContext?.connectedPathsByCanonicalPath?.toPersistentMap()?.builder()
                ?: persistentMapOf<GQLOperationPath, ImmutableSet<GQLOperationPath>>().builder(),
        protected open val canonicalPathByConnectedPath:
            PersistentMap.Builder<GQLOperationPath, GQLOperationPath> =
            existingGraphContext?.canonicalPathByConnectedPath?.toPersistentMap()?.builder()
                ?: persistentMapOf<GQLOperationPath, GQLOperationPath>().builder(),
        protected open val transformerCallablesByPath:
            PersistentMap.Builder<GQLOperationPath, TransformerCallable> =
            existingGraphContext?.transformerCallablesByPath?.toPersistentMap()?.builder()
                ?: persistentMapOf<GQLOperationPath, TransformerCallable>().builder(),
        protected open val dataElementCallableBuildersByPath:
            PersistentMap.Builder<GQLOperationPath, DataElementCallable.Builder> =
            existingGraphContext?.dataElementCallableBuildersByPath?.toPersistentMap()?.builder()
                ?: persistentMapOf<GQLOperationPath, DataElementCallable.Builder>().builder(),
        protected open val featureCalculatorCallablesByPath:
            PersistentMap.Builder<GQLOperationPath, FeatureCalculatorCallable> =
            existingGraphContext?.featureCalculatorCallablesByPath?.toPersistentMap()?.builder()
                ?: persistentMapOf<GQLOperationPath, FeatureCalculatorCallable>().builder(),
        protected open val featureJsonValueStoresByPath:
            PersistentMap.Builder<GQLOperationPath, FeatureJsonValueStore> =
            existingGraphContext?.featureJsonValueStoresByPath?.toPersistentMap()?.builder()
                ?: persistentMapOf<GQLOperationPath, FeatureJsonValueStore>().builder(),
        protected open val featureJsonValuePublishersByPath:
            PersistentMap.Builder<GQLOperationPath, FeatureJsonValuePublisher> =
            existingGraphContext?.featureJsonValuePublishersByPath?.toPersistentMap()?.builder()
                ?: persistentMapOf<GQLOperationPath, FeatureJsonValuePublisher>().builder(),
        protected open val lastUpdatedDataElementPathsByDataElementPath:
            PersistentMap.Builder<GQLOperationPath, GQLOperationPath> =
            existingGraphContext
                ?.lastUpdatedDataElementPathsByDataElementPath
                ?.toPersistentMap()
                ?.builder() ?: persistentMapOf<GQLOperationPath, GQLOperationPath>().builder()
    ) : RequestMaterializationGraphContext.Builder<B> {

        companion object {
            /**
             * @param WB - Wide Builder Type
             * @param NB - Narrow Builder Type
             */
            private inline fun <reified WB, reified NB : WB> WB.applyOnBuilder(
                builderUpdater: WB.() -> Unit
            ): NB {
                return this.apply(builderUpdater) as NB
            }
        }

        override fun materializationMetamodel(
            materializationMetamodel: MaterializationMetamodel
        ): B = this.applyOnBuilder { this.materializationMetamodel = materializationMetamodel }

        override fun variableKeys(variableKeys: ImmutableSet<String>): B =
            this.applyOnBuilder { this.variableKeys = variableKeys.toPersistentSet().builder() }

        override fun rawInputContextKeys(rawInputContextKeys: ImmutableSet<String>): B =
            this.applyOnBuilder {
                this.rawInputContextKeys = rawInputContextKeys.toPersistentSet().builder()
            }

        override fun addPassThroughColumn(name: String): B =
            this.applyOnBuilder { this.passThroughColumns.add(name) }

        override fun putConnectedFieldPathForCoordinates(
            fieldCoordinates: FieldCoordinates,
            path: GQLOperationPath
        ): B =
            this.applyOnBuilder {
                this.connectedFieldPathsByCoordinates.put(
                    fieldCoordinates,
                    this.connectedFieldPathsByCoordinates
                        .getOrElse(fieldCoordinates, ::persistentSetOf)
                        .toPersistentSet()
                        .add(path)
                )
            }

        override fun putConnectedPathForCanonicalPath(
            canonicalPath: GQLOperationPath,
            path: GQLOperationPath
        ): B =
            this.applyOnBuilder {
                if (
                    canonicalPath.refersToSelectionOnFragmentSpread() ||
                        canonicalPath.containsAliasForField()
                ) {
                    throw ServiceError.of(
                        "canonical_path input should not refer to alias or fragment_spread"
                    )
                }
                this.connectedPathsByCanonicalPath.put(
                    canonicalPath,
                    this.connectedPathsByCanonicalPath
                        .getOrElse(canonicalPath, ::persistentSetOf)
                        .toPersistentSet()
                        .add(path)
                )
                this.canonicalPathByConnectedPath.put(path, canonicalPath)
            }

        override fun requestGraph(
            requestGraph:
                DirectedPersistentGraph<
                    GQLOperationPath,
                    QueryComponentContext,
                    MaterializationEdge
                >
        ): B = this.applyOnBuilder { this.requestGraph = requestGraph }

        override fun putTransformerCallableForPath(
            path: GQLOperationPath,
            transformerCallable: TransformerCallable
        ): B =
            this.applyOnBuilder { this.transformerCallablesByPath.put(path, transformerCallable) }

        override fun putDataElementCallableBuilderForPath(
            path: GQLOperationPath,
            dataElementCallableBuilder: DataElementCallable.Builder
        ): B =
            this.applyOnBuilder {
                this.dataElementCallableBuildersByPath.put(path, dataElementCallableBuilder)
            }

        override fun putFeatureCalculatorCallableForPath(
            path: GQLOperationPath,
            featureCalculatorCallable: FeatureCalculatorCallable,
        ): B =
            this.applyOnBuilder {
                this.featureCalculatorCallablesByPath.put(path, featureCalculatorCallable)
            }

        override fun putFeatureJsonValueStoreForPath(
            path: GQLOperationPath,
            featureJsonValueStore: FeatureJsonValueStore
        ): B =
            this.applyOnBuilder {
                this.featureJsonValueStoresByPath.put(path, featureJsonValueStore)
            }

        override fun putFeatureJsonValuePublisherForPath(
            path: GQLOperationPath,
            featureJsonValuePublisher: FeatureJsonValuePublisher
        ): B =
            this.applyOnBuilder {
                this.featureJsonValuePublishersByPath.put(path, featureJsonValuePublisher)
            }

        override fun putLastUpdatedDataElementPathForDataElementPath(
            dataElementPath: GQLOperationPath,
            lastUpdatedDataElementPath: GQLOperationPath
        ): B =
            this.applyOnBuilder {
                this.lastUpdatedDataElementPathsByDataElementPath.put(
                    dataElementPath,
                    lastUpdatedDataElementPath
                )
            }

        override fun queryComponentContextFactory(
            queryComponentContextFactory: QueryComponentContextFactory
        ): B =
            this.applyOnBuilder { this.queryComponentContextFactory = queryComponentContextFactory }

        override fun gqlDocumentSpecFactory(gqlDocumentSpecFactory: GQLDocumentSpecFactory): B =
            this.applyOnBuilder { this.gqlDocumentSpecFactory = gqlDocumentSpecFactory }

        override fun gqlDocumentComposer(gqlDocumentComposer: GQLDocumentComposer): B =
            this.applyOnBuilder { this.gqlDocumentComposer = gqlDocumentComposer }
    }

    internal class DefaultStandardQueryBuilder(
        private val existingStandardQuery: StandardQuery? = null,
        private var operationName: String? = existingStandardQuery?.operationName,
        private var document: Document? = existingStandardQuery?.document
    ) :
        DefaultGraphContextBaseBuilder<StandardQuery.Builder>(
            existingGraphContext = existingStandardQuery
        ),
        StandardQuery.Builder {

        override fun operationName(operationName: String): StandardQuery.Builder =
            this.apply { this.operationName = operationName }

        override fun document(document: Document): StandardQuery.Builder =
            this.apply { this.document = document }

        override fun build(): StandardQuery {
            return eagerEffect<String, StandardQuery> {
                    ensureNotNull(materializationMetamodel) {
                        "materialization_metamodel not provided"
                    }
                    ensureNotNull(requestGraph) { "request_graph not provided" }
                    ensureNotNull(queryComponentContextFactory) {
                        "query_component_context_factory not provided"
                    }
                    ensureNotNull(gqlDocumentSpecFactory) {
                        "gql_document_spec_factory not provided"
                    }
                    ensureNotNull(gqlDocumentComposer) { "gql_document_composer not provided" }
                    ensureNotNull(operationName) { "operation_name not provided" }
                    ensureNotNull(document) { "document not provided" }
                    DefaultStandardQuery(
                        materializationMetamodel = materializationMetamodel!!,
                        variableKeys = variableKeys.build(),
                        rawInputContextKeys = rawInputContextKeys.build(),
                        connectedFieldPathsByCoordinates = connectedFieldPathsByCoordinates.build(),
                        connectedPathsByCanonicalPath = connectedPathsByCanonicalPath.build(),
                        canonicalPathByConnectedPath = canonicalPathByConnectedPath.build(),
                        requestGraph = requestGraph!!,
                        passThroughColumns = passThroughColumns.build(),
                        transformerCallablesByPath = transformerCallablesByPath.build(),
                        dataElementCallableBuildersByPath =
                            dataElementCallableBuildersByPath.build(),
                        featureCalculatorCallablesByPath = featureCalculatorCallablesByPath.build(),
                        featureJsonValueStoresByPath = featureJsonValueStoresByPath.build(),
                        featureJsonValuePublishersByPath = featureJsonValuePublishersByPath.build(),
                        lastUpdatedDataElementPathsByDataElementPath =
                            lastUpdatedDataElementPathsByDataElementPath.build(),
                        queryComponentContextFactory = queryComponentContextFactory!!,
                        gqlDocumentSpecFactory = gqlDocumentSpecFactory!!,
                        gqlDocumentComposer = gqlDocumentComposer!!,
                        operationName = operationName!!,
                        document = document!!,
                    )
                }
                .fold(
                    { message: String ->
                        throw ServiceError.of(
                            "unable to create %s [ message: %s ]",
                            StandardNamingConventions.SNAKE_CASE.deriveName(
                                StandardQuery::class.simpleName ?: "<NA>"
                            ),
                            message
                        )
                    },
                    ::identity
                )
        }
    }

    internal data class DefaultStandardQuery(
        override val materializationMetamodel: MaterializationMetamodel,
        override val variableKeys: PersistentSet<String>,
        override val rawInputContextKeys: PersistentSet<String>,
        override val connectedFieldPathsByCoordinates:
            PersistentMap<FieldCoordinates, ImmutableSet<GQLOperationPath>>,
        override val connectedPathsByCanonicalPath:
            PersistentMap<GQLOperationPath, ImmutableSet<GQLOperationPath>>,
        override val canonicalPathByConnectedPath:
            PersistentMap<GQLOperationPath, GQLOperationPath>,
        override val requestGraph:
            DirectedPersistentGraph<GQLOperationPath, QueryComponentContext, MaterializationEdge>,
        override val passThroughColumns: PersistentSet<String>,
        override val transformerCallablesByPath:
            PersistentMap<GQLOperationPath, TransformerCallable>,
        override val dataElementCallableBuildersByPath:
            PersistentMap<GQLOperationPath, DataElementCallable.Builder>,
        override val featureCalculatorCallablesByPath:
            PersistentMap<GQLOperationPath, FeatureCalculatorCallable>,
        override val featureJsonValueStoresByPath:
            PersistentMap<GQLOperationPath, FeatureJsonValueStore>,
        override val featureJsonValuePublishersByPath:
            PersistentMap<GQLOperationPath, FeatureJsonValuePublisher>,
        override val lastUpdatedDataElementPathsByDataElementPath:
            PersistentMap<GQLOperationPath, GQLOperationPath>,
        override val queryComponentContextFactory: QueryComponentContextFactory,
        override val gqlDocumentComposer: GQLDocumentComposer,
        override val gqlDocumentSpecFactory: GQLDocumentSpecFactory,
        override val operationName: String,
        override val document: Document,
    ) : StandardQuery {

        override fun update(
            transformer: StandardQuery.Builder.() -> StandardQuery.Builder
        ): StandardQuery {
            return transformer.invoke(DefaultStandardQueryBuilder(this)).build()
        }
    }

    internal class DefaultTabularQueryBuilder(
        private val existingTabularQuery: TabularQuery? = null,
        private val outputColumnNames: PersistentSet.Builder<String> =
            existingTabularQuery?.outputColumnNames?.toPersistentSet()?.builder()
                ?: persistentSetOf<String>().builder(),
        private val unhandledColumnNames: PersistentList.Builder<String> =
            existingTabularQuery?.unhandledOutputColumnNames?.toPersistentList()?.builder()
                ?: persistentListOf<String>().builder()
    ) :
        DefaultGraphContextBaseBuilder<TabularQuery.Builder>(
            existingGraphContext = existingTabularQuery
        ),
        TabularQuery.Builder {

        override fun outputColumnNames(
            outputColumnNames: ImmutableSet<String>
        ): TabularQuery.Builder = this.apply { this.outputColumnNames.addAll(outputColumnNames) }

        override fun addUnhandledColumnName(unhandledColumnName: String): TabularQuery.Builder =
            this.apply { this.outputColumnNames.add(unhandledColumnName) }

        override fun dropHeadUnhandledColumnName(): TabularQuery.Builder =
            this.apply { this.unhandledColumnNames.removeFirst() }

        override fun build(): TabularQuery {
            return eagerEffect<String, TabularQuery> {
                    ensureNotNull(materializationMetamodel) {
                        "materialization_metamodel not provided"
                    }
                    ensureNotNull(requestGraph) { "request_graph not provided" }
                    ensureNotNull(queryComponentContextFactory) {
                        "query_component_context_factory not provided"
                    }
                    ensureNotNull(gqlDocumentSpecFactory) {
                        "gql_document_spec_factory not provided"
                    }
                    ensureNotNull(gqlDocumentComposer) { "gql_document_composer not provided" }
                    ensure(outputColumnNames.isNotEmpty()) {
                        "no output_column_names have been provided"
                    }
                    DefaultTabularQuery(
                        materializationMetamodel = materializationMetamodel!!,
                        variableKeys = variableKeys.build(),
                        rawInputContextKeys = rawInputContextKeys.build(),
                        connectedFieldPathsByCoordinates = connectedFieldPathsByCoordinates.build(),
                        connectedPathsByCanonicalPath = connectedPathsByCanonicalPath.build(),
                        canonicalPathByConnectedPath = canonicalPathByConnectedPath.build(),
                        requestGraph = requestGraph!!,
                        passThroughColumns = passThroughColumns.build(),
                        transformerCallablesByPath = transformerCallablesByPath.build(),
                        dataElementCallableBuildersByPath =
                            dataElementCallableBuildersByPath.build(),
                        featureCalculatorCallablesByPath = featureCalculatorCallablesByPath.build(),
                        featureJsonValueStoresByPath = featureJsonValueStoresByPath.build(),
                        featureJsonValuePublishersByPath = featureJsonValuePublishersByPath.build(),
                        lastUpdatedDataElementPathsByDataElementPath =
                            lastUpdatedDataElementPathsByDataElementPath.build(),
                        queryComponentContextFactory = queryComponentContextFactory!!,
                        gqlDocumentSpecFactory = gqlDocumentSpecFactory!!,
                        gqlDocumentComposer = gqlDocumentComposer!!,
                        outputColumnNames = outputColumnNames.build(),
                        unhandledOutputColumnNames = unhandledColumnNames.build()
                    )
                }
                .fold(
                    { message: String ->
                        throw ServiceError.of(
                            "unable to create %s [ message: %s ]",
                            StandardNamingConventions.SNAKE_CASE.deriveName(
                                TabularQuery::class.simpleName ?: "<NA>"
                            ),
                            message
                        )
                    },
                    ::identity
                )
        }
    }

    internal data class DefaultTabularQuery(
        override val materializationMetamodel: MaterializationMetamodel,
        override val variableKeys: PersistentSet<String>,
        override val rawInputContextKeys: PersistentSet<String>,
        override val connectedFieldPathsByCoordinates:
            PersistentMap<FieldCoordinates, ImmutableSet<GQLOperationPath>>,
        override val connectedPathsByCanonicalPath:
            PersistentMap<GQLOperationPath, ImmutableSet<GQLOperationPath>>,
        override val canonicalPathByConnectedPath:
            PersistentMap<GQLOperationPath, GQLOperationPath>,
        override val requestGraph:
            DirectedPersistentGraph<GQLOperationPath, QueryComponentContext, MaterializationEdge>,
        override val passThroughColumns: PersistentSet<String>,
        override val transformerCallablesByPath:
            PersistentMap<GQLOperationPath, TransformerCallable>,
        override val dataElementCallableBuildersByPath:
            PersistentMap<GQLOperationPath, DataElementCallable.Builder>,
        override val featureCalculatorCallablesByPath:
            PersistentMap<GQLOperationPath, FeatureCalculatorCallable>,
        override val featureJsonValueStoresByPath:
            PersistentMap<GQLOperationPath, FeatureJsonValueStore>,
        override val featureJsonValuePublishersByPath:
            PersistentMap<GQLOperationPath, FeatureJsonValuePublisher>,
        override val lastUpdatedDataElementPathsByDataElementPath:
            PersistentMap<GQLOperationPath, GQLOperationPath>,
        override val queryComponentContextFactory: QueryComponentContextFactory,
        override val gqlDocumentSpecFactory: GQLDocumentSpecFactory,
        override val gqlDocumentComposer: GQLDocumentComposer,
        override val outputColumnNames: PersistentSet<String>,
        override val unhandledOutputColumnNames: PersistentList<String>
    ) : TabularQuery {

        override fun update(
            transformer: TabularQuery.Builder.() -> TabularQuery.Builder
        ): TabularQuery {
            return transformer.invoke(DefaultTabularQueryBuilder(this)).build()
        }
    }

    override fun standardQueryBuilder(): StandardQuery.Builder {
        return DefaultStandardQueryBuilder()
    }

    override fun tabularQueryBuilder(): TabularQuery.Builder {
        return DefaultTabularQueryBuilder()
    }
}
