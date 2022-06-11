package funcify.feature.datasource.gql

import funcify.feature.schema.datasource.DataSourceType
import funcify.feature.schema.datasource.SourceAttribute
import funcify.feature.schema.datasource.SourceContainerType
import funcify.feature.schema.datasource.SourceIndex
import funcify.feature.tools.container.attempt.Try
import graphql.language.Node

interface SourceIndexGqlSdlDefinitionFactory<in SI : SourceIndex> {

    companion object {

        fun defaultFactoryBuilder(): StepBuilder {
            return DefaultSourceIndexGqlSdlDefinitionFactory.builder()
        }
    }

    val dataSourceType: DataSourceType

    fun createGraphQLSDLNodeForSourceIndex(sourceIndex: SI): Try<Node<*>>

    interface StepBuilder {

        fun dataSourceType(dataSourceType: DataSourceType): SourceContainerMapperStep
    }

    interface SourceContainerMapperStep {

        fun <SC : SourceContainerType<SA>, SA : SourceAttribute> sourceContainerTypeMapper(
            sourceContainerTypeMapper: SourceContainerTypeGqlSdlObjectTypeDefinitionMapper<SC>
        ): SourceAttributeMapperStep<SA>
    }

    interface SourceAttributeMapperStep<SA : SourceAttribute> {

        fun sourceAttributeMapper(
            sourceAttributeMapper: SourceAttributeGqlSdlFieldDefinitionMapper<SA>
        ): BuildStep<SA>
    }

    interface BuildStep<SI : SourceIndex> {

        fun build(): SourceIndexGqlSdlDefinitionFactory<SI>
    }
}
