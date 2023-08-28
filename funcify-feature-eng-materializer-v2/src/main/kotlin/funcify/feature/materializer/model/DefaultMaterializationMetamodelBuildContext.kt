package funcify.feature.materializer.model

import funcify.feature.materializer.model.MaterializationMetamodelBuildContext.Builder
import funcify.feature.schema.FeatureEngineeringModel
import funcify.feature.schema.dataelement.DomainSpecifiedDataElementSource
import funcify.feature.schema.directive.alias.AliasCoordinatesRegistry
import funcify.feature.schema.feature.FeatureSpecifiedFeatureCalculator
import funcify.feature.schema.path.operation.GQLOperationPath
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLSchema
import graphql.schema.GraphQLSchemaElement
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf

internal data class DefaultMaterializationMetamodelBuildContext(
    override val featureEngineeringModel: FeatureEngineeringModel,
    override val materializationGraphQLSchema: GraphQLSchema,
    override val childCanonicalPathsByParentPath:
        PersistentMap<GQLOperationPath, PersistentSet<GQLOperationPath>>,
    override val querySchemaElementsByCanonicalPath:
        PersistentMap<GQLOperationPath, GraphQLSchemaElement>,
    override val fieldCoordinatesByCanonicalPath:
        PersistentMap<GQLOperationPath, PersistentSet<FieldCoordinates>>,
    override val canonicalPathsByFieldCoordinates:
        PersistentMap<FieldCoordinates, PersistentSet<GQLOperationPath>>,
    override val domainSpecifiedDataElementSourceByPath:
        PersistentMap<GQLOperationPath, DomainSpecifiedDataElementSource>,
    override val domainSpecifiedDataElementSourcesByCoordinates:
        PersistentMap<FieldCoordinates, DomainSpecifiedDataElementSource>,
    override val featureSpecifiedFeatureCalculatorsByPath:
        PersistentMap<GQLOperationPath, FeatureSpecifiedFeatureCalculator>,
    override val featurePathsByName: PersistentMap<String, GQLOperationPath>,
    override val aliasCoordinatesRegistry: AliasCoordinatesRegistry,
) : MaterializationMetamodelBuildContext {

    companion object {

        fun createInitial(
            featureEngineeringModel: FeatureEngineeringModel,
            materializationGraphQLSchema: GraphQLSchema
        ): MaterializationMetamodelBuildContext {
            return DefaultMaterializationMetamodelBuildContext(
                featureEngineeringModel = featureEngineeringModel,
                materializationGraphQLSchema = materializationGraphQLSchema,
                childCanonicalPathsByParentPath = persistentMapOf(),
                querySchemaElementsByCanonicalPath = persistentMapOf(),
                fieldCoordinatesByCanonicalPath = persistentMapOf(),
                canonicalPathsByFieldCoordinates = persistentMapOf(),
                domainSpecifiedDataElementSourceByPath = persistentMapOf(),
                domainSpecifiedDataElementSourcesByCoordinates = persistentMapOf(),
                featureSpecifiedFeatureCalculatorsByPath = persistentMapOf(),
                featurePathsByName = persistentMapOf(),
                aliasCoordinatesRegistry = AliasCoordinatesRegistry.empty(),
            )
        }

        internal class DefaultBuilder(
            private val existingFacts: DefaultMaterializationMetamodelBuildContext,
            private var featureEngineeringModel: FeatureEngineeringModel =
                existingFacts.featureEngineeringModel,
            private var materializationGraphQLSchema: GraphQLSchema =
                existingFacts.materializationGraphQLSchema,
            private val childCanonicalPathsByParentPath:
                PersistentMap.Builder<GQLOperationPath, PersistentSet<GQLOperationPath>> =
                existingFacts.childCanonicalPathsByParentPath.builder(),
            private val querySchemaElementsByPath:
                PersistentMap.Builder<GQLOperationPath, GraphQLSchemaElement> =
                existingFacts.querySchemaElementsByCanonicalPath.builder(),
            private val fieldCoordinatesByPath:
                PersistentMap.Builder<GQLOperationPath, PersistentSet<FieldCoordinates>> =
                existingFacts.fieldCoordinatesByCanonicalPath.builder(),
            private val pathsByFieldCoordinates:
                PersistentMap.Builder<FieldCoordinates, PersistentSet<GQLOperationPath>> =
                existingFacts.canonicalPathsByFieldCoordinates.builder(),
            private val domainSpecifiedDataElementSourceByPath:
                PersistentMap.Builder<GQLOperationPath, DomainSpecifiedDataElementSource> =
                existingFacts.domainSpecifiedDataElementSourceByPath.builder(),
            private val domainSpecifiedDataElementSourcesByCoordinates:
                PersistentMap.Builder<FieldCoordinates, DomainSpecifiedDataElementSource> =
                existingFacts.domainSpecifiedDataElementSourcesByCoordinates.builder(),
            private val featureSpecifiedFeatureCalculatorsByPath:
                PersistentMap.Builder<GQLOperationPath, FeatureSpecifiedFeatureCalculator> =
                existingFacts.featureSpecifiedFeatureCalculatorsByPath.builder(),
            private val featurePathsByName: PersistentMap.Builder<String, GQLOperationPath> =
                existingFacts.featurePathsByName.builder(),
            private var aliasCoordinatesRegistry: AliasCoordinatesRegistry =
                existingFacts.aliasCoordinatesRegistry,
        ) : Builder {

            override fun featureEngineeringModel(
                featureEngineeringModel: FeatureEngineeringModel
            ): Builder = this.apply { this.featureEngineeringModel = featureEngineeringModel }

            override fun materializationGraphQLSchema(
                materializationGraphQLSchema: GraphQLSchema
            ): Builder =
                this.apply { this.materializationGraphQLSchema = materializationGraphQLSchema }

            override fun addChildPathForParentPath(
                parentPath: GQLOperationPath,
                childPath: GQLOperationPath
            ): Builder =
                this.apply {
                    this.childCanonicalPathsByParentPath.put(
                        parentPath,
                        this.childCanonicalPathsByParentPath
                            .getOrElse(parentPath, ::persistentSetOf)
                            .add(childPath)
                    )
                }

            override fun putGraphQLSchemaElementForPath(
                path: GQLOperationPath,
                element: GraphQLSchemaElement,
            ): Builder = this.apply { this.querySchemaElementsByPath.put(path, element) }

            override fun putFieldCoordinatesForPath(
                path: GQLOperationPath,
                fieldCoordinates: FieldCoordinates
            ): Builder =
                this.apply {
                    this.fieldCoordinatesByPath.put(
                        path,
                        this.fieldCoordinatesByPath
                            .getOrElse(path, ::persistentSetOf)
                            .add(fieldCoordinates)
                    )
                }

            override fun putPathForFieldCoordinates(
                fieldCoordinates: FieldCoordinates,
                path: GQLOperationPath
            ): Builder =
                this.apply {
                    this.pathsByFieldCoordinates.put(
                        fieldCoordinates,
                        this.pathsByFieldCoordinates
                            .getOrElse(fieldCoordinates, ::persistentSetOf)
                            .add(path)
                    )
                }

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

            override fun putDomainSpecifiedDataElementSourceForCoordinates(
                fieldCoordinates: FieldCoordinates,
                domainSpecifiedDataElementSource: DomainSpecifiedDataElementSource,
            ): Builder =
                this.apply {
                    this.domainSpecifiedDataElementSourcesByCoordinates.put(
                        fieldCoordinates,
                        domainSpecifiedDataElementSource
                    )
                }

            override fun putFeatureSpecifiedFeatureCalculatorForPath(
                path: GQLOperationPath,
                featureSpecifiedFeatureCalculator: FeatureSpecifiedFeatureCalculator,
            ): Builder =
                this.apply {
                    this.featureSpecifiedFeatureCalculatorsByPath.put(
                        path,
                        featureSpecifiedFeatureCalculator
                    )
                }

            override fun putFeatureNameForPath(
                name: String,
                gqlOperationPath: GQLOperationPath
            ): Builder = this.apply { this.featurePathsByName.put(name, gqlOperationPath) }

            override fun aliasCoordinatesRegistry(
                aliasCoordinatesRegistry: AliasCoordinatesRegistry
            ): Builder = this.apply { this.aliasCoordinatesRegistry = aliasCoordinatesRegistry }

            override fun build(): MaterializationMetamodelBuildContext {
                return DefaultMaterializationMetamodelBuildContext(
                    featureEngineeringModel = featureEngineeringModel,
                    materializationGraphQLSchema = materializationGraphQLSchema,
                    childCanonicalPathsByParentPath = childCanonicalPathsByParentPath.build(),
                    querySchemaElementsByCanonicalPath = querySchemaElementsByPath.build(),
                    fieldCoordinatesByCanonicalPath = fieldCoordinatesByPath.build(),
                    canonicalPathsByFieldCoordinates = pathsByFieldCoordinates.build(),
                    domainSpecifiedDataElementSourceByPath =
                        domainSpecifiedDataElementSourceByPath.build(),
                    domainSpecifiedDataElementSourcesByCoordinates =
                        domainSpecifiedDataElementSourcesByCoordinates.build(),
                    featureSpecifiedFeatureCalculatorsByPath =
                        featureSpecifiedFeatureCalculatorsByPath.build(),
                    featurePathsByName = featurePathsByName.build(),
                    aliasCoordinatesRegistry = aliasCoordinatesRegistry,
                )
            }
        }
    }

    override fun update(transformer: Builder.() -> Builder): MaterializationMetamodelBuildContext {
        return transformer.invoke(DefaultBuilder(this)).build()
    }
}
