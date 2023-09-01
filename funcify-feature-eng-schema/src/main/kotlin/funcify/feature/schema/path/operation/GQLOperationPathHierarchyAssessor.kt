package funcify.feature.schema.path.operation

import arrow.core.getOrElse
import funcify.feature.schema.path.operation.GQLOperationPathHierarchyAssessor.RelationshipType

internal object GQLOperationPathHierarchyAssessor :
    (GQLOperationPath, GQLOperationPath) -> RelationshipType {

    enum class RelationshipType {
        IDENTITY,
        PARENT_CHILD,
        CHILD_PARENT,
        SIBLING_SIBLING,
        ANCESTOR_DESCENDENT,
        DESCENDENT_ANCESTOR,
        NOT_RELATED
    }

    override fun invoke(p1: GQLOperationPath, p2: GQLOperationPath): RelationshipType {
        return when {
            p1 == p2 -> {
                RelationshipType.IDENTITY
            }
            p1.getParentPath().map { pp: GQLOperationPath -> pp == p2 }.getOrElse { false } -> {
                RelationshipType.CHILD_PARENT
            }
            p2.getParentPath().map { pp: GQLOperationPath -> pp == p1 }.getOrElse { false } -> {
                RelationshipType.PARENT_CHILD
            }
            p1.getParentPath().zip(p2.getParentPath(), GQLOperationPath::equals).getOrElse {
                false
            } -> {
                RelationshipType.SIBLING_SIBLING
            }
            else -> {
                when {
                    p1.isRoot() -> {
                        RelationshipType.ANCESTOR_DESCENDENT
                    }
                    p2.isRoot() -> {
                        RelationshipType.DESCENDENT_ANCESTOR
                    }
                    else -> {
                        assessNonRootAncestorOrUnrelatedRelationship(p1, p2)
                    }
                }
            }
        }
    }

    private fun assessNonRootAncestorOrUnrelatedRelationship(
        p1: GQLOperationPath,
        p2: GQLOperationPath
    ): RelationshipType {
        return when {
            p1.selection.size < p2.selection.size -> {
                if (
                    p1.selection
                        .asSequence()
                        .zip(p2.selection.asSequence(), SelectionSegment::equals)
                        .firstOrNull { r: Boolean -> !r } != false
                ) {
                    RelationshipType.ANCESTOR_DESCENDENT
                } else {
                    RelationshipType.NOT_RELATED
                }
            }
            p1.selection.size > p2.selection.size -> {
                if (
                    p1.selection
                        .asSequence()
                        .zip(p2.selection.asSequence(), SelectionSegment::equals)
                        .firstOrNull { r: Boolean -> !r } != false
                ) {
                    RelationshipType.DESCENDENT_ANCESTOR
                } else {
                    RelationshipType.NOT_RELATED
                }
            }
            else -> {
                if (
                    p1.selection
                        .asSequence()
                        .zip(p2.selection.asSequence(), SelectionSegment::equals)
                        .firstOrNull { r: Boolean -> !r } != false
                ) {
                    assessNonRootAncestorOrUnrelatedRelationshipOnArgumentName(p1, p2)
                } else {
                    RelationshipType.NOT_RELATED
                }
            }
        }
    }

    private fun assessNonRootAncestorOrUnrelatedRelationshipOnArgumentName(
        p1: GQLOperationPath,
        p2: GQLOperationPath
    ): RelationshipType {
        return when {
            p1.argument.isEmpty() && p2.argument.isEmpty() -> {
                assessNonRootAncestorOrUnrelatedRelationshipOnDirectiveName(p1, p2)
            }
            p1.argument.isEmpty() && p2.argument.isDefined() -> {
                RelationshipType.ANCESTOR_DESCENDENT
            }
            p1.argument.isDefined() && p2.argument.isEmpty() -> {
                RelationshipType.DESCENDENT_ANCESTOR
            }
            else -> {
                assessNonRootAncestorOrUnrelatedRelationshipOnArgumentPath(p1, p2)
            }
        }
    }

    private fun assessNonRootAncestorOrUnrelatedRelationshipOnDirectiveName(
        p1: GQLOperationPath,
        p2: GQLOperationPath
    ): RelationshipType {
        return when {
            p1.directive.isEmpty() && p2.directive.isEmpty() -> {
                RelationshipType.IDENTITY
            }
            p1.directive.isEmpty() && p2.directive.isDefined() -> {
                RelationshipType.ANCESTOR_DESCENDENT
            }
            p1.directive.isDefined() && p2.directive.isEmpty() -> {
                RelationshipType.DESCENDENT_ANCESTOR
            }
            else -> {
                assessNonRootAncestorOrUnrelatedRelationshipOnDirectivePath(p1, p2)
            }
        }
    }

    private fun assessNonRootAncestorOrUnrelatedRelationshipOnArgumentPath(
        p1: GQLOperationPath,
        p2: GQLOperationPath
    ): RelationshipType {
        return when {
            p1.argument
                .zip(p2.argument) {
                    (an1: String, ap1: List<String>),
                    (an2: String, ap2: List<String>) ->
                    an1 == an2 && ap1.isEmpty() && ap2.isNotEmpty()
                }
                .getOrElse { false } -> {
                RelationshipType.ANCESTOR_DESCENDENT
            }
            p1.argument
                .zip(p2.argument) {
                    (an1: String, ap1: List<String>),
                    (an2: String, ap2: List<String>) ->
                    an1 == an2 && ap1.isNotEmpty() && ap2.isEmpty()
                }
                .getOrElse { false } -> {
                RelationshipType.DESCENDENT_ANCESTOR
            }
            p1.argument
                .zip(p2.argument) {
                    (an1: String, ap1: List<String>),
                    (an2: String, ap2: List<String>) ->
                    an1 != an2
                }
                .getOrElse { false } -> {
                RelationshipType.NOT_RELATED
            }
            else -> {
                p1.argument
                    .zip(p2.argument) {
                        (an1: String, ap1: List<String>),
                        (an2: String, ap2: List<String>) ->
                        when {
                            ap1.size < ap2.size -> {
                                if (
                                    ap1.asSequence()
                                        .zip(ap2.asSequence(), String::equals)
                                        .firstOrNull { r: Boolean -> !r } != false
                                ) {
                                    RelationshipType.ANCESTOR_DESCENDENT
                                } else {
                                    RelationshipType.NOT_RELATED
                                }
                            }
                            ap1.size > ap2.size -> {
                                if (
                                    ap1.asSequence()
                                        .zip(ap2.asSequence(), String::equals)
                                        .firstOrNull { r: Boolean -> !r } != false
                                ) {
                                    RelationshipType.DESCENDENT_ANCESTOR
                                } else {
                                    RelationshipType.NOT_RELATED
                                }
                            }
                            else -> {
                                if (
                                    ap1.asSequence()
                                        .zip(ap2.asSequence(), String::equals)
                                        .firstOrNull { r: Boolean -> !r } != false
                                ) {
                                    assessNonRootAncestorOrUnrelatedRelationshipOnDirectiveName(
                                        p1,
                                        p2
                                    )
                                } else {
                                    RelationshipType.NOT_RELATED
                                }
                            }
                        }
                    }
                    .getOrElse { RelationshipType.NOT_RELATED }
            }
        }
    }

    private fun assessNonRootAncestorOrUnrelatedRelationshipOnDirectivePath(
        p1: GQLOperationPath,
        p2: GQLOperationPath
    ): RelationshipType {
        return when {
            p1.directive.isEmpty() && p2.directive.isEmpty() -> {
                RelationshipType.IDENTITY
            }
            p1.directive
                .zip(p2.directive) {
                    (dn1: String, dp1: List<String>),
                    (dn2: String, dp2: List<String>) ->
                    dn1 == dn2 && dp1.isEmpty() && dp2.isNotEmpty()
                }
                .getOrElse { false } -> {
                RelationshipType.ANCESTOR_DESCENDENT
            }
            p1.directive
                .zip(p2.directive) {
                    (dn1: String, dp1: List<String>),
                    (dn2: String, dp2: List<String>) ->
                    dn1 == dn2 && dp1.isNotEmpty() && dp2.isEmpty()
                }
                .getOrElse { false } -> {
                RelationshipType.DESCENDENT_ANCESTOR
            }
            p1.directive
                .zip(p2.directive) {
                    (dn1: String, dp1: List<String>),
                    (dn2: String, dp2: List<String>) ->
                    dn1 != dn2
                }
                .getOrElse { false } -> {
                RelationshipType.NOT_RELATED
            }
            else -> {
                p1.directive
                    .zip(p2.directive) {
                        (dn1: String, dp1: List<String>),
                        (dn2: String, dp2: List<String>) ->
                        when {
                            dp1.size < dp2.size -> {
                                if (
                                    dp1.asSequence()
                                        .zip(dp2.asSequence(), String::equals)
                                        .firstOrNull { r: Boolean -> !r } != false
                                ) {
                                    RelationshipType.ANCESTOR_DESCENDENT
                                } else {
                                    RelationshipType.NOT_RELATED
                                }
                            }
                            dp1.size > dp2.size -> {
                                if (
                                    dp1.asSequence()
                                        .zip(dp2.asSequence(), String::equals)
                                        .firstOrNull { r: Boolean -> !r } != false
                                ) {
                                    RelationshipType.DESCENDENT_ANCESTOR
                                } else {
                                    RelationshipType.NOT_RELATED
                                }
                            }
                            else -> {
                                if (
                                    dp1.asSequence()
                                        .zip(dp2.asSequence(), String::equals)
                                        .firstOrNull { r: Boolean -> !r } != false
                                ) {
                                    RelationshipType.IDENTITY
                                } else {
                                    RelationshipType.NOT_RELATED
                                }
                            }
                        }
                    }
                    .getOrElse { RelationshipType.NOT_RELATED }
            }
        }
    }
}
