package funcify.feature.datasource.gql

import funcify.feature.schema.datasource.DataSourceType
import funcify.feature.schema.datasource.SourceAttribute
import funcify.feature.schema.datasource.SourceContainerType
import funcify.feature.schema.datasource.SourceIndex
import funcify.feature.tools.container.attempt.Try
import graphql.language.Node
import kotlin.reflect.KClass

interface SourceIndexGqlSdlDefinitionFactory<in SI : SourceIndex> {

    companion object {

        fun defaultFactoryBuilder(): StepBuilder {
            return DefaultSourceIndexGqlSdlDefinitionFactory.builder()
        }
    }

    val dataSourceType: DataSourceType

    fun createGraphQLSDLNodeForSourceIndex(sourceIndex: SI): Try<Node<*>>

    interface StepBuilder {

        fun <SI : SourceIndex> sourceIndexType(sourceIndexType: KClass<SI>): DataSourceTypeStep<SI>
    }

    interface DataSourceTypeStep<SI : SourceIndex> {

        fun dataSourceType(dataSourceType: DataSourceType): SourceContainerMapperStep<SI>
    }

    interface SourceContainerMapperStep<SI : SourceIndex> {

        fun <SC : SourceContainerType<SA>, SA : SourceAttribute> sourceContainerTypeMapper(
            sourceContainerTypeMapper: SourceContainerTypeGqlSdlObjectTypeDefinitionMapper<SC>
        ): SourceAttributeMapperStep<SI, SC, SA>
    }

    interface SourceAttributeMapperStep<
        SI : SourceIndex, SC : SourceContainerType<SA>, SA : SourceAttribute> {

        fun sourceAttributeMapper(
            sourceAttributeMapper: SourceAttributeGqlSdlFieldDefinitionMapper<SA>
        ): BuildStep<SI>
    }

    interface BuildStep<SI : SourceIndex> {

        fun build(): SourceIndexGqlSdlDefinitionFactory<SI>
    }
}
