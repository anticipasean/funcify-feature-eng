package funcify.feature.datasource.rest.naming

import arrow.core.identity
import funcify.feature.naming.NamingConvention
import funcify.feature.naming.NamingConventionFactory
import funcify.feature.naming.StandardNamingConventions

object RestApiSourceNamingConventions {

    enum class ConventionType {
        PATH_GROUP_TYPE_NAMING_CONVENTION,
        PATH_NAME_FIELD_NAMING_CONVENTION,
        PROPERTY_NAME_FIELD_NAMING_CONVENTION,
        REQUEST_RESPONSE_TYPE_NAMING_CONVENTION
    }

    private val PATH_GROUP_TYPE_NAMING_CONVENTION: NamingConvention<String> by lazy {
        NamingConventionFactory.getDefaultFactory()
            .createConventionFrom(StandardNamingConventions.PASCAL_CASE)
            .mapping<String>(::identity)
            .namedAndIdentifiedBy("PathGroupTypeName", PATH_GROUP_TYPE_NAMING_CONVENTION)
    }

    private val REQUEST_RESPONSE_TYPE_NAMING_CONVENTION: NamingConvention<String> by lazy {
        NamingConventionFactory.getDefaultFactory()
            .createConventionFrom(StandardNamingConventions.PASCAL_CASE)
            .mapping<String>(::identity)
            .namedAndIdentifiedBy(
                "RequestResponseTypeName",
                REQUEST_RESPONSE_TYPE_NAMING_CONVENTION
            )
    }

    private val PATH_NAME_FIELD_NAMING_CONVENTION: NamingConvention<String> by lazy {
        NamingConventionFactory.getDefaultFactory()
            .createConventionFrom(StandardNamingConventions.SNAKE_CASE)
            .mapping(::identity)
            .namedAndIdentifiedBy("PathNameFieldName", PROPERTY_NAME_FIELD_NAMING_CONVENTION)
    }

    private val PROPERTY_NAME_FIELD_NAMING_CONVENTION: NamingConvention<String> by lazy {
        NamingConventionFactory.getDefaultFactory()
            .createConventionFrom(StandardNamingConventions.SNAKE_CASE)
            .mapping(::identity)
            .namedAndIdentifiedBy("PropertyNameFieldName", PROPERTY_NAME_FIELD_NAMING_CONVENTION)
    }

    fun getPathGroupTypeNamingConventionForPathGroupPathName(): NamingConvention<String> {
        return PATH_GROUP_TYPE_NAMING_CONVENTION
    }

    fun getRequestOrResponseTypeNamingConventionForRequestOrResponsePathName():
        NamingConvention<String> {
        return REQUEST_RESPONSE_TYPE_NAMING_CONVENTION
    }

    fun getFieldNamingConventionForPathName(): NamingConvention<String> {
        return PATH_NAME_FIELD_NAMING_CONVENTION
    }

    fun getFieldNamingConventionForJsonPropertyName(): NamingConvention<String> {
        return PROPERTY_NAME_FIELD_NAMING_CONVENTION
    }
}
