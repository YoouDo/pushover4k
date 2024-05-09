package de.kleinkop.pushover4k.client.http

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Create multipart form-data based on a publication on "Ralph's Blog"
 */
class HttpClientMultipartFormBody {
    private val content: MutableMap<String, Any> = mutableMapOf()

    fun plus(name: String, value: String): HttpClientMultipartFormBody {
        content[name] = value
        return this
    }

    fun plusIfSet(name: String, isSet: Boolean, value: String): HttpClientMultipartFormBody =
        if (isSet) {
            plus(name, value)
        } else {
            this
        }

    fun plusIfSet(name: String, value: String?): HttpClientMultipartFormBody =
        value
            ?.let { this.plus(name, it) }
            ?: this

    fun plusIfSet(name: String, value: Number?): HttpClientMultipartFormBody {
        return value
            ?.let { this.plus(name, it.toString()) }
            ?: this
    }

    fun plusIfSet(name: String, values: List<String>): HttpClientMultipartFormBody =
        if (values.isNotEmpty()) {
            plus(name, values.joinToString(separator = ","))
        } else {
            this
        }

    fun plusImageIfSet(name: String, file: File?): HttpClientMultipartFormBody {
        file?.also {
            content[name] = it
        }
        return this
    }

    /**
     * Build multipart content
     * @return Pair of value of header "Content-Type" and lists of ByteArrays
     */
    fun build(boundary: String): Pair<String, List<ByteArray>> {
        val byteArrays = ArrayList<ByteArray>()
        val separator = "--$boundary\r\nContent-Disposition: form-data; name=".toByteArray(StandardCharsets.UTF_8)

        val data: Map<String, Any> = content.toMap()
        for (entry in data.entries) {
            byteArrays.add(separator)
            when (entry.value) {
                is File -> {
                    val file = entry.value as File
                    val path = Path.of(file.toURI())
                    val mimeType = Files.probeContentType(path)
                    byteArrays.add(
                        "\"${entry.key}\"; filename=\"${path.fileName}\"\r\nContent-Type: $mimeType\r\n\r\n".toByteArray(
                            StandardCharsets.UTF_8,
                        ),
                    )
                    byteArrays.add(Files.readAllBytes(path))
                    byteArrays.add("\r\n".toByteArray(StandardCharsets.UTF_8))
                }

                else -> byteArrays.add("\"${entry.key}\"\r\n\r\n${entry.value}\r\n".toByteArray(StandardCharsets.UTF_8))
            }
        }
        byteArrays.add("--$boundary--".toByteArray(StandardCharsets.UTF_8))
        return "multipart/form-data;boundary=$boundary" to byteArrays
    }
}
