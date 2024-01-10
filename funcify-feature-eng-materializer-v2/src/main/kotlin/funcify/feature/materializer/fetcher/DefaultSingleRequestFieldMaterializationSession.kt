package funcify.feature.materializer.fetcher

import arrow.core.Option
import arrow.core.continuations.eagerEffect
import arrow.core.continuations.ensureNotNull
import arrow.core.filterIsInstance
import arrow.core.identity
import arrow.core.toOption
import funcify.feature.error.ServiceError
import funcify.feature.materializer.fetcher.SingleRequestFieldMaterializationSession.Builder
import funcify.feature.materializer.session.GraphQLSingleRequestSession
import funcify.feature.schema.path.result.GQLResultPath
import graphql.schema.DataFetchingEnvironment
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLImplementingType
import graphql.schema.GraphQLTypeUtil

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
                            "current_session [ type: %s ] has not been provided"
                                .format(GraphQLSingleRequestSession::class.qualifiedName)
                        }
                        ensureNotNull(dataFetchingEnvironment) {
                            "data_fetching_environment [ type: %s ] has not been provided"
                                .format(DataFetchingEnvironment::class.qualifiedName)
                        }
                        DefaultSingleRequestFieldMaterializationSession(
                            dataFetchingEnvironment = dataFetchingEnvironment!!,
                            singleRequestSession = currentSession!!
                        )
                    }
                    .fold({ message: String -> throw ServiceError.of(message) }, ::identity)
            }
        }
    }

    override val gqlResultPath: GQLResultPath by lazy {
        GQLResultPath.fromNativeResultPath(dataFetchingEnvironment.executionStepInfo.path)
    }

    override val parentImplementingType: Option<GraphQLImplementingType> by lazy {
        dataFetchingEnvironment.parentType
            .toOption()
            .mapNotNull(GraphQLTypeUtil::unwrapAll)
            .filterIsInstance<GraphQLImplementingType>()
    }

    override val fieldCoordinates: Option<FieldCoordinates> by lazy {
        parentImplementingType.map(GraphQLImplementingType::getName).map { tn: String ->
            FieldCoordinates.coordinates(tn, this.field.name)
        }
    }

    override fun update(
        transformer: Builder.() -> Builder
    ): SingleRequestFieldMaterializationSession {
        return transformer.invoke(DefaultBuilder(this)).build()
    }
}
