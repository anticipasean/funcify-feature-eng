package funcify.feature.materializer.schema

import arrow.core.continuations.eagerEffect
import arrow.core.continuations.ensureNotNull
import arrow.core.identity
import funcify.feature.error.ServiceError
import funcify.feature.schema.dataelement.DataElementSource
import funcify.feature.schema.path.operation.GQLOperationPath
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
    override val dataElementSource: DataElementSource
) : DomainSpecifiedDataElementSource {

    companion object {

        fun builder(): DomainSpecifiedDataElementSource.Builder {
            return DefaultBuilder()
        }

        private class DefaultBuilder(
            private var domainFieldCoordinates: FieldCoordinates? = null,
            private var domainPath: GQLOperationPath? = null,
            private var domainFieldDefinition: GraphQLFieldDefinition? = null,
            private var argumentsByPath: PersistentMap.Builder<GQLOperationPath, GraphQLArgument> =
                persistentMapOf<GQLOperationPath, GraphQLArgument>().builder(),
            private var argumentsByName: PersistentMap.Builder<String, GraphQLArgument> =
                persistentMapOf<String, GraphQLArgument>().builder(),
            private var argumentsWithDefaultValuesByName:
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
