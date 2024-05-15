package funcify.feature.schema.path.result

import funcify.feature.schema.path.operation.AliasedFieldSegment
import funcify.feature.schema.path.operation.FieldSegment
import funcify.feature.schema.path.operation.FragmentSpreadSegment
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.schema.path.operation.InlineFragmentSegment
import funcify.feature.schema.path.operation.SelectedField
import funcify.feature.schema.path.operation.SelectionSegment
import funcify.feature.tools.container.attempt.Try
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

internal object OperationToSchemaResultPathTransformer : (GQLOperationPath) -> Try<GQLResultPath> {

    private val cache: ConcurrentMap<GQLOperationPath, Try<GQLResultPath>> = ConcurrentHashMap()

    override fun invoke(operationPath: GQLOperationPath): Try<GQLResultPath> {
        return cache.computeIfAbsent(operationPath) { op: GQLOperationPath ->
            Try.attempt { GQLResultPath.of(appendElementSegmentsToBuilder(op)) }
        }
    }

    private fun appendElementSegmentsToBuilder(
        op: GQLOperationPath
    ): (GQLResultPath.Builder) -> GQLResultPath.Builder {
        return { builder: GQLResultPath.Builder ->
            when {
                op.refersToDirective() || op.refersToObjectFieldWithinDirectiveArgumentValue() -> {
                    throw IllegalArgumentException(
                        """%s refers to directive or object field 
                            |within directive argument value; 
                            |cannot be converted into %s"""
                            .format(
                                GQLOperationPath::class.simpleName,
                                GQLResultPath::class.simpleName
                            )
                    )
                }
                op.refersToArgument() || op.refersToObjectFieldWithinArgumentValue() -> {
                    throw IllegalArgumentException(
                        """%s refers to argument or object field 
                            |within argument; 
                            |cannot be converted into %s"""
                            .format(
                                GQLOperationPath::class.simpleName,
                                GQLResultPath::class.simpleName
                            )
                    )
                }
                else -> {
                    op.selection.asSequence().fold(builder) {
                        b: GQLResultPath.Builder,
                        ss: SelectionSegment ->
                        when (ss) {
                            is AliasedFieldSegment -> {
                                b.appendNameSegment(ss.alias)
                            }
                            is FieldSegment -> {
                                b.appendNameSegment(ss.fieldName)
                            }
                            is FragmentSpreadSegment -> {
                                when (val sf: SelectedField = ss.selectedField) {
                                    is AliasedFieldSegment -> {
                                        b.appendNameSegment(sf.alias)
                                    }
                                    is FieldSegment -> {
                                        b.appendNameSegment(sf.fieldName)
                                    }
                                }
                            }
                            is InlineFragmentSegment -> {
                                when (val sf: SelectedField = ss.selectedField) {
                                    is AliasedFieldSegment -> {
                                        b.appendNameSegment(sf.alias)
                                    }
                                    is FieldSegment -> {
                                        b.appendNameSegment(sf.fieldName)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
