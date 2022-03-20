package funcify.naming.encoder

import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.util.stream.IntStream
import kotlin.streams.asSequence


/**
 *
 * @author smccarron
 * @created 3/16/22
 */
object URICompatibleStringEncoder : (String, Charset) -> String {

    private val unreservedCharsAsIntsSet: ImmutableSet<Int> by lazy {
        listOf(IntStream.rangeClosed('a'.code,
                                     'z'.code),
               IntStream.rangeClosed('A'.code,
                                     'Z'.code),
               IntStream.rangeClosed('0'.code,
                                     '9'.code),
               IntStream.of('-'.code,
                            '.'.code,
                            '_'.code,
                            '~'.code)).parallelStream()
                .flatMapToInt { istrm -> istrm }
                .asSequence()
                .fold(persistentSetOf(),
                      { acc, i ->
                          acc.add(i)
                      })
    }


    private fun isUnreservedInURIs(c: Int): Boolean {
        return unreservedCharsAsIntsSet.contains(c)
    }

    fun invoke(inputString: String): String {
        return invoke(inputString,
                      Charsets.UTF_8)
    }

    override fun invoke(inputString: String,
                        characterSet: Charset): String {
        if (inputString.isEmpty()) {
            return inputString
        }
        inputString.byteInputStream(characterSet)
                .use { bais: ByteArrayInputStream -> // create output stream sized to number of available bytes in input stream
                    // factor of 3 since if size of n possible characters all requiring encoding: X => %XX
                    // would mean 3n slots need to be available
                    val baos = ByteArrayOutputStream(bais.available() * 3)
                    var currentByteAsInt: Int
                    while ((bais.read()
                                    .also { currentByteAsInt = it }) >= 0) {
                        if (isUnreservedInURIs(currentByteAsInt)) { // if not a reserved character, then output directly to output stream
                            baos.write(currentByteAsInt)
                        } else { // if a reserved character, encode with a '%' and the hexadecimal string form
                            // of specific bit sets derived from the current byte as int following the URI spec
                            // for encoding
                            baos.write('%'.code)
                            val hex1 = Character.forDigit(currentByteAsInt shr 4 and 0xF,
                                                          16)
                                    .uppercaseChar()
                            val hex2 = Character.forDigit(currentByteAsInt and 0xF,
                                                          16)
                                    .uppercaseChar()
                            baos.write(hex1.code)
                            baos.write(hex2.code)
                        }
                    }
                    return baos.toString(characterSet)
                }
    }


}