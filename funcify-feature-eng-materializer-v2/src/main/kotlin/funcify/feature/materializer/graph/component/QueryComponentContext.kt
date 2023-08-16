package funcify.feature.materializer.graph.component

import arrow.core.Option
import funcify.feature.schema.path.operation.GQLOperationPath
import graphql.language.Argument
import graphql.language.Field

/**
 * @author smccarron
 * @created 2023-08-06
 */
sealed interface QueryComponentContext {

    val path: GQLOperationPath

    interface Builder<B : Builder<B>> {

        fun path(path: GQLOperationPath): B
    }

    interface SelectedFieldComponentContext : QueryComponentContext {

        override val path: GQLOperationPath

        val field: Option<Field>

        interface Builder : QueryComponentContext.Builder<Builder> {

            override fun path(path: GQLOperationPath): Builder

            fun field(field: Field): Builder

            fun build(): SelectedFieldComponentContext
        }
    }

    interface FieldArgumentComponentContext : QueryComponentContext {

        override val path: GQLOperationPath

        val argument: Option<Argument>

        interface Builder : QueryComponentContext.Builder<Builder> {

            override fun path(path: GQLOperationPath): Builder

            fun argument(argument: Argument): Builder

            fun build(): FieldArgumentComponentContext
        }
    }
}
