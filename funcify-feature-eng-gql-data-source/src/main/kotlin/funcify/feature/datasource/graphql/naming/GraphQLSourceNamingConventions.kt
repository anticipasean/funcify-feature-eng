package funcify.feature.datasource.graphql.naming

import funcify.feature.datasource.graphql.naming.GraphQLSourceNamingConventions.ConventionType.FIELD_NAMING_CONVENTION
import funcify.feature.datasource.graphql.naming.GraphQLSourceNamingConventions.ConventionType.PATH_NAMING_CONVENTION
import funcify.feature.naming.NamingConvention
import funcify.feature.naming.NamingConventionFactory
import graphql.schema.GraphQLFieldDefinition


/**
 *
 * @author smccarron
 * @created 4/9/22
 */
object GraphQLSourceNamingConventions {

    enum class ConventionType {
        PATH_NAMING_CONVENTION,
        FIELD_NAMING_CONVENTION
    }

    private val FIELD_DEFINITION_PATH_NAMING_CONVENTION: NamingConvention<GraphQLFieldDefinition> by lazy {
        NamingConventionFactory.getDefaultFactory()
                .createConventionFor<GraphQLFieldDefinition>()
                .whenInputProvided {
                    treatAsOneSegment { graphQLFieldDefinition: GraphQLFieldDefinition ->
                        graphQLFieldDefinition.name
                    }
                }
                .followConvention {
                    forEverySegment {
                        forAnyCharacter {
                            transformCharactersByWindow {
                                anyUppercaseCharacter().precededByALowercaseLetter()
                                        .transformInto { c: Char ->
                                            "_$c"
                                        }
                            }
                        }
                    }
                    splitAnySegmentsWith('_')
                    forEverySegment {
                        forAnyCharacter {
                            makeAllLowercase()
                        }
                    }
                }
                .joinSegmentsWith('_')
                .namedAndIdentifiedBy("GraphQLFieldDefinitionPathName",
                                      PATH_NAMING_CONVENTION)
    }

    private val FIELD_DEFINITION_FIELD_NAMING_CONVENTION: NamingConvention<GraphQLFieldDefinition> by lazy {
        NamingConventionFactory.getDefaultFactory()
                .createConventionFor<GraphQLFieldDefinition>()
                .whenInputProvided {
                    treatAsOneSegment { graphQLFieldDefinition: GraphQLFieldDefinition ->
                        graphQLFieldDefinition.name
                    }
                }
                .followConvention {
                    forEverySegment {
                        forAnyCharacter {
                            transformCharactersByWindow {
                                anyUppercaseCharacter().precededByALowercaseLetter()
                                        .transformInto { c: Char ->
                                            "_$c"
                                        }
                            }
                        }
                    }
                    splitAnySegmentsWith('_')
                }
                .joinSegmentsWithoutDelimiter()
                .namedAndIdentifiedBy("GraphQLFieldDefinitionFieldName",
                                      FIELD_NAMING_CONVENTION)
    }

    fun getPathNamingConventionForGraphQLFieldDefinitions(): NamingConvention<GraphQLFieldDefinition> {
        return FIELD_DEFINITION_PATH_NAMING_CONVENTION
    }

    fun getFieldNamingConventionForGraphQLFieldDefinitions(): NamingConvention<GraphQLFieldDefinition> {
        return FIELD_DEFINITION_FIELD_NAMING_CONVENTION
    }


}