package funcify.feature.datasource.rest.naming

import arrow.core.identity
import funcify.feature.naming.NamingConvention
import funcify.feature.naming.NamingConventionFactory
import funcify.feature.naming.StandardNamingConventions

object RestApiSourceNamingConventions {

    enum class ConventionType {
        PATH_GROUP_TYPE_NAMING_CONVENTION,
        PROPERTY_NAME_FIELD_NAMING_CONVENTION,
        REQUEST_TYPE_NAMING_CONVENTION,
        RESPONSE_TYPE_NAMING_CONVENTION
    }

    private val PATH_GROUP_TYPE_NAMING_CONVENTION: NamingConvention<String> by lazy {
        NamingConventionFactory.getDefaultFactory()
            .createConventionFrom(StandardNamingConventions.PASCAL_CASE)
            .mapping<String>(::identity)
            .namedAndIdentifiedBy(
                RestApiSourceNamingConventions::class.qualifiedName!!,
                ConventionType.PATH_GROUP_TYPE_NAMING_CONVENTION
            )
    }

    private val REQUEST_TYPE_NAMING_CONVENTION: NamingConvention<String> by lazy {
        NamingConventionFactory.getDefaultFactory()
            .createConventionFrom(StandardNamingConventions.PASCAL_CASE)
            .mapping<String> { s -> s + "Input" }
            .namedAndIdentifiedBy(
                RestApiSourceNamingConventions::class.qualifiedName!!,
                ConventionType.REQUEST_TYPE_NAMING_CONVENTION
            )
    }

    private val RESPONSE_TYPE_NAMING_CONVENTION: NamingConvention<String> by lazy {
        NamingConventionFactory.getDefaultFactory()
            .createConventionFrom(StandardNamingConventions.PASCAL_CASE)
            .mapping<String>(::identity)
            .namedAndIdentifiedBy(
                RestApiSourceNamingConventions::class.qualifiedName!!,
                ConventionType.RESPONSE_TYPE_NAMING_CONVENTION
            )
    }

    private val PROPERTY_NAME_FIELD_NAMING_CONVENTION: NamingConvention<String> by lazy {
        NamingConventionFactory.getDefaultFactory()
            .createConventionFrom(StandardNamingConventions.CAMEL_CASE)
            .mapping(::identity)
            .namedAndIdentifiedBy(
                RestApiSourceNamingConventions::class.qualifiedName!!,
                ConventionType.PROPERTY_NAME_FIELD_NAMING_CONVENTION
            )
    }

    fun getPathGroupTypeNamingConvention(): NamingConvention<String> {
        return PATH_GROUP_TYPE_NAMING_CONVENTION
    }

    fun getRequestTypeNamingConvention(): NamingConvention<String> {
        return REQUEST_TYPE_NAMING_CONVENTION
    }

    fun getResponseTypeNamingConvention(): NamingConvention<String> {
        return RESPONSE_TYPE_NAMING_CONVENTION
    }
    
    fun getFieldNamingConvention(): NamingConvention<String> {
        return PROPERTY_NAME_FIELD_NAMING_CONVENTION
    }
}
