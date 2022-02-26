package funcify.feature

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication


/**
 *
 * @author smccarron
 * @created 1/31/22
 */
@SpringBootApplication(scanBasePackages = ["funcify.feature"])
class FuncifyFeatureEngineeringServiceApplication {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            SpringApplication.run(FuncifyFeatureEngineeringServiceApplication::class.java, *args)
        }
    }
}