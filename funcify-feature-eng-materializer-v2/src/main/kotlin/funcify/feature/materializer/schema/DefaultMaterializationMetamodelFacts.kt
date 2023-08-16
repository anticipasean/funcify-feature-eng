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
    override val childCanonicalPathsByParentPath:
        PersistentMap<GQLOperationPath, PersistentSet<GQLOperationPath>>,
    override val querySchemaElementsByCanonicalPath:
        PersistentMap<GQLOperationPath, GraphQLSchemaElement>,
    override val fieldCoordinatesByCanonicalPath: PersistentMap<GQLOperationPath, FieldCoordinates>,
    override val canonicalPathsByFieldCoordinates:
        PersistentMap<FieldCoordinates, GQLOperationPath>,
    override val domainSpecifiedDataElementSourceByPath:
        PersistentMap<GQLOperationPath, DomainSpecifiedDataElementSource>
) : MaterializationMetamodelFacts {

    companion object {

        fun empty(): MaterializationMetamodelFacts {
            return DefaultMaterializationMetamodelFacts(
                childCanonicalPathsByParentPath = persistentMapOf(),
                querySchemaElementsByCanonicalPath = persistentMapOf(),
                fieldCoordinatesByCanonicalPath = persistentMapOf(),
                canonicalPathsByFieldCoordinates = persistentMapOf(),
                domainSpecifiedDataElementSourceByPath = persistentMapOf(),
            )
        }

        internal class DefaultBuilder(
            private val existingFacts: DefaultMaterializationMetamodelFacts,
            private val childCanonicalPathsByParentPath:
                PersistentMap.Builder<GQLOperationPath, PersistentSet<GQLOperationPath>> =
                existingFacts.childCanonicalPathsByParentPath.builder(),
            private val querySchemaElementsByPath:
                PersistentMap.Builder<GQLOperationPath, GraphQLSchemaElement> =
                existingFacts.querySchemaElementsByCanonicalPath.builder(),
            private val fieldCoordinatesByPath:
                PersistentMap.Builder<GQLOperationPath, FieldCoordinates> =
                existingFacts.fieldCoordinatesByCanonicalPath.builder(),
            private val pathsByFieldCoordinates:
                PersistentMap.Builder<FieldCoordinates, GQLOperationPath> =
                existingFacts.canonicalPathsByFieldCoordinates.builder(),
            private val domainSpecifiedDataElementSourceByPath:
                PersistentMap.Builder<GQLOperationPath, DomainSpecifiedDataElementSource> =
                existingFacts.domainSpecifiedDataElementSourceByPath.builder(),
        ) : Builder {

            override fun addChildPathForParentPath(
                parentPath: GQLOperationPath,
                childPath: GQLOperationPath
            ): Builder =
                this.apply {
                    childCanonicalPathsByParentPath.put(
                        parentPath,
                        childCanonicalPathsByParentPath
                            .getOrElse(parentPath, ::persistentSetOf)
                            .add(childPath)
                    )
                }

            override fun putGraphQLSchemaElementForPath(
                path: GQLOperationPath,
                element: GraphQLSchemaElement,
            ): Builder = this.apply { querySchemaElementsByPath.put(path, element) }

            override fun putFieldCoordinatesForPath(
                path: GQLOperationPath,
                fieldCoordinates: FieldCoordinates
            ): Builder = this.apply { fieldCoordinatesByPath.put(path, fieldCoordinates) }

            override fun putPathForFieldCoordinates(
                fieldCoordinates: FieldCoordinates,
                path: GQLOperationPath
            ): Builder = this.apply { pathsByFieldCoordinates.put(fieldCoordinates, path) }

            override fun putDomainSpecifiedDataElementSourceForPath(
                path: GQLOperationPath,
                domainSpecifiedDataElementSource: DomainSpecifiedDataElementSource,
            ): Builder =
                this.apply {
                    this.domainSpecifiedDataElementSourceByPath.put(
                        path,
                        domainSpecifiedDataElementSource
                    )
                }

            override fun build(): MaterializationMetamodelFacts {
                return DefaultMaterializationMetamodelFacts(
                    childCanonicalPathsByParentPath = childCanonicalPathsByParentPath.build(),
                    querySchemaElementsByCanonicalPath = querySchemaElementsByPath.build(),
                    fieldCoordinatesByCanonicalPath = fieldCoordinatesByPath.build(),
                    canonicalPathsByFieldCoordinates = pathsByFieldCoordinates.build(),
                    domainSpecifiedDataElementSourceByPath =
                        domainSpecifiedDataElementSourceByPath.build()
                )
            }
        }
    }

    override fun update(transformer: Builder.() -> Builder): MaterializationMetamodelFacts {
        return transformer.invoke(DefaultBuilder(this)).build()
    }
}
