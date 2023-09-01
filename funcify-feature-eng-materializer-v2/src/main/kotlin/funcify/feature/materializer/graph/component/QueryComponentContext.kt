package funcify.feature.materializer.graph.component

import arrow.core.Option
import funcify.feature.schema.path.operation.GQLOperationPath
import graphql.language.Argument
import graphql.language.Field
import graphql.schema.FieldCoordinates

/**
 * @author smccarron
 * @created 2023-08-06
 */
sealed interface QueryComponentContext {

    val path: GQLOperationPath

    val fieldCoordinates: Option<FieldCoordinates>

    interface Builder<B : Builder<B>> {

        fun path(path: GQLOperationPath): B

        fun fieldCoordinates(fieldCoordinates: FieldCoordinates): B
    }

    interface SelectedFieldComponentContext : QueryComponentContext {

        override val path: GQLOperationPath

        override val fieldCoordinates: Option<FieldCoordinates>

        val field: Option<Field>

        interface Builder : QueryComponentContext.Builder<Builder> {

            override fun path(path: GQLOperationPath): Builder

            override fun fieldCoordinates(fieldCoordinates: FieldCoordinates): Builder

            fun field(field: Field): Builder

            fun build(): SelectedFieldComponentContext
        }
    }

    interface FieldArgumentComponentContext : QueryComponentContext {

        override val path: GQLOperationPath

        override val fieldCoordinates: Option<FieldCoordinates>

        val argument: Option<Argument>

        interface Builder : QueryComponentContext.Builder<Builder> {

            override fun path(path: GQLOperationPath): Builder

            override fun fieldCoordinates(fieldCoordinates: FieldCoordinates): Builder

            fun argument(argument: Argument): Builder

            fun build(): FieldArgumentComponentContext
        }
    }
}
