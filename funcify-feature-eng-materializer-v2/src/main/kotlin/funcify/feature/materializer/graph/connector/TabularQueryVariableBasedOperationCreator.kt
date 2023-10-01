package funcify.feature.materializer.graph.connector

import arrow.core.Option
import arrow.core.getOrElse
import arrow.core.getOrNone
import arrow.core.orElse
import arrow.core.toOption
import funcify.feature.error.ServiceError
import funcify.feature.materializer.graph.component.QueryComponentContext
import funcify.feature.materializer.graph.component.QueryComponentContext.SelectedFieldComponentContext
import funcify.feature.materializer.graph.connector.TabularQueryVariableBasedOperationCreator.TabularQueryCompositionContext.*
import funcify.feature.materializer.graph.context.TabularQuery
import funcify.feature.schema.dataelement.DomainSpecifiedDataElementSource
import funcify.feature.schema.feature.FeatureSpecifiedFeatureCalculator
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.OptionExtensions.sequence
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentSetValueMap
import funcify.feature.tools.extensions.SequenceExtensions.firstOrNone
import funcify.feature.tools.extensions.SequenceExtensions.flatMapOptions
import funcify.feature.tools.extensions.StringExtensions.flatten
import graphql.language.Field
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLArgument
import kotlinx.collections.immutable.*
import org.slf4j.Logger

