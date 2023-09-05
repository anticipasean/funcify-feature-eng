package funcify.feature.datasource.graphql.factory

import arrow.core.Option
import arrow.core.continuations.eagerEffect
import arrow.core.continuations.ensureNotNull
import arrow.core.filterIsInstance
import arrow.core.identity
import arrow.core.lastOrNone
import arrow.core.toOption
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.datasource.graphql.SchemaOnlyDataElementSource
import funcify.feature.error.ServiceError
import funcify.feature.naming.StandardNamingConventions
import funcify.feature.schema.dataelement.DataElementCallable
import funcify.feature.schema.path.operation.FieldSegment
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import graphql.language.Field
import graphql.language.SDLDefinition
import graphql.language.Selection
import graphql.language.SelectionSet
import graphql.language.Value
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLFieldsContainer
import graphql.schema.GraphQLTypeUtil
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentSet
import org.slf4j.Logger
import reactor.core.publisher.Mono

internal class DefaultSchemaOnlyDataElementSource(
    override val name: String,
    override val sourceSDLDefinitions: PersistentSet<SDLDefinition<*>>
) : SchemaOnlyDataElementSource {

    companion object {
        private class SchemaOnlyDataElementSourceCallableBuilder :
            BaseDataElementCallableBuilder<SchemaOnlyDataElementSourceCallableBuilder>() {

            override fun build(): DataElementCallable {
                return eagerEffect<String, SchemaOnlyDataElementSourceCallable> {
                        ensureNotNull(domainFieldCoordinates) {
                            "domain_field_coordinates not provided"
                        }
                        ensureNotNull(domainPath) { "domain_path not provided" }
                        ensureNotNull(domainGraphQLFieldDefinition) {
                            "domain_graphql_field_definition not provided"
                        }
                        ensure(
                            domainFieldCoordinates
                                .toOption()
                                .filter { fc: FieldCoordinates ->
                                    fc.fieldName == domainGraphQLFieldDefinition!!.name
                                }
                                .isDefined()
                        ) {
                            "domain_field_coordinates.field_name does not match domain_graphql_field_definition.name"
                        }
                        ensure(
                            domainFieldCoordinates
                                .toOption()
                                .flatMap { fc: FieldCoordinates ->
                                    domainPath!!
                                        .selection
                                        .lastOrNone()
                                        .filterIsInstance<FieldSegment>()
                                        .map(FieldSegment::fieldName)
                                        .filter { fn: String -> fc.fieldName == fn }
                                }
                                .isDefined()
                        ) {
                            "domain_field_coordinates.field_name does not match last selection of domain_path"
                        }
                        ensure(fieldSelection != null || fieldPathSelections.isNotEmpty()) {
                            "neither a selected_field nor field_path_selections have been provided"
                        }
                        ensure(
                            fieldSelection == null ||
                                fieldSelection
                                    .toOption()
                                    .filter { f: Field ->
                                        f.name == domainFieldCoordinates!!.fieldName
                                    }
                                    .zip(domainGraphQLFieldDefinition.toOption())
                                    .flatMap { (f: Field, gfd: GraphQLFieldDefinition) ->
                                        f.toOption()
                                            .mapNotNull(Field::getSelectionSet)
                                            .mapNotNull(SelectionSet::getSelections)
                                            .filter(List<Selection<*>>::isNotEmpty)
                                            .and(
                                                GraphQLTypeUtil.unwrapAll(gfd.type)
                                                    .toOption()
                                                    .filterIsInstance<GraphQLFieldsContainer>()
                                                    .map(
                                                        GraphQLFieldsContainer::getFieldDefinitions
                                                    )
                                                    .filter(
                                                        List<GraphQLFieldDefinition>::isNotEmpty
                                                    )
                                            )
                                    }
                                    .isDefined()
                        ) {
                            """selected_field either does not match domain_field_coordinates.field_name 
                                |or has sub-selections 
                                |but does not match a graphql_field_definition with child nodes"""
                                .flatten()
                        }
                        ensure(
                            fieldPathSelections.asSequence().all { p: GQLOperationPath ->
                                p.isDescendentTo(domainPath!!)
                            }
                        ) {
                            "not all field_path_selections belong within domain path"
                        }
                        SchemaOnlyDataElementSourceCallable(
                            domainFieldCoordinates = domainFieldCoordinates!!,
                            domainPath = domainPath!!,
                            domainGraphQLFieldDefinition = domainGraphQLFieldDefinition!!,
                            selections = fieldPathSelections.build(),
                            selectedField = fieldSelection.toOption(),
                            directivePathSelections = directivePathSelections.build(),
                            directivePathSelectionsWithValues =
                                directivePathValueSelections.build(),
                        )
                    }
                    .fold(
                        { message: String ->
                            throw ServiceError.of(
                                """unable to create %s 
                                |for graphql_data_element_source [ message: %s ]"""
                                    .flatten(),
                                SchemaOnlyDataElementSourceCallable::class.simpleName,
                                message
                            )
                        },
                        ::identity
                    )
            }
        }

        private class SchemaOnlyDataElementSourceCallable(
            override val domainFieldCoordinates: FieldCoordinates,
            override val domainPath: GQLOperationPath,
            override val domainGraphQLFieldDefinition: GraphQLFieldDefinition,
            override val selections: ImmutableSet<GQLOperationPath>,
            // override val argumentsByPath: ImmutableMap<GQLOperationPath, GraphQLArgument>,
            // override val selectionsByPath: ImmutableMap<GQLOperationPath,
            // GraphQLFieldDefinition>,
            private val selectedField: Option<Field>,
            private val directivePathSelections: ImmutableSet<GQLOperationPath>,
            private val directivePathSelectionsWithValues: ImmutableMap<GQLOperationPath, Value<*>>,
        ) : DataElementCallable {

            companion object {
                private val METHOD_TAG: String =
                    StandardNamingConventions.SNAKE_CASE.deriveName(
                            SchemaOnlyDataElementSourceCallable::class.simpleName ?: "<NA>"
                        )
                        .qualifiedForm + ".invoke"
                private val logger: Logger = loggerFor<SchemaOnlyDataElementSourceCallable>()
            }

            override fun invoke(
                arguments: ImmutableMap<GQLOperationPath, JsonNode>
            ): Mono<JsonNode> {
                logger.debug(
                    "{}: [ arguments.key: {} ]",
                    METHOD_TAG,
                    arguments.keys.asSequence().joinToString(", ", "{ ", " }")
                )
                return Mono.error { TODO("${METHOD_TAG} not yet implemented") }
            }
        }
    }

    override fun builder(): DataElementCallable.Builder {
        return SchemaOnlyDataElementSourceCallableBuilder()
    }
}
