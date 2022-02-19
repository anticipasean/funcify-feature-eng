package funcify.feature.datasource.db.mssql

import java.time.Duration


/**
 * Basic set of properties needed to make a connection to a SQL Server Database
 *
 * @author smccarron
 * @created 2/16/22
 */
data class SQLServerConnectionProperties(val connectTimeout: Duration = Duration.ofSeconds(30),
                                         val database: String,
                                         val host: String,
                                         val hostNameInCertificate: String,
                                         val password: CharSequence = "",
                                         val port: Int = 1433,
                                         val sendStringParametersAsUnicode: Boolean = false,
                                         val ssl: Boolean = false,
                                         val username: String = "")
