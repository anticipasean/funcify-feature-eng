package funcify.feature.materializer.session


/**
 *
 * @author smccarron
 * @created 2/9/22
 */
interface FeatureMaterializationSessionFactory {

    fun createMaterializationSession(): FeatureMaterializationSession

}