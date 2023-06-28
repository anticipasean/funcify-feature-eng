package funcify.feature.schema

import graphql.language.FieldDefinition

/**
 * @author smccarron
 * @created 2023-06-28
 */
interface Transformer {

    val sdlFieldDefinition: FieldDefinition

}
