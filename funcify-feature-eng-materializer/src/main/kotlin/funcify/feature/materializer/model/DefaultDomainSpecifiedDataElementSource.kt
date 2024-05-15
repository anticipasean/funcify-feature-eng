package funcify.feature.materializer.model

import arrow.core.continuations.eagerEffect
import arrow.core.continuations.ensureNotNull
import arrow.core.filterIsInstance
import arrow.core.getOrElse
import arrow.core.getOrNone
import arrow.core.identity
import arrow.core.lastOrNone
import arrow.core.toOption
import funcify.feature.error.ServiceError
import funcify.feature.schema.dataelement.DataElementSource
import funcify.feature.schema.dataelement.DomainSpecifiedDataElementSource
import funcify.feature.schema.directive.temporal.LastUpdatedCoordinatesRegistry
import funcify.feature.schema.path.operation.FieldSegment
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.tools.extensions.OptionExtensions.sequence
import funcify.feature.tools.extensions.PersistentMapExtensions.reduceEntriesToPersistentMap
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentMap
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentSetValueMap
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentSet
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

internal data class DefaultDomainSpecifiedDataElementSource(
    override val domainFieldCoordinates: FieldCoordinates,
    override val domainPath: GQLOperationPath,
    override val domainFieldDefinition: GraphQLFieldDefinition,
    override val allArgumentsByPath: PersistentMap<GQLOperationPath, GraphQLArgument>,
    override val dataElementSource: DataElementSource,
    override val graphQLSchema: GraphQLSchema,
    override val lastUpdatedCoordinatesRegistry: LastUpdatedCoordinatesRegistry
) : DomainSpecifiedDataElementSource {

    companion object {

        fun builder(): DomainSpecifiedDataElementSource.Builder {
            return DefaultBuilder()
        }

        private class DefaultBuilder(
            private var domainFieldCoordinates: FieldCoordinates? = null,
            private var domainPath: GQLOperationPath? = null,
            private var domainFieldDefinition: GraphQLFieldDefinition? = null,
            private val argumentsByPath: PersistentMap.Builder<GQLOperationPath, GraphQLArgument> =
                persistentMapOf<GQLOperationPath, GraphQLArgument>().builder(),
            private var dataElementSource: DataElementSource? = null,
            private var graphQLSchema: GraphQLSchema? = null,
            private var lastUpdatedCoordinatesRegistry: LastUpdatedCoordinatesRegistry? = null
        ) : DomainSpecifiedDataElementSource.Builder {

            override fun domainFieldCoordinates(
                domainFieldCoordinates: FieldCoordinates
            ): DomainSpecifiedDataElementSource.Builder =
                this.apply { this.domainFieldCoordinates = domainFieldCoordinates }

            override fun domainPath(
                domainPath: GQLOperationPath
            ): DomainSpecifiedDataElementSource.Builder =
                this.apply { this.domainPath = domainPath }

            override fun domainFieldDefinition(
                domainFieldDefinition: GraphQLFieldDefinition
            ): DomainSpecifiedDataElementSource.Builder =
                this.apply { this.domainFieldDefinition = domainFieldDefinition }

            override fun putArgumentForPathWithinDomain(
                argumentPath: GQLOperationPath,
                graphQLArgument: GraphQLArgument
            ): DomainSpecifiedDataElementSource.Builder =
                this.apply { this.argumentsByPath.put(argumentPath, graphQLArgument) }

            override fun dataElementSource(
                dataElementSource: DataElementSource
            ): DomainSpecifiedDataElementSource.Builder =
                this.apply { this.dataElementSource = dataElementSource }

            override fun graphQLSchema(
                graphQLSchema: GraphQLSchema
            ): DomainSpecifiedDataElementSource.Builder =
                this.apply { this.graphQLSchema = graphQLSchema }

            override fun lastUpdatedCoordinatesRegistry(
                lastUpdatedCoordinatesRegistry: LastUpdatedCoordinatesRegistry
            ): DomainSpecifiedDataElementSource.Builder =
                this.apply { this.lastUpdatedCoordinatesRegistry = lastUpdatedCoordinatesRegistry }

            override fun build(): DomainSpecifiedDataElementSource {
                return eagerEffect<String, DomainSpecifiedDataElementSource> {
                        ensureNotNull(domainFieldCoordinates) { "domainFieldCoordinates is null" }
                        ensureNotNull(domainPath) { "domainPath is null" }
                        ensureNotNull(domainFieldDefinition) { "domainFieldDefinition is null" }
                        ensureNotNull(dataElementSource) { "dataElementSource is null" }
                        ensureNotNull(graphQLSchema) { "graphQLSchema is null" }
                        ensure(domainFieldCoordinates!!.fieldName == domainFieldDefinition!!.name) {
                            "domain_field_coordinates.field_name does not match domain_field_definition.name"
                        }
                        ensure(!domainPath!!.containsAliasForField()) {
                            "domain_path cannot be aliased"
                        }
                        ensure(
                            domainPath
                                .toOption()
                                .map(GQLOperationPath::selection)
                                .getOrElse(::emptyList)
                                .lastOrNone()
                                .filterIsInstance<FieldSegment>()
                                .map(FieldSegment::fieldName)
                                .exists(domainFieldCoordinates!!.fieldName::equals)
                        ) {
                            "domain_path[-1].field_name does not match domain_field_coordinates.field_name"
                        }
                        ensure(
                            graphQLSchema!!.getFieldDefinition(domainFieldCoordinates) ==
                                domainFieldDefinition
                        ) {
                            "graphql_schema.get_field_definition(domain_field_coordinates) not equal to domain_field_definition"
                        }
                        ensure(
                            argumentsByPath.asSequence().all {
                                (p: GQLOperationPath, ga: GraphQLArgument) ->
                                p.isDescendentTo(domainPath!!)
                            }
                        ) {
                            "not all arguments within arguments_by_path are descendents of the domain_path [ %s ]"
                                .format(domainPath!!)
                        }
                        ensureNotNull(lastUpdatedCoordinatesRegistry) {
                            "last_updated_coordinates_registry is null"
                        }
                        DefaultDomainSpecifiedDataElementSource(
                            domainFieldCoordinates = domainFieldCoordinates!!,
                            domainPath = domainPath!!,
                            domainFieldDefinition = domainFieldDefinition!!,
                            allArgumentsByPath = argumentsByPath.build(),
                            dataElementSource = dataElementSource!!,
                            graphQLSchema = graphQLSchema!!,
                            lastUpdatedCoordinatesRegistry = lastUpdatedCoordinatesRegistry!!
                        )
                    }
                    .fold(
                        { message: String ->
                            throw ServiceError.of(
                                "unable to create instance of [ type: %s ] due to [ message: %s ]",
                                DomainSpecifiedDataElementSource::class.qualifiedName,
                                message
                            )
                        },
                        ::identity
                    )
            }
        }
    }

    override val domainArgumentsByPath: PersistentMap<GQLOperationPath, GraphQLArgument> by lazy {
        allArgumentsByPath
            .asSequence()
            .filter { (p: GQLOperationPath, _: GraphQLArgument) -> domainPath.isParentTo(p) }
            .reduceEntriesToPersistentMap()
    }

    override val domainArgumentsByName: PersistentMap<String, GraphQLArgument> by lazy {
        domainArgumentsByPath
            .asSequence()
            .map { (_: GQLOperationPath, a: GraphQLArgument) -> a.name to a }
            .reducePairsToPersistentMap()
    }

    override val domainArgumentPathsByName: PersistentMap<String, GQLOperationPath> by lazy {
        domainArgumentsByPath
            .asSequence()
            .map { (p: GQLOperationPath, a: GraphQLArgument) -> a.name to p }
            .reducePairsToPersistentMap()
    }

    override val domainArgumentsWithDefaultValuesByPath:
        PersistentMap<GQLOperationPath, GraphQLArgument> by lazy {
        allArgumentsByPath
            .asSequence()
            .filter { (p: GQLOperationPath, a: GraphQLArgument) ->
                domainPath.isParentTo(p) && a.hasSetDefaultValue()
            }
            .reduceEntriesToPersistentMap()
    }

    override val domainArgumentsWithDefaultValuesByName:
        PersistentMap<String, GraphQLArgument> by lazy {
        domainArgumentsWithDefaultValuesByPath
            .asSequence()
            .map { (_: GQLOperationPath, a: GraphQLArgument) -> a.name to a }
            .reducePairsToPersistentMap()
    }

    override val domainArgumentsWithoutDefaultValuesByPath:
        PersistentMap<GQLOperationPath, GraphQLArgument> by lazy {
        allArgumentsByPath
            .asSequence()
            .filter { (p: GQLOperationPath, a: GraphQLArgument) ->
                domainPath.isParentTo(p) && !a.hasSetDefaultValue()
            }
            .reduceEntriesToPersistentMap()
    }

    override val domainArgumentsWithoutDefaultValuesByName:
        PersistentMap<String, GraphQLArgument> by lazy {
        domainArgumentsWithoutDefaultValuesByPath
            .asSequence()
            .map { (_: GQLOperationPath, a: GraphQLArgument) -> a.name to a }
            .reducePairsToPersistentMap()
    }

    override val allArgumentsWithDefaultValuesByPath:
        PersistentMap<GQLOperationPath, GraphQLArgument> by lazy {
        allArgumentsByPath
            .asSequence()
            .filter { (_: GQLOperationPath, a: GraphQLArgument) -> a.hasSetDefaultValue() }
            .reduceEntriesToPersistentMap()
    }

    override val allArgumentsWithoutDefaultValuesByPath:
        PersistentMap<GQLOperationPath, GraphQLArgument> by lazy {
        allArgumentsByPath
            .asSequence()
            .filter { (_: GQLOperationPath, a: GraphQLArgument) -> !a.hasSetDefaultValue() }
            .reduceEntriesToPersistentMap()
    }

    override fun findPathsForRequiredArgumentsForSelections(
        selections: Set<GQLOperationPath>
    ): Set<GQLOperationPath> {
        return requiredArgsSetCachingCalculator.invoke(selections)
    }

    // TODO: Consider whether definition of required arg needs to be limited to non-nullable data
    // typed args without default values (optional arg definition would then be expanded)
    private val requiredArgsSetCachingCalculator:
        (Set<GQLOperationPath>) -> Set<GQLOperationPath> by lazy {
        val argPathsByParentPath: PersistentMap<GQLOperationPath, PersistentSet<GQLOperationPath>> =
            allArgumentsByPath
                .asSequence()
                .filter { (_: GQLOperationPath, ga: GraphQLArgument) -> !ga.hasSetDefaultValue() }
                .flatMap { (p: GQLOperationPath, _: GraphQLArgument) ->
                    p.getParentPath().map { pp: GQLOperationPath -> pp to p }.sequence()
                }
                .reducePairsToPersistentSetValueMap()
        val cache: ConcurrentMap<Set<GQLOperationPath>, Set<GQLOperationPath>> =
            ConcurrentHashMap();
        { selections: Set<GQLOperationPath> ->
            cache.computeIfAbsent(
                selections.toPersistentSet(),
                argumentSelectionCalculation(domainPath, argPathsByParentPath)
            )
        }
    }

    private fun argumentSelectionCalculation(
        domainPath: GQLOperationPath,
        argPathsByParentPath: PersistentMap<GQLOperationPath, PersistentSet<GQLOperationPath>>
    ): (Set<GQLOperationPath>) -> Set<GQLOperationPath> {
        return { selections: Set<GQLOperationPath> ->
            val visitedPaths: MutableSet<GQLOperationPath> =
                mutableSetOf<GQLOperationPath>().apply { add(domainPath) }
            selections
                .asSequence()
                .fold(
                    argPathsByParentPath
                        .getOrNone(domainPath)
                        .getOrElse(::persistentSetOf)
                        .builder()
                ) {
                    selectedArgumentPaths: PersistentSet.Builder<GQLOperationPath>,
                    selection: GQLOperationPath ->
                    var p: GQLOperationPath = selection
                    while (!p.isRoot() && p !in visitedPaths) {
                        selectedArgumentPaths.addAll(
                            argPathsByParentPath.getOrNone(p).getOrElse(::persistentSetOf)
                        )
                        visitedPaths.add(p)
                        p = p.getParentPath().getOrElse(GQLOperationPath::getRootPath)
                    }
                    selectedArgumentPaths
                }
                .build()
        }
    }

    override fun findPathsForOptionalArgumentsForSelections(
        selections: Set<GQLOperationPath>
    ): Set<GQLOperationPath> {
        return optionalArgsSetCachingCalculator.invoke(selections)
    }

    // TODO: Consider whether definition of optional arg needs to be expanded such that nullable
    // data typed args are considered
    private val optionalArgsSetCachingCalculator:
        (Set<GQLOperationPath>) -> Set<GQLOperationPath> by lazy {
        val argPathsByParentPath: PersistentMap<GQLOperationPath, PersistentSet<GQLOperationPath>> =
            allArgumentsByPath
                .asSequence()
                .filter { (_: GQLOperationPath, ga: GraphQLArgument) -> ga.hasSetDefaultValue() }
                .flatMap { (p: GQLOperationPath, _: GraphQLArgument) ->
                    p.getParentPath().map { pp: GQLOperationPath -> pp to p }.sequence()
                }
                .reducePairsToPersistentSetValueMap()
        val cache: ConcurrentMap<Set<GQLOperationPath>, Set<GQLOperationPath>> =
            ConcurrentHashMap();
        { selections: Set<GQLOperationPath> ->
            cache.computeIfAbsent(
                selections.toPersistentSet(),
                argumentSelectionCalculation(domainPath, argPathsByParentPath)
            )
        }
    }

    override val domainDataElementSourceGraphQLSchema: GraphQLSchema by lazy {
        val newQueryType: GraphQLObjectType =
            graphQLSchema.queryType.transform { gotb: GraphQLObjectType.Builder ->
                gotb.clearFields()
                gotb.field(domainFieldDefinition)
            }
        graphQLSchema.transform { gsb: GraphQLSchema.Builder -> gsb.query(newQueryType) }
    }
}
