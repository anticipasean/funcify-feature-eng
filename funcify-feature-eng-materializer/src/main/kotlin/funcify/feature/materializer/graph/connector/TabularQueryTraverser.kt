package funcify.feature.materializer.graph.connector

import arrow.core.*
import funcify.feature.error.ServiceError
import funcify.feature.materializer.graph.component.QueryComponentContext
import funcify.feature.materializer.graph.component.QueryComponentContext.FieldComponentContext
import funcify.feature.materializer.graph.connector.TabularQueryTraverser.TabularQueryTraversalContext.*
import funcify.feature.materializer.graph.context.TabularQuery
import funcify.feature.schema.dataelement.DomainSpecifiedDataElementSource
import funcify.feature.schema.feature.FeatureSpecifiedFeatureCalculator
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.OptionExtensions.recurse
import funcify.feature.tools.extensions.OptionExtensions.sequence
import funcify.feature.tools.extensions.PairExtensions.fold
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentSetValueMap
import funcify.feature.tools.extensions.SequenceExtensions.firstOrNone
import funcify.feature.tools.extensions.SequenceExtensions.flatMapOptions
import funcify.feature.tools.extensions.SequenceExtensions.recurseBreadthFirst
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
import graphql.language.Argument
import graphql.language.Field
import graphql.language.Value
import graphql.language.VariableReference
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLCompositeType
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLTypeUtil
import graphql.schema.InputValueWithState
import kotlinx.collections.immutable.*
import org.slf4j.Logger

/**
 * @author smccarron
 * @created 2023-10-13
 */
internal object TabularQueryTraverser : (TabularQuery) -> Iterable<QueryComponentContext> {

    private const val METHOD_TAG: String = "tabular_query_traverser.invoke"
    private val logger: Logger = loggerFor<TabularQueryTraverser>()

    override fun invoke(tabularQuery: TabularQuery): Iterable<QueryComponentContext> {
        logger.info(
            "{}: [ tabular_query.variable_keys.size: {}, tabular_query.raw_input_context_keys.size: {} ]",
            METHOD_TAG,
            tabularQuery.variableKeys.size,
            tabularQuery.rawInputContextKeys.size
        )
        // TODO: Impose rule that no data element may share the same name as a feature
        return Try.success(DefaultTabularQueryTraversalContext.empty())
            .map(matchRawInputContextKeysWithDomainSpecifiedDataElementSources(tabularQuery))
            .map(matchVariableKeysWithDomainSpecifiedDataElementSourceArguments(tabularQuery))
            .filter(contextContainsErrors(), createAggregateErrorFromContext())
            .map(addAllDomainDataElementsWithCompleteVariableKeyArgumentSets(tabularQuery))
            .filter(contextContainsErrors(), createAggregateErrorFromContext())
            .map(matchExpectedOutputColumnNamesToFeaturePathsOrDataElementCoordinates(tabularQuery))
            .filter(contextContainsErrors(), createAggregateErrorFromContext())
            .map(connectDataElementCoordinatesToPathsUnderSupportedSources(tabularQuery))
            .filter(contextContainsErrors(), createAggregateErrorFromContext())
            .map(createSequenceOfDataElementQueryComponentContexts(tabularQuery))
            .map(createSequenceOfFeatureQueryComponentContexts(tabularQuery))
            .map(combineDataElementAndFeatureQueryComponentContextsIntoIterable())
            .orElseThrow()
    }

    private fun matchRawInputContextKeysWithDomainSpecifiedDataElementSources(
        tabularQuery: TabularQuery
    ): (TabularQueryTraversalContext) -> TabularQueryTraversalContext {
        return { tqcc: TabularQueryTraversalContext ->
            tqcc.update {
                tabularQuery.rawInputContextKeys.asSequence().fold(this) {
                    cb: TabularQueryTraversalContext.Builder,
                    k: String ->
                    when (
                        val dsdes: DomainSpecifiedDataElementSource? =
                            matchRawInputContextKeyWithDomainSpecifiedDataElementSource(
                                    tabularQuery,
                                    k
                                )
                                .orNull()
                    ) {
                        null -> {
                            cb.addPassthruRawInputContextKey(k)
                        }
                        else -> {
                            cb.putRawInputContextKeyForDataElementSourcePath(dsdes.domainPath, k)
                        }
                    }
                }
            }
        }
    }

