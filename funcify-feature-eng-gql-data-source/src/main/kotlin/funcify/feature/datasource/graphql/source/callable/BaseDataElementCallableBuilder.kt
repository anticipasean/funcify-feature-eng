package funcify.feature.datasource.graphql.source.callable

import funcify.feature.schema.dataelement.DataElementCallable
import funcify.feature.schema.dataelement.DomainSpecifiedDataElementSource
import funcify.feature.schema.path.operation.GQLOperationPath
import graphql.language.Value
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf

/**
 * @author smccarron
 * @created 2023-09-04
 */
internal abstract class BaseDataElementCallableBuilder<B : DataElementCallable.Builder>(
    protected val existingCallable: DataElementCallable? = null,
    protected var domainSpecifiedDataElementSource: DomainSpecifiedDataElementSource? = null,
    protected var fieldPathSelections: PersistentSet.Builder<GQLOperationPath> =
        persistentSetOf<GQLOperationPath>().builder(),
    protected var directivePathSelections: PersistentSet.Builder<GQLOperationPath> =
        persistentSetOf<GQLOperationPath>().builder(),
    protected var directivePathValueSelections: PersistentMap.Builder<GQLOperationPath, Value<*>> =
        persistentMapOf<GQLOperationPath, Value<*>>().builder()
) : DataElementCallable.Builder {

    companion object {
        private inline fun <reified WB, reified NB : WB> WB.applyToBuilder(
            builderUpdater: WB.() -> Unit
        ): NB {
            return this.apply(builderUpdater) as NB
        }
    }

    override fun selectDomain(
        domainSpecifiedDataElementSource: DomainSpecifiedDataElementSource
    ): B =
        this.applyToBuilder {
            this.domainSpecifiedDataElementSource = domainSpecifiedDataElementSource
        }

    override fun selectPathWithinDomain(path: GQLOperationPath): B =
        this.applyToBuilder {
            when {
                path.refersToDirective() -> {
                    this.directivePathSelections.add(path)
                }
                !path.refersToArgument() -> {
                    this.fieldPathSelections.add(path)
                }
            }
        }

    override fun selectDirectivePathWithValueWithinDomain(
        path: GQLOperationPath,
        value: Value<*>
    ): B =
        this.applyToBuilder {
            if (path.refersToDirective()) {
                this.directivePathValueSelections.put(path, value)
            }
        }

    override fun selectAllPathsWithinDomain(selections: Iterable<GQLOperationPath>): B =
        this.applyToBuilder {
            selections.asSequence().forEach { p: GQLOperationPath ->
                when {
                    p.refersToDirective() -> {
                        this.directivePathSelections.add(p)
                    }
                    !p.refersToArgument() -> {
                        this.fieldPathSelections.add(p)
                    }
                }
            }
        }
}
