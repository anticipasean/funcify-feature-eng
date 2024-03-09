package funcify.feature.materializer.graph.component

import arrow.core.continuations.eagerEffect
import arrow.core.continuations.ensureNotNull
import arrow.core.identity
import funcify.feature.error.ServiceError
import funcify.feature.materializer.graph.component.QueryComponentContext.ArgumentComponentContext
import funcify.feature.materializer.graph.component.QueryComponentContext.FieldComponentContext
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
        B : QueryComponentContext.Builder<B>
    >(
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

    internal class DefaultFieldComponentContextBuilder(
        private val existingSelectedFieldComponentContext: DefaultFieldComponentContext? = null,
        private var field: Field? = existingSelectedFieldComponentContext?.field
    ) :
        DefaultQueryComponentContextBuilder<FieldComponentContext.Builder>(
            existingContext = existingSelectedFieldComponentContext
        ),
        FieldComponentContext.Builder {

        override fun field(field: Field): FieldComponentContext.Builder =
            this.apply { this.field = field }

        override fun build(): FieldComponentContext {
            return eagerEffect<String, FieldComponentContext> {
                    ensureNotNull(path) { "path not provided" }
                    ensureNotNull(fieldCoordinates) { "field_coordinates not provided" }
                    ensureNotNull(field) { "field not provided" }
                    ensureNotNull(canonicalPath) { "canonical_path not provided" }
                    DefaultFieldComponentContext(
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
                                FieldComponentContext::class.simpleName ?: "<NA>"
                            ),
                            message
                        )
                    },
                    ::identity
                )
        }
    }

    internal data class DefaultFieldComponentContext(
        override val path: GQLOperationPath,
        override val fieldCoordinates: FieldCoordinates,
        override val field: Field,
        override val canonicalPath: GQLOperationPath,
    ) : FieldComponentContext {}

    internal class DefaultArgumentComponentContextBuilder(
        private val existingFieldArgumentComponentContext: DefaultArgumentComponentContext? = null,
        private var argument: Argument? = existingFieldArgumentComponentContext?.argument
    ) :
        DefaultQueryComponentContextBuilder<ArgumentComponentContext.Builder>(
            existingContext = existingFieldArgumentComponentContext
        ),
        ArgumentComponentContext.Builder {

        override fun argument(argument: Argument): ArgumentComponentContext.Builder =
            this.apply { this.argument = argument }

        override fun build(): ArgumentComponentContext {
            return eagerEffect<String, ArgumentComponentContext> {
                    ensureNotNull(path) { "path not provided" }
                    ensureNotNull(fieldCoordinates) { "field_coordinates not provided" }
                    ensureNotNull(argument) { "argument not provided" }
                    ensureNotNull(canonicalPath) { "canonical_path not provided" }
                    DefaultArgumentComponentContext(
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
                                ArgumentComponentContext::class.simpleName ?: "<NA>"
                            ),
                            message
                        )
                    },
                    ::identity
                )
        }
    }

    internal data class DefaultArgumentComponentContext(
        override val path: GQLOperationPath,
        override val fieldCoordinates: FieldCoordinates,
        override val argument: Argument,
        override val canonicalPath: GQLOperationPath,
    ) : ArgumentComponentContext {

        override val fieldArgumentLocation: Pair<FieldCoordinates, String> by lazy {
            fieldCoordinates to argument.name
        }
    }

    override fun fieldComponentContextBuilder(): FieldComponentContext.Builder {
        return DefaultFieldComponentContextBuilder()
    }

    override fun argumentComponentContextBuilder(): ArgumentComponentContext.Builder {
        return DefaultArgumentComponentContextBuilder()
    }
}
