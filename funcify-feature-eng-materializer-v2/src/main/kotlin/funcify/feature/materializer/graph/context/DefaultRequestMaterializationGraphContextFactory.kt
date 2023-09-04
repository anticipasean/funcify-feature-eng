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
import funcify.feature.schema.feature.FeatureCalculatorCallable
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.schema.transformer.TransformerCallable
import graphql.language.Document
import graphql.language.Node
import kotlinx.collections.immutable.*

internal object DefaultRequestMaterializationGraphContextFactory :
    RequestMaterializationGraphContextFactory {

    internal abstract class DefaultGraphContextBaseBuilder<
        B : RequestMaterializationGraphContext.Builder<B>>(
        protected open val existingGraphContext: RequestMaterializationGraphContext? = null,
        protected open var materializationMetamodel: MaterializationMetamodel? =
            existingGraphContext?.materializationMetamodel,
        protected open var requestGraph:
            DirectedPersistentGraph<GQLOperationPath, Node<*>, MaterializationEdge>? =
            existingGraphContext?.requestGraph,
        protected open var queryComponentContextFactory: QueryComponentContextFactory? =
            existingGraphContext?.queryComponentContextFactory,
        protected open var variableKeys: PersistentSet.Builder<String> =
            existingGraphContext?.variableKeys?.toPersistentSet()?.builder()
                ?: persistentSetOf<String>().builder(),
        protected open var rawInputContextKeys: PersistentSet.Builder<String> =
            existingGraphContext?.rawInputContextKeys?.toPersistentSet()?.builder()
                ?: persistentSetOf<String>().builder(),
        protected open val passThroughColumns: PersistentSet.Builder<String> =
            existingGraphContext?.passThroughColumns?.toPersistentSet()?.builder()
                ?: persistentSetOf<String>().builder(),
        protected open val transformerCallablesByPath:
            PersistentMap.Builder<GQLOperationPath, TransformerCallable> =
            existingGraphContext?.transformerCallablesByPath?.toPersistentMap()?.builder()
                ?: persistentMapOf<GQLOperationPath, TransformerCallable>().builder(),
        protected open val dataElementCallablesByPath:
            PersistentMap.Builder<GQLOperationPath, DataElementCallable> =
            existingGraphContext?.dataElementCallablesByPath?.toPersistentMap()?.builder()
                ?: persistentMapOf<GQLOperationPath, DataElementCallable>().builder(),
        protected open val featureCalculatorCallablesByPath:
            PersistentMap.Builder<GQLOperationPath, FeatureCalculatorCallable> =
            existingGraphContext?.featureCalculatorCallablesByPath?.toPersistentMap()?.builder()
                ?: persistentMapOf<GQLOperationPath, FeatureCalculatorCallable>().builder(),
        protected open val addedVertexContexts: PersistentList.Builder<QueryComponentContext> =
            existingGraphContext?.addedVertexContexts?.toPersistentList()?.builder()
                ?: persistentListOf<QueryComponentContext>().builder()
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

        override fun requestGraph(
            requestGraph: DirectedPersistentGraph<GQLOperationPath, Node<*>, MaterializationEdge>
        ): B = this.applyOnBuilder { this.requestGraph = requestGraph }

        override fun putTransformerCallableForPath(
            path: GQLOperationPath,
            transformerCallable: TransformerCallable
        ): B =
            this.applyOnBuilder { this.transformerCallablesByPath.put(path, transformerCallable) }

        override fun putDataElementCallableForPath(
            path: GQLOperationPath,
            dataElementCallable: DataElementCallable
        ): B =
            this.applyOnBuilder { this.dataElementCallablesByPath.put(path, dataElementCallable) }

        override fun putFeatureCalculatorCallableForPath(
            path: GQLOperationPath,
            featureCalculatorCallable: FeatureCalculatorCallable,
        ): B =
            this.applyOnBuilder {
                this.featureCalculatorCallablesByPath.put(path, featureCalculatorCallable)
            }

        override fun queryComponentContextFactory(
            queryComponentContextFactory: QueryComponentContextFactory
        ): B =
            this.applyOnBuilder { this.queryComponentContextFactory = queryComponentContextFactory }

        override fun addVertexContext(nextVertex: QueryComponentContext): B =
            this.applyOnBuilder { this.addedVertexContexts.add(nextVertex) }

        override fun addedVertexContexts(addedVertexContexts: Iterable<QueryComponentContext>): B =
            this.applyOnBuilder { this.addedVertexContexts.addAll(addedVertexContexts) }

        override fun dropFirstAddedVertex(): B =
            this.applyOnBuilder { this.addedVertexContexts.removeFirst() }
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
                    ensureNotNull(operationName) { "operation_name not provided" }
                    ensureNotNull(document) { "document not provided" }
                    DefaultStandardQuery(
                        materializationMetamodel = materializationMetamodel!!,
                        variableKeys = variableKeys.build(),
                        rawInputContextKeys = rawInputContextKeys.build(),
                        requestGraph = requestGraph!!,
                        passThroughColumns = passThroughColumns.build(),
                        transformerCallablesByPath = transformerCallablesByPath.build(),
                        dataElementCallablesByPath = dataElementCallablesByPath.build(),
                        featureCalculatorCallablesByPath = featureCalculatorCallablesByPath.build(),
                        queryComponentContextFactory = queryComponentContextFactory!!,
                        addedVertexContexts = addedVertexContexts.build(),
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
        override val requestGraph:
            DirectedPersistentGraph<GQLOperationPath, Node<*>, MaterializationEdge>,
        override val passThroughColumns: PersistentSet<String>,
        override val transformerCallablesByPath:
            PersistentMap<GQLOperationPath, TransformerCallable>,
        override val dataElementCallablesByPath:
            PersistentMap<GQLOperationPath, DataElementCallable>,
        override val featureCalculatorCallablesByPath:
            PersistentMap<GQLOperationPath, FeatureCalculatorCallable>,
        override val queryComponentContextFactory: QueryComponentContextFactory,
        override val addedVertexContexts: PersistentList<QueryComponentContext>,
        override val operationName: String,
        override val document: Document
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
                    ensure(outputColumnNames.isNotEmpty()) {
                        "no output_column_names have been provided"
                    }
                    DefaultTabularQuery(
                        materializationMetamodel = materializationMetamodel!!,
                        variableKeys = variableKeys.build(),
                        rawInputContextKeys = rawInputContextKeys.build(),
                        requestGraph = requestGraph!!,
                        passThroughColumns = passThroughColumns.build(),
                        transformerCallablesByPath = transformerCallablesByPath.build(),
                        dataElementCallablesByPath = dataElementCallablesByPath.build(),
                        featureCalculatorCallablesByPath = featureCalculatorCallablesByPath.build(),
                        queryComponentContextFactory = queryComponentContextFactory!!,
                        addedVertexContexts = addedVertexContexts.build(),
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
        override val variableKeys: ImmutableSet<String>,
        override val rawInputContextKeys: ImmutableSet<String>,
        override val requestGraph:
            DirectedPersistentGraph<GQLOperationPath, Node<*>, MaterializationEdge>,
        override val passThroughColumns: ImmutableSet<String>,
        override val transformerCallablesByPath:
            ImmutableMap<GQLOperationPath, TransformerCallable>,
        override val dataElementCallablesByPath:
            ImmutableMap<GQLOperationPath, DataElementCallable>,
        override val featureCalculatorCallablesByPath:
            ImmutableMap<GQLOperationPath, FeatureCalculatorCallable>,
        override val queryComponentContextFactory: QueryComponentContextFactory,
        override val addedVertexContexts: ImmutableList<QueryComponentContext>,
        override val outputColumnNames: ImmutableSet<String>,
        override val unhandledOutputColumnNames: ImmutableList<String>
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
