package funcify.feature.datasource.graphql.source.callable

import arrow.core.continuations.eagerEffect
import arrow.core.continuations.ensureNotNull
import arrow.core.filterIsInstance
import arrow.core.identity
import arrow.core.lastOrNone
import arrow.core.toOption
import funcify.feature.error.ServiceError
import funcify.feature.schema.dataelement.DataElementCallable
import funcify.feature.schema.path.operation.FieldSegment
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import graphql.language.Field
import graphql.language.Selection
import graphql.language.SelectionSet
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLFieldsContainer
import graphql.schema.GraphQLTypeUtil
import org.slf4j.Logger

internal class SchemaOnlyDataElementSourceCallableBuilder :
    BaseDataElementCallableBuilder<SchemaOnlyDataElementSourceCallableBuilder>() {

    companion object {
        private val logger: Logger = loggerFor<SchemaOnlyDataElementSourceCallable>()
    }

    override fun build(): DataElementCallable {
        if (logger.isDebugEnabled) {
            logger.debug("build: [ domain_field_coordinates: {} ]", this.domainFieldCoordinates)
        }
        return eagerEffect<String, SchemaOnlyDataElementSourceCallable> {
                ensureNotNull(domainFieldCoordinates) { "domain_field_coordinates not provided" }
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
                            .filter { f: Field -> f.name == domainFieldCoordinates!!.fieldName }
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
                                            .map(GraphQLFieldsContainer::getFieldDefinitions)
                                            .filter(List<GraphQLFieldDefinition>::isNotEmpty)
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
                    directivePathSelectionsWithValues = directivePathValueSelections.build(),
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