    private fun matchRawInputContextKeyWithDomainSpecifiedDataElementSource(
        tabularQuery: TabularQuery,
        rawInputContextKey: String
    ): Option<DomainSpecifiedDataElementSource> {
        // TODO: Add lookup_by_name for domain_specified_data_element_sources sparing the need for
        // these lookups if this is determined to be the best way to deduce what domains have been
        // provided in raw_input_context
        return tabularQuery.materializationMetamodel.querySchemaElementsByPath
            .getOrNone(tabularQuery.materializationMetamodel.dataElementElementTypePath)
            .filterIsInstance<GraphQLFieldDefinition>()
            .map(GraphQLFieldDefinition::getType)
            .map(GraphQLTypeUtil::unwrapAll)
            .filterIsInstance<GraphQLCompositeType>()
            .map(GraphQLCompositeType::getName)
            .map { tn: String -> FieldCoordinates.coordinates(tn, rawInputContextKey) }
            .flatMap(
                tabularQuery.materializationMetamodel
                    .domainSpecifiedDataElementSourceByCoordinates::getOrNone
            )
    }

    private fun matchVariableKeysWithDomainSpecifiedDataElementSourceArguments(
        tabularQuery: TabularQuery
    ): (TabularQueryTraversalContext) -> TabularQueryTraversalContext {
        return { tqcc: TabularQueryTraversalContext ->
            tqcc.update {
                tabularQuery.variableKeys.asSequence().fold(this) {
                    cb: TabularQueryTraversalContext.Builder,
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
                                        dsdes.domainArgumentPathsByName.getOrNone(argName)
                                    }
                            }
                            .flatMapOptions()
                            .toPersistentSet()
                    }
                    .filter(ImmutableSet<GQLOperationPath>::isNotEmpty)
            }
    }

    private fun contextContainsErrors(): (TabularQueryTraversalContext) -> Boolean {
        return { tqcc: TabularQueryTraversalContext -> tqcc.errors.isNotEmpty() }
    }

    private fun createAggregateErrorFromContext(): (TabularQueryTraversalContext) -> Throwable {
        return { tqcc: TabularQueryTraversalContext ->
            ServiceError.builder()
                .message("unable to create tabular query operation")
                .addAllServiceErrorsToHistory(tqcc.errors)
                .build()
        }
    }

    private fun addAllDomainDataElementsWithCompleteVariableKeyArgumentSets(
        tabularQuery: TabularQuery
    ): (TabularQueryTraversalContext) -> TabularQueryTraversalContext {
        return { tqcc: TabularQueryTraversalContext ->
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
                            // TODO: Convert to cacheable operation
                            dsdes.allArgumentsWithoutDefaultValuesByPath.asSequence().all {
                                (p: GQLOperationPath, _: GraphQLArgument) ->
                                argPathsSet.contains(p)
                            }
                        }
                        .getOrElse { false }
                }
                .map(Map.Entry<GQLOperationPath, Set<GQLOperationPath>>::key)
                .toSet()
                .let { dataElementDomainPaths: Set<GQLOperationPath> ->
                    when {
                        dataElementDomainPaths.isNotEmpty() -> {
                            tqcc.update {
                                putAllDomainDataElementSourcePathsForCompleteVariableArgumentSet(
                                    dataElementDomainPaths
                                )
                            }
                        }
                        tqcc.rawInputContextKeysByDataElementSourcePath.isNotEmpty() -> {
                            tqcc
                        }
                        else -> {
                            // TODO: This may need to be revisited for case where caller does not
                            // want any feature or data element values but still provides variables
                            // and/or raw_input_context for some reason: the passthru_columns only
                            // use case
                            tqcc.update {
                                addError(
                                    ServiceError.of(
                                        """none of the data element arguments specified 
                                        |within variables set correspond to a _complete_ argument 
                                        |set for a domain data element source nor 
                                        |do any raw_input_context.keys provide known data_element_sources"""
                                            .flatten()
                                    )
                                )
                            }
                        }
                    }
                }
        }
    }

    private fun matchExpectedOutputColumnNamesToFeaturePathsOrDataElementCoordinates(
        tabularQuery: TabularQuery
    ): (TabularQueryTraversalContext) -> TabularQueryTraversalContext {
        return { tqcc: TabularQueryTraversalContext ->
            tqcc.update {
                tabularQuery.outputColumnNames.asSequence().fold(this) {
                    cb: TabularQueryTraversalContext.Builder,
                    cn: String ->
                    matchExpectedOutputColumnNameToFeaturePath(tabularQuery, cn)
                        .map { fp: GQLOperationPath ->
                            cb.putFeaturePathForExpectedOutputColumnName(cn, fp)
                        }
                        .orElse {
                            matchExpectedOutputColumnNameToDataElementFieldCoordinates(
                                    tabularQuery,
                                    cn
                                )
                                .map { fcs: Set<FieldCoordinates> ->
                                    cb.putDataElementFieldCoordinatesForExpectedOutputColumnName(
                                        cn,
                                        fcs
                                    )
                                }
                        }
                        .orElse {
                            matchExpectedOutputColumnNameToPassThruRawInputContextKey(tqcc, cn)
                                .map { column: String -> cb.addSelectedPassthruColumn(column) }
                        }
                        .getOrElse {
                            cb.addError(
                                ServiceError.of(
                                    """expected output column [ %s ] does not match 
                                    |feature or data_element name or alias or 
                                    |pass-thru value within raw_input_context"""
                                        .flatten(),
                                    cn
                                )
                            )
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

    private fun matchExpectedOutputColumnNameToPassThruRawInputContextKey(
        tabularQueryTraversalContext: TabularQueryTraversalContext,
        columnName: String
    ): Option<String> {
        return when {
            tabularQueryTraversalContext.passthruRawInputContextKeys.contains(columnName) -> {
                columnName.some()
            }
            else -> {
                None
            }
        }
    }

    private fun connectDataElementCoordinatesToPathsUnderSupportedSources(
        tabularQuery: TabularQuery
    ): (TabularQueryTraversalContext) -> TabularQueryTraversalContext {
        return { tqcc: TabularQueryTraversalContext ->
            tqcc.update {
                tqcc.dataElementFieldCoordinatesByExpectedOutputColumnName
                    .asSequence()
                    .map { (cn: String, fcs: ImmutableSet<FieldCoordinates>) ->
                        createFieldContextForEachDomainDataElementSourceUnderWhichColumnAvailable(
                            tqcc,
                            tabularQuery,
                            cn,
                            fcs
                        )
                    }
                    .fold(this) {
                        tb: TabularQueryTraversalContext.Builder,
                        (cn: String, sfccs: List<FieldComponentContext>) ->
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

    private fun createFieldContextForEachDomainDataElementSourceUnderWhichColumnAvailable(
        tabularQueryTraversalContext: TabularQueryTraversalContext,
        tabularQuery: TabularQuery,
        columnName: String,
        coordinates: ImmutableSet<FieldCoordinates>
    ): Pair<String, List<FieldComponentContext>> {
        return tabularQueryTraversalContext.rawInputContextKeysByDataElementSourcePath.keys
            .asSequence()
            .plus(tabularQueryTraversalContext.domainDataElementSourcePathsForVariables)
            .map { ddesp: GQLOperationPath ->
                coordinates
                    .asSequence()
                    .map { fc: FieldCoordinates ->
                        tabularQuery.materializationMetamodel.firstPathWithFieldCoordinatesUnderPath
                            .invoke(fc, ddesp)
                            .map { p: GQLOperationPath -> fc to p }
                    }
                    .flatMapOptions()
                    .firstOrNone()
            }
            .flatMapOptions()
            .map { (fc: FieldCoordinates, p: GQLOperationPath) ->
                when {
                    fc.fieldName == columnName -> {
                        tabularQuery.queryComponentContextFactory
                            .fieldComponentContextBuilder()
                            .field(Field.newField().name(fc.fieldName).build())
                            .fieldCoordinates(fc)
                            .canonicalPath(p)
                            .path(p)
                            .build()
                    }
                    else -> {
                        tabularQuery.queryComponentContextFactory
                            .fieldComponentContextBuilder()
                            .field(Field.newField().name(fc.fieldName).alias(columnName).build())
                            .fieldCoordinates(fc)
                            .canonicalPath(p)
                            .path(
                                p.transform {
                                    dropTailSelectionSegment()
                                    aliasedField(columnName, fc.fieldName)
                                }
                            )
                            .build()
                    }
                }
            }
            .toList()
            .let { sfccs: List<FieldComponentContext> -> columnName to sfccs }
    }

    private fun createSequenceOfDataElementQueryComponentContexts(
        tabularQuery: TabularQuery
    ): (TabularQueryTraversalContext) -> TabularQueryTraversalContext {
        return { tqcc: TabularQueryTraversalContext ->
            tqcc.dataElementFieldComponentContextsByOutputColumnName.values
                .asSequence()
                .flatMap(ImmutableList<FieldComponentContext>::asSequence)
                .fold(
                    persistentMapOf<GQLOperationPath, PersistentSet<GQLOperationPath>>() to
                        persistentMapOf<GQLOperationPath, FieldComponentContext>()
                ) { (childrenByParentPaths, dataElementContextsByPath), sfcc ->
                    updateChildPathsByParentPathMapWithPath(childrenByParentPaths, sfcc.path) to
                        dataElementContextsByPath.put(sfcc.path, sfcc)
                }
                .fold { childrenByParentPaths, dataElementContextsByPath ->
                    createQueryComponentContextsForEachDataElementPath(
                        tabularQuery,
                        tqcc,
                        dataElementContextsByPath,
                        childrenByParentPaths
                    )
                }
        }
    }

    private fun updateChildPathsByParentPathMapWithPath(
        childPathsByParentPathMap: PersistentMap<GQLOperationPath, PersistentSet<GQLOperationPath>>,
        nextPath: GQLOperationPath,
    ): PersistentMap<GQLOperationPath, PersistentSet<GQLOperationPath>> {
        return (childPathsByParentPathMap to nextPath)
            .toOption()
            .filter { (pm, p) -> p !in pm }
            .map { (pm, p) -> pm.put(p, pm.getOrElse(p, ::persistentSetOf)) to p }
            .recurse { (pm, p) ->
                when (val pp: GQLOperationPath? = p.getParentPath().orNull()) {
                    null -> {
                        pm.right().some()
                    }
                    in pm -> {
                        pm.put(pp, pm.getOrElse(pp, ::persistentSetOf).add(p)).right().some()
                    }
                    else -> {
                        (pm.put(pp, pm.getOrElse(pp, ::persistentSetOf).add(p)) to pp).left().some()
                    }
                }
            }
            .getOrElse { childPathsByParentPathMap }
    }

    private fun createQueryComponentContextsForEachDataElementPath(
        tabularQuery: TabularQuery,
        context: TabularQueryTraversalContext,
        dataElementContextsByPath: PersistentMap<GQLOperationPath, FieldComponentContext>,
        childrenByParentPaths: PersistentMap<GQLOperationPath, PersistentSet<GQLOperationPath>>,
    ): TabularQueryTraversalContext {
        return sequenceOf(tabularQuery.materializationMetamodel.dataElementElementTypePath)
            .recurseBreadthFirst { p: GQLOperationPath ->
                sequenceOf(p.right())
                    .plus(
                        childrenByParentPaths
                            .getOrNone(p)
                            .sequence()
                            .flatMap(ImmutableSet<GQLOperationPath>::asSequence)
                            .map(GQLOperationPath::left)
                    )
            }
            .flatMap { p: GQLOperationPath ->
                when (p) {
                    tabularQuery.materializationMetamodel.dataElementElementTypePath -> {
                        sequenceOf(
                            tabularQuery.queryComponentContextFactory
                                .fieldComponentContextBuilder()
                                .fieldCoordinates(
                                    tabularQuery.materializationMetamodel.featureEngineeringModel
                                        .dataElementFieldCoordinates
                                )
                                .field(
                                    Field.newField()
                                        .name(
                                            tabularQuery.materializationMetamodel
                                                .featureEngineeringModel
                                                .dataElementFieldCoordinates
                                                .fieldName
                                        )
                                        .build()
                                )
                                .canonicalPath(
                                    tabularQuery.materializationMetamodel.dataElementElementTypePath
                                )
                                .path(
                                    tabularQuery.materializationMetamodel.dataElementElementTypePath
                                )
                                .build()
                        )
                    }
                    in tabularQuery.materializationMetamodel
                        .domainSpecifiedDataElementSourceByPath -> {
                        tabularQuery.materializationMetamodel.domainSpecifiedDataElementSourceByPath
                            .getOrNone(p)
                            .sequence()
                            .flatMap { dsdes: DomainSpecifiedDataElementSource ->
                                sequenceOf(
                                        tabularQuery.queryComponentContextFactory
                                            .fieldComponentContextBuilder()
                                            .fieldCoordinates(dsdes.domainFieldCoordinates)
                                            .path(p)
                                            .canonicalPath(dsdes.domainPath)
                                            .field(
                                                Field.newField()
                                                    .name(dsdes.domainFieldCoordinates.fieldName)
                                                    .build()
                                            )
                                            .build()
                                    )
                                    .plus(
                                        dsdes.allArgumentsByPath.asSequence().map {
                                            (ap: GQLOperationPath, ga: GraphQLArgument) ->
                                            tabularQuery.queryComponentContextFactory
                                                .argumentComponentContextBuilder()
                                                .argument(
                                                    Argument.newArgument()
                                                        .name(ga.name)
                                                        .value(
                                                            extractVariableReferenceOrDefaultValueForArgument(
                                                                tabularQuery,
                                                                dsdes.domainFieldCoordinates,
                                                                ga
                                                            )
                                                        )
                                                        .build()
                                                )
                                                .fieldCoordinates(dsdes.domainFieldCoordinates)
                                                .path(ap)
                                                .canonicalPath(ap)
                                                .build()
                                        }
                                    )
                            }
                    }
                    in dataElementContextsByPath -> {
                        dataElementContextsByPath.getOrNone(p).sequence()
                    }
                    else -> {
                        tabularQuery.materializationMetamodel.fieldCoordinatesByPath
                            .getOrNone(p)
                            .sequence()
                            .flatMap(ImmutableSet<FieldCoordinates>::asSequence)
                            .firstOrNone()
                            .map { fc: FieldCoordinates ->
                                tabularQuery.queryComponentContextFactory
                                    .fieldComponentContextBuilder()
                                    .path(p)
                                    // TODO: This is not the canonical path in some cases; Modify
                                    // logic accordingly
                                    .canonicalPath(p)
                                    .fieldCoordinates(fc)
                                    .field(Field.newField().name(fc.fieldName).build())
                                    .build()
                            }
                            .sequence()
                    }
                }
            }
            .fold(
                persistentListOf<QueryComponentContext>(),
                PersistentList<QueryComponentContext>::add
            )
            .let { qccs: List<QueryComponentContext> ->
                context.update { addAllQueryComponentContexts(qccs) }
            }
    }

    private fun extractVariableReferenceOrDefaultValueForArgument(
        tabularQuery: TabularQuery,
        fieldCoordinates: FieldCoordinates,
        graphQLArgument: GraphQLArgument,
    ): Value<*> {
        // TODO: Consider whether to permit aliases on field_names referenced in raw_input_context
        return fieldCoordinates.fieldName
            .toOption()
            .filter(tabularQuery.rawInputContextKeys::contains)
            .map { fn: String ->
                VariableReference.newVariableReference().name(graphQLArgument.name).build()
            }
            .orElse {
                graphQLArgument.name.toOption().filter(tabularQuery.variableKeys::contains).map {
                    n: String ->
                    VariableReference.newVariableReference().name(n).build()
                }
            }
            .orElse {
                graphQLArgument.name
                    .toOption()
                    .map { n: String ->
                        tabularQuery.materializationMetamodel.aliasCoordinatesRegistry
                            .getAllAliasesForFieldArgument(fieldCoordinates to n)
                    }
                    .map(ImmutableSet<String>::asSequence)
                    .fold(::emptySequence, ::identity)
                    .filter(tabularQuery.variableKeys::contains)
                    .firstOrNone()
                    .map { n: String -> VariableReference.newVariableReference().name(n).build() }
            }
            .orElse {
                graphQLArgument
                    .toOption()
                    .filter(GraphQLArgument::hasSetDefaultValue)
                    .mapNotNull { ga: GraphQLArgument -> ga.argumentDefaultValue }
                    .filter(InputValueWithState::isLiteral)
                    .mapNotNull(InputValueWithState::getValue)
                    .filterIsInstance<Value<*>>()
            }
            .successIfDefined {
                ServiceError.of(
                    "unable to extract argument value variable reference or default value for [ argument.name: %s ]",
                    graphQLArgument.name
                )
            }
            .orElseThrow()
    }

    private fun createSequenceOfFeatureQueryComponentContexts(
        tabularQuery: TabularQuery
    ): (TabularQueryTraversalContext) -> TabularQueryTraversalContext {
        return { tqcc: TabularQueryTraversalContext ->
            tqcc.featurePathByExpectedOutputColumnName.values
                .asSequence()
                .fold(persistentMapOf<GQLOperationPath, PersistentSet<GQLOperationPath>>()) {
                    childrenByParentPath,
                    featurePath ->
                    updateChildPathsByParentPathMapWithPath(childrenByParentPath, featurePath)
                }
                .let { childrenByParentPath ->
                    createQueryComponentContextsForEachFeaturePath(
                        tabularQuery,
                        tqcc,
                        childrenByParentPath
                    )
                }
        }
    }

    private fun createQueryComponentContextsForEachFeaturePath(
        tabularQuery: TabularQuery,
        context: TabularQueryTraversalContext,
        childrenByParentPaths: PersistentMap<GQLOperationPath, PersistentSet<GQLOperationPath>>
    ): TabularQueryTraversalContext {
        return sequenceOf(tabularQuery.materializationMetamodel.featureElementTypePath)
            .recurseBreadthFirst { p: GQLOperationPath ->
                sequenceOf(p.right())
                    .plus(
                        childrenByParentPaths
                            .getOrNone(p)
                            .sequence()
                            .flatMap(ImmutableSet<GQLOperationPath>::asSequence)
                            .map(GQLOperationPath::left)
                    )
            }
            .flatMap { p: GQLOperationPath ->
                when (p) {
                    tabularQuery.materializationMetamodel.featureElementTypePath -> {
                        sequenceOf(
                            tabularQuery.queryComponentContextFactory
                                .fieldComponentContextBuilder()
                                .canonicalPath(p)
                                .path(p)
                                .fieldCoordinates(
                                    tabularQuery.materializationMetamodel.featureEngineeringModel
                                        .featureFieldCoordinates
                                )
                                .field(
                                    Field.newField()
                                        .name(
                                            tabularQuery.materializationMetamodel
                                                .featureEngineeringModel
                                                .featureFieldCoordinates
                                                .fieldName
                                        )
                                        .build()
                                )
                                .build()
                        )
                    }
                    in tabularQuery.materializationMetamodel
                        .featureSpecifiedFeatureCalculatorsByPath -> {
                        tabularQuery.materializationMetamodel
                            .featureSpecifiedFeatureCalculatorsByPath
                            .getOrNone(p)
                            .sequence()
                            .flatMap { fsfc: FeatureSpecifiedFeatureCalculator ->
                                sequenceOf(
                                        tabularQuery.queryComponentContextFactory
                                            .fieldComponentContextBuilder()
                                            .field(Field.newField().name(fsfc.featureName).build())
                                            .fieldCoordinates(fsfc.featureFieldCoordinates)
                                            .path(fsfc.featurePath)
                                            .canonicalPath(fsfc.featurePath)
                                            .build()
                                    )
                                    .plus(
                                        fsfc.argumentsByPath.asSequence().map {
                                            (ap: GQLOperationPath, ga: GraphQLArgument) ->
                                            tabularQuery.queryComponentContextFactory
                                                .argumentComponentContextBuilder()
                                                .argument(
                                                    Argument.newArgument()
                                                        .name(ga.name)
                                                        .value(extractDefaultValueForArgument(ga))
                                                        .build()
                                                )
                                                .fieldCoordinates(fsfc.featureFieldCoordinates)
                                                .path(ap)
                                                .canonicalPath(ap)
                                                .build()
                                        }
                                    )
                            }
                    }
                    else -> {
                        tabularQuery.materializationMetamodel.fieldCoordinatesByPath
                            .getOrNone(p)
                            .map(ImmutableSet<FieldCoordinates>::asSequence)
                            .fold(::emptySequence, ::identity)
                            .firstOrNone()
                            .map { fc: FieldCoordinates ->
                                tabularQuery.queryComponentContextFactory
                                    .fieldComponentContextBuilder()
                                    .field(Field.newField().name(fc.fieldName).build())
                                    .fieldCoordinates(fc)
                                    .canonicalPath(p)
                                    .path(p)
                                    .build()
                            }
                            .sequence()
                    }
                }
            }
            .toPersistentList()
            .let { qcccs: List<QueryComponentContext> ->
                context.update { addAllQueryComponentContexts(qcccs) }
            }
    }

    private fun extractDefaultValueForArgument(graphQLArgument: GraphQLArgument): Value<*> {
        return graphQLArgument
            .toOption()
            .filter(GraphQLArgument::hasSetDefaultValue)
            .mapNotNull { ga: GraphQLArgument -> ga.argumentDefaultValue }
            .filter(InputValueWithState::isLiteral)
            .mapNotNull(InputValueWithState::getValue)
            .filterIsInstance<Value<*>>()
            .successIfDefined {
                ServiceError.of(
                    """default literal value [ type: %s ] could 
                    |not be found for argument [ name: %s ]"""
                        .flatten(),
                    Value::class.qualifiedName,
                    graphQLArgument.name
                )
            }
            .orElseThrow()
    }

    private fun combineDataElementAndFeatureQueryComponentContextsIntoIterable():
        (TabularQueryTraversalContext) -> Iterable<QueryComponentContext> {
        return { tqcc: TabularQueryTraversalContext -> tqcc.queryComponentContexts }
    }

    private interface TabularQueryTraversalContext {
        val rawInputContextKeysByDataElementSourcePath: ImmutableMap<GQLOperationPath, String>
        val passthruRawInputContextKeys: ImmutableSet<String>
        val argumentPathSetsForVariables: ImmutableMap<String, ImmutableSet<GQLOperationPath>>
        val domainDataElementSourcePathsForVariables: ImmutableSet<GQLOperationPath>
        val featurePathByExpectedOutputColumnName: ImmutableMap<String, GQLOperationPath>
        val dataElementFieldCoordinatesByExpectedOutputColumnName:
            ImmutableMap<String, ImmutableSet<FieldCoordinates>>
        val selectedPassthruColumns: ImmutableSet<String>
        val dataElementFieldComponentContextsByOutputColumnName:
            ImmutableMap<String, ImmutableList<FieldComponentContext>>
        val queryComponentContexts: ImmutableList<QueryComponentContext>
        val errors: ImmutableList<ServiceError>

        fun update(transformer: Builder.() -> Builder): TabularQueryTraversalContext

        interface Builder {

            fun putRawInputContextKeyForDataElementSourcePath(
                dataElementSourcePath: GQLOperationPath,
                rawInputContextKey: String
            ): Builder

            fun addPassthruRawInputContextKey(passthruRawInputContextKey: String): Builder

            fun putArgumentPathSetForVariable(
                variableKey: String,
                argumentPathSet: Set<GQLOperationPath>
            ): Builder

            fun addDomainDataElementSourcePathForCompleteVariableArgumentSet(
                domainDataElementPath: GQLOperationPath
            ): Builder

            fun putAllDomainDataElementSourcePathsForCompleteVariableArgumentSet(
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

            fun addSelectedPassthruColumn(selectedPassthruColumnName: String): Builder

            fun putSelectedFieldComponentContextsForOutputColumnName(
                columnName: String,
                fieldComponentContexts: List<FieldComponentContext>
            ): Builder

            fun addAllQueryComponentContexts(
                queryComponentContexts: List<QueryComponentContext>
            ): Builder

            fun addQueryComponentContext(queryComponentContext: QueryComponentContext): Builder

            fun addError(serviceError: ServiceError): Builder

            fun build(): TabularQueryTraversalContext
        }
    }

    private class DefaultTabularQueryTraversalContext(
        override val rawInputContextKeysByDataElementSourcePath:
            PersistentMap<GQLOperationPath, String>,
        override val passthruRawInputContextKeys: PersistentSet<String>,
        override val argumentPathSetsForVariables:
            PersistentMap<String, PersistentSet<GQLOperationPath>>,
        override val domainDataElementSourcePathsForVariables: PersistentSet<GQLOperationPath>,
        override val featurePathByExpectedOutputColumnName: PersistentMap<String, GQLOperationPath>,
        override val dataElementFieldCoordinatesByExpectedOutputColumnName:
            PersistentMap<String, PersistentSet<FieldCoordinates>>,
        override val selectedPassthruColumns: PersistentSet<String>,
        override val dataElementFieldComponentContextsByOutputColumnName:
            PersistentMap<String, PersistentList<FieldComponentContext>>,
        override val queryComponentContexts: PersistentList<QueryComponentContext>,
        override val errors: PersistentList<ServiceError>,
    ) : TabularQueryTraversalContext {
        companion object {
            fun empty(): TabularQueryTraversalContext {
                return DefaultTabularQueryTraversalContext(
                    rawInputContextKeysByDataElementSourcePath = persistentMapOf(),
                    passthruRawInputContextKeys = persistentSetOf(),
                    argumentPathSetsForVariables = persistentMapOf(),
                    domainDataElementSourcePathsForVariables = persistentSetOf(),
                    featurePathByExpectedOutputColumnName = persistentMapOf(),
                    dataElementFieldCoordinatesByExpectedOutputColumnName = persistentMapOf(),
                    selectedPassthruColumns = persistentSetOf(),
                    dataElementFieldComponentContextsByOutputColumnName = persistentMapOf(),
                    queryComponentContexts = persistentListOf(),
                    errors = persistentListOf()
                )
            }

            private class DefaultBuilder(
                private val existingContext: DefaultTabularQueryTraversalContext,
                private val rawInputContextKeysByDataElementSourcePath:
                    PersistentMap.Builder<GQLOperationPath, String> =
                    existingContext.rawInputContextKeysByDataElementSourcePath.builder(),
                private val passthruRawInputContextKeys: PersistentSet.Builder<String> =
                    existingContext.passthruRawInputContextKeys.builder(),
                private val argumentPathSetsForVariables:
                    PersistentMap.Builder<String, PersistentSet<GQLOperationPath>> =
                    existingContext.argumentPathSetsForVariables.builder(),
                private val domainDataElementSourcePathsForCompleteVariableArgumentSets:
                    PersistentSet.Builder<GQLOperationPath> =
                    existingContext.domainDataElementSourcePathsForVariables.builder(),
                private val featurePathByExpectedOutputColumnName:
                    PersistentMap.Builder<String, GQLOperationPath> =
                    existingContext.featurePathByExpectedOutputColumnName.builder(),
                private val dataElementFieldCoordinatesByExpectedOutputColumnName:
                    PersistentMap.Builder<String, PersistentSet<FieldCoordinates>> =
                    existingContext.dataElementFieldCoordinatesByExpectedOutputColumnName.builder(),
                private val selectedPassthruColumns: PersistentSet.Builder<String> =
                    existingContext.selectedPassthruColumns.builder(),
                private val dataElementFieldComponentContextsByOutputColumnName:
                    PersistentMap.Builder<String, PersistentList<FieldComponentContext>> =
                    existingContext.dataElementFieldComponentContextsByOutputColumnName.builder(),
                private val queryComponentContexts: PersistentList.Builder<QueryComponentContext> =
                    existingContext.queryComponentContexts.builder(),
                private val errors: PersistentList.Builder<ServiceError> =
                    existingContext.errors.builder()
            ) : Builder {

                override fun putRawInputContextKeyForDataElementSourcePath(
                    dataElementSourcePath: GQLOperationPath,
                    rawInputContextKey: String,
                ): Builder =
                    this.apply {
                        this.rawInputContextKeysByDataElementSourcePath.put(
                            dataElementSourcePath,
                            rawInputContextKey
                        )
                    }

                override fun addPassthruRawInputContextKey(
                    passthruRawInputContextKey: String
                ): Builder =
                    this.apply { this.passthruRawInputContextKeys.add(passthruRawInputContextKey) }

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

                override fun addDomainDataElementSourcePathForCompleteVariableArgumentSet(
                    domainDataElementPath: GQLOperationPath
                ): Builder =
                    this.apply {
                        this.domainDataElementSourcePathsForCompleteVariableArgumentSets.add(
                            domainDataElementPath
                        )
                    }

                override fun putAllDomainDataElementSourcePathsForCompleteVariableArgumentSet(
                    domainDataElementSourcePaths: Set<GQLOperationPath>
                ): Builder =
                    this.apply {
                        this.domainDataElementSourcePathsForCompleteVariableArgumentSets.addAll(
                            domainDataElementSourcePaths
                        )
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

                override fun addSelectedPassthruColumn(
                    selectedPassthruColumnName: String
                ): Builder =
                    this.apply { this.selectedPassthruColumns.add(selectedPassthruColumnName) }

                override fun putSelectedFieldComponentContextsForOutputColumnName(
                    columnName: String,
                    fieldComponentContexts: List<FieldComponentContext>
                ): Builder =
                    this.apply {
                        this.dataElementFieldComponentContextsByOutputColumnName.put(
                            columnName,
                            this.dataElementFieldComponentContextsByOutputColumnName
                                .getOrElse(columnName, ::persistentListOf)
                                .addAll(fieldComponentContexts)
                        )
                    }

                override fun addAllQueryComponentContexts(
                    queryComponentContexts: List<QueryComponentContext>
                ): Builder =
                    this.apply { this.queryComponentContexts.addAll(queryComponentContexts) }

                override fun addQueryComponentContext(
                    queryComponentContext: QueryComponentContext
                ): Builder = this.apply { this.queryComponentContexts.add(queryComponentContext) }

                override fun build(): TabularQueryTraversalContext {
                    return DefaultTabularQueryTraversalContext(
                        rawInputContextKeysByDataElementSourcePath =
                            rawInputContextKeysByDataElementSourcePath.build(),
                        passthruRawInputContextKeys = passthruRawInputContextKeys.build(),
                        argumentPathSetsForVariables = argumentPathSetsForVariables.build(),
                        domainDataElementSourcePathsForVariables =
                            domainDataElementSourcePathsForCompleteVariableArgumentSets.build(),
                        featurePathByExpectedOutputColumnName =
                            featurePathByExpectedOutputColumnName.build(),
                        dataElementFieldCoordinatesByExpectedOutputColumnName =
                            dataElementFieldCoordinatesByExpectedOutputColumnName.build(),
                        selectedPassthruColumns = selectedPassthruColumns.build(),
                        dataElementFieldComponentContextsByOutputColumnName =
                            dataElementFieldComponentContextsByOutputColumnName.build(),
                        queryComponentContexts = queryComponentContexts.build(),
                        errors = errors.build()
                    )
                }
            }
        }

        override fun update(transformer: Builder.() -> Builder): TabularQueryTraversalContext {
            return transformer.invoke(DefaultBuilder(this)).build()
        }
    }
}
