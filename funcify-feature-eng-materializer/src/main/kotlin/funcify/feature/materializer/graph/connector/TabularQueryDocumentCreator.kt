package funcify.feature.materializer.graph.connector

import arrow.core.*
import funcify.feature.error.ServiceError
import funcify.feature.materializer.graph.connector.TabularQueryDocumentCreator.Companion.TabularQueryCompositionContext.*
import funcify.feature.materializer.graph.context.TabularQuery
import funcify.feature.schema.dataelement.DomainSpecifiedDataElementSource
import funcify.feature.schema.document.GQLDocumentComposer
import funcify.feature.schema.document.GQLDocumentSpec
import funcify.feature.schema.document.GQLDocumentSpecFactory
import funcify.feature.schema.feature.FeatureSpecifiedFeatureCalculator
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.OptionExtensions.sequence
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentSetValueMap
import funcify.feature.tools.extensions.SequenceExtensions.firstOrNone
import funcify.feature.tools.extensions.SequenceExtensions.flatMapOptions
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
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
    private val documentSpecFactory: GQLDocumentSpecFactory,
    private val gqlDocumentComposer: GQLDocumentComposer
) {

    companion object {
        private const val METHOD_TAG: String = "create_document_for_tabular_query"
        private val logger: Logger = loggerFor<TabularQueryDocumentCreator>()

        private interface TabularQueryCompositionContext {
            val rawInputContextKeysByDataElementSourcePath: ImmutableMap<GQLOperationPath, String>
            val rawInputContextDataElementSourceArgumentPathsForVariablesToCreate:
                ImmutableMap<String, ImmutableSet<GQLOperationPath>>
            val passthruRawInputContextKeys: ImmutableSet<String>
            val anyArgumentPathSetsMatchingVariables:
                ImmutableMap<String, ImmutableSet<GQLOperationPath>>
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
            val errors: ImmutableList<ServiceError>

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

                fun putArgumentPathSetMatchingVariableKey(
                    variableKey: String,
                    argumentPathSet: Set<GQLOperationPath>
                ): Builder

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

                fun addError(serviceError: ServiceError): Builder

                fun build(): TabularQueryCompositionContext
            }
        }

        private class DefaultTabularQueryCompositionContext(
            override val rawInputContextKeysByDataElementSourcePath:
                PersistentMap<GQLOperationPath, String>,
            override val rawInputContextDataElementSourceArgumentPathsForVariablesToCreate:
                PersistentMap<String, PersistentSet<GQLOperationPath>>,
            override val passthruRawInputContextKeys: PersistentSet<String>,
            override val anyArgumentPathSetsMatchingVariables:
                PersistentMap<String, PersistentSet<GQLOperationPath>>,
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
                PersistentMap<String, PersistentSet<GQLOperationPath>>,
            override val errors: PersistentList<ServiceError>,
        ) : TabularQueryCompositionContext {

            companion object {
                fun empty(): TabularQueryCompositionContext {
                    return DefaultTabularQueryCompositionContext(
                        rawInputContextKeysByDataElementSourcePath = persistentMapOf(),
                        rawInputContextDataElementSourceArgumentPathsForVariablesToCreate =
                            persistentMapOf(),
                        passthruRawInputContextKeys = persistentSetOf(),
                        anyArgumentPathSetsMatchingVariables = persistentMapOf(),
                        argumentPathSetsForVariableKeysForDomainDataElementSourcesToSupport =
                            persistentMapOf(),
                        domainDataElementSourcePathsWithCompleteVariableArgumentSets =
                            persistentSetOf(),
                        featurePathByExpectedOutputColumnName = persistentMapOf(),
                        dataElementFieldCoordinatesByExpectedOutputColumnName = persistentMapOf(),
                        selectedPassthruColumns = persistentSetOf(),
                        dataElementPathsByExpectedOutputColumnName = persistentMapOf(),
                        errors = persistentListOf()
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
                    private val anyArgumentPathSetsMatchingVariables:
                        PersistentMap.Builder<String, PersistentSet<GQLOperationPath>> =
                        existingContext.anyArgumentPathSetsMatchingVariables.builder(),
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
                        existingContext.dataElementPathsByExpectedOutputColumnName.builder(),
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

                    override fun putArgumentPathSetMatchingVariableKey(
                        variableKey: String,
                        argumentPathSet: Set<GQLOperationPath>,
                    ): Builder =
                        this.apply {
                            this.anyArgumentPathSetsMatchingVariables.put(
                                variableKey,
                                this.anyArgumentPathSetsMatchingVariables
                                    .getOrElse(variableKey, ::persistentSetOf)
                                    .addAll(argumentPathSet)
                            )
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
                            anyArgumentPathSetsMatchingVariables =
                                anyArgumentPathSetsMatchingVariables.build(),
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
                                dataElementPathsByExpectedOutputColumnName.build(),
                            errors = errors.build()
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

    fun createDocumentForTabularQuery(tabularQuery: TabularQuery): Document {
        logger.info(
            "{}: [ tabular_query.variable_keys.size: {}, tabular_query.raw_input_context_keys.size: {} ]",
            METHOD_TAG,
            tabularQuery.variableKeys.size,
            tabularQuery.rawInputContextKeys.size
        )
        // TODO: Impose rule that no data element may share the same name as a feature
        return Try.success(DefaultTabularQueryCompositionContext.empty())
            .map(matchRawInputContextKeysWithDomainSpecifiedDataElementSources(tabularQuery))
            .map(matchVariableKeysWithDomainSpecifiedDataElementSourceArguments(tabularQuery))
            .map(addAllDomainDataElementsWithCompleteVariableKeyArgumentSets(tabularQuery))
            .filter(contextDoesNotContainErrors(), createAggregateErrorFromContext())
            .map(matchExpectedOutputColumnNamesToFeaturePathsOrDataElementCoordinates(tabularQuery))
            .filter(contextDoesNotContainErrors(), createAggregateErrorFromContext())
            .map(connectDataElementCoordinatesToPathsUnderSupportedSources(tabularQuery))
            .filter(contextDoesNotContainErrors(), createAggregateErrorFromContext())
            .flatMap(createDocumentFromContext(tabularQuery))
            .peek(logSuccess(), logFailure())
            .orElseThrow()
    }

    private fun matchRawInputContextKeysWithDomainSpecifiedDataElementSources(
        tabularQuery: TabularQuery
    ): (TabularQueryCompositionContext) -> TabularQueryCompositionContext {
        return { tqcc: TabularQueryCompositionContext ->
            runCatching {
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
                                    dsdes.allArgumentsWithoutDefaultValuesByPath
                                        .asSequence()
                                        .map { (ap: GQLOperationPath, ga: GraphQLArgument) ->
                                            ga.name to ap
                                        }
                                        .forEach { (vkToCreate: String, ap: GQLOperationPath) ->
                                            cb
                                                .putRawInputContextDataElementArgumentForVariableToCreate(
                                                    vkToCreate,
                                                    ap
                                                )
                                        }
                                    cb.putRawInputContextKeyForDataElementSourcePath(
                                        dsdes.domainPath,
                                        k
                                    )
                                }
                            }
                        }
                    }
                }
                .fold(::identity) { t: Throwable ->
                    tqcc.update {
                        addError(
                            when (t) {
                                is ServiceError -> t
                                else ->
                                    ServiceError.builder()
                                        .message(
                                            "error occurred when matching raw input context key"
                                        )
                                        .cause(t)
                                        .build()
                            }
                        )
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
        return tabularQuery.materializationMetamodel.materializationGraphQLSchema
            .getFieldDefinition(
                tabularQuery.materializationMetamodel.featureEngineeringModel
                    .dataElementFieldCoordinates
            )
            .toOption()
            .mapNotNull(GraphQLFieldDefinition::getType)
            .mapNotNull(GraphQLTypeUtil::unwrapAll)
            .filterIsInstance<GraphQLNamedOutputType>()
            .mapNotNull(GraphQLNamedOutputType::getName)
            .successIfDefined {
                ServiceError.of(
                    """data element type name for field_definition at 
                    |[ coordinates: %s ] expected but not found in schema"""
                        .flatten(),
                    tabularQuery.materializationMetamodel.featureEngineeringModel
                        .dataElementFieldCoordinates
                )
            }
            .map { tn: String -> FieldCoordinates.coordinates(tn, rawInputContextKey) }
            .map(
                tabularQuery.materializationMetamodel
                    .domainSpecifiedDataElementSourceByCoordinates::getOrNone
            )
            .orElseThrow()
    }

    private fun matchVariableKeysWithDomainSpecifiedDataElementSourceArguments(
        tabularQuery: TabularQuery
    ): (TabularQueryCompositionContext) -> TabularQueryCompositionContext {
        return { tqcc: TabularQueryCompositionContext ->
            tqcc
                .update {
                    tabularQuery.variableKeys.asSequence().fold(this) {
                        cb: TabularQueryCompositionContext.Builder,
                        vk: String ->
                        when (
                            val argumentPathSet: ImmutableSet<GQLOperationPath>? =
                                matchVariableKeyToDataElementArgumentPaths(tabularQuery, vk)
                                    .orNull()
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
                                cb.putArgumentPathSetMatchingVariableKey(vk, argumentPathSet)
                            }
                        }
                    }
                }
                .also { t ->
                    logger.debug(
                        "{}: [ status: matching variables to domain_specified_data_element_source_arguments ][ {} ]",
                        METHOD_TAG,
                        t.anyArgumentPathSetsMatchingVariables.asSequence().joinToString(",\n") {
                            (v, ps) ->
                            "$v: ${ps.joinToString(", ", "{ ", " }")}"
                        }
                    )
                }
        }
    }

    private fun matchVariableKeyToDataElementArgumentPaths(
        tabularQuery: TabularQuery,
        variableKey: String
    ): Option<ImmutableSet<GQLOperationPath>> {
        // TODO: Consider whether to flip these, assessing for aliases preset before standard field
        // names
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

    private fun contextDoesNotContainErrors(): (TabularQueryCompositionContext) -> Boolean {
        return { tqcc: TabularQueryCompositionContext -> tqcc.errors.isEmpty() }
    }

    private fun createAggregateErrorFromContext(): (TabularQueryCompositionContext) -> Throwable {
        return { tqcc: TabularQueryCompositionContext ->
            ServiceError.builder()
                .message("unable to create tabular query operation")
                .addAllServiceErrorsToHistory(tqcc.errors)
                .build()
        }
    }

    private fun addAllDomainDataElementsWithCompleteVariableKeyArgumentSets(
        tabularQuery: TabularQuery
    ): (TabularQueryCompositionContext) -> TabularQueryCompositionContext {
        return { tqcc: TabularQueryCompositionContext ->
            tqcc.anyArgumentPathSetsMatchingVariables
                .asSequence()
                .flatMap { (vk: String, argPathsSet: Set<GQLOperationPath>) ->
                    argPathsSet.asSequence().flatMap { ap: GQLOperationPath ->
                        ap.getParentPath().sequence().map { pp: GQLOperationPath ->
                            pp to (vk to ap)
                        }
                    }
                }
                .reducePairsToPersistentSetValueMap()
                .asSequence()
                .filter { (pp: GQLOperationPath, vkToAps: Set<Pair<String, GQLOperationPath>>) ->
                    tabularQuery.materializationMetamodel.domainSpecifiedDataElementSourceByPath
                        .getOrNone(pp)
                        .map { dsdes: DomainSpecifiedDataElementSource ->
                            // Must provide all domain arguments or at least those lacking default
                            // argument values
                            // TODO: Convert to cacheable operation
                            val argPathsSet: Set<GQLOperationPath> =
                                vkToAps
                                    .asSequence()
                                    .filter { (_: String, ap: GQLOperationPath) ->
                                        dsdes.domainPath.isParentTo(ap)
                                    }
                                    .map { (_: String, ap: GQLOperationPath) -> ap }
                                    .toSet()
                            dsdes.domainArgumentsWithoutDefaultValuesByPath.asSequence().all {
                                (p: GQLOperationPath, _: GraphQLArgument) ->
                                argPathsSet.contains(p)
                            }
                        }
                        .getOrElse { false }
                }
                .fold(tqcc) {
                    c: TabularQueryCompositionContext,
                    (ddesp: GQLOperationPath, vkaps: Set<Pair<String, GQLOperationPath>>) ->
                    c.update {
                        vkaps.asSequence().reducePairsToPersistentSetValueMap().forEach {
                            (vk: String, ps: Set<GQLOperationPath>) ->
                            putArgumentPathSetsForVariableKeysForDomainDataElementSourcesToSupport(
                                vk,
                                ps
                            )
                        }
                        addDomainDataElementSourcePathWithCompleteVariableArgumentSet(ddesp)
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

    private fun createDocumentFromContext(
        tabularQuery: TabularQuery
    ): (TabularQueryCompositionContext) -> Try<Document> {
        return { tqcc: TabularQueryCompositionContext ->
            tqcc.dataElementPathsByExpectedOutputColumnName.values
                .asSequence()
                .flatten()
                .plus(tqcc.featurePathByExpectedOutputColumnName.values.asSequence())
                .fold(documentSpecFactory.builder()) {
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
                    gqlDocumentComposer.composeDocumentFromSpecWithSchema(
                        spec,
                        tabularQuery.materializationMetamodel.materializationGraphQLSchema
                    )
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
