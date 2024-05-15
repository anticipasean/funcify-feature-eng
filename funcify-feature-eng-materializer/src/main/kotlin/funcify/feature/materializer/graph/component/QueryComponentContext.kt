package funcify.feature.materializer.graph.component

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

    val fieldCoordinates: FieldCoordinates

    val canonicalPath: GQLOperationPath

    interface Builder<B : Builder<B>> {

        fun path(path: GQLOperationPath): B

        fun fieldCoordinates(fieldCoordinates: FieldCoordinates): B

        fun canonicalPath(canonicalPath: GQLOperationPath): B
    }

    interface FieldComponentContext : QueryComponentContext {

        override val path: GQLOperationPath

        override val fieldCoordinates: FieldCoordinates

        val field: Field

        interface Builder : QueryComponentContext.Builder<Builder> {

            override fun path(path: GQLOperationPath): Builder

            override fun fieldCoordinates(fieldCoordinates: FieldCoordinates): Builder

            override fun canonicalPath(canonicalPath: GQLOperationPath): Builder

            fun field(field: Field): Builder

            fun build(): FieldComponentContext
        }
    }

    interface ArgumentComponentContext : QueryComponentContext {

        override val path: GQLOperationPath

        override val fieldCoordinates: FieldCoordinates

        val argument: Argument

        val fieldArgumentLocation: Pair<FieldCoordinates, String>

        interface Builder : QueryComponentContext.Builder<Builder> {

            override fun path(path: GQLOperationPath): Builder

            override fun fieldCoordinates(fieldCoordinates: FieldCoordinates): Builder

            override fun canonicalPath(canonicalPath: GQLOperationPath): Builder

            fun argument(argument: Argument): Builder

            fun build(): ArgumentComponentContext
        }
    }
}
