package funcify.feature.materializer.fetcher

import funcify.feature.materializer.fetcher.SingleRequestFieldMaterializationSession.Builder
import funcify.feature.materializer.session.GraphQLSingleRequestSession
import graphql.schema.DataFetchingEnvironment

internal data class DefaultSingleRequestFieldMaterializationSession(
    override val dataFetchingEnvironment: DataFetchingEnvironment,
    override val singleRequestSession: GraphQLSingleRequestSession
) : SingleRequestFieldMaterializationSession {

    companion object {
        internal class DefaultBuilder(
            val existingSession: DefaultSingleRequestFieldMaterializationSession,
            var dataFetchingEnvironment: DataFetchingEnvironment =
                existingSession.dataFetchingEnvironment,
        ) : Builder {

            override fun dataFetchingEnvironment(
                dataFetchingEnvironment: DataFetchingEnvironment
            ): Builder {
                this.dataFetchingEnvironment = dataFetchingEnvironment
                return this
            }

            override fun build(): SingleRequestFieldMaterializationSession {
                return existingSession.copy(dataFetchingEnvironment = dataFetchingEnvironment)
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
