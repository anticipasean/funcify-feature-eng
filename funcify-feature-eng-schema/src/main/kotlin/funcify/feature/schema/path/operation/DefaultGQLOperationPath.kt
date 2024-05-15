package funcify.feature.schema.path.operation

import arrow.core.Option
import arrow.core.getOrElse
import arrow.core.getOrNone
import arrow.core.none
import arrow.core.some
import arrow.core.toOption
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.*
import kotlin.reflect.KClass
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList

/**
 * @author smccarron
 * @created 2/20/22
 */
internal data class DefaultGQLOperationPath(
    override val scheme: String = GQLOperationPath.GRAPHQL_OPERATION_PATH_SCHEME,
    override val selection: PersistentList<SelectionSegment> = persistentListOf(),
    override val argument: Option<Pair<String, PersistentList<String>>> = none(),
    override val directive: Option<Pair<String, PersistentList<String>>> = none()
) : GQLOperationPath {

    companion object {

        internal class DefaultBuilder(
            private val existingPath: DefaultGQLOperationPath? = null,
            private var inputScheme: String =
                existingPath?.scheme ?: GQLOperationPath.GRAPHQL_OPERATION_PATH_SCHEME,
            private val selectionBuilder: PersistentList.Builder<SelectionSegment> =
                existingPath?.selection?.builder()
                    ?: persistentListOf<SelectionSegment>().builder(),
            private var argumentName: String? = existingPath?.argument?.orNull()?.first,
            private var argumentPathBuilder: PersistentList.Builder<String> =
                existingPath?.argument?.orNull()?.second?.builder()
                    ?: persistentListOf<String>().builder(),
            private var directiveName: String? = existingPath?.directive?.orNull()?.first,
            private var directivePathBuilder: PersistentList.Builder<String> =
                existingPath?.directive?.orNull()?.second?.builder()
                    ?: persistentListOf<String>().builder(),
        ) : GQLOperationPath.Builder {

            override fun scheme(scheme: String): GQLOperationPath.Builder {
                inputScheme =
                    scheme.toOption().map(String::trim).filter(String::isNotEmpty).getOrElse {
                        inputScheme
                    }
                return this
            }

            private fun noBlanksInSelectionSegment(ss: SelectionSegment): Boolean {
                return when (ss) {
                    is SelectedField -> {
                        noBlanksInSelectedField(ss)
                    }
                    is InlineFragmentSegment -> {
                        ss.typeName.isNotBlank() && noBlanksInSelectedField(ss.selectedField)
                    }
                    is FragmentSpreadSegment -> {
                        ss.typeName.isNotBlank() &&
                            ss.fragmentName.isNotBlank() &&
                            noBlanksInSelectedField(ss.selectedField)
                    }
                }
            }

            private fun noBlanksInSelectedField(sf: SelectedField): Boolean {
                return when (sf) {
                    is FieldSegment -> {
                        sf.fieldName.isNotBlank()
                    }
                    is AliasedFieldSegment -> {
                        sf.alias.isNotBlank() && sf.fieldName.isNotBlank()
                    }
                }
            }

            override fun prependSelection(
                vararg selectionSegment: SelectionSegment
            ): GQLOperationPath.Builder {
                selectionBuilder.addAll(
                    0,
                    selectionSegment.asSequence().filter(::noBlanksInSelectionSegment).toList()
                )
                return this
            }

            override fun prependSelections(
                selectionSegments: List<SelectionSegment>
            ): GQLOperationPath.Builder {
                selectionBuilder.addAll(
                    0,
                    selectionSegments.asSequence().filter(::noBlanksInSelectionSegment).toList()
                )
                return this
            }

            override fun appendSelection(
                vararg selectionSegment: SelectionSegment
            ): GQLOperationPath.Builder {
                selectionSegment.asSequence().filter(::noBlanksInSelectionSegment).fold(
                    selectionBuilder
                ) { sb, ss ->
                    sb.add(ss)
                    sb
                }
                return this
            }

            override fun appendSelections(
                selectionSegments: List<SelectionSegment>
            ): GQLOperationPath.Builder {
                selectionSegments.asSequence().filter(::noBlanksInSelectionSegment).fold(
                    selectionBuilder
                ) { sb, ss ->
                    sb.add(ss)
                    sb
                }
                return this
            }

            override fun dropHeadSelectionSegment(): GQLOperationPath.Builder {
                if (selectionBuilder.isNotEmpty()) {
                    selectionBuilder.removeFirst()
                }
                return this
            }

            override fun dropTailSelectionSegment(): GQLOperationPath.Builder {
                if (selectionBuilder.isNotEmpty()) {
                    selectionBuilder.removeLast()
                }
                return this
            }

            override fun clearSelection(): GQLOperationPath.Builder {
                selectionBuilder.clear()
                return this
            }

            override fun argument(
                name: String,
                pathSegments: List<String>
            ): GQLOperationPath.Builder {
                return when (val trimmedName: String = name.trim()) {
                    "" -> {
                        this
                    }
                    else -> {
                        this.argumentName = trimmedName
                        this.argumentPathBuilder =
                            pathSegments
                                .asSequence()
                                .map(String::trim)
                                .filter(String::isNotEmpty)
                                .toPersistentList()
                                .builder()
                        this
                    }
                }
            }

            override fun argument(
                name: String,
                vararg pathSegment: String
            ): GQLOperationPath.Builder {
                return when (val trimmedName: String = name.trim()) {
                    "" -> {
                        this
                    }
                    else -> {
                        this.argumentName = trimmedName
                        this.argumentPathBuilder =
                            pathSegment
                                .asSequence()
                                .map(String::trim)
                                .filter(String::isNotEmpty)
                                .toPersistentList()
                                .builder()
                        this
                    }
                }
            }

            override fun prependArgumentPathSegment(
                vararg pathSegment: String
            ): GQLOperationPath.Builder {
                if (this.argumentName != null && pathSegment.isNotEmpty()) {
                    (pathSegment.size - 1)
                        .downTo(0)
                        .asSequence()
                        .map { i: Int -> pathSegment[i] }
                        .map(String::trim)
                        .filter(String::isNotEmpty)
                        .fold(this.argumentPathBuilder) { apb, s ->
                            apb.add(0, s)
                            apb
                        }
                }
                return this
            }

            override fun prependArgumentPathSegments(
                pathSegments: List<String>
            ): GQLOperationPath.Builder {
                if (this.argumentName != null && pathSegments.isNotEmpty()) {
                    this.argumentPathBuilder.addAll(
                        0,
                        pathSegments
                            .asSequence()
                            .map(String::trim)
                            .filter(String::isNotEmpty)
                            .toList()
                    )
                }
                return this
            }

            override fun appendArgumentPathSegment(
                vararg pathSegment: String
            ): GQLOperationPath.Builder {
                if (this.argumentName != null) {
                    pathSegment.asSequence().map(String::trim).filter(String::isNotEmpty).fold(
                        argumentPathBuilder
                    ) { apb, s ->
                        apb.add(s)
                        apb
                    }
                }
                return this
            }

            override fun appendArgumentPathSegments(
                pathSegments: List<String>
            ): GQLOperationPath.Builder {
                if (this.argumentName != null) {
                    pathSegments.asSequence().map(String::trim).filter(String::isNotEmpty).fold(
                        argumentPathBuilder
                    ) { apb, s ->
                        apb.add(s)
                        apb
                    }
                }
                return this
            }

            override fun dropHeadArgumentPathSegment(): GQLOperationPath.Builder {
                if (argumentName != null && argumentPathBuilder.isNotEmpty()) {
                    argumentPathBuilder.removeFirst()
                }
                return this
            }

            override fun dropTailArgumentPathSegment(): GQLOperationPath.Builder {
                if (this.argumentName != null && this.argumentPathBuilder.isNotEmpty()) {
                    this.argumentPathBuilder.removeLast()
                }
                return this
            }

            override fun clearArgument(): GQLOperationPath.Builder {
                this.argumentName = null
                this.argumentPathBuilder.clear()
                return this
            }

            override fun directive(
                name: String,
                pathSegments: List<String>
            ): GQLOperationPath.Builder {
                return when (val trimmedName: String = name.trim()) {
                    "" -> {
                        this
                    }
                    else -> {
                        this.directiveName = trimmedName
                        this.directivePathBuilder =
                            pathSegments
                                .asSequence()
                                .map(String::trim)
                                .filter(String::isNotEmpty)
                                .toPersistentList()
                                .builder()
                        this
                    }
                }
            }

            override fun directive(
                name: String,
                vararg pathSegment: String
            ): GQLOperationPath.Builder {
                return when (val trimmedName: String = name.trim()) {
                    "" -> {
                        this
                    }
                    else -> {
                        this.directiveName = trimmedName
                        this.directivePathBuilder =
                            pathSegment
                                .asSequence()
                                .map(String::trim)
                                .filter(String::isNotEmpty)
                                .toPersistentList()
                                .builder()
                        this
                    }
                }
            }

            override fun prependDirectivePathSegment(
                vararg pathSegment: String
            ): GQLOperationPath.Builder {
                if (this.directiveName != null && pathSegment.isNotEmpty()) {
                    (pathSegment.size - 1)
                        .downTo(0)
                        .asSequence()
                        .map { i: Int -> pathSegment[i] }
                        .map(String::trim)
                        .filter(String::isNotEmpty)
                        .fold(directivePathBuilder) { dpb, s ->
                            dpb.add(0, s)
                            dpb
                        }
                }
                return this
            }

            override fun prependDirectivePathSegments(
                pathSegments: List<String>
            ): GQLOperationPath.Builder {
                if (this.directiveName != null && pathSegments.isNotEmpty()) {
                    this.directivePathBuilder.addAll(
                        0,
                        pathSegments
                            .asSequence()
                            .map(String::trim)
                            .filter(String::isNotEmpty)
                            .toList()
                    )
                }
                return this
            }

            override fun appendDirectivePathSegment(
                vararg pathSegment: String
            ): GQLOperationPath.Builder {
                if (this.directiveName != null) {
                    pathSegment.asSequence().map(String::trim).filter(String::isNotEmpty).fold(
                        directivePathBuilder
                    ) { dpb, s ->
                        dpb.add(s)
                        dpb
                    }
                }
                return this
            }

            override fun appendDirectivePathSegments(
                pathSegments: List<String>
            ): GQLOperationPath.Builder {
                if (this.directiveName != null) {
                    pathSegments.asSequence().map(String::trim).filter(String::isNotEmpty).fold(
                        directivePathBuilder
                    ) { dpb, s ->
                        dpb.add(s)
                        dpb
                    }
                }
                return this
            }

            override fun dropHeadDirectivePathSegment(): GQLOperationPath.Builder {
                if (directiveName != null && directivePathBuilder.isNotEmpty()) {
                    directivePathBuilder.removeFirst()
                }
                return this
            }

            override fun dropTailDirectivePathSegment(): GQLOperationPath.Builder {
                if (directiveName != null && directivePathBuilder.isNotEmpty()) {
                    directivePathBuilder.removeLast()
                }
                return this
            }

            override fun clearDirective(): GQLOperationPath.Builder {
                this.directiveName = null
                this.directivePathBuilder.clear()
                return this
            }

            override fun build(): GQLOperationPath {
                return DefaultGQLOperationPath(
                    scheme = inputScheme,
                    selection = selectionBuilder.build(),
                    argument =
                        when (argumentName) {
                            null -> {
                                none()
                            }
                            else -> {
                                (argumentName!! to argumentPathBuilder.build()).some()
                            }
                        },
                    directive =
                        when (directiveName) {
                            null -> {
                                none()
                            }
                            else -> {
                                (directiveName!! to directivePathBuilder.build()).some()
                            }
                        }
                )
            }
        }

        private enum class SelectionType {
            CONTAINS_FRAGMENT_SPREAD_REFERENCE,
            CONTAINS_INLINE_FRAGMENT_REFERENCE,
            CONTAINS_ALIASED_FIELD
        }
    }

    private val internedURI: URI by lazy {
        URI.create(
            buildString {
                append(scheme)
                append(':')
                append(
                    selection
                        .asSequence()
                        .map(SelectionSegment::toString)
                        .map { s: String -> URLEncoder.encode(s, StandardCharsets.UTF_8) }
                        .joinToString("/", "/")
                )
                if (argument.isDefined()) {
                    append("?")
                    append(argument.orNull()?.first)
                    if (argument.orNull()?.second?.isNotEmpty() == true) {
                        append("=")
                        append(
                            (argument.orNull()?.second?.asSequence() ?: emptySequence())
                                .map { ps: String -> URLEncoder.encode(ps, StandardCharsets.UTF_8) }
                                .joinToString("/", "/")
                        )
                    }
                }
                if (directive.isDefined()) {
                    append("#")
                    append(directive.orNull()?.first)
                    if (directive.orNull()?.second?.isNotEmpty() == true) {
                        append("=")
                        append(
                            (directive.orNull()?.second?.asSequence() ?: emptySequence())
                                .map { ps: String -> URLEncoder.encode(ps, StandardCharsets.UTF_8) }
                                .joinToString("/", "/")
                        )
                    }
                }
            }
        )
    }

    private val internedParentPath: Option<GQLOperationPath> by lazy { super.getParentPath() }

    private val internedStringRep: String by lazy { internedURI.toASCIIString() }

    private val internedSelectionTypes: Set<SelectionType> by lazy {
        when {
            selection.isEmpty() -> {
                EnumSet.noneOf(SelectionType::class.java)
            }
            else -> {
                val selectionSegmentGroupCounts: Map<KClass<out SelectionSegment>, Int> =
                    selection.fold(mutableMapOf<KClass<out SelectionSegment>, Int>()) {
                        mm: MutableMap<KClass<out SelectionSegment>, Int>,
                        ss: SelectionSegment ->
                        when (ss) {
                            is FieldSegment -> {}
                            is AliasedFieldSegment -> {
                                mm[AliasedFieldSegment::class] =
                                    (mm[AliasedFieldSegment::class] ?: 0) + 1
                            }
                            is FragmentSpreadSegment -> {
                                when (ss.selectedField) {
                                    is AliasedFieldSegment -> {
                                        mm[AliasedFieldSegment::class] =
                                            (mm[AliasedFieldSegment::class] ?: 0) + 1
                                        mm[FragmentSpreadSegment::class] =
                                            (mm[FragmentSpreadSegment::class] ?: 0) + 1
                                    }
                                    is FieldSegment -> {
                                        mm[FragmentSpreadSegment::class] =
                                            (mm[FragmentSpreadSegment::class] ?: 0) + 1
                                    }
                                }
                            }
                            is InlineFragmentSegment -> {
                                when (ss.selectedField) {
                                    is AliasedFieldSegment -> {
                                        mm[AliasedFieldSegment::class] =
                                            (mm[AliasedFieldSegment::class] ?: 0) + 1
                                        mm[InlineFragmentSegment::class] =
                                            (mm[InlineFragmentSegment::class] ?: 0) + 1
                                    }
                                    is FieldSegment -> {
                                        mm[InlineFragmentSegment::class] =
                                            (mm[InlineFragmentSegment::class] ?: 0) + 1
                                    }
                                }
                            }
                        }
                        mm
                    }
                when {
                    selectionSegmentGroupCounts
                        .getOrNone(InlineFragmentSegment::class)
                        .filter { it > 0 }
                        .zip(
                            selectionSegmentGroupCounts
                                .getOrNone(FragmentSpreadSegment::class)
                                .filter { it > 0 }
                        )
                        .isDefined() -> {
                        EnumSet.of(
                            SelectionType.CONTAINS_FRAGMENT_SPREAD_REFERENCE,
                            SelectionType.CONTAINS_INLINE_FRAGMENT_REFERENCE
                        )
                    }
                    selectionSegmentGroupCounts
                        .getOrNone(InlineFragmentSegment::class)
                        .filter { it > 0 }
                        .isDefined() -> {
                        EnumSet.of(SelectionType.CONTAINS_INLINE_FRAGMENT_REFERENCE)
                    }
                    selectionSegmentGroupCounts
                        .getOrNone(FragmentSpreadSegment::class)
                        .filter { it > 0 }
                        .isDefined() -> {
                        EnumSet.of(SelectionType.CONTAINS_FRAGMENT_SPREAD_REFERENCE)
                    }
                    else -> {
                        EnumSet.noneOf(SelectionType::class.java)
                    }
                }.let { fragmentSelectionType: EnumSet<SelectionType> ->
                    when {
                        selectionSegmentGroupCounts
                            .getOrNone(AliasedFieldSegment::class)
                            .filter { it > 0 }
                            .isDefined() -> {
                            fragmentSelectionType.apply {
                                add(SelectionType.CONTAINS_ALIASED_FIELD)
                            }
                        }
                        else -> {
                            fragmentSelectionType
                        }
                    }
                }
            }
        }
    }

    private val internedUnaliased: GQLOperationPath by lazy {
        if (SelectionType.CONTAINS_ALIASED_FIELD !in internedSelectionTypes) {
            this
        } else {
            selection
                .fold(DefaultBuilder(this).clearSelection()) {
                    b: GQLOperationPath.Builder,
                    ss: SelectionSegment ->
                    when (ss) {
                        is AliasedFieldSegment -> {
                            b.appendField(ss.fieldName)
                        }
                        is FieldSegment -> {
                            b.appendField(ss.fieldName)
                        }
                        is FragmentSpreadSegment -> {
                            b.appendFragmentSpread(
                                ss.fragmentName,
                                ss.typeName,
                                when (val sf: SelectedField = ss.selectedField) {
                                    is AliasedFieldSegment -> sf.fieldName
                                    is FieldSegment -> sf.fieldName
                                }
                            )
                        }
                        is InlineFragmentSegment -> {
                            b.appendInlineFragment(
                                ss.typeName,
                                when (val sf: SelectedField = ss.selectedField) {
                                    is AliasedFieldSegment -> sf.fieldName
                                    is FieldSegment -> sf.fieldName
                                }
                            )
                        }
                    }
                }
                .build()
        }
    }

    override fun refersToSelectionOnFragment(): Boolean {
        return super.refersToSelection() &&
            SelectionType.CONTAINS_FRAGMENT_SPREAD_REFERENCE in internedSelectionTypes ||
            SelectionType.CONTAINS_INLINE_FRAGMENT_REFERENCE in internedSelectionTypes
    }

    override fun refersToSelectionOnInlineFragment(): Boolean {
        return super.refersToSelection() &&
            SelectionType.CONTAINS_INLINE_FRAGMENT_REFERENCE in internedSelectionTypes
    }

    override fun refersToSelectionOnFragmentSpread(): Boolean {
        return super.refersToSelection() &&
            SelectionType.CONTAINS_FRAGMENT_SPREAD_REFERENCE in internedSelectionTypes
    }

    override fun containsAliasForField(): Boolean {
        return SelectionType.CONTAINS_ALIASED_FIELD in internedSelectionTypes
    }

    override fun toURI(): URI {
        return internedURI
    }

    override fun getParentPath(): Option<GQLOperationPath> {
        return internedParentPath
    }

    override fun transform(
        mapper: GQLOperationPath.Builder.() -> GQLOperationPath.Builder
    ): GQLOperationPath {
        return mapper.invoke(DefaultBuilder(this)).build()
    }

    override fun toString(): String {
        return internedStringRep
    }

    override fun toDecodedURIString(): String {
        return buildString {
            append(scheme)
            append(':')
            append(selection.asSequence().joinToString("/", "/"))
            if (argument.isDefined()) {
                append("?")
                append(argument.orNull()?.first)
                if (argument.orNull()?.second?.isNotEmpty() == true) {
                    append("=")
                    append(
                        (argument.orNull()?.second?.asSequence() ?: emptySequence()).joinToString(
                            "/",
                            "/"
                        )
                    )
                }
            }
            if (directive.isDefined()) {
                append("#")
                append(directive.orNull()?.first)
                if (directive.orNull()?.second?.isNotEmpty() == true) {
                    append("=")
                    append(
                        (directive.orNull()?.second?.asSequence() ?: emptySequence()).joinToString(
                            "/",
                            "/"
                        )
                    )
                }
            }
        }
    }

    override fun toUnaliasedPath(): GQLOperationPath {
        return internedUnaliased
    }
}
