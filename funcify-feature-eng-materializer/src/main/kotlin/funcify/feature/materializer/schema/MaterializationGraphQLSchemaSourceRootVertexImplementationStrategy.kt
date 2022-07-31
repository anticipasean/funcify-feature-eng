package funcify.feature.materializer.schema

import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext.SourceRootVertexSDLDefinitionCreationContext
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionImplementationStrategy
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.TryExtensions.successIfNonNull
import graphql.language.ObjectTypeDefinition
import org.slf4j.Logger

/**
 *
 * @author smccarron
 * @created 2022-07-31
 */
internal class MaterializationGraphQLSchemaSourceRootVertexImplementationStrategy :
    SchematicVertexSDLDefinitionImplementationStrategy {

    companion object {
        private val logger: Logger =
            loggerFor<MaterializationGraphQLSchemaSourceRootVertexImplementationStrategy>()
    }

    override fun canBeAppliedToContext(
        context: SchematicVertexSDLDefinitionCreationContext<*>
    ): Boolean {
        return context is SourceRootVertexSDLDefinitionCreationContext
    }

    override fun applyToContext(
        context: SchematicVertexSDLDefinitionCreationContext<*>
    ): Try<SchematicVertexSDLDefinitionCreationContext<*>> {
        logger.debug("apply_to_context: [ context.path: ${context.path} ]")
        when (context) {
            is SourceRootVertexSDLDefinitionCreationContext -> {
                val containerTypeRepresentationsString =
                    context.compositeSourceContainerType
                        .getSourceContainerTypeByDataSource()
                        .asSequence()
                        .map { (dsKey, sct) ->
                            "datasource.name: ${dsKey.name}, container_type.name: ${sct.name}"
                        }
                        .joinToString(", ", "{ ", " }")
                logger.debug(
                    "source_root_container_type_representations: ${containerTypeRepresentationsString}"
                )
                return context
                    .update {
                        addSDLDefinitionForSchematicPath(
                            context.path,
                            ObjectTypeDefinition.newObjectTypeDefinition().name("Query").build()
                        )
                    }
                    .successIfNonNull()
            }
            else -> {
                return context.successIfNonNull()
            }
        }
    }
}
