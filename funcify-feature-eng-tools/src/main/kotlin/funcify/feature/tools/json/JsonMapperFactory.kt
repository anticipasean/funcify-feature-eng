package funcify.feature.tools.json

interface JsonMapperFactory {

    companion object {

        fun defaultFactory(): JsonMapperFactory {
            return DefaultJsonMapperFactory
        }
    }

    fun builder(): JsonMapper.Builder
}
