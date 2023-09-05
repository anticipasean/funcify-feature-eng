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
import funcify.feature.schema.path.operation.FieldSegment
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.tools.extensions.OptionExtensions.sequence
import funcify.feature.tools.extensions.PersistentMapExtensions.reduceEntriesToPersistentMap
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentMap
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLFieldDefinition
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf

internal data class DefaultDomainSpecifiedDataElementSource(
    override val domainFieldCoordinates: FieldCoordinates,
    override val domainPath: GQLOperationPath,
    override val domainFieldDefinition: GraphQLFieldDefinition,
    override val argumentsByPath: ImmutableMap<GQLOperationPath, GraphQLArgument>,
    override val argumentsByName: ImmutableMap<String, GraphQLArgument>,
    override val argumentsWithDefaultValuesByName: ImmutableMap<String, GraphQLArgument>,
    override val dataElementSource: DataElementSource,
) : DomainSpecifiedDataElementSource {

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
            private val argumentsByPath: PersistentMap.Builder<GQLOperationPath, GraphQLArgument> =
                persistentMapOf<GQLOperationPath, GraphQLArgument>().builder(),
            private val argumentsByName: PersistentMap.Builder<String, GraphQLArgument> =
                persistentMapOf<String, GraphQLArgument>().builder(),
            private val argumentsWithDefaultValuesByName:
                PersistentMap.Builder<String, GraphQLArgument> =
                persistentMapOf<String, GraphQLArgument>().builder(),
            private var dataElementSource: DataElementSource? = null,
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

            override fun putArgumentForPath(
                path: GQLOperationPath,
                argument: GraphQLArgument
            ): DomainSpecifiedDataElementSource.Builder =
                this.apply { this.argumentsByPath.put(path, argument) }

            override fun putAllPathArguments(
                pathArgumentPairs: Map<GQLOperationPath, GraphQLArgument>
            ): DomainSpecifiedDataElementSource.Builder =
                this.apply { this.argumentsByPath.putAll(pathArgumentPairs) }

            override fun putArgumentForName(
                name: String,
                argument: GraphQLArgument
            ): DomainSpecifiedDataElementSource.Builder =
                this.apply { this.argumentsByName.put(name, argument) }

            override fun putAllNameArguments(
                nameArgumentPairs: Map<String, GraphQLArgument>
            ): DomainSpecifiedDataElementSource.Builder =
                this.apply { this.argumentsByName.putAll(nameArgumentPairs) }

            override fun putArgumentsWithDefaultValuesForName(
                name: String,
                argument: GraphQLArgument
            ): DomainSpecifiedDataElementSource.Builder =
                this.apply { this.argumentsWithDefaultValuesByName.put(name, argument) }

            override fun putAllNameArgumentsWithDefaultValues(
                nameArgumentPairs: Map<String, GraphQLArgument>
            ): DomainSpecifiedDataElementSource.Builder =
                this.apply { this.argumentsWithDefaultValuesByName.putAll(nameArgumentPairs) }

            override fun dataElementSource(
                dataElementSource: DataElementSource
            ): DomainSpecifiedDataElementSource.Builder =
                this.apply { this.dataElementSource = dataElementSource }

            override fun build(): DomainSpecifiedDataElementSource {
                return eagerEffect<String, DomainSpecifiedDataElementSource> {
                        ensureNotNull(domainFieldCoordinates) { "domainFieldCoordinates is null" }
                        ensureNotNull(domainPath) { "domainPath is null" }
                        ensureNotNull(domainFieldDefinition) { "domainFieldDefinition is null" }
                        ensureNotNull(dataElementSource) { "dataElementSource is null" }
                        ensure(domainFieldCoordinates!!.fieldName == domainFieldDefinition!!.name) {
                            "domain_field_coordinates.field_name does not match domain_field_definition.name"
                        }
                        ensure(!domainPath!!.referentAliased()) { "domain_path cannot be aliased" }
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
                        DefaultDomainSpecifiedDataElementSource(
                            domainFieldCoordinates = domainFieldCoordinates!!,
                            domainPath = domainPath!!,
                            domainFieldDefinition = domainFieldDefinition!!,
                            argumentsByPath = argumentsByPath.build(),
                            argumentsByName = argumentsByName.build(),
                            argumentsWithDefaultValuesByName =
                                argumentsWithDefaultValuesByName.build(),
                            dataElementSource = dataElementSource!!
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
