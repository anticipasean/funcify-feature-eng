package funcify.feature.naming.registry

import funcify.feature.naming.NamingConvention
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlin.reflect.KClass


/**
 * Heterogeneous type-safe container setup with type parameterized keys for
 * different naming conventions potentially with the same name but
 * handling different input types
 * @author smccarron
 * @created 3/16/22
 */
interface NamingConventionRegistry {

    val namingConventionByKey: ImmutableMap<RegistryKey<*>, NamingConvention<*>>

    fun hasNamingConventionsFor(conventionName: String): Boolean {
        return getNamingConventionsWithConventionName(conventionName).isNotEmpty()
    }

    fun <I : Any> hasNamingConventionFor(conventionName: String,
                                         inputType: KClass<I>): Boolean {
        return getNamingConventionFor(conventionName,
                                      inputType) != null
    }

    /**
     * @implementation: should use the name of the convention from the convention name property on
     * the naming convention to create the naming_convention_key instance
     */
    fun <I : Any> registerNamingConvention(inputType: KClass<I>,
                                           convention: NamingConvention<I>): NamingConventionRegistry

    /**
     * @implementation: should check whether name in key matches that of name in convention before updating registry
     * and potentially throw an error if the registry cannot accept this pair
     */
    fun <I : Any> registerNamingConvention(key: RegistryKey<I>,
                                           convention: NamingConvention<I>): NamingConventionRegistry

    /**
     * @implementation: to make this more performant, could have separate
     * map<string, list<naming_convention>> or map<string, list<pair<naming_convention_key, naming_convention>>>
     * in backing field
     */
    fun getNamingConventionsWithConventionName(conventionName: String): ImmutableList<Pair<RegistryKey<*>, NamingConvention<*>>>

    fun <I : Any> getNamingConventionFor(conventionName: String,
                                         input: I): NamingConvention<I>? {
        return getNamingConventionsWithConventionName(conventionName).find({ nckNcPair ->
                                                                               nckNcPair.first.inputType.isInstance(input)
                                                                           })
                .let { nckNcPair ->
                    @Suppress("UNCHECKED_CAST")
                    val nc = nckNcPair?.second as? NamingConvention<I>
                    nc
                }
    }

    fun <I : Any> getNamingConventionForInputType(conventionName: String,
                                                  inputType: KClass<I>): NamingConvention<I>? {
        return getNamingConventionsWithConventionName(conventionName).find({ nckNcPair ->
                                                                               if (nckNcPair.first.inputType.java.isPrimitive) {
                                                                                   nckNcPair.first.inputType.javaPrimitiveType == inputType.javaPrimitiveType
                                                                               } else {
                                                                                   nckNcPair.first.inputType.java.isAssignableFrom(inputType.java)
                                                                               }
                                                                           })
                .let { nckNcPair ->
                    @Suppress("UNCHECKED_CAST")
                    val nc = nckNcPair?.second as? NamingConvention<I>
                    nc
                }
    }

}