package funcify.feature.materializer.fetcher

import funcify.feature.materializer.fetcher.SingleRequestFieldMaterializationSession.Builder
import funcify.feature.materializer.schema.RequestParameterEdge
import funcify.feature.materializer.session.GraphQLSingleRequestSession
import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.container.graph.PathBasedGraph
import graphql.schema.DataFetchingEnvironment

internal data class DefaultSingleRequestFieldMaterializationSession(
    override val dataFetchingEnvironment: DataFetchingEnvironment,
    override val singleRequestSession: GraphQLSingleRequestSession,
    override val requestMaterializationGraph:
        PathBasedGraph<SchematicPath, SchematicVertex, RequestParameterEdge> =
        PathBasedGraph.emptyTwoToOnePathsToEdgeGraph()
) : SingleRequestFieldMaterializationSession {

    companion object {
        internal class DefaultBuilder(
            val existingSession: DefaultSingleRequestFieldMaterializationSession,
            var dataFetchingEnvironment: DataFetchingEnvironment =
                existingSession.dataFetchingEnvironment,
            var requestMaterializationGraph:
                PathBasedGraph<SchematicPath, SchematicVertex, RequestParameterEdge> =
                existingSession.requestMaterializationGraph
        ) : Builder {

            override fun dataFetchingEnvironment(
                dataFetchingEnvironment: DataFetchingEnvironment
            ): Builder {
                this.dataFetchingEnvironment = dataFetchingEnvironment
                return this
            }

            override fun requestMaterializationGraph(
                requestMaterializationGraph:
                    PathBasedGraph<SchematicPath, SchematicVertex, RequestParameterEdge>
            ): Builder {
                this.requestMaterializationGraph = requestMaterializationGraph
                return this
            }

            override fun build(): SingleRequestFieldMaterializationSession {
                return existingSession.copy(
                    dataFetchingEnvironment = dataFetchingEnvironment,
                    requestMaterializationGraph = requestMaterializationGraph
                )
            }
        }
    }

    override fun update(
        transformer: Builder.() -> Builder
    ): SingleRequestFieldMaterializationSession {
        val builder: Builder = DefaultBuilder(this)
        return transformer.invoke(builder).build()
    }
}
