package funcify.feature.materializer.fetcher

import arrow.core.Option
import arrow.core.none
import arrow.core.toOption
import funcify.feature.materializer.fetcher.SingleRequestFieldMaterializationSession.Builder
import funcify.feature.materializer.service.RequestDispatchMaterializationPhase
import funcify.feature.materializer.service.RequestParameterMaterializationGraphPhase
import funcify.feature.materializer.session.GraphQLSingleRequestSession
import graphql.schema.DataFetchingEnvironment

internal data class DefaultSingleRequestFieldMaterializationSession(
    override val dataFetchingEnvironment: DataFetchingEnvironment,
    override val singleRequestSession: GraphQLSingleRequestSession,
    override val requestParameterMaterializationGraphPhase:
        Option<RequestParameterMaterializationGraphPhase> =
        none(),
    override val requestDispatchMaterializationGraphPhase:
        Option<RequestDispatchMaterializationPhase> =
        none()
) : SingleRequestFieldMaterializationSession {

    companion object {
        internal class DefaultBuilder(
            val existingSession: DefaultSingleRequestFieldMaterializationSession,
            var dataFetchingEnvironment: DataFetchingEnvironment =
                existingSession.dataFetchingEnvironment,
            var requestParameterMaterializationGraphPhase:
                RequestParameterMaterializationGraphPhase? =
                existingSession.requestParameterMaterializationGraphPhase.orNull(),
            var requestDispatchMaterializationPhase: RequestDispatchMaterializationPhase? =
                existingSession.requestDispatchMaterializationGraphPhase.orNull()
        ) : Builder {

            override fun dataFetchingEnvironment(
                dataFetchingEnvironment: DataFetchingEnvironment
            ): Builder {
                this.dataFetchingEnvironment = dataFetchingEnvironment
                return this
            }

            override fun requestParameterMaterializationGraphPhase(
                requestParameterMaterializationGraphPhase: RequestParameterMaterializationGraphPhase
            ): Builder {
                this.requestParameterMaterializationGraphPhase =
                    requestParameterMaterializationGraphPhase
                return this
            }

            override fun requestDispatchMaterializationPhase(
                requestDispatchMaterializationPhase: RequestDispatchMaterializationPhase
            ): Builder {
                this.requestDispatchMaterializationPhase = requestDispatchMaterializationPhase
                return this
            }

            override fun build(): SingleRequestFieldMaterializationSession {
                return existingSession.copy(
                    dataFetchingEnvironment = dataFetchingEnvironment,
                    requestParameterMaterializationGraphPhase =
                        requestParameterMaterializationGraphPhase.toOption(),
                    requestDispatchMaterializationGraphPhase =
                        requestDispatchMaterializationPhase.toOption()
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
