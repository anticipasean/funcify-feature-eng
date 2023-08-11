package funcify.feature.materializer.schema

import funcify.feature.materializer.schema.MaterializationMetamodelFacts.Builder
import funcify.feature.schema.path.operation.GQLOperationPath
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLSchemaElement
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf

internal data class DefaultMaterializationMetamodelFacts(
    override val querySchemaElementsByPath: PersistentMap<GQLOperationPath, GraphQLSchemaElement>,
    override val fieldCoordinatesByPath:
        PersistentMap<GQLOperationPath, PersistentSet<FieldCoordinates>>,
    override val pathsByFieldCoordinates:
        PersistentMap<FieldCoordinates, PersistentSet<GQLOperationPath>>
) : MaterializationMetamodelFacts {

    companion object {

        fun empty(): MaterializationMetamodelFacts {
            return DefaultMaterializationMetamodelFacts(
                querySchemaElementsByPath = persistentMapOf(),
                fieldCoordinatesByPath = persistentMapOf(),
                pathsByFieldCoordinates = persistentMapOf()
            )
        }

        internal class DefaultBuilder(
            private val existingFacts: DefaultMaterializationMetamodelFacts,
            private val querySchemaElementsByPath:
                PersistentMap.Builder<GQLOperationPath, GraphQLSchemaElement> =
                existingFacts.querySchemaElementsByPath.builder(),
            private val fieldCoordinatesByPath:
                PersistentMap.Builder<GQLOperationPath, PersistentSet<FieldCoordinates>> =
                existingFacts.fieldCoordinatesByPath.builder(),
            private val pathsByFieldCoordinates:
                PersistentMap.Builder<FieldCoordinates, PersistentSet<GQLOperationPath>> =
                existingFacts.pathsByFieldCoordinates.builder()
        ) : Builder {

            override fun putGraphQLSchemaElement(
                path: GQLOperationPath,
                element: GraphQLSchemaElement,
            ): Builder = this.apply { querySchemaElementsByPath.put(path, element) }

            override fun putFieldCoordinates(
                path: GQLOperationPath,
                fieldCoordinates: FieldCoordinates
            ): Builder =
                this.apply {
                    fieldCoordinatesByPath.put(
                        path,
                        fieldCoordinatesByPath
                            .getOrElse(path, ::persistentSetOf)
                            .add(fieldCoordinates)
                    )
                }

            override fun putPath(
                fieldCoordinates: FieldCoordinates,
                path: GQLOperationPath
            ): Builder =
                this.apply {
                    pathsByFieldCoordinates.put(
                        fieldCoordinates,
                        pathsByFieldCoordinates
                            .getOrElse(fieldCoordinates, ::persistentSetOf)
                            .add(path)
                    )
                }

            override fun build(): MaterializationMetamodelFacts {
                return DefaultMaterializationMetamodelFacts(
                    querySchemaElementsByPath = querySchemaElementsByPath.build(),
                    fieldCoordinatesByPath = fieldCoordinatesByPath.build(),
                    pathsByFieldCoordinates = pathsByFieldCoordinates.build()
                )
            }
        }
    }

    override fun update(transformer: Builder.() -> Builder): MaterializationMetamodelFacts {
        return transformer.invoke(DefaultBuilder(this)).build()
    }
}
