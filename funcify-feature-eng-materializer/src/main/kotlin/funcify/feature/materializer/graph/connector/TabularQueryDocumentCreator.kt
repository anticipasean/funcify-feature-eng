package funcify.feature.materializer.graph.connector

import arrow.core.*
import funcify.feature.error.ServiceError
import funcify.feature.materializer.context.document.DefaultTabularDocumentContextFactory
import funcify.feature.materializer.context.document.TabularDocumentContext
import funcify.feature.materializer.context.document.TabularDocumentContextFactory
import funcify.feature.materializer.graph.connector.TabularQueryDocumentCreator.Companion.TabularQueryCompositionContext.*
import funcify.feature.materializer.graph.context.TabularQuery
import funcify.feature.materializer.input.context.RawInputContext
import funcify.feature.schema.dataelement.DomainSpecifiedDataElementSource
import funcify.feature.schema.document.GQLDocumentSpec
import funcify.feature.schema.feature.FeatureSpecifiedFeatureCalculator
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.OptionExtensions.sequence
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentSetValueMap
import funcify.feature.tools.extensions.SequenceExtensions.firstOrNone
import funcify.feature.tools.extensions.SequenceExtensions.flatMapOptions
import funcify.feature.tools.extensions.StringExtensions.flatten
import graphql.language.Document
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLNamedOutputType
import graphql.schema.GraphQLTypeUtil
import kotlinx.collections.immutable.*
import org.slf4j.Logger

/**
 * @author smccarron
 * @created 2023-10-13
 */
