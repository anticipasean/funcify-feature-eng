package funcify.feature.materializer.fetcher

import arrow.core.continuations.eagerEffect
import arrow.core.continuations.ensureNotNull
import arrow.core.identity
import funcify.feature.error.ServiceError
import funcify.feature.materializer.fetcher.SingleRequestFieldMaterializationSession.Builder
import funcify.feature.materializer.session.GraphQLSingleRequestSession
import graphql.schema.DataFetchingEnvironment

internal data class DefaultSingleRequestFieldMaterializationSession(
    override val dataFetchingEnvironment: DataFetchingEnvironment,
    override val singleRequestSession: GraphQLSingleRequestSession
) : SingleRequestFieldMaterializationSession {

    companion object {
        internal class DefaultBuilder(
            private val existingSession: DefaultSingleRequestFieldMaterializationSession?,
            private var currentSession: GraphQLSingleRequestSession? =
                existingSession?.singleRequestSession,
            private var dataFetchingEnvironment: DataFetchingEnvironment? =
                existingSession?.dataFetchingEnvironment
        ) : Builder {
            override fun singleRequestSession(
                singleRequestSession: GraphQLSingleRequestSession
            ): Builder {
                this.currentSession = singleRequestSession
                return this
            }

            override fun dataFetchingEnvironment(
                dataFetchingEnvironment: DataFetchingEnvironment
            ): Builder {
                this.dataFetchingEnvironment = dataFetchingEnvironment
                return this
            }

            override fun build(): SingleRequestFieldMaterializationSession {
                return eagerEffect<String, SingleRequestFieldMaterializationSession> {
                        ensureNotNull(currentSession) {
                            "current_session [ type: %s ] has not been provided".format(
                                GraphQLSingleRequestSession::class.qualifiedName
                            )
                        }
                        ensureNotNull(dataFetchingEnvironment) {
                            "data_fetching_environment [ type: %s ] has not been provided".format(
                                DataFetchingEnvironment::class.qualifiedName
                            )
                        }
                        DefaultSingleRequestFieldMaterializationSession(
                            dataFetchingEnvironment!!,
                            currentSession!!
                        )
                    }
                    .fold({ message: String -> throw ServiceError.of(message) }, ::identity)
            }
        }
    }

    override fun update(
        transformer: Builder.() -> Builder
    ): SingleRequestFieldMaterializationSession {
        return transformer.invoke(DefaultBuilder(this)).build()
    }
}
