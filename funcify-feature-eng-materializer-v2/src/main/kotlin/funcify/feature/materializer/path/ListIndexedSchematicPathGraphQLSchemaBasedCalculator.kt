package funcify.feature.materializer.path

import arrow.core.Option
import arrow.core.firstOrNone
import arrow.core.left
import arrow.core.right
import arrow.core.toOption
import funcify.feature.datasource.graphql.type.GraphQLOutputFieldsContainerTypeExtractor
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.tools.extensions.SequenceExtensions.recurse
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLSchema
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.toPersistentList
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

internal object ListIndexedSchematicPathGraphQLSchemaBasedCalculator :
        (GQLOperationPath, GraphQLSchema) -> Option<GQLOperationPath> {

    private val listIndexedGQLOperationPathUsingGraphQLSchemaMemoizer:
                (GQLOperationPath, GraphQLSchema) -> Option<GQLOperationPath> by lazy {
        val cache: ConcurrentMap<Pair<GQLOperationPath, GraphQLSchema>, GQLOperationPath> =
            ConcurrentHashMap()
        ({ unindexedPath: GQLOperationPath, graphQLSchema: GraphQLSchema ->
            cache
                .computeIfAbsent(
                    unindexedPath to graphQLSchema,
                    listIndexedSchematicPathUsingGraphQLSchemaCalculator()
                )
                .toOption()
        })
    }

    override fun invoke(
        unindexedPath: GQLOperationPath,
        correspondingSchema: GraphQLSchema
    ): Option<GQLOperationPath> {
        return listIndexedGQLOperationPathUsingGraphQLSchemaMemoizer(
            unindexedPath,
            correspondingSchema
        )
    }

    private fun listIndexedSchematicPathUsingGraphQLSchemaCalculator():
                (Pair<GQLOperationPath, GraphQLSchema>) -> GQLOperationPath? {
        return { (unindexedPath: GQLOperationPath, correspondingSchema: GraphQLSchema) ->
            unindexedPath
                .toOption()
                .flatMap { sp ->
                    sp.selection.firstOrNone().flatMap { n ->
                        correspondingSchema.queryType.getFieldDefinition(n).toOption().map { gfd ->
                            sp.selection.toPersistentList().removeAt(0) to gfd
                        }
                    }
                }
                .fold(::emptySequence, ::sequenceOf)
                .recurse { (ps: PersistentList<String>, gqlf: GraphQLFieldDefinition) ->
                    when (gqlf.type) {
                        is GraphQLNonNull -> {
                            if ((gqlf.type as GraphQLNonNull).wrappedType is GraphQLList) {
                                sequenceOf(
                                    StringBuilder(gqlf.name)
                                        .append('[')
                                        .append(0)
                                        .append(']')
                                        .toString()
                                        .right()
                                )
                            } else {
                                sequenceOf(gqlf.name.right())
                            }
                        }
                        is GraphQLList -> {
                            sequenceOf(
                                StringBuilder(gqlf.name)
                                    .append('[')
                                    .append(0)
                                    .append(']')
                                    .toString()
                                    .right()
                            )
                        }
                        else -> {
                            sequenceOf(gqlf.name.right())
                        }
                    }.plus(
                        GraphQLOutputFieldsContainerTypeExtractor.invoke(gqlf.type)
                            .zip(ps.firstOrNone())
                            .flatMap { (c, n) -> c.getFieldDefinition(n).toOption() }
                            .map { f -> (ps.removeAt(0) to f).left() }
                            .fold(::emptySequence, ::sequenceOf)
                    )
                }
                .let { sSeq ->
                    val listIndexedPath = GQLOperationPath.of { fields(sSeq.toList()) }
                    when (listIndexedPath.level()) {
                        unindexedPath.level() -> listIndexedPath
                        else -> null
                    }
                }
        }
    }
}