internal object TabularQueryVariableBasedOperationCreator :
    (TabularQuery) -> Iterable<QueryComponentContext> {

    private const val METHOD_TAG: String = "tabular_query_variable_based_operation_creator.invoke"
    private val logger: Logger = loggerFor<TabularQueryVariableBasedOperationCreator>()

    override fun invoke(tabularQuery: TabularQuery): Iterable<QueryComponentContext> {
        logger.info(
            "{}: [ tabular_query.variable_keys.size: {} ]",
            METHOD_TAG,
            tabularQuery.variableKeys.size
        )
        // TODO: Impose rule that no data element may share the same name as a feature
        Try.success(DefaultTabularQueryCompositionContext.empty())
            .map(matchVariableKeysWithDomainSpecifiedDataElementSourceArguments(tabularQuery))
            .filter(contextContainsErrors(), createAggregateErrorFromContext())
            .map(determineAllDomainDataElementSourcePathsWithCompleteArgumentSets(tabularQuery))
            .filter(contextContainsErrors(), createAggregateErrorFromContext())
            .map(matchExpectedOutputColumnNamesToFeaturePathsOrDataElementCoordinates(tabularQuery))
            .filter(contextContainsErrors(), createAggregateErrorFromContext())
            .map(connectDataElementCoordinatesToPathsUnderSupportedSources(tabularQuery))
            .filter(contextContainsErrors(), createAggregateErrorFromContext())
        TODO("Not yet implemented")
    }

    private fun matchVariableKeysWithDomainSpecifiedDataElementSourceArguments(
        tabularQuery: TabularQuery
    ): (TabularQueryCompositionContext) -> TabularQueryCompositionContext {
        return { tqcc: TabularQueryCompositionContext ->
            tqcc.update {
                tabularQuery.variableKeys.asSequence().fold(this) {
                    cb: TabularQueryCompositionContext.Builder,
                    vk: String ->
                    when (
                        val argumentPathSet: ImmutableSet<GQLOperationPath>? =
                            matchVariableKeyToDataElementArgumentPaths(tabularQuery, vk).orNull()
                    ) {
                        null -> {
                            cb.addError(
                                ServiceError.of(
                                    """variable [ key: %s ] does not match 
                                    |any of the names or aliases 
                                    |for arguments to supported data element 
                                    |sources for a tabular query"""
                                        .flatten(),
                                    vk
                                )
                            )
                        }
                        else -> {
                            cb.putArgumentPathSetForVariable(vk, argumentPathSet)
                        }
                    }
                }
            }
        }
    }

    private fun matchVariableKeyToDataElementArgumentPaths(
        tabularQuery: TabularQuery,
        variableKey: String
    ): Option<ImmutableSet<GQLOperationPath>> {
        return tabularQuery.materializationMetamodel.dataElementPathByFieldArgumentName
            .getOrNone(variableKey)
            .filter(ImmutableSet<GQLOperationPath>::isNotEmpty)
            .orElse {
                tabularQuery.materializationMetamodel.aliasCoordinatesRegistry
                    .getFieldArgumentsWithAlias(variableKey)
                    .toOption()
                    .filter(ImmutableSet<Pair<FieldCoordinates, String>>::isNotEmpty)
                    .map { fieldArgumentLocations: ImmutableSet<Pair<FieldCoordinates, String>> ->
                        fieldArgumentLocations
                            .asSequence()
                            .map { (fc: FieldCoordinates, argName: String) ->
                                tabularQuery.materializationMetamodel
                                    .domainSpecifiedDataElementSourceByCoordinates
                                    .getOrNone(fc)
                                    .flatMap { dsdes: DomainSpecifiedDataElementSource ->
                                        dsdes.argumentPathsByName.getOrNone(argName)
                                    }
                            }
                            .flatMapOptions()
                            .toPersistentSet()
                    }
                    .filter(ImmutableSet<GQLOperationPath>::isNotEmpty)
            }
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

    private fun determineAllDomainDataElementSourcePathsWithCompleteArgumentSets(
        tabularQuery: TabularQuery
    ): (TabularQueryCompositionContext) -> TabularQueryCompositionContext {
        return { tqcc: TabularQueryCompositionContext ->
            tqcc.argumentPathSetsForVariables
                .asSequence()
                .map(Map.Entry<String, ImmutableSet<GQLOperationPath>>::value)
                .flatMap(ImmutableSet<GQLOperationPath>::asSequence)
                .flatMap { p: GQLOperationPath ->
                    p.getParentPath().map { pp: GQLOperationPath -> pp to p }.sequence()
                }
                .reducePairsToPersistentSetValueMap()
                .asSequence()
                .filter { (pp: GQLOperationPath, argPathsSet: Set<GQLOperationPath>) ->
                    tabularQuery.materializationMetamodel.domainSpecifiedDataElementSourceByPath
                        .getOrNone(pp)
                        .map { dsdes: DomainSpecifiedDataElementSource ->
                            // Must provide all arguments or at least those lacking default
                            // argument values
                            dsdes.argumentsByPath.size == argPathsSet.size ||
                                // TODO: Cacheable operation
                                dsdes.argumentsWithoutDefaultValuesByPath.asSequence().all {
                                    (p: GQLOperationPath, _: GraphQLArgument) ->
                                    argPathsSet.contains(p)
                                }
                        }
                        .getOrElse { false }
                }
                .map(Map.Entry<GQLOperationPath, Set<GQLOperationPath>>::key)
                .toSet()
                .let { dataElementDomainPaths: Set<GQLOperationPath> ->
                    if (dataElementDomainPaths.isNotEmpty()) {
                        tqcc.update { putAllDomainDataElementSourcePaths(dataElementDomainPaths) }
                    } else {
                        tqcc.update {
                            addError(
                                ServiceError.of(
                                    "none of the data element arguments specified within variables set correspond to a _complete_ argument set for a domain data element source"
                                )
                            )
                        }
                    }
                }
        }
    }

    private fun matchExpectedOutputColumnNamesToFeaturePathsOrDataElementCoordinates(
        tabularQuery: TabularQuery
    ): (TabularQueryCompositionContext) -> TabularQueryCompositionContext {
        return { tqcc: TabularQueryCompositionContext ->
            tqcc.update {
                tabularQuery.outputColumnNames.asSequence().fold(this) {
                    cb: TabularQueryCompositionContext.Builder,
                    cn: String ->
                    when (
                        val fp: GQLOperationPath? =
                            matchExpectedOutputColumnNameToFeaturePath(tabularQuery, cn).orNull()
                    ) {
                        null -> {
                            when (
                                val defc: ImmutableSet<FieldCoordinates>? =
                                    matchExpectedOutputColumnNameToDataElementFieldCoordinates(
                                            tabularQuery,
                                            cn
                                        )
                                        .orNull()
                            ) {
                                null -> {
                                    cb.addError(
                                        ServiceError.of(
                                            "[ column_name: %s ] does not match feature or data_element name or alias",
                                            cn
                                        )
                                    )
                                }
                                else -> {
                                    cb.putDataElementFieldCoordinatesForExpectedOutputColumnName(
                                        cn,
                                        defc
                                    )
                                }
                            }
                        }
                        else -> {
                            cb.putFeaturePathForExpectedOutputColumnName(cn, fp)
                        }
                    }
                }
            }
        }
    }

    private fun matchExpectedOutputColumnNameToFeaturePath(
        tabularQuery: TabularQuery,
        columnName: String
    ): Option<GQLOperationPath> {
        return tabularQuery.materializationMetamodel.featurePathsByName
            .getOrNone(columnName)
            .orElse {
                tabularQuery.materializationMetamodel.aliasCoordinatesRegistry
                    .getFieldsWithAlias(columnName)
                    .asSequence()
                    .map { fc: FieldCoordinates ->
                        tabularQuery.materializationMetamodel
                            .featureSpecifiedFeatureCalculatorsByCoordinates
                            .getOrNone(fc)
                            .map(FeatureSpecifiedFeatureCalculator::featurePath)
                    }
                    .flatMapOptions()
                    .firstOrNone()
            }
    }

    private fun matchExpectedOutputColumnNameToDataElementFieldCoordinates(
        tabularQuery: TabularQuery,
        columnName: String
    ): Option<ImmutableSet<FieldCoordinates>> {
        return tabularQuery.materializationMetamodel.dataElementFieldCoordinatesByFieldName
            .getOrNone(columnName)
            .filter(ImmutableSet<FieldCoordinates>::isNotEmpty)
            .orElse {
                tabularQuery.materializationMetamodel.aliasCoordinatesRegistry
                    .getFieldsWithAlias(columnName)
                    .toOption()
                    .filter(ImmutableSet<FieldCoordinates>::isNotEmpty)
            }
    }

    private fun connectDataElementCoordinatesToPathsUnderSupportedSources(
        tabularQuery: TabularQuery
    ): (TabularQueryCompositionContext) -> TabularQueryCompositionContext {
        return { tqcc: TabularQueryCompositionContext ->
            tqcc.update {
                tqcc.dataElementFieldCoordinatesByExpectedOutputColumnName
                    .asSequence()
                    .map { (cn: String, fcs: ImmutableSet<FieldCoordinates>) ->
                        tqcc.domainDataElementSourcePaths
                            .asSequence()
                            .map { ddep: GQLOperationPath ->
                                fcs.asSequence()
                                    .map { fc: FieldCoordinates ->
                                        tabularQuery.materializationMetamodel
                                            .firstPathWithFieldCoordinatesUnderPath
                                            .invoke(fc, ddep)
                                            .map { p: GQLOperationPath -> fc to p }
                                    }
                                    .flatMapOptions()
                                    .firstOrNone()
                                    .map { (fc: FieldCoordinates, p: GQLOperationPath) ->
                                        when {
                                            fc.fieldName == cn -> {
                                                tabularQuery.queryComponentContextFactory
                                                    .selectedFieldComponentContextBuilder()
                                                    .field(
                                                        Field.newField().name(fc.fieldName).build()
                                                    )
                                                    .fieldCoordinates(fc)
                                                    .canonicalPath(p)
                                                    .path(p)
                                                    .build()
                                            }
                                            else -> {
                                                tabularQuery.queryComponentContextFactory
                                                    .selectedFieldComponentContextBuilder()
                                                    .field(
                                                        Field.newField()
                                                            .name(fc.fieldName)
                                                            .alias(cn)
                                                            .build()
                                                    )
                                                    .fieldCoordinates(fc)
                                                    .canonicalPath(p)
                                                    .path(
                                                        p.transform {
                                                            dropTailSelectionSegment()
                                                            aliasedField(cn, fc.fieldName)
                                                        }
                                                    )
                                                    .build()
                                            }
                                        }
                                    }
                            }
                            .flatMapOptions()
                            .toList()
                            .let { sfccs: List<SelectedFieldComponentContext> -> cn to sfccs }
                    }
                    .fold(this) {
                        tb: TabularQueryCompositionContext.Builder,
                        (cn: String, sfccs: List<SelectedFieldComponentContext>) ->
                        when {
                            sfccs.isNotEmpty() -> {
                                tb.putSelectedFieldComponentContextsForOutputColumnName(cn, sfccs)
                            }
                            else -> {
                                tb.addError(
                                    ServiceError.of(
                                        """data element with [ column_name: %s ] 
                                        |does not match path under domain data element 
                                        |sources specified through variables 
                                        |in the request"""
                                            .flatten(),
                                        cn
                                    )
                                )
                            }
                        }
                    }
            }
        }
    }

    private interface TabularQueryCompositionContext {
        val argumentPathSetsForVariables: ImmutableMap<String, ImmutableSet<GQLOperationPath>>
        val domainDataElementSourcePaths: ImmutableSet<GQLOperationPath>
        val featurePathByExpectedOutputColumnName: ImmutableMap<String, GQLOperationPath>
        val dataElementFieldCoordinatesByExpectedOutputColumnName:
            ImmutableMap<String, ImmutableSet<FieldCoordinates>>
        val dataElementFieldComponentContextsByOutputColumnName:
            ImmutableMap<String, ImmutableList<SelectedFieldComponentContext>>
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
                selectedFieldComponentContexts: List<SelectedFieldComponentContext>
            ): Builder

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
            PersistentMap<String, PersistentList<SelectedFieldComponentContext>>,
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
                    PersistentMap.Builder<String, PersistentList<SelectedFieldComponentContext>> =
                    existingContext.dataElementFieldComponentContextsByOutputColumnName.builder(),
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
                    selectedFieldComponentContexts: List<SelectedFieldComponentContext>
                ): Builder =
                    this.apply {
                        this.dataElementFieldComponentContextsByOutputColumnName.put(
                            columnName,
                            this.dataElementFieldComponentContextsByOutputColumnName
                                .getOrElse(columnName, ::persistentListOf)
                                .addAll(selectedFieldComponentContexts)
                        )
                    }

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
