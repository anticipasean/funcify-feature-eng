package funcify.feature.datasource.sdl

import funcify.feature.datasource.sdl.impl.DefaultSourceIndexGqlSdlDefinitionFactory
import funcify.feature.schema.datasource.DataSourceType
import funcify.feature.schema.datasource.SourceAttribute
import funcify.feature.schema.datasource.SourceContainerType
import funcify.feature.schema.datasource.SourceIndex
import funcify.feature.tools.container.attempt.Try
import graphql.language.Node
import kotlin.reflect.KClass

interface SourceIndexGqlSdlDefinitionFactory<SI : SourceIndex<SI>> {

    companion object {

        fun defaultFactoryBuilder(): StepBuilder {
            return DefaultSourceIndexGqlSdlDefinitionFactory.builder()
        }
    }

    val sourceIndexType: KClass<SI>

    val dataSourceType: DataSourceType

    fun createGraphQLSDLNodeForSourceIndex(sourceIndex: SI): Try<Node<*>>

    interface StepBuilder {

        fun <SI : SourceIndex<SI>> sourceIndexType(
            sourceIndexType: KClass<SI>
        ): DataSourceTypeStep<SI>
    }

    interface DataSourceTypeStep<SI : SourceIndex<SI>> {

        fun dataSourceType(dataSourceType: DataSourceType): SourceContainerMapperStep<SI>
    }

    interface SourceContainerMapperStep<SI : SourceIndex<SI>> {

        fun <SC : SourceContainerType<SI, SA>, SA : SourceAttribute<SI>> sourceContainerTypeMapper(
            sourceContainerTypeMapper: SourceContainerTypeGqlSdlObjectTypeDefinitionMapper<SC>
        ): SourceAttributeMapperStep<SI, SC, SA>
    }

    interface SourceAttributeMapperStep<
        SI : SourceIndex<SI>, SC : SourceContainerType<SI, SA>, SA : SourceAttribute<SI>> {

        fun sourceAttributeMapper(
            sourceAttributeMapper: SourceAttributeGqlSdlFieldDefinitionMapper<SA>
        ): BuildStep<SI>
    }

    interface BuildStep<SI : SourceIndex<SI>> {

        fun build(): SourceIndexGqlSdlDefinitionFactory<SI>
    }
}
