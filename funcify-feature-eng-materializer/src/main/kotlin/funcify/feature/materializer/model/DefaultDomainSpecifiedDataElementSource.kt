package funcify.feature.materializer.model

import arrow.core.continuations.eagerEffect
import arrow.core.continuations.ensureNotNull
import arrow.core.filterIsInstance
import arrow.core.getOrElse
import arrow.core.getOrNone
import arrow.core.identity
import arrow.core.lastOrNone
import funcify.feature.error.ServiceError
import funcify.feature.schema.dataelement.DataElementSource
import funcify.feature.schema.dataelement.DomainSpecifiedDataElementSource
import funcify.feature.schema.directive.temporal.LastUpdatedCoordinatesRegistry
import funcify.feature.schema.path.operation.FieldSegment
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.tools.extensions.OptionExtensions.sequence
import funcify.feature.tools.extensions.PersistentMapExtensions.reduceEntriesToPersistentMap
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentMap
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLFieldDefinition
import kotlinx.collections.immutable.ImmutableMap

internal data class DefaultDomainSpecifiedDataElementSource(
    override val domainFieldCoordinates: FieldCoordinates,
    override val domainPath: GQLOperationPath,
    override val domainFieldDefinition: GraphQLFieldDefinition,
    override val dataElementSource: DataElementSource,
    override val lastUpdatedCoordinatesRegistry: LastUpdatedCoordinatesRegistry
) : DomainSpecifiedDataElementSource {

    override val argumentsByPath: ImmutableMap<GQLOperationPath, GraphQLArgument> by lazy {
        domainFieldDefinition.arguments
            .asSequence()
            .map { ga: GraphQLArgument -> domainPath.transform { argument(ga.name) } to ga }
            .reducePairsToPersistentMap()
    }

    override val argumentsByName: ImmutableMap<String, GraphQLArgument> by lazy {
        domainFieldDefinition.arguments
            .asSequence()
            .map { ga: GraphQLArgument -> ga.name to ga }
            .reducePairsToPersistentMap()
    }

    override val argumentsWithDefaultValuesByName: ImmutableMap<String, GraphQLArgument> by lazy {
        domainFieldDefinition.arguments
            .asSequence()
            .filter(GraphQLArgument::hasSetDefaultValue)
            .map { ga: GraphQLArgument -> ga.name to ga }
            .reducePairsToPersistentMap()
    }

    override val argumentPathsByName: ImmutableMap<String, GQLOperationPath> by lazy {
        argumentsByPath
            .asSequence()
            .map { (p: GQLOperationPath, a: GraphQLArgument) -> a.name to p }
            .reducePairsToPersistentMap()
    }

    override val argumentsWithoutDefaultValuesByName:
        ImmutableMap<String, GraphQLArgument> by lazy {
        argumentsByName
            .asSequence()
            .filter { (n: String, _: GraphQLArgument) -> n !in argumentsWithDefaultValuesByName }
            .reduceEntriesToPersistentMap()
    }

    override val argumentsWithoutDefaultValuesByPath:
        ImmutableMap<GQLOperationPath, GraphQLArgument> by lazy {
        argumentsWithoutDefaultValuesByName
            .asSequence()
            .flatMap { (n: String, a: GraphQLArgument) ->
                argumentPathsByName.getOrNone(n).map { p: GQLOperationPath -> p to a }.sequence()
            }
            .reducePairsToPersistentMap()
    }

    companion object {

        fun builder(): DomainSpecifiedDataElementSource.Builder {
            return DefaultBuilder()
        }

        private class DefaultBuilder(
            private var domainFieldCoordinates: FieldCoordinates? = null,
            private var domainPath: GQLOperationPath? = null,
            private var domainFieldDefinition: GraphQLFieldDefinition? = null,
            private var dataElementSource: DataElementSource? = null,
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

            override fun dataElementSource(
                dataElementSource: DataElementSource
            ): DomainSpecifiedDataElementSource.Builder =
                this.apply { this.dataElementSource = dataElementSource }

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
                        ensure(domainFieldCoordinates!!.fieldName == domainFieldDefinition!!.name) {
                            "domain_field_coordinates.field_name does not match domain_field_definition.name"
                        }
                        ensure(!domainPath!!.containsAliasForField()) {
                            "domain_path cannot be aliased"
                        }
                        ensure(
                            domainPath!!
                                .selection
                                .lastOrNone()
                                .filterIsInstance<FieldSegment>()
                                .map { fs: FieldSegment ->
                                    fs.fieldName == domainFieldCoordinates!!.fieldName
                                }
                                .getOrElse { false }
                        ) {
                            "domain_path[-1].field_name does not match domain_field_coordinates.field_name"
                        }
                        ensureNotNull(lastUpdatedCoordinatesRegistry) {
                            "last_updated_coordinates_registry is null"
                        }
                        DefaultDomainSpecifiedDataElementSource(
                            domainFieldCoordinates = domainFieldCoordinates!!,
                            domainPath = domainPath!!,
                            domainFieldDefinition = domainFieldDefinition!!,
                            dataElementSource = dataElementSource!!,
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
}
