package funcify.feature.materializer.path

import arrow.core.Option
import arrow.core.firstOrNone
import arrow.core.left
import arrow.core.right
import arrow.core.toOption
import funcify.feature.datasource.graphql.type.GraphQLOutputFieldsContainerTypeExtractor
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.extensions.SequenceExtensions.recurse
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLSchema
import kotlinx.collections.immutable.PersistentList
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import kotlinx.collections.immutable.toPersistentList

internal object ListIndexedSchematicPathGraphQLSchemaBasedCalculator :
    (SchematicPath, GraphQLSchema) -> Option<SchematicPath> {

    private val listIndexedSchematicPathUsingGraphQLSchemaMemoizer:
        (SchematicPath, GraphQLSchema) -> Option<SchematicPath> by lazy {
        val cache: ConcurrentMap<Pair<SchematicPath, GraphQLSchema>, SchematicPath> =
            ConcurrentHashMap()
        ({ unindexedPath: SchematicPath, graphQLSchema: GraphQLSchema ->
            cache
                .computeIfAbsent(
                    unindexedPath to graphQLSchema,
                    listIndexedSchematicPathUsingGraphQLSchemaCalculator()
                )
                .toOption()
        })
    }

    override fun invoke(
        unindexedPath: SchematicPath,
        correspondingSchema: GraphQLSchema
    ): Option<SchematicPath> {
        return listIndexedSchematicPathUsingGraphQLSchemaMemoizer(
            unindexedPath,
            correspondingSchema
        )
    }

    private fun listIndexedSchematicPathUsingGraphQLSchemaCalculator():
        (Pair<SchematicPath, GraphQLSchema>) -> SchematicPath? {
        return { (unindexedPath: SchematicPath, correspondingSchema: GraphQLSchema) ->
            unindexedPath
                .toOption()
                .flatMap { sp ->
                    sp.pathSegments.firstOrNone().flatMap { n ->
                        correspondingSchema.queryType.getFieldDefinition(n).toOption().map { gfd ->
                            sp.pathSegments.toPersistentList().removeAt(0) to gfd
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
                    val listIndexedPath = SchematicPath.of { pathSegments(sSeq.toList()) }
                    when (listIndexedPath.level()) {
                        unindexedPath.level() -> listIndexedPath
                        else -> null
                    }
                }
        }
    }
}
