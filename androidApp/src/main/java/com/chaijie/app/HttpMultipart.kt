package com.chaijie.app

import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.io.File

/**
 * multipart/form-data 批量上传（HTTP），供缩略图识别 / 原图入库使用。
 *
 * 注意：必须用 setFixedLengthStreamingMode 预计算 Content-Length，
 * 否则 Android HttpURLConnection 对大 body（>2MB）会静默丢弃数据
 * （服务端收到 Content-Length 但 body 为空 → 400）。
 */
object HttpMultipart {

    /**
     * 上传多个文件。
     * @param parts 每个元素: Pair(文件路径, 内容提供者(返回 InputStream))
     * @param extraFields 可选附加表单字段（如 groups JSON），作为 multipart 文本 part 输出
     */
    fun upload(
        url: String,
        field: String,
        parts: List<Pair<String, () -> InputStream>>,
        extraFields: Map<String, String> = emptyMap(),
        timeoutMs: Int = 300000,
    ): String? {
        val boundary = "----KuiklyBoundary" + System.currentTimeMillis()
        val crlf = "\r\n".toByteArray()

        // 预计算 multipart body 总长度（含所有 header + 文件字节 + 尾部 boundary）
        var totalLength = 0L
        // 附加表单字段
        for ((k, v) in extraFields) {
            // "--boundary\r\n"
            totalLength += 2L + boundary.length + crlf.size
            // "Content-Disposition: form-data; name="k"\r\n\r\n"
            totalLength += ("Content-Disposition: form-data; name=\"$k\"").toByteArray().size + crlf.size + crlf.size
            totalLength += v.toByteArray().size + crlf.size
        }
        val headerBuf = ByteArray(1024)
        for ((name, _) in parts) {
            // "--boundary\r\n"
            totalLength += 2L + boundary.length + crlf.size
            // "Content-Disposition: form-data; name="field"; filename="name"\r\n"
            totalLength += ("Content-Disposition: form-data; name=\"$field\"; filename=\"$name\"").toByteArray().size + crlf.size
            // "Content-Type: application/octet-stream\r\n\r\n"
            totalLength += "Content-Type: application/octet-stream".toByteArray().size + crlf.size + crlf.size
            // 文件字节 + 尾部 crlf
            val size = try { File(name).length() } catch (e: Exception) { 0L }
            totalLength += size + crlf.size
        }
        // 尾部 "--boundary--\r\n"
        totalLength += 2L + boundary.length + 2L + crlf.size

        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = timeoutMs
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (ChaijieApp)")
            // 关键：固定长度流模式，避免 HttpURLConnection 内部缓冲导致大 body 丢失
            conn.setFixedLengthStreamingMode(totalLength)

            val out = conn.outputStream
            val buf = ByteArray(64 * 1024)
            // 先输出附加表单字段
            for ((k, v) in extraFields) {
                out.write("--$boundary".toByteArray())
                out.write(crlf)
                out.write("Content-Disposition: form-data; name=\"$k\"".toByteArray())
                out.write(crlf)
                out.write(crlf)
                out.write(v.toByteArray())
                out.write(crlf)
            }
            for ((name, streamProvider) in parts) {
                out.write("--$boundary".toByteArray())
                out.write(crlf)
                out.write("Content-Disposition: form-data; name=\"$field\"; filename=\"$name\"".toByteArray())
                out.write(crlf)
                out.write("Content-Type: application/octet-stream".toByteArray())
                out.write(crlf)
                out.write(crlf)
                streamProvider().use { input ->
                    var n: Int
                    while (input.read(buf).also { n = it } != -1) {
                        out.write(buf, 0, n)
                    }
                }
                out.write(crlf)
            }
            out.write("--$boundary--".toByteArray())
            out.write(crlf)
            out.flush()
            out.close()

            val code = conn.responseCode
            if (code in 200..299) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                return body
            }
            return null
        } finally {
            conn.disconnect()
        }
    }
}
