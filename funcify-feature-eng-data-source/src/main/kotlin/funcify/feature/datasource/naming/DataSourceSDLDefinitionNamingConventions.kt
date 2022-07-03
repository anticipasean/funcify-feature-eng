package funcify.feature.datasource.naming

import funcify.feature.naming.NamingConvention
import funcify.feature.naming.NamingConventionFactory
import funcify.feature.schema.datasource.SourceAttribute
import funcify.feature.schema.datasource.SourceContainerType

object DataSourceSDLDefinitionNamingConventions {

    enum class ConventionType {
        OBJECT_TYPE_NAMING_CONVENTION,
        FIELD_NAMING_CONVENTION
    }

    val OBJECT_TYPE_NAMING_CONVENTION: NamingConvention<SourceContainerType<*, *>> by lazy {
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
                DataSourceSDLDefinitionNamingConventions::class.simpleName!!,
                ConventionType.OBJECT_TYPE_NAMING_CONVENTION
            )
    }

        val FIELD_NAMING_CONVENTION: NamingConvention<SourceAttribute<*>> by lazy {
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
                DataSourceSDLDefinitionNamingConventions::class.simpleName!!,
                ConventionType.FIELD_NAMING_CONVENTION
            )
    }
}