internal class TabularQueryDocumentCreator(
    private val tabularDocumentContextFactory: TabularDocumentContextFactory =
        DefaultTabularDocumentContextFactory()
) {

    companion object {
        private const val METHOD_TAG: String = "create_document_context_for_tabular_query"
        private val logger: Logger = loggerFor<TabularQueryDocumentCreator>()

        private interface TabularQueryCompositionContext {
            val rawInputContextKeysByDataElementSourcePath: ImmutableMap<GQLOperationPath, String>
            val rawInputContextDataElementSourceArgumentPathsForVariablesToCreate:
                ImmutableMap<String, ImmutableSet<GQLOperationPath>>
            val passthruRawInputContextKeys: ImmutableSet<String>
            val argumentPathSetsForVariableKeysForDomainDataElementSourcesToSupport:
                ImmutableMap<String, ImmutableSet<GQLOperationPath>>
            val domainDataElementSourcePathsWithCompleteVariableArgumentSets:
                ImmutableSet<GQLOperationPath>
            val featurePathByExpectedOutputColumnName: ImmutableMap<String, GQLOperationPath>
            val dataElementFieldCoordinatesByExpectedOutputColumnName:
                ImmutableMap<String, ImmutableSet<FieldCoordinates>>
            val dataElementPathsByExpectedOutputColumnName:
                ImmutableMap<String, ImmutableSet<GQLOperationPath>>
            val selectedPassthruColumns: ImmutableSet<String>

            fun update(transformer: Builder.() -> Builder): TabularQueryCompositionContext

            interface Builder {

                fun putRawInputContextKeyForDataElementSourcePath(
                    dataElementSourcePath: GQLOperationPath,
                    rawInputContextKey: String
                ): Builder

                fun putRawInputContextDataElementArgumentForVariableToCreate(
                    variableToCreate: String,
                    dataElementArgumentPath: GQLOperationPath
                ): Builder

                fun addPassthruRawInputContextKey(passthruRawInputContextKey: String): Builder

                fun putArgumentPathSetsForVariableKeysForDomainDataElementSourcesToSupport(
                    variableKey: String,
                    argumentPathSet: Set<GQLOperationPath>
                ): Builder

                fun addDomainDataElementSourcePathWithCompleteVariableArgumentSet(
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

                fun putDataElementPathsForExpectedOutputColumnName(
                    columnName: String,
                    dataElementPaths: Set<GQLOperationPath>
                ): Builder

                fun build(): TabularQueryCompositionContext
            }
        }

        private class DefaultTabularQueryCompositionContext(
            override val rawInputContextKeysByDataElementSourcePath:
                PersistentMap<GQLOperationPath, String>,
            override val rawInputContextDataElementSourceArgumentPathsForVariablesToCreate:
                PersistentMap<String, PersistentSet<GQLOperationPath>>,
            override val passthruRawInputContextKeys: PersistentSet<String>,
            override val argumentPathSetsForVariableKeysForDomainDataElementSourcesToSupport:
                PersistentMap<String, PersistentSet<GQLOperationPath>>,
            override val domainDataElementSourcePathsWithCompleteVariableArgumentSets:
                PersistentSet<GQLOperationPath>,
            override val featurePathByExpectedOutputColumnName:
                PersistentMap<String, GQLOperationPath>,
            override val dataElementFieldCoordinatesByExpectedOutputColumnName:
                PersistentMap<String, PersistentSet<FieldCoordinates>>,
            override val selectedPassthruColumns: PersistentSet<String>,
            override val dataElementPathsByExpectedOutputColumnName:
                PersistentMap<String, PersistentSet<GQLOperationPath>>
        ) : TabularQueryCompositionContext {

            companion object {
                fun empty(): TabularQueryCompositionContext {
                    return DefaultTabularQueryCompositionContext(
                        rawInputContextKeysByDataElementSourcePath = persistentMapOf(),
                        rawInputContextDataElementSourceArgumentPathsForVariablesToCreate =
                            persistentMapOf(),
                        passthruRawInputContextKeys = persistentSetOf(),
                        argumentPathSetsForVariableKeysForDomainDataElementSourcesToSupport =
                            persistentMapOf(),
                        domainDataElementSourcePathsWithCompleteVariableArgumentSets =
                            persistentSetOf(),
                        featurePathByExpectedOutputColumnName = persistentMapOf(),
                        dataElementFieldCoordinatesByExpectedOutputColumnName = persistentMapOf(),
                        selectedPassthruColumns = persistentSetOf(),
                        dataElementPathsByExpectedOutputColumnName = persistentMapOf()
                    )
                }

                private class DefaultBuilder(
                    private val existingContext: DefaultTabularQueryCompositionContext,
                    private val rawInputContextKeysByDataElementSourcePath:
                        PersistentMap.Builder<GQLOperationPath, String> =
                        existingContext.rawInputContextKeysByDataElementSourcePath.builder(),
                    private val rawInputContextDataElementSourceArgumentPathsForVariablesToCreate:
                        PersistentMap.Builder<String, PersistentSet<GQLOperationPath>> =
                        existingContext
                            .rawInputContextDataElementSourceArgumentPathsForVariablesToCreate
                            .builder(),
                    private val passthruRawInputContextKeys: PersistentSet.Builder<String> =
                        existingContext.passthruRawInputContextKeys.builder(),
                    private val argumentPathSetsForVariableKeysForDomainDataElementSourcesToSupport:
                        PersistentMap.Builder<String, PersistentSet<GQLOperationPath>> =
                        existingContext
                            .argumentPathSetsForVariableKeysForDomainDataElementSourcesToSupport
                            .builder(),
                    private val domainDataElementSourcePathsForCompleteVariableArgumentSets:
                        PersistentSet.Builder<GQLOperationPath> =
                        existingContext.domainDataElementSourcePathsWithCompleteVariableArgumentSets
                            .builder(),
                    private val featurePathByExpectedOutputColumnName:
                        PersistentMap.Builder<String, GQLOperationPath> =
                        existingContext.featurePathByExpectedOutputColumnName.builder(),
                    private val dataElementFieldCoordinatesByExpectedOutputColumnName:
                        PersistentMap.Builder<String, PersistentSet<FieldCoordinates>> =
                        existingContext.dataElementFieldCoordinatesByExpectedOutputColumnName
                            .builder(),
                    private val selectedPassthruColumns: PersistentSet.Builder<String> =
                        existingContext.selectedPassthruColumns.builder(),
                    private val dataElementPathsByExpectedOutputColumnName:
                        PersistentMap.Builder<String, PersistentSet<GQLOperationPath>> =
                        existingContext.dataElementPathsByExpectedOutputColumnName.builder()
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

                    override fun putRawInputContextDataElementArgumentForVariableToCreate(
                        variableToCreate: String,
                        dataElementArgumentPath: GQLOperationPath
                    ): Builder =
                        this.apply {
                            this.rawInputContextDataElementSourceArgumentPathsForVariablesToCreate
                                .put(
                                    variableToCreate,
                                    this
                                        .rawInputContextDataElementSourceArgumentPathsForVariablesToCreate
                                        .getOrElse(variableToCreate, ::persistentSetOf)
                                        .add(dataElementArgumentPath)
                                )
                        }

                    override fun addPassthruRawInputContextKey(
                        passthruRawInputContextKey: String
                    ): Builder =
                        this.apply {
                            this.passthruRawInputContextKeys.add(passthruRawInputContextKey)
                        }

                    override fun putArgumentPathSetsForVariableKeysForDomainDataElementSourcesToSupport(
                        variableKey: String,
                        argumentPathSet: Set<GQLOperationPath>
                    ): Builder =
                        this.apply {
                            this.argumentPathSetsForVariableKeysForDomainDataElementSourcesToSupport
                                .put(
                                    variableKey,
                                    this
                                        .argumentPathSetsForVariableKeysForDomainDataElementSourcesToSupport
                                        .getOrElse(variableKey, ::persistentSetOf)
                                        .addAll(argumentPathSet)
                                )
                        }

                    override fun addDomainDataElementSourcePathWithCompleteVariableArgumentSet(
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

                    override fun putDataElementPathsForExpectedOutputColumnName(
                        columnName: String,
                        dataElementPaths: Set<GQLOperationPath>,
                    ): Builder =
                        this.apply {
                            this.dataElementPathsByExpectedOutputColumnName.put(
                                columnName,
                                this.dataElementPathsByExpectedOutputColumnName
                                    .getOrElse(columnName, ::persistentSetOf)
                                    .addAll(dataElementPaths)
                            )
                        }

                    override fun addSelectedPassthruColumn(
                        selectedPassthruColumnName: String
                    ): Builder =
                        this.apply { this.selectedPassthruColumns.add(selectedPassthruColumnName) }

                    override fun build(): TabularQueryCompositionContext {
                        return DefaultTabularQueryCompositionContext(
                            rawInputContextKeysByDataElementSourcePath =
                                rawInputContextKeysByDataElementSourcePath.build(),
                            rawInputContextDataElementSourceArgumentPathsForVariablesToCreate =
                                rawInputContextDataElementSourceArgumentPathsForVariablesToCreate
                                    .build(),
                            passthruRawInputContextKeys = passthruRawInputContextKeys.build(),
                            argumentPathSetsForVariableKeysForDomainDataElementSourcesToSupport =
                                argumentPathSetsForVariableKeysForDomainDataElementSourcesToSupport
                                    .build(),
                            domainDataElementSourcePathsWithCompleteVariableArgumentSets =
                                domainDataElementSourcePathsForCompleteVariableArgumentSets.build(),
                            featurePathByExpectedOutputColumnName =
                                featurePathByExpectedOutputColumnName.build(),
                            dataElementFieldCoordinatesByExpectedOutputColumnName =
                                dataElementFieldCoordinatesByExpectedOutputColumnName.build(),
                            selectedPassthruColumns = selectedPassthruColumns.build(),
                            dataElementPathsByExpectedOutputColumnName =
                                dataElementPathsByExpectedOutputColumnName.build()
                        )
                    }
                }
            }

            override fun update(
                transformer: Builder.() -> Builder
            ): TabularQueryCompositionContext {
                return transformer.invoke(DefaultBuilder(this)).build()
            }
        }
    }

    fun createDocumentContextForTabularQuery(tabularQuery: TabularQuery): TabularDocumentContext {
        logger.info(
            "{}: [ tabular_query.variable_keys.size: {}, tabular_query.raw_input_context_keys.size: {} ]",
            METHOD_TAG,
            tabularQuery.variableKeys.size,
            tabularQuery.rawInputContextKeys.size
        )
        // TODO: Impose rule that no data element may share the same name as a feature
        return Try.success(DefaultTabularQueryCompositionContext.empty())
            .map(matchRawInputContextKeysWithDomainSpecifiedDataElementSources(tabularQuery))
            .map(addAllDomainDataElementsWithCompleteVariableKeyArgumentSets(tabularQuery))
            .map(matchExpectedOutputColumnNamesToFeaturePathsOrDataElementCoordinates(tabularQuery))
            .map(connectDataElementCoordinatesToPathsUnderSupportedSources(tabularQuery))
            .flatMap(createDocumentContextFromContext(tabularQuery))
            .peek(logSuccess(), logFailure())
            .orElseThrow()
    }

    private fun matchRawInputContextKeysWithDomainSpecifiedDataElementSources(
        tabularQuery: TabularQuery
    ): (TabularQueryCompositionContext) -> TabularQueryCompositionContext {
        return { tqcc: TabularQueryCompositionContext ->
            tqcc.update {
                tabularQuery.rawInputContextKeys.asSequence().fold(this) {
                    cb: TabularQueryCompositionContext.Builder,
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
                            dsdes.domainArgumentsWithoutDefaultValuesByPath
                                .asSequence()
                                .map { (ap: GQLOperationPath, ga: GraphQLArgument) ->
                                    makeRawInputContextVariableNameFromDomainDataElementSourceAndArgument(
                                        dsdes,
                                        ga
                                    ) to ap
                                }
                                .forEach { (varToCreate: String, ap: GQLOperationPath) ->
                                    cb.putRawInputContextDataElementArgumentForVariableToCreate(
                                        varToCreate,
                                        ap
                                    )
                                }
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
        return Try.attemptNullable {
                tabularQuery.materializationMetamodel.materializationGraphQLSchema
                    .getFieldDefinition(
                        tabularQuery.materializationMetamodel.featureEngineeringModel
                            .dataElementFieldCoordinates
                    )
            }
            .orElseGet(::none)
            .mapNotNull(GraphQLFieldDefinition::getType)
            .mapNotNull(GraphQLTypeUtil::unwrapAll)
            .filterIsInstance<GraphQLNamedOutputType>()
            .mapNotNull(GraphQLNamedOutputType::getName)
            .map { tn: String -> FieldCoordinates.coordinates(tn, rawInputContextKey) }
            .flatMap(
                tabularQuery.materializationMetamodel
                    .domainSpecifiedDataElementSourceByCoordinates::getOrNone
            )
    }

    private fun makeRawInputContextVariableNameFromDomainDataElementSourceAndArgument(
        domainSpecifiedDataElementSource: DomainSpecifiedDataElementSource,
        graphQLArgument: GraphQLArgument
    ): String {
        return buildString {
            append(RawInputContext.RAW_INPUT_CONTEXT_VARIABLE_PREFIX)
            append('_')
            append(domainSpecifiedDataElementSource.domainFieldDefinition.name)
            append('_')
            append(graphQLArgument.name)
        }
    }

    private fun addAllDomainDataElementsWithCompleteVariableKeyArgumentSets(
        tabularQuery: TabularQuery
    ): (TabularQueryCompositionContext) -> TabularQueryCompositionContext {
        return { tqcc: TabularQueryCompositionContext ->
            tabularQuery.matchingArgumentPathsToVariableKeyByDomainDataElementPaths
                .asSequence()
                .flatMap { (dp: GQLOperationPath, argVars: Map<GQLOperationPath, String>) ->
                    tabularQuery.materializationMetamodel.domainSpecifiedDataElementSourceByPath
                        .getOrNone(dp)
                        .filter { dsdes: DomainSpecifiedDataElementSource ->
                            dsdes.domainArgumentsWithoutDefaultValuesByPath.keys.containsAll(
                                argVars.keys
                            )
                        }
                        .map { _: DomainSpecifiedDataElementSource -> dp to argVars }
                        .sequence()
                }
                .fold(tqcc) {
                    c: TabularQueryCompositionContext,
                    (dp: GQLOperationPath, argVars: ImmutableMap<GQLOperationPath, String>) ->
                    c.update {
                        argVars
                            .asSequence()
                            .map { (ap, vk) -> vk to ap }
                            .reducePairsToPersistentSetValueMap()
                            .forEach { (vk: String, ps: Set<GQLOperationPath>) ->
                                // TODO: Consider adding reverse input method to DocumentSpec that
                                // takes (argPath, varKey) mapping instead of just (varKey,
                                // Set[argPath]) removing the need for a reduction into a
                                // PersistentMap[P, PersistentSet[V]]
                                putArgumentPathSetsForVariableKeysForDomainDataElementSourcesToSupport(
                                    vk,
                                    ps
                                )
                            }
                        addDomainDataElementSourcePathWithCompleteVariableArgumentSet(dp)
                    }
                }
                .also { t ->
                    logger.debug(
                        "{}: [ status: assessing which domain_specified_data_element_sources have complete argument sets from variables ][ complete domain_specified_data_element_argument_sets: {} ]",
                        METHOD_TAG,
                        t.domainDataElementSourcePathsWithCompleteVariableArgumentSets.joinToString(
                            ", ",
                            "{ ",
                            " }"
                        )
                    )
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
                            throw ServiceError.of(
                                """expected output column [ %s ] does not match 
                                    |feature or data_element name or alias or 
                                    |pass-thru value within raw_input_context"""
                                    .flatten(),
                                cn
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
        return tabularQuery.materializationMetamodel.aliasCoordinatesRegistry
            .getFieldsWithAlias(columnName)
            .toOption()
            .filter(ImmutableSet<FieldCoordinates>::isNotEmpty)
            .orElse {
                tabularQuery.materializationMetamodel.dataElementFieldCoordinatesByFieldName
                    .getOrNone(columnName)
                    .filter(ImmutableSet<FieldCoordinates>::isNotEmpty)
            }
    }

    private fun matchExpectedOutputColumnNameToPassThruRawInputContextKey(
        tabularQueryCompositionContext: TabularQueryCompositionContext,
        columnName: String
    ): Option<String> {
        return when {
            tabularQueryCompositionContext.passthruRawInputContextKeys.contains(columnName) -> {
                columnName.some()
            }
            else -> {
                None
            }
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
                        createPathForEachDomainDataElementSourceUnderWhichColumnAvailable(
                            tqcc,
                            tabularQuery,
                            cn,
                            fcs
                        )
                    }
                    .fold(this) {
                        tb: TabularQueryCompositionContext.Builder,
                        (cn: String, ps: Set<GQLOperationPath>) ->
                        when {
                            ps.isNotEmpty() -> {
                                tb.putDataElementPathsForExpectedOutputColumnName(cn, ps)
                            }
                            else -> {
                                throw ServiceError.of(
                                    """data element with [ column_name: %s ] 
                                        |does not match path under domain data element 
                                        |sources specified through variables 
                                        |in the request"""
                                        .flatten(),
                                    cn
                                )
                            }
                        }
                    }
            }
        }
    }

    private fun createPathForEachDomainDataElementSourceUnderWhichColumnAvailable(
        tabularQueryCompositionContext: TabularQueryCompositionContext,
        tabularQuery: TabularQuery,
        columnName: String,
        coordinates: ImmutableSet<FieldCoordinates>
    ): Pair<String, Set<GQLOperationPath>> {
        return tabularQueryCompositionContext.rawInputContextKeysByDataElementSourcePath.keys
            .asSequence()
            .plus(
                tabularQueryCompositionContext
                    .domainDataElementSourcePathsWithCompleteVariableArgumentSets
            )
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
                        p
                    }
                    else -> {
                        p.transform {
                            dropTailSelectionSegment()
                            aliasedField(columnName, fc.fieldName)
                        }
                    }
                }
            }
            .toSet()
            .let { ps: Set<GQLOperationPath> -> columnName to ps }
    }

    private fun createDocumentContextFromContext(
        tabularQuery: TabularQuery
    ): (TabularQueryCompositionContext) -> Try<TabularDocumentContext> {
        return { tqcc: TabularQueryCompositionContext ->
            tqcc.dataElementPathsByExpectedOutputColumnName.values
                .asSequence()
                .flatten()
                .plus(tqcc.featurePathByExpectedOutputColumnName.values.asSequence())
                .fold(tabularQuery.gqlDocumentSpecFactory.builder()) {
                    sb: GQLDocumentSpec.Builder,
                    p: GQLOperationPath ->
                    sb.addFieldPath(p)
                }
                .let { sb: GQLDocumentSpec.Builder ->
                    sb.putAllArgumentPathsForVariableNames(
                            tqcc.rawInputContextDataElementSourceArgumentPathsForVariablesToCreate
                        )
                        .putAllArgumentPathsForVariableNames(
                            tqcc.argumentPathSetsForVariableKeysForDomainDataElementSourcesToSupport
                        )
                        .build()
                }
                .let { spec: GQLDocumentSpec ->
                    tabularQuery.gqlDocumentComposer.composeDocumentFromSpecWithSchema(
                        spec,
                        tabularQuery.materializationMetamodel.materializationGraphQLSchema
                    )
                }
                .map { d: Document ->
                    tqcc.dataElementPathsByExpectedOutputColumnName
                        .asSequence()
                        .fold(
                            tabularDocumentContextFactory
                                .builder()
                                .document(d)
                                .putAllFeaturePathsForFieldNames(
                                    tqcc.featurePathByExpectedOutputColumnName
                                )
                                .addAllPassThruExpectedOutputFieldNames(
                                    tqcc.selectedPassthruColumns
                                )
                        ) {
                            tdcb: TabularDocumentContext.Builder,
                            (cn: String, deps: ImmutableSet<GQLOperationPath>) ->
                            when {
                                deps.size > 1 -> {
                                    throw ServiceError.of(
                                        """[ expected_output_field_name: %s ] does not uniquely 
                                           |identify a data element in schema; 
                                           |an alias should be created for the 
                                           |intended data element in order to 
                                           |uniquely identify it within tabular queries 
                                           |[ paths: %s ]"""
                                            .flatten(),
                                        cn,
                                        deps.asSequence().joinToString(", ", "{ ", " }")
                                    )
                                }
                                else -> {
                                    tdcb.putDataElementPathForFieldName(cn, deps.first())
                                }
                            }
                        }
                        .build()
                }
        }
    }

    private fun <T> logSuccess(): (T) -> Unit {
        return { _: T -> logger.debug("{}: [ status: successful ]", METHOD_TAG) }
    }

    private fun logFailure(): (Throwable) -> Unit {
        return { t: Throwable ->
            logger.error(
                "{}: [ status: failed ][ message/json: {} ]",
                METHOD_TAG,
                (t as? ServiceError)?.toJsonNode() ?: t.message
            )
        }
    }
}
