package funcify.feature.materializer.tools

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.materializer.tools.SchematicPathBasedJsonRetrievalFunction.Builder
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.path.SchematicPath
import funcify.feature.schema.vertex.ParameterJunctionVertex
import funcify.feature.schema.vertex.ParameterLeafVertex
import funcify.feature.schema.vertex.SourceJunctionVertex
import funcify.feature.schema.vertex.SourceLeafVertex
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.container.deferred.Deferred
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentSet

/**
 *
 * @author smccarron
 * @created 2022-08-11
 */
internal class DefaultSchematicPathBasedJsonRetrievalFunctionFactory :
    SchematicPathBasedJsonRetrievalFunctionFactory {

    companion object {

        internal data class DefaultSchematicPathBasedJsonRetrievalFunction(
            override val dataSource: DataSource<*>,
            internal val parameterVertices:
                PersistentSet<Either<ParameterJunctionVertex, ParameterLeafVertex>> =
                persistentSetOf(),
            internal val sourceVertices:
                PersistentSet<Either<SourceJunctionVertex, SourceLeafVertex>> =
                persistentSetOf()
        ) : SchematicPathBasedJsonRetrievalFunction {

            override val parameterPaths: ImmutableSet<SchematicPath> by lazy {
                parameterVertices
                    .asSequence()
                    .map { jvOrlv -> jvOrlv.fold({ pjv -> pjv.path }, { plv -> plv.path }) }
                    .toPersistentSet()
            }

            override val sourcePaths: ImmutableSet<SchematicPath> by lazy {
                sourceVertices
                    .asSequence()
                    .map { jvOrlv -> jvOrlv.fold({ sjv -> sjv.path }, { slv -> slv.path }) }
                    .toPersistentSet()
            }

            override fun update(
                transformer: Builder.() -> Builder
            ): Try<SchematicPathBasedJsonRetrievalFunction> {
                return transformer.invoke(DefaultBuilder(this)).build()
            }

            override fun invoke(
                valuesByParameterPaths: ImmutableMap<SchematicPath, JsonNode>
            ): Deferred<JsonNode> {
                TODO("Not yet implemented")
            }
        }

        internal class DefaultBuilder(
            private val existingFunction: DefaultSchematicPathBasedJsonRetrievalFunction? = null,
            private var dataSource: DataSource<*>? = existingFunction?.dataSource,
            private var parameterVertices:
                PersistentSet.Builder<Either<ParameterJunctionVertex, ParameterLeafVertex>> =
                existingFunction?.parameterVertices?.builder()
                    ?: persistentSetOf<Either<ParameterJunctionVertex, ParameterLeafVertex>>()
                        .builder(),
            private var sourceVertices:
                PersistentSet.Builder<Either<SourceJunctionVertex, SourceLeafVertex>> =
                existingFunction?.sourceVertices?.builder()
                    ?: persistentSetOf<Either<SourceJunctionVertex, SourceLeafVertex>>().builder()
        ) : Builder {

            override fun dataSource(dataSource: DataSource<*>): Builder {
                this.dataSource = dataSource
                return this
            }

            override fun addRequestParameter(
                parameterJunctionVertex: ParameterJunctionVertex
            ): Builder {
                this.parameterVertices.add(parameterJunctionVertex.left())
                return this
            }

            override fun addRequestParameter(parameterLeafVertex: ParameterLeafVertex): Builder {
                this.parameterVertices.add(parameterLeafVertex.right())
                return this
            }

            override fun addSourceTarget(sourceJunctionVertex: SourceJunctionVertex): Builder {
                this.sourceVertices.add(sourceJunctionVertex.left())
                return this
            }

            override fun addSourceTarget(sourceLeafVertex: SourceLeafVertex): Builder {
                this.sourceVertices.add(sourceLeafVertex.right())
                return this
            }

            override fun build(): Try<SchematicPathBasedJsonRetrievalFunction> {
                TODO("Not yet implemented")
            }
        }
    }

    override fun builder(): Builder {
        TODO("Not yet implemented")
    }
}
