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
    override val childPathsByParentPath:
        PersistentMap<GQLOperationPath, PersistentSet<GQLOperationPath>>,
    override val querySchemaElementsByPath: PersistentMap<GQLOperationPath, GraphQLSchemaElement>,
    override val fieldCoordinatesByPath:
        PersistentMap<GQLOperationPath, PersistentSet<FieldCoordinates>>,
    override val pathsByFieldCoordinates:
        PersistentMap<FieldCoordinates, PersistentSet<GQLOperationPath>>,
    override val domainSpecifiedDataElementSourceByPath:
        PersistentMap<GQLOperationPath, DomainSpecifiedDataElementSource>,
    override val domainSpecifiedDataElementSourcesByCoordinates:
        PersistentMap<FieldCoordinates, DomainSpecifiedDataElementSource>,
    override val dataElementFieldCoordinatesByFieldName:
        PersistentMap<String, PersistentSet<FieldCoordinates>>,
    override val dataElementPathsByFieldName:
        PersistentMap<String, PersistentSet<GQLOperationPath>>,
    override val dataElementPathsByFieldArgumentName:
        PersistentMap<String, PersistentSet<GQLOperationPath>>,
    override val featureSpecifiedFeatureCalculatorsByPath:
        PersistentMap<GQLOperationPath, FeatureSpecifiedFeatureCalculator>,
    override val featurePathsByFieldName: PersistentMap<String, GQLOperationPath>,
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
                childPathsByParentPath = persistentMapOf(),
                querySchemaElementsByPath = persistentMapOf(),
                fieldCoordinatesByPath = persistentMapOf(),
                pathsByFieldCoordinates = persistentMapOf(),
                domainSpecifiedDataElementSourceByPath = persistentMapOf(),
                domainSpecifiedDataElementSourcesByCoordinates = persistentMapOf(),
                dataElementFieldCoordinatesByFieldName = persistentMapOf(),
                dataElementPathsByFieldName = persistentMapOf(),
                dataElementPathsByFieldArgumentName = persistentMapOf(),
                featureSpecifiedFeatureCalculatorsByPath = persistentMapOf(),
                featurePathsByFieldName = persistentMapOf(),
                aliasCoordinatesRegistry = AliasCoordinatesRegistry.empty(),
            )
        }

        internal class DefaultBuilder(
            private val existingFacts: DefaultMaterializationMetamodelBuildContext,
            private var featureEngineeringModel: FeatureEngineeringModel =
                existingFacts.featureEngineeringModel,
            private var materializationGraphQLSchema: GraphQLSchema =
                existingFacts.materializationGraphQLSchema,
            private val childPathsByParentPath:
                PersistentMap.Builder<GQLOperationPath, PersistentSet<GQLOperationPath>> =
                existingFacts.childPathsByParentPath.builder(),
            private val querySchemaElementsByPath:
                PersistentMap.Builder<GQLOperationPath, GraphQLSchemaElement> =
                existingFacts.querySchemaElementsByPath.builder(),
            private val fieldCoordinatesByPath:
                PersistentMap.Builder<GQLOperationPath, PersistentSet<FieldCoordinates>> =
                existingFacts.fieldCoordinatesByPath.builder(),
            private val pathsByFieldCoordinates:
                PersistentMap.Builder<FieldCoordinates, PersistentSet<GQLOperationPath>> =
                existingFacts.pathsByFieldCoordinates.builder(),
            private val domainSpecifiedDataElementSourceByPath:
                PersistentMap.Builder<GQLOperationPath, DomainSpecifiedDataElementSource> =
                existingFacts.domainSpecifiedDataElementSourceByPath.builder(),
            private val domainSpecifiedDataElementSourcesByCoordinates:
                PersistentMap.Builder<FieldCoordinates, DomainSpecifiedDataElementSource> =
                existingFacts.domainSpecifiedDataElementSourcesByCoordinates.builder(),
            private val dataElementFieldCoordinatesByFieldName:
                PersistentMap.Builder<String, PersistentSet<FieldCoordinates>> =
                existingFacts.dataElementFieldCoordinatesByFieldName.builder(),
            private val dataElementPathsByFieldName:
                PersistentMap.Builder<String, PersistentSet<GQLOperationPath>> =
                existingFacts.dataElementPathsByFieldName.builder(),
            private val dataElementPathsByFieldArgumentName:
                PersistentMap.Builder<String, PersistentSet<GQLOperationPath>> =
                existingFacts.dataElementPathsByFieldArgumentName.builder(),
            private val featureSpecifiedFeatureCalculatorsByPath:
                PersistentMap.Builder<GQLOperationPath, FeatureSpecifiedFeatureCalculator> =
                existingFacts.featureSpecifiedFeatureCalculatorsByPath.builder(),
            private val featurePathsByFieldName: PersistentMap.Builder<String, GQLOperationPath> =
                existingFacts.featurePathsByFieldName.builder(),
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
                    this.childPathsByParentPath.put(
                        parentPath,
                        this.childPathsByParentPath
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

            override fun putFieldCoordinatesForDataElementFieldName(
                name: String,
                fieldCoordinates: FieldCoordinates
            ): Builder =
                this.apply {
                    this.dataElementFieldCoordinatesByFieldName.put(
                        name,
                        this.dataElementFieldCoordinatesByFieldName
                            .getOrElse(name, ::persistentSetOf)
                            .add(fieldCoordinates)
                    )
                }

            override fun putPathForDataElementFieldName(
                name: String,
                path: GQLOperationPath
            ): Builder =
                this.apply {
                    this.dataElementPathsByFieldName.put(
                        name,
                        this.dataElementPathsByFieldName
                            .getOrElse(name, ::persistentSetOf)
                            .add(path)
                    )
                }

            override fun putPathForDataElementFieldArgumentName(
                name: String,
                path: GQLOperationPath
            ): Builder =
                this.apply {
                    this.dataElementPathsByFieldArgumentName.put(
                        name,
                        this.dataElementPathsByFieldArgumentName
                            .getOrElse(name, ::persistentSetOf)
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
            ): Builder = this.apply { this.featurePathsByFieldName.put(name, gqlOperationPath) }

            override fun aliasCoordinatesRegistry(
                aliasCoordinatesRegistry: AliasCoordinatesRegistry
            ): Builder = this.apply { this.aliasCoordinatesRegistry = aliasCoordinatesRegistry }

            override fun build(): MaterializationMetamodelBuildContext {
                return DefaultMaterializationMetamodelBuildContext(
                    featureEngineeringModel = featureEngineeringModel,
                    materializationGraphQLSchema = materializationGraphQLSchema,
                    childPathsByParentPath = childPathsByParentPath.build(),
                    querySchemaElementsByPath = querySchemaElementsByPath.build(),
                    fieldCoordinatesByPath = fieldCoordinatesByPath.build(),
                    pathsByFieldCoordinates = pathsByFieldCoordinates.build(),
                    domainSpecifiedDataElementSourceByPath =
                        domainSpecifiedDataElementSourceByPath.build(),
                    domainSpecifiedDataElementSourcesByCoordinates =
                        domainSpecifiedDataElementSourcesByCoordinates.build(),
                    dataElementFieldCoordinatesByFieldName =
                        dataElementFieldCoordinatesByFieldName.build(),
                    dataElementPathsByFieldName = dataElementPathsByFieldName.build(),
                    dataElementPathsByFieldArgumentName =
                        dataElementPathsByFieldArgumentName.build(),
                    featureSpecifiedFeatureCalculatorsByPath =
                        featureSpecifiedFeatureCalculatorsByPath.build(),
                    featurePathsByFieldName = featurePathsByFieldName.build(),
                    aliasCoordinatesRegistry = aliasCoordinatesRegistry,
                )
            }
        }
    }

    override fun update(transformer: Builder.() -> Builder): MaterializationMetamodelBuildContext {
        return transformer.invoke(DefaultBuilder(this)).build()
    }
}
