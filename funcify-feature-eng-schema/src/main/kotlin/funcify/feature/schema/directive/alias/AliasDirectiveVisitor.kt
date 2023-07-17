package funcify.feature.schema.directive.alias

import arrow.core.filterIsInstance
import arrow.core.getOrElse
import arrow.core.toOption
import funcify.feature.directive.AliasDirective
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import graphql.language.Argument
import graphql.language.Directive
import graphql.language.FieldDefinition
import graphql.language.Node
import graphql.language.NodeVisitorStub
import graphql.language.StringValue
import graphql.util.Breadcrumb
import graphql.util.TraversalControl
import graphql.util.TraverserContext
import org.slf4j.Logger

/**
 * @author smccarron
 * @created 2023-07-17
 */
internal class AliasDirectiveVisitor : NodeVisitorStub() {

    companion object {
        private val logger: Logger = loggerFor<AliasDirectiveVisitor>()
    }

    override fun visitFieldDefinition(
        node: FieldDefinition,
        context: TraverserContext<Node<*>>
    ): TraversalControl {
        return if (node.hasDirective(AliasDirective.name)) {
            node.getDirectives(AliasDirective.name).fold(TraversalControl.CONTINUE) {
                tc: TraversalControl,
                d: Directive ->
                visitDirective(d, context)
            }
        } else {
            TraversalControl.CONTINUE
        }
    }

    override fun visitDirective(
        node: Directive,
        context: TraverserContext<Node<*>>
    ): TraversalControl {
        if (node.name == AliasDirective.name) {
            logger.info(
                "alias directive found: {}",
                context.breadcrumbs.asSequence().joinToString(", ", "[ ", " ]")
            )
            val aliasRegistry: AttributeAliasRegistry =
                context.getNewAccumulate<AttributeAliasRegistry>()
            val sp: SchematicPath =
                SchematicPath.of {
                    pathSegments(
                        context.breadcrumbs
                            .asSequence()
                            .mapNotNull { bc: Breadcrumb<Node<*>> -> bc.node }
                            .filterIsInstance<FieldDefinition>()
                            .map { fd: FieldDefinition -> fd.name }
                            .toList()
                    )
                }
            logger.info("alias schematicPath: {}", sp)
            val updatedRegistry: AttributeAliasRegistry =
                aliasRegistry.registerSourceVertexPathWithAlias(
                    sp,
                    node.argumentsByName["name"]
                        .toOption()
                        .map(Argument::getValue)
                        .filterIsInstance<StringValue>()
                        .map(StringValue::getValue)
                        .getOrElse { "<NA>" }
                )
            context.setAccumulate(updatedRegistry)
        }
        return TraversalControl.CONTINUE
    }
}
