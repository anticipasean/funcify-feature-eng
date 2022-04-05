package funcify.feature.naming.registry

import funcify.feature.naming.NamingConvention
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentHashMap
import kotlinx.collections.immutable.toPersistentList
import kotlin.reflect.KClass


/**
 *
 * @author smccarron
 * @created 3/17/22
 */
data class DefaultNamingConventionRegistry(override val namingConventionByKey: ImmutableMap<RegistryKey<*>, NamingConvention<*>> = persistentMapOf()) : NamingConventionRegistry {

    private val namingConventionsByName: ImmutableMap<String, ImmutableList<Pair<RegistryKey<*>, NamingConvention<*>>>> by lazy {
        namingConventionByKey.asSequence()
                .groupBy { entry -> entry.key.conventionName }
                .map { entry ->
                    entry.key to entry.value.asSequence()
                            .map { e -> e.key to e.value }
                            .toPersistentList()
                }
                .fold(persistentMapOf(),
                      { acc, pair ->
                          acc.put(pair.first,
                                  pair.second)
                      })
    }

    override fun <I : Any> registerNamingConvention(inputType: KClass<I>,
                                                    convention: NamingConvention<I>): NamingConventionRegistry {
        return DefaultNamingConventionRegistry(namingConventionByKey.toPersistentHashMap()
                                                       .put(DefaultRegistryKey(convention.conventionName,
                                                                               inputType),
                                                            convention))
    }

    override fun <I : Any> registerNamingConvention(key: RegistryKey<I>,
                                                    convention: NamingConvention<I>): NamingConventionRegistry {
        return if (key.conventionName != convention.conventionName) {
            val message = """
                        |the convention_name for the ${RegistryKey::class.java.name} 
                        |does not match that on the convention: 
                        |[ expected: \"${convention.conventionName}\", actual: \"${key.conventionName}\" ]
                        """.trimMargin()
            throw IllegalArgumentException(message)
        } else {
            DefaultNamingConventionRegistry(namingConventionByKey.toPersistentHashMap()
                                                    .put(key,
                                                         convention))
        }
    }

    override fun getNamingConventionsWithConventionName(conventionName: String): ImmutableList<Pair<RegistryKey<*>, NamingConvention<*>>> {
        return namingConventionsByName.getOrDefault(conventionName,
                                                    persistentListOf())
    }

}
