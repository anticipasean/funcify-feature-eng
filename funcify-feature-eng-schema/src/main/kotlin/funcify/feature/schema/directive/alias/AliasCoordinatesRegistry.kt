package funcify.feature.schema.directive.alias

import arrow.core.Option
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
    ): Result<AliasCoordinatesRegistry>

    fun registerFieldArgumentWithAlias(
        fieldArgumentLocation: Pair<FieldCoordinates, String>,
        alias: String
    ): Result<AliasCoordinatesRegistry>

    fun isAliasForField(alias: String): Boolean {
        return getFieldWithAlias(alias).isDefined()
    }

    fun isAliasForFieldArgument(alias: String): Boolean {
        return getFieldArgumentWithAlias(alias).isNotEmpty()
    }

    fun getFieldWithAlias(alias: String): Option<FieldCoordinates>

    fun getFieldArgumentWithAlias(alias: String): Option<Pair<FieldCoordinates, String>>

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
