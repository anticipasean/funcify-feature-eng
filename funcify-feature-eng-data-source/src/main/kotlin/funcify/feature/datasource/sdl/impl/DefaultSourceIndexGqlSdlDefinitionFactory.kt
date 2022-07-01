package funcify.feature.datasource.sdl.impl

import funcify.feature.datasource.sdl.SourceAttributeGqlSdlFieldDefinitionMapper
import funcify.feature.datasource.sdl.SourceContainerTypeGqlSdlObjectTypeDefinitionMapper
import funcify.feature.datasource.sdl.SourceIndexGqlSdlDefinitionFactory
import funcify.feature.schema.datasource.DataSourceType
import funcify.feature.schema.datasource.SourceAttribute
import funcify.feature.schema.datasource.SourceContainerType
import funcify.feature.schema.datasource.SourceIndex
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import graphql.language.Node
import org.slf4j.Logger
import kotlin.reflect.KClass

internal class DefaultSourceIndexGqlSdlDefinitionFactory<
    SI : SourceIndex<SI>, SC : SourceContainerType<SI, SA>, SA : SourceAttribute<SI>>(
    override val sourceIndexType: KClass<SI>,
    override val dataSourceType: DataSourceType,
    private val sourceContainerTypeMapper: SourceContainerTypeGqlSdlObjectTypeDefinitionMapper<SC>,
    private val sourceAttributeMapper: SourceAttributeGqlSdlFieldDefinitionMapper<SA>
) : SourceIndexGqlSdlDefinitionFactory<SI> {

    companion object {

        private val logger: Logger = loggerFor<DefaultSourceIndexGqlSdlDefinitionFactory<*, *, *>>()

        fun builder(): SourceIndexGqlSdlDefinitionFactory.StepBuilder {
            return DefaultStepBuilder()
        }

        internal class DefaultStepBuilder : SourceIndexGqlSdlDefinitionFactory.StepBuilder {

            override fun <SI : SourceIndex<SI>> sourceIndexType(
                sourceIndexType: KClass<SI>
            ): SourceIndexGqlSdlDefinitionFactory.DataSourceTypeStep<SI> {
                return DefaultDataSourceTypeStep<SI>(sourceIndexType)
            }
        }

        internal class DefaultDataSourceTypeStep<SI : SourceIndex<SI>>(
            private val sourceIndexType: KClass<SI>
        ) : SourceIndexGqlSdlDefinitionFactory.DataSourceTypeStep<SI> {

            override fun dataSourceType(
                dataSourceType: DataSourceType
            ): SourceIndexGqlSdlDefinitionFactory.SourceContainerMapperStep<SI> {
                return DefaultSourceContainerMapperStep(sourceIndexType, dataSourceType)
            }
        }

        internal class DefaultSourceContainerMapperStep<SI : SourceIndex<SI>>(
            private val sourceIndexType: KClass<SI>,
            private val dataSourceType: DataSourceType
        ) : SourceIndexGqlSdlDefinitionFactory.SourceContainerMapperStep<SI> {

            override fun <
                SC : SourceContainerType<SI, SA>,
                SA : SourceAttribute<SI>> sourceContainerTypeMapper(
                sourceContainerTypeMapper: SourceContainerTypeGqlSdlObjectTypeDefinitionMapper<SC>
            ): SourceIndexGqlSdlDefinitionFactory.SourceAttributeMapperStep<SI, SC, SA> {
                return DefaultSourceAttributeMapperStep<SI, SC, SA>(
                    sourceIndexType,
                    dataSourceType,
                    sourceContainerTypeMapper
                )
            }
        }

        internal class DefaultSourceAttributeMapperStep<
            SI : SourceIndex<SI>, SC : SourceContainerType<SI, SA>, SA : SourceAttribute<SI>>(
            private val sourceIndexType: KClass<SI>,
            private val dataSourceType: DataSourceType,
            private val sourceContainerTypeMapper:
                SourceContainerTypeGqlSdlObjectTypeDefinitionMapper<SC>
        ) : SourceIndexGqlSdlDefinitionFactory.SourceAttributeMapperStep<SI, SC, SA> {

            override fun sourceAttributeMapper(
                sourceAttributeMapper: SourceAttributeGqlSdlFieldDefinitionMapper<SA>
            ): SourceIndexGqlSdlDefinitionFactory.BuildStep<SI> {
                return DefaultBuildStep<SI, SC, SA>(
                    sourceIndexType,
                    dataSourceType,
                    sourceContainerTypeMapper,
                    sourceAttributeMapper
                )
            }
        }

        internal class DefaultBuildStep<
            SI : SourceIndex<SI>, SC : SourceContainerType<SI, SA>, SA : SourceAttribute<SI>>(
            private val sourceIndexType: KClass<SI>,
            private val dataSourceType: DataSourceType,
            private val sourceContainerTypeMapper:
                SourceContainerTypeGqlSdlObjectTypeDefinitionMapper<SC>,
            private val sourceAttributeMapper: SourceAttributeGqlSdlFieldDefinitionMapper<SA>
        ) : SourceIndexGqlSdlDefinitionFactory.BuildStep<SI> {

            override fun build(): SourceIndexGqlSdlDefinitionFactory<SI> {
                return DefaultSourceIndexGqlSdlDefinitionFactory<SI, SC, SA>(
                    sourceIndexType,
                    dataSourceType,
                    sourceContainerTypeMapper,
                    sourceAttributeMapper
                )
            }
        }
    }

    override fun createGraphQLSDLNodeForSourceIndex(sourceIndex: SI): Try<Node<*>> {
        logger.debug(
            "create_graphql_sdl_node_for_source_index: [ source_index.name: ${sourceIndex.name} ]"
        )
        if (sourceIndex.dataSourceLookupKey.dataSourceType != dataSourceType) {
            return Try.failure<Node<*>>(
                    IllegalArgumentException(
                        """source_index.data_source_type does not match 
                           |expected data_source_type: [ expected: ${dataSourceType}, 
                           |actual: ${sourceIndex.dataSourceLookupKey.dataSourceType} ]
                           |""".flattenIntoOneLine()
                    )
                )
                .peekIfFailure(failureLogger())
        }
        return when (sourceIndex) {
            is SourceContainerType<*, *> ->
                Try.attempt({
                        @Suppress("UNCHECKED_CAST") //
                        sourceIndex as SC
                    })
                    .flatMap { sc ->
                        sourceContainerTypeMapper
                            .convertSourceContainerTypeIntoGraphQLObjectTypeSDLDefinition(sc)
                    }
            is SourceAttribute<*> ->
                Try.attempt({
                        @Suppress("UNCHECKED_CAST") //
                        sourceIndex as SA
                    })
                    .flatMap { sa ->
                        sourceAttributeMapper.convertSourceAttributeIntoGraphQLFieldSDLDefinition(
                            sa
                        )
                    }
            else ->
                Try.failure(
                    IllegalStateException(
                        """unsupported source_index sub_type: [ 
                            |source_index.type: ${sourceIndex::class.qualifiedName} ]
                            |""".flattenIntoOneLine()
                    )
                )
        }.peekIfFailure(failureLogger())
    }

    private fun failureLogger(): (Throwable) -> Unit = { t: Throwable ->
        logger.error(
            """create_graphql_sdl_node_for_source_index: [ status: failed ] 
               |[ type: ${t::class.simpleName}, message: ${t.message} ]
               |""".flattenIntoOneLine(),
            t
        )
    }
}
