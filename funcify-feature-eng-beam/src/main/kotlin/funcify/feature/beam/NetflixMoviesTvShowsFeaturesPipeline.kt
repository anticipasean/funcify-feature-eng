package funcify.feature.beam

import java.io.Reader
import java.nio.channels.Channels
import java.nio.charset.Charset
import org.apache.beam.sdk.Pipeline
import org.apache.beam.sdk.io.FileIO
import org.apache.beam.sdk.io.FileIO.ReadableFile
import org.apache.beam.sdk.io.TextIO
import org.apache.beam.sdk.options.Default
import org.apache.beam.sdk.options.Description
import org.apache.beam.sdk.options.PipelineOptions
import org.apache.beam.sdk.options.PipelineOptionsFactory
import org.apache.beam.sdk.options.Validation
import org.apache.beam.sdk.transforms.FlatMapElements
import org.apache.beam.sdk.transforms.InferableFunction
import org.apache.beam.sdk.values.PDone
import org.apache.beam.sdk.values.TypeDescriptor
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVPrinter
import org.apache.commons.csv.CSVRecord

object NetflixMoviesTvShowsFeaturesPipeline {

    class ConvertIntoCsvRecordsFn(
        private val charset: Charset = Charsets.UTF_8,
        private val csvFormat: CSVFormat = CSVFormat.DEFAULT
    ) : InferableFunction<ReadableFile, Iterable<CSVRecord>>() {

        @Throws(Exception::class)
        override fun apply(input: ReadableFile): Iterable<CSVRecord> {
            return Channels.newReader(input.open(), charset).use { r: Reader ->
                CSVParser.parse(r, csvFormat)
            }
        }
    }

    class ConvertCSVRecordIntoTextFn() : InferableFunction<CSVRecord, String>() {

        @Throws(Exception::class)
        override fun apply(input: CSVRecord): String {
            return CSVPrinter()
        }
    }

    interface NetflixShowsFeaturesPipelineOptions : PipelineOptions {

        @get:Description("Path of the file to read from")
        @get:Default.String("netflix_movies_and_tv_shows_202306091725/netflix_titles.csv")
        var inputFile: String

        @get:Description(
            "Character encoding in csv file to read from as defined in java.nio.charset.Charset.availableCharsets"
        )
        @get:Default.String("UTF-8")
        var charSet: String

        @get:Description("Format as defined in org.apache.commons.csv.CSVFormat.Predefined")
        @get:Default.String("DEFAULT")
        var csvFormat: String

        @get:Description("Path of the file to write to")
        @get:Validation.Required //
        var output: String
    }

    @JvmStatic
    fun runCSV(options: NetflixShowsFeaturesPipelineOptions) {
        val charset: Charset =
            try {
                Charset.forName(options.charSet)
            } catch (t: Throwable) {
                Charsets.UTF_8
            }
        val csvFormat: CSVFormat =
            CSVFormat.Predefined.values()
                .asSequence()
                .firstOrNull { pd: CSVFormat.Predefined ->
                    pd.name.equals(options.csvFormat, ignoreCase = true)
                }
                ?.format
                ?: CSVFormat.DEFAULT
        val p: Pipeline = Pipeline.create(options)
        p.apply("MatchFilePattern", FileIO.match().filepattern(options.inputFile))
            .apply("ReadMatchedFile", FileIO.readMatches())
            .apply(
                "ConvertIntoCsvRecords",
                FlatMapElements.into(TypeDescriptor.of(CSVRecord::class.java))
                    .via(ConvertIntoCsvRecordsFn(charset, csvFormat))
            )
            .apply<PDone>("WriteCounts", TextIO.write().to(options.output))

        p.run().waitUntilFinish()
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val options: NetflixShowsFeaturesPipelineOptions =
            (PipelineOptionsFactory.fromArgs(*args).withValidation()
                as NetflixShowsFeaturesPipelineOptions)
        runCSV(options)
    }
}
