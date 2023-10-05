package funcify.feature.materializer.graph.connector

import funcify.feature.error.ServiceError
import funcify.feature.materializer.graph.component.QueryComponentContext
import funcify.feature.materializer.graph.connector.TabularQueryRawInputBasedOperationCreator.TabularQueryCompositionContext.Builder
import funcify.feature.materializer.graph.context.TabularQuery
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import graphql.schema.FieldCoordinates
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentSet
import org.slf4j.Logger

internal object TabularQueryRawInputBasedOperationCreator :
    (TabularQuery) -> Iterable<QueryComponentContext> {

    private const val METHOD_TAG: String = "tabular_query_raw_input_based_operation_creator.invoke"
    private val logger: Logger = loggerFor<TabularQueryRawInputBasedOperationCreator>()

    override fun invoke(tabularQuery: TabularQuery): Iterable<QueryComponentContext> {
        logger.info(
            "{}: [ tabular_query.raw_input_context_keys.size: {} ]",
            METHOD_TAG,
            tabularQuery.rawInputContextKeys.size
        )
        Try.success(DefaultTabularQueryCompositionContext.empty())
            .map(matchRawInputContextKeysWithDomainSpecifiedDataElementSources(tabularQuery))
            .filter(contextContainsErrors(), createAggregateErrorFromContext())
            .map(matchVariableKeysWithDomainSpecifiedDataElementSourceArguments(tabularQuery))
            .filter(contextContainsErrors(), createAggregateErrorFromContext())
            .map(matchExpectedOutputColumnNamesToFeaturePathsOrDataElementCoordinates(tabularQuery))
            .filter(contextContainsErrors(), createAggregateErrorFromContext())
            .map(connectDataElementCoordinatesToPathsUnderSupportedSources(tabularQuery))
            .filter(contextContainsErrors(), createAggregateErrorFromContext())
        TODO("Not yet implemented")
    }

    private fun matchRawInputContextKeysWithDomainSpecifiedDataElementSources(
        tabularQuery: TabularQuery
    ): (TabularQueryCompositionContext) -> TabularQueryCompositionContext {
        TODO("Not yet implemented")
    }

    private fun contextContainsErrors(): (TabularQueryCompositionContext) -> Boolean {
        return { tqcc: TabularQueryCompositionContext -> tqcc.errors.isNotEmpty() }
    }

    private fun createAggregateErrorFromContext(): (TabularQueryCompositionContext) -> Throwable {
        return { tqcc: TabularQueryCompositionContext ->
            ServiceError.builder()
                .message("unable to create tabular query operation")
                .addAllServiceErrorsToHistory(tqcc.errors)
                .build()
        }
    }

    private fun matchVariableKeysWithDomainSpecifiedDataElementSourceArguments(
        tabularQuery: TabularQuery
    ): (TabularQueryCompositionContext) -> TabularQueryCompositionContext {
        TODO("Not yet implemented")
    }

    private fun matchExpectedOutputColumnNamesToFeaturePathsOrDataElementCoordinates(
        tabularQuery: TabularQuery
    ): (TabularQueryCompositionContext) -> TabularQueryCompositionContext {
        TODO("Not yet implemented")
    }

    private fun connectDataElementCoordinatesToPathsUnderSupportedSources(
        tabularQuery: TabularQuery
    ): (TabularQueryCompositionContext) -> TabularQueryCompositionContext {
        TODO("Not yet implemented")
    }

    private interface TabularQueryCompositionContext {
        val argumentPathSetsForVariables: ImmutableMap<String, ImmutableSet<GQLOperationPath>>
        val domainDataElementSourcePaths: ImmutableSet<GQLOperationPath>
        val featurePathByExpectedOutputColumnName: ImmutableMap<String, GQLOperationPath>
        val dataElementFieldCoordinatesByExpectedOutputColumnName:
            ImmutableMap<String, ImmutableSet<FieldCoordinates>>
        val dataElementFieldComponentContextsByOutputColumnName:
            ImmutableMap<String, ImmutableList<QueryComponentContext.SelectedFieldComponentContext>>
        val queryComponentContexts: ImmutableList<QueryComponentContext>
        val errors: ImmutableList<ServiceError>

        fun update(transformer: Builder.() -> Builder): TabularQueryCompositionContext

        interface Builder {
            fun putArgumentPathSetForVariable(
                variableKey: String,
                argumentPathSet: Set<GQLOperationPath>
            ): Builder

            fun putDomainDataElementSourcePath(domainDataElementPath: GQLOperationPath): Builder

            fun putAllDomainDataElementSourcePaths(
                domainDataElementSourcePaths: Set<GQLOperationPath>
            ): Builder

            fun putFeaturePathForExpectedOutputColumnName(
                columnName: String,
                featurePath: GQLOperationPath
            ): Builder

            fun putDataElementFieldCoordinatesForExpectedOutputColumnName(
                columnName: String,
                dataElementFieldCoordinatesSet: Set<FieldCoordinates>
            ): Builder

            fun putSelectedFieldComponentContextsForOutputColumnName(
                columnName: String,
                selectedFieldComponentContexts:
                    List<QueryComponentContext.SelectedFieldComponentContext>
            ): Builder

            fun addAllQueryComponentContexts(
                queryComponentContexts: List<QueryComponentContext>
            ): Builder

            fun addQueryComponentContext(queryComponentContext: QueryComponentContext): Builder

            fun addError(serviceError: ServiceError): Builder

            fun build(): TabularQueryCompositionContext
        }
    }

    private class DefaultTabularQueryCompositionContext(
        override val argumentPathSetsForVariables:
            PersistentMap<String, PersistentSet<GQLOperationPath>>,
        override val domainDataElementSourcePaths: PersistentSet<GQLOperationPath>,
        override val featurePathByExpectedOutputColumnName: PersistentMap<String, GQLOperationPath>,
        override val dataElementFieldCoordinatesByExpectedOutputColumnName:
            PersistentMap<String, PersistentSet<FieldCoordinates>>,
        override val dataElementFieldComponentContextsByOutputColumnName:
            PersistentMap<
                String, PersistentList<QueryComponentContext.SelectedFieldComponentContext>
            >,
        override val queryComponentContexts: PersistentList<QueryComponentContext>,
        override val errors: PersistentList<ServiceError>,
    ) : TabularQueryCompositionContext {
        companion object {
            fun empty(): TabularQueryCompositionContext {
                return DefaultTabularQueryCompositionContext(
                    argumentPathSetsForVariables = persistentMapOf(),
                    domainDataElementSourcePaths = persistentSetOf(),
                    featurePathByExpectedOutputColumnName = persistentMapOf(),
                    dataElementFieldCoordinatesByExpectedOutputColumnName = persistentMapOf(),
                    dataElementFieldComponentContextsByOutputColumnName = persistentMapOf(),
                    queryComponentContexts = persistentListOf(),
                    errors = persistentListOf()
                )
            }

            private class DefaultBuilder(
                private val existingContext: DefaultTabularQueryCompositionContext,
                private val argumentPathSetsForVariables:
                    PersistentMap.Builder<String, PersistentSet<GQLOperationPath>> =
                    existingContext.argumentPathSetsForVariables.builder(),
                private val domainDataElementSourcePaths: PersistentSet.Builder<GQLOperationPath> =
                    existingContext.domainDataElementSourcePaths.builder(),
                private val featurePathByExpectedOutputColumnName:
                    PersistentMap.Builder<String, GQLOperationPath> =
                    existingContext.featurePathByExpectedOutputColumnName.builder(),
                private val dataElementFieldCoordinatesByExpectedOutputColumnName:
                    PersistentMap.Builder<String, PersistentSet<FieldCoordinates>> =
                    existingContext.dataElementFieldCoordinatesByExpectedOutputColumnName.builder(),
                private val dataElementFieldComponentContextsByOutputColumnName:
                    PersistentMap.Builder<
                        String, PersistentList<QueryComponentContext.SelectedFieldComponentContext>
                    > =
                    existingContext.dataElementFieldComponentContextsByOutputColumnName.builder(),
                private val queryComponentContexts: PersistentList.Builder<QueryComponentContext> =
                    existingContext.queryComponentContexts.builder(),
                private val errors: PersistentList.Builder<ServiceError> =
                    existingContext.errors.builder()
            ) : Builder {

                override fun putArgumentPathSetForVariable(
                    variableKey: String,
                    argumentPathSet: Set<GQLOperationPath>,
                ): Builder =
                    this.apply {
                        this.argumentPathSetsForVariables.put(
                            variableKey,
                            argumentPathSet.toPersistentSet()
                        )
                    }

                override fun putDomainDataElementSourcePath(
                    domainDataElementPath: GQLOperationPath
                ): Builder =
                    this.apply { this.domainDataElementSourcePaths.add(domainDataElementPath) }

                override fun putAllDomainDataElementSourcePaths(
                    domainDataElementSourcePaths: Set<GQLOperationPath>
                ): Builder =
                    this.apply {
                        this.domainDataElementSourcePaths.addAll(domainDataElementSourcePaths)
                    }

                override fun putFeaturePathForExpectedOutputColumnName(
                    columnName: String,
                    featurePath: GQLOperationPath
                ): Builder =
                    this.apply {
                        this.featurePathByExpectedOutputColumnName.put(columnName, featurePath)
                    }

                override fun addError(serviceError: ServiceError): Builder =
                    this.apply { this.errors.add(serviceError) }

                override fun putDataElementFieldCoordinatesForExpectedOutputColumnName(
                    columnName: String,
                    dataElementFieldCoordinatesSet: Set<FieldCoordinates>
                ): Builder =
                    this.apply {
                        this.dataElementFieldCoordinatesByExpectedOutputColumnName.put(
                            columnName,
                            dataElementFieldCoordinatesSet.toPersistentSet()
                        )
                    }

                override fun putSelectedFieldComponentContextsForOutputColumnName(
                    columnName: String,
                    selectedFieldComponentContexts:
                        List<QueryComponentContext.SelectedFieldComponentContext>
                ): Builder =
                    this.apply {
                        this.dataElementFieldComponentContextsByOutputColumnName.put(
                            columnName,
                            this.dataElementFieldComponentContextsByOutputColumnName
                                .getOrElse(columnName, ::persistentListOf)
                                .addAll(selectedFieldComponentContexts)
                        )
                    }

                override fun addAllQueryComponentContexts(
                    queryComponentContexts: List<QueryComponentContext>
                ): Builder =
                    this.apply { this.queryComponentContexts.addAll(queryComponentContexts) }

                override fun addQueryComponentContext(
                    queryComponentContext: QueryComponentContext
                ): Builder = this.apply { this.queryComponentContexts.add(queryComponentContext) }

                override fun build(): TabularQueryCompositionContext {
                    return DefaultTabularQueryCompositionContext(
                        argumentPathSetsForVariables = argumentPathSetsForVariables.build(),
                        domainDataElementSourcePaths = domainDataElementSourcePaths.build(),
                        featurePathByExpectedOutputColumnName =
                            featurePathByExpectedOutputColumnName.build(),
                        dataElementFieldCoordinatesByExpectedOutputColumnName =
                            dataElementFieldCoordinatesByExpectedOutputColumnName.build(),
                        dataElementFieldComponentContextsByOutputColumnName =
                            dataElementFieldComponentContextsByOutputColumnName.build(),
                        queryComponentContexts = queryComponentContexts.build(),
                        errors = errors.build()
                    )
                }
            }
        }

        override fun update(transformer: Builder.() -> Builder): TabularQueryCompositionContext {
            return transformer.invoke(DefaultBuilder(this)).build()
        }
    }
}
