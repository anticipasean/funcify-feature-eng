package funcify.feature.schema.directive.alias

import arrow.core.Option
import arrow.core.getOrElse
import arrow.core.getOrNone
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import funcify.feature.error.ServiceError
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentMap
import funcify.feature.tools.extensions.PersistentMapExtensions.streamEntries
import funcify.feature.tools.extensions.StringExtensions.flatten
import graphql.schema.FieldCoordinates
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf

/**
 * @author smccarron
 * @created 2023-08-22
 */
internal data class DefaultAliasCoordinatesRegistry(
    private val fieldAliasesByCoordinates: PersistentMap<FieldCoordinates, PersistentSet<String>>,
    private val fieldArgumentAliasesByLocation:
        PersistentMap<Pair<FieldCoordinates, String>, PersistentSet<String>>,
) : AliasCoordinatesRegistry {

    private val fieldCoordinatesByAlias: PersistentMap<String, FieldCoordinates> by lazy {
        fieldAliasesByCoordinates
            .streamEntries()
            .parallel()
            .flatMap { (fc: FieldCoordinates, aliases: PersistentSet<String>) ->
                aliases.stream().map { a: String -> a to fc }
            }
            .reducePairsToPersistentMap()
    }

    private val fieldArgumentLocationsByAlias:
        PersistentMap<String, Pair<FieldCoordinates, String>> by lazy {
        fieldArgumentAliasesByLocation
            .streamEntries()
            .parallel()
            .flatMap { (faLoc: Pair<FieldCoordinates, String>, aliases: PersistentSet<String>) ->
                aliases.stream().map { a: String -> a to faLoc }
            }
            .reducePairsToPersistentMap()
    }

    private val internedJsonRep: JsonNode by lazy {
        fieldAliasesByCoordinates
            .asSequence()
            .map { (fc: FieldCoordinates, aliases: PersistentSet<String>) ->
                JsonNodeFactory.instance
                    .objectNode()
                    .put("typeName", fc.typeName)
                    .put("fieldName", fc.fieldName)
                    .set<ObjectNode>(
                        "aliases",
                        aliases.fold(JsonNodeFactory.instance.arrayNode()) {
                            an: ArrayNode,
                            a: String ->
                            an.add(a)
                        }
                    )
            }
            .plus(
                fieldArgumentAliasesByLocation.asSequence().map {
                    (faLoc: Pair<FieldCoordinates, String>, aliases: PersistentSet<String>) ->
                    JsonNodeFactory.instance
                        .objectNode()
                        .put("typeName", faLoc.first.typeName)
                        .put("fieldName", faLoc.first.fieldName)
                        .put("argumentName", faLoc.second)
                        .set<ObjectNode>(
                            "aliases",
                            aliases.fold(JsonNodeFactory.instance.arrayNode()) {
                                an: ArrayNode,
                                a: String ->
                                an.add(a)
                            }
                        )
                }
            )
            .fold(JsonNodeFactory.instance.arrayNode()) { an: ArrayNode, on: ObjectNode ->
                an.add(on)
            }
    }

    private val internedStringRep: String by lazy { internedJsonRep.toString() }

    override fun toString(): String {
        return internedStringRep
    }

    override fun registerFieldWithAlias(
        fieldCoordinates: FieldCoordinates,
        alias: String
    ): Result<AliasCoordinatesRegistry> {
        return when {
            fieldAliasesByCoordinates
                .getOrNone(fieldCoordinates)
                .filter { aliases: PersistentSet<String> -> alias in aliases }
                .isDefined() -> {
                Result.success(this)
            }
            fieldCoordinatesByAlias
                .getOrNone(alias)
                .filter { fc: FieldCoordinates -> fc != fieldCoordinates }
                .isDefined() -> {
                Result.failure(
                    ServiceError.of(
                        "[ alias: %s ] already defined for different [ field_coordinates: %s ]",
                        alias,
                        fieldCoordinatesByAlias.getOrNone(alias).orNull()
                    )
                )
            }
            else -> {
                Result.success(
                    DefaultAliasCoordinatesRegistry(
                        fieldAliasesByCoordinates =
                            fieldAliasesByCoordinates.put(
                                fieldCoordinates,
                                fieldAliasesByCoordinates
                                    .getOrElse(fieldCoordinates, ::persistentSetOf)
                                    .add(alias)
                            ),
                        fieldArgumentAliasesByLocation = fieldArgumentAliasesByLocation
                    )
                )
            }
        }
    }

    override fun registerFieldArgumentWithAlias(
        fieldArgumentLocation: Pair<FieldCoordinates, String>,
        alias: String
    ): Result<AliasCoordinatesRegistry> {
        return when {
            fieldArgumentAliasesByLocation
                .getOrNone(fieldArgumentLocation)
                .filter { aliases: PersistentSet<String> -> alias in aliases }
                .isDefined() -> {
                Result.success(this)
            }
            fieldArgumentLocationsByAlias
                .getOrNone(alias)
                .filter { fa: Pair<FieldCoordinates, String> -> fieldArgumentLocation != fa }
                .isDefined() -> {
                val faLoc: Option<Pair<FieldCoordinates, String>> =
                    fieldArgumentLocationsByAlias.getOrNone(alias)
                Result.failure(
                    ServiceError.of(
                        """[ alias: %s ] already defined for different 
                        |[ field_argument_location: { coordinates: %s, argument_name: %s } ]"""
                            .flatten(),
                        alias,
                        faLoc.orNull()?.first,
                        faLoc.orNull()?.second
                    )
                )
            }
            else -> {
                Result.success(
                    DefaultAliasCoordinatesRegistry(
                        fieldAliasesByCoordinates = fieldAliasesByCoordinates,
                        fieldArgumentAliasesByLocation =
                            fieldArgumentAliasesByLocation.put(
                                fieldArgumentLocation,
                                fieldArgumentAliasesByLocation
                                    .getOrElse(fieldArgumentLocation, ::persistentSetOf)
                                    .add(alias)
                            )
                    )
                )
            }
        }
    }

    override fun getFieldWithAlias(alias: String): Option<FieldCoordinates> {
        return fieldCoordinatesByAlias.getOrNone(alias)
    }

    override fun getFieldArgumentWithAlias(alias: String): Option<Pair<FieldCoordinates, String>> {
        return fieldArgumentLocationsByAlias.getOrNone(alias)
    }

    override fun getAllAliasesForField(fieldCoordinates: FieldCoordinates): ImmutableSet<String> {
        return fieldAliasesByCoordinates.getOrNone(fieldCoordinates).getOrElse(::persistentSetOf)
    }

    override fun getAllAliasesForFieldArgument(
        fieldArgumentLocation: Pair<FieldCoordinates, String>
    ): ImmutableSet<String> {
        return fieldArgumentAliasesByLocation
            .getOrNone(fieldArgumentLocation)
            .getOrElse(::persistentSetOf)
    }
}
