package funcify.feature.materializer.session

import java.util.*


/**
 *
 * @author smccarron
 * @created 2/9/22
 */
interface MaterializationSession {

    fun getSessionIdentifier(): UUID

}