package funcify.feature.datasource.naming

import funcify.feature.naming.NamingConvention
import funcify.feature.naming.NamingConventionFactory
import funcify.feature.schema.datasource.SourceAttribute
import funcify.feature.schema.datasource.SourceContainerType

internal object SchemaDefinitionLanguageNamingConventions {

    enum class ConventionType {
        OBJECT_TYPE_NAMING_CONVENTION,
        FIELD_NAMING_CONVENTION
    }

    internal val OBJECT_TYPE_NAMING_CONVENTION:
        NamingConvention<SourceContainerType<*, *>> by lazy {
        NamingConventionFactory.getDefaultFactory()
            .createConventionFor<SourceContainerType<*, *>>()
            .whenInputProvided {
                extractOneOrMoreSegmentsWith { sct ->
                    sct.name.nameSegments.asSequence().map { ns -> ns.value }.toList()
                }
            }
            .followConvention {
                splitAnySegmentsWith('_')
                forEverySegment { forLeadingCharacters { makeEachLeadingCharacterUppercase() } }
            }
            .joinSegmentsWithoutDelimiter()
            .namedAndIdentifiedBy(
                SchemaDefinitionLanguageNamingConventions::class.simpleName!!,
                ConventionType.OBJECT_TYPE_NAMING_CONVENTION
            )
    }

    internal val FIELD_NAMING_CONVENTION: NamingConvention<SourceAttribute<*>> by lazy {
        NamingConventionFactory.getDefaultFactory()
            .createConventionFor<SourceAttribute<*>>()
            .whenInputProvided {
                extractOneOrMoreSegmentsWith { sa ->
                    sa.name.nameSegments.asSequence().map { ns -> ns.value }.toList()
                }
            }
            .followConvention {
                splitAnySegmentsWith('_')
                forEverySegment { forLeadingCharacters { makeEachLeadingCharacterUppercase() } }
                forFirstSegment { makeLeadingCharacterOfFirstSegmentLowercase() }
            }
            .joinSegmentsWithoutDelimiter()
            .namedAndIdentifiedBy(
                SchemaDefinitionLanguageNamingConventions::class.simpleName!!,
                ConventionType.FIELD_NAMING_CONVENTION
            )
    }
}
