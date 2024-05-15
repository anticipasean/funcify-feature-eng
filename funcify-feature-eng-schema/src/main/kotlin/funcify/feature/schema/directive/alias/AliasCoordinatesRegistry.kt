package funcify.feature.schema.directive.alias

import graphql.schema.FieldCoordinates
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentMapOf

/**
 * @author smccarron
 * @created 2023-08-22
 */
interface AliasCoordinatesRegistry {

    companion object {
        fun empty(): AliasCoordinatesRegistry {
            return DefaultAliasCoordinatesRegistry(
                fieldAliasesByCoordinates = persistentMapOf(),
                fieldArgumentAliasesByLocation = persistentMapOf()
            )
        }
    }

    fun registerFieldWithAlias(
        fieldCoordinates: FieldCoordinates,
        alias: String
    ): AliasCoordinatesRegistry

    fun registerFieldArgumentWithAlias(
        fieldArgumentLocation: Pair<FieldCoordinates, String>,
        alias: String
    ): AliasCoordinatesRegistry

    fun isAliasForField(alias: String): Boolean {
        return getFieldsWithAlias(alias).isNotEmpty()
    }

    fun isAliasForFieldArgument(alias: String): Boolean {
        return getFieldArgumentsWithAlias(alias).isNotEmpty()
    }

    fun getFieldsWithAlias(alias: String): ImmutableSet<FieldCoordinates>

    fun getFieldArgumentsWithAlias(alias: String): ImmutableSet<Pair<FieldCoordinates, String>>

    fun getAllAliasesForField(fieldCoordinates: FieldCoordinates): ImmutableSet<String>

    fun getAllAliasesForFieldArgument(
        fieldCoordinates: FieldCoordinates,
        argumentName: String
    ): ImmutableSet<String> {
        return getAllAliasesForFieldArgument(
            fieldArgumentLocation = fieldCoordinates to argumentName
        )
    }

    fun getAllAliasesForFieldArgument(
        fieldArgumentLocation: Pair<FieldCoordinates, String>
    ): ImmutableSet<String>
}
