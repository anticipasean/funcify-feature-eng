package funcify.feature.materializer.graph.component

import arrow.core.continuations.eagerEffect
import arrow.core.continuations.ensureNotNull
import arrow.core.identity
import funcify.feature.error.ServiceError
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
        protected open var fieldCoordinates: FieldCoordinates? = existingContext?.fieldCoordinates
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
    }

    internal class DefaultSelectedFieldComponentContextBuilder(
        private val existingSelectedFieldComponentContext: DefaultSelectedFieldComponentContext? =
            null,
        private var field: Field? = existingSelectedFieldComponentContext?.field
    ) :
        DefaultQueryComponentContextBuilder<
            QueryComponentContext.SelectedFieldComponentContext.Builder
        >(existingContext = existingSelectedFieldComponentContext),
        QueryComponentContext.SelectedFieldComponentContext.Builder {

        override fun field(
            field: Field
        ): QueryComponentContext.SelectedFieldComponentContext.Builder =
            this.apply { this.field = field }

        override fun build(): QueryComponentContext.SelectedFieldComponentContext {
            return eagerEffect<String, QueryComponentContext.SelectedFieldComponentContext> {
                    ensureNotNull(path) { "path not provided" }
                    ensureNotNull(fieldCoordinates) { "field_coordinates not provided" }
                    ensureNotNull(field) { "field not provided" }
                    DefaultSelectedFieldComponentContext(path!!, fieldCoordinates!!, field!!)
                }
                .fold(
                    { message: String ->
                        throw ServiceError.of(
                            "unable to create %s [ message: %s ]",
                            StandardNamingConventions.SNAKE_CASE.deriveName(
                                QueryComponentContext.SelectedFieldComponentContext::class
                                    .simpleName
                                    ?: "<NA>"
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
        override val field: Field
    ) : QueryComponentContext.SelectedFieldComponentContext {}

    internal class DefaultFieldArgumentComponentContextBuilder(
        private val existingFieldArgumentComponentContext: DefaultFieldArgumentComponentContext? =
            null,
        private var argument: Argument? = existingFieldArgumentComponentContext?.argument
    ) :
        DefaultQueryComponentContextBuilder<
            QueryComponentContext.FieldArgumentComponentContext.Builder
        >(existingContext = existingFieldArgumentComponentContext),
        QueryComponentContext.FieldArgumentComponentContext.Builder {

        override fun argument(
            argument: Argument
        ): QueryComponentContext.FieldArgumentComponentContext.Builder =
            this.apply { this.argument = argument }

        override fun build(): QueryComponentContext.FieldArgumentComponentContext {
            return eagerEffect<String, QueryComponentContext.FieldArgumentComponentContext> {
                    ensureNotNull(path) { "path not provided" }
                    ensureNotNull(fieldCoordinates) { "field_coordinates not provided" }
                    ensureNotNull(argument) { "argument not provided" }
                    DefaultFieldArgumentComponentContext(path!!, fieldCoordinates!!, argument!!)
                }
                .fold(
                    { message: String ->
                        throw ServiceError.of(
                            "unable to create %s [ message: %s ]",
                            StandardNamingConventions.SNAKE_CASE.deriveName(
                                QueryComponentContext.FieldArgumentComponentContext::class
                                    .simpleName
                                    ?: "<NA>"
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
        override val argument: Argument
    ) : QueryComponentContext.FieldArgumentComponentContext {}

    override fun selectedFieldComponentContextBuilder():
        QueryComponentContext.SelectedFieldComponentContext.Builder {
        return DefaultSelectedFieldComponentContextBuilder()
    }

    override fun fieldArgumentComponentContextBuilder():
        QueryComponentContext.FieldArgumentComponentContext.Builder {
        return DefaultFieldArgumentComponentContextBuilder()
    }
}
