package funcify.feature.materializer.graph.component

import arrow.core.continuations.eagerEffect
import arrow.core.continuations.ensureNotNull
import arrow.core.identity
import funcify.feature.error.ServiceError
import funcify.feature.materializer.graph.component.QueryComponentContext.FieldArgumentComponentContext
import funcify.feature.materializer.graph.component.QueryComponentContext.SelectedFieldComponentContext
import funcify.feature.naming.StandardNamingConventions
import funcify.feature.schema.path.operation.GQLOperationPath
import graphql.language.Argument
import graphql.language.Field
import graphql.schema.FieldCoordinates

/**
 * @author smccarron
 * @created 2023-09-03
 */
internal object DefaultQueryComponentContextFactory : QueryComponentContextFactory {

    internal abstract class DefaultQueryComponentContextBuilder<
        B : QueryComponentContext.Builder<B>>(
        protected open val existingContext: QueryComponentContext? = null,
        protected open var path: GQLOperationPath? = existingContext?.path,
        protected open var fieldCoordinates: FieldCoordinates? = existingContext?.fieldCoordinates,
        protected open var canonicalPath: GQLOperationPath? = existingContext?.canonicalPath,
    ) : QueryComponentContext.Builder<B> {

        companion object {
            private inline fun <reified WB, reified NB : WB> WB.applyToBuilder(
                builderUpdater: WB.() -> Unit
            ): NB {
                return this.apply(builderUpdater) as NB
            }
        }

        override fun path(path: GQLOperationPath): B = this.applyToBuilder { this.path = path }

        override fun fieldCoordinates(fieldCoordinates: FieldCoordinates): B =
            this.applyToBuilder { this.fieldCoordinates = fieldCoordinates }

        override fun canonicalPath(canonicalPath: GQLOperationPath): B =
            this.applyToBuilder { this.canonicalPath = canonicalPath }
    }

    internal class DefaultSelectedFieldComponentContextBuilder(
        private val existingSelectedFieldComponentContext: DefaultSelectedFieldComponentContext? =
            null,
        private var field: Field? = existingSelectedFieldComponentContext?.field
    ) :
        DefaultQueryComponentContextBuilder<SelectedFieldComponentContext.Builder>(
            existingContext = existingSelectedFieldComponentContext
        ),
        SelectedFieldComponentContext.Builder {

        override fun field(field: Field): SelectedFieldComponentContext.Builder =
            this.apply { this.field = field }

        override fun build(): SelectedFieldComponentContext {
            return eagerEffect<String, SelectedFieldComponentContext> {
                    ensureNotNull(path) { "path not provided" }
                    ensureNotNull(fieldCoordinates) { "field_coordinates not provided" }
                    ensureNotNull(field) { "field not provided" }
                    ensureNotNull(canonicalPath) { "canonical_path not provided" }
                    DefaultSelectedFieldComponentContext(
                        path = path!!,
                        fieldCoordinates = fieldCoordinates!!,
                        field = field!!,
                        canonicalPath = canonicalPath!!,
                    )
                }
                .fold(
                    { message: String ->
                        throw ServiceError.of(
                            "unable to create %s [ message: %s ]",
                            StandardNamingConventions.SNAKE_CASE.deriveName(
                                SelectedFieldComponentContext::class.simpleName ?: "<NA>"
                            ),
                            message
                        )
                    },
                    ::identity
                )
        }
    }

    internal data class DefaultSelectedFieldComponentContext(
        override val path: GQLOperationPath,
        override val fieldCoordinates: FieldCoordinates,
        override val field: Field,
        override val canonicalPath: GQLOperationPath,
    ) : SelectedFieldComponentContext {}

    internal class DefaultFieldArgumentComponentContextBuilder(
        private val existingFieldArgumentComponentContext: DefaultFieldArgumentComponentContext? =
            null,
        private var argument: Argument? = existingFieldArgumentComponentContext?.argument
    ) :
        DefaultQueryComponentContextBuilder<FieldArgumentComponentContext.Builder>(
            existingContext = existingFieldArgumentComponentContext
        ),
        FieldArgumentComponentContext.Builder {

        override fun argument(argument: Argument): FieldArgumentComponentContext.Builder =
            this.apply { this.argument = argument }

        override fun build(): FieldArgumentComponentContext {
            return eagerEffect<String, FieldArgumentComponentContext> {
                    ensureNotNull(path) { "path not provided" }
                    ensureNotNull(fieldCoordinates) { "field_coordinates not provided" }
                    ensureNotNull(argument) { "argument not provided" }
                    ensureNotNull(canonicalPath) { "canonical_path not provided" }
                    DefaultFieldArgumentComponentContext(
                        path = path!!,
                        fieldCoordinates = fieldCoordinates!!,
                        argument = argument!!,
                        canonicalPath = canonicalPath!!,
                    )
                }
                .fold(
                    { message: String ->
                        throw ServiceError.of(
                            "unable to create %s [ message: %s ]",
                            StandardNamingConventions.SNAKE_CASE.deriveName(
                                FieldArgumentComponentContext::class.simpleName ?: "<NA>"
                            ),
                            message
                        )
                    },
                    ::identity
                )
        }
    }

    internal data class DefaultFieldArgumentComponentContext(
        override val path: GQLOperationPath,
        override val fieldCoordinates: FieldCoordinates,
        override val argument: Argument,
        override val canonicalPath: GQLOperationPath,
    ) : FieldArgumentComponentContext {}

    override fun selectedFieldComponentContextBuilder(): SelectedFieldComponentContext.Builder {
        return DefaultSelectedFieldComponentContextBuilder()
    }

    override fun fieldArgumentComponentContextBuilder(): FieldArgumentComponentContext.Builder {
        return DefaultFieldArgumentComponentContextBuilder()
    }
}
