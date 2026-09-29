package app.amisles.hanime.data.download

import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.data.remote.VideoAntiHotlink
import app.amisles.hanime.domain.model.DownloadStatus
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.withPermit
import okhttp3.Request

/**
 * 单连接顺序下载引擎：首下前的 Range/吞吐探测，以及单连接续传下载。
 *
 * 首下降级、续传（恢复/失败重试）、服务器不支持分块三条路径都走这里。
 * 以 [DownloadManager] 的扩展函数承载，避免把类内状态整体放开为 internal。
 */
/**
 * 首下前发一个小窗口 Range 请求，同时：1) 确认服务器支持 206 Range；2) 测量单连接稳态吞吐，
 * 作为自适应并行连接数的依据。探测窗口 [0, PROBE_WINDOW) 读后即弃。
 */
internal suspend fun DownloadManager.probeSupportAndThroughput(taskId: Int, url: String): ProbeResult? {
    // 探测占一条真实连接，纳入全局连接预算
    return connectionPermits.withPermit { probeSupportAndThroughputInternal(taskId, url) }
}

internal suspend fun DownloadManager.probeSupportAndThroughputInternal(taskId: Int, url: String): ProbeResult? {
    // 显式 catch：此前整体包在 runCatching 里，任何挂起点上的取消都会被吞成 null
    return try {
        val t0 = System.nanoTime()
        val call = client.newCall(
            Request.Builder().url(url)
                .header("User-Agent", DOWNLOAD_UA)
                .header(VideoAntiHotlink.REFERER_HEADER, VideoAntiHotlink.referer)
                .header("Range", "bytes=0-${PROBE_WINDOW - 1}")
                .get().build()
        )
        trackCall(taskId, call) // 复用任务级 Call 跟踪，暂停时可由 cancelTaskCalls 中断
        val resp = call.execute()
        resp.use { r ->
            if (r.code != 206) {
                null
            } else {
                val total = r.header("Content-Range")
                    ?.let { Regex("/(\\d+)$").find(it)?.groupValues?.get(1)?.toLongOrNull() } ?: 0L
                val input = r.body.byteStream()
                val buf = ByteArray(PROBE_READ_BUFFER_SIZE)
                var read = 0L
                var steadyStartAt = 0L
                val deadline = t0 + PROBE_MAX_DURATION_MS * 1_000_000
                while (read < PROBE_WINDOW) {
                    if (System.nanoTime() >= deadline) break   // 慢链路不为测速卡住
                    val n = input.read(buf)
                    if (n == -1) break
                    read += n
                    // 首次跨过突发区间时开始计时：此前读到的字节不计入速率分子
                    if (steadyStartAt == 0L && read >= PROBE_WARMUP_BYTES) steadyStartAt = System.nanoTime()
                }
                val measuredBytes = if (steadyStartAt != 0L) read - PROBE_WARMUP_BYTES else read
                val dt = (if (steadyStartAt != 0L) System.nanoTime() - steadyStartAt
                    else System.nanoTime() - t0) / 1e9
                val bps = if (dt > 0.05 && measuredBytes > 0) (measuredBytes / dt).toLong() else 0L
                // 诊断：突发速率 vs 稳态速率。两者相差一个数量级可确证「服务端先给突发额度、随后限速」
                val warmupNanos = if (steadyStartAt != 0L) steadyStartAt - t0 else 0L
                val warmupBps = if (warmupNanos > 1_000_000L) {
                    (PROBE_WARMUP_BYTES * 1_000_000_000L) / warmupNanos
                } else {
                    0L
                }
                AppLogger.log(
                    "DownloadManager",
                    "诊断: 探测 读到=${read / 1024}KB 突发段=${warmupBps / 1024}KB/s " +
                        "稳态段=${bps / 1024}KB/s(窗口${measuredBytes / 1024}KB/${(dt * 1000).toInt()}ms) " +
                        "总量=$total 网络类型=${currentNetworkClass()}"
                )
                ProbeResult(supportsRange = true, totalBytes = total, bps = bps)
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        AppLogger.log("DownloadManager", "首下探测失败，降级单连接: ${e.message}")
        null
    }
}

/**
 * 单连接顺序下载（首下降级 / 续传 / 不支持分块路径）。
 * 支持 Range 续传与限次重试：建连失败与流中断都会从磁盘已写长度接续，
 * 避免弱网下长视频一次瞬断即整任务失败。每 500ms 节流更新进度。
 */
internal suspend fun DownloadManager.downloadFileSingle(taskId: Int, url: String, filePath: String, resumeBytes: Long) {
    // 单连接路径同样占一条真实连接，整段纳入全局连接预算
    connectionPermits.withPermit {
        downloadFileSingleInternal(taskId, url, filePath, resumeBytes)
    }
}

internal suspend fun DownloadManager.downloadFileSingleInternal(taskId: Int, url: String, filePath: String, resumeBytes: Long) {
    var resume = resumeBytes
    var attempt = 0
    while (true) {
        try {
            downloadSingleConnectionAttempt(taskId, url, filePath, resume)
            return
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: SourceChangedException) {
            // 源文件已变：不重试，交给上层丢弃旧进度后整份重下
            throw e
        } catch (e: IOException) {
            if (currentCoroutineContext()[Job]?.isActive != true || isTaskCancelled(taskId)) throw e
            attempt++
            if (attempt >= MAX_CHUNK_ATTEMPTS) {
                updateTask(taskId) { it.copy(status = DownloadStatus.FAILED, errorMessage = classifyError(e)) }
                return
            }
            AppLogger.log("DownloadManager", "单连接下载中断，1s 后重试($attempt): ${e.message}")
            delay(1000)
            // append 写入下 file.length() 即已落盘字节数，直接从该处接续
            resume = runCatching { File(filePath).length() }.getOrDefault(resume)
        }
    }
}

private suspend fun DownloadManager.downloadSingleConnectionAttempt(taskId: Int, url: String, filePath: String, resumeBytes: Long) {
    val requestBuilder = Request.Builder()
        .url(url)
        .header("User-Agent", DOWNLOAD_UA)
        .header(VideoAntiHotlink.REFERER_HEADER, VideoAntiHotlink.referer)
        .get()

    if (resumeBytes > 0) {
        requestBuilder.header("Range", "bytes=$resumeBytes-")
    }

    val call = client.newCall(requestBuilder.build())
    trackCall(taskId, call)
    call.execute().use { resp ->
        if (resumeBytes > 0 && resp.code != 206 && !resp.isSuccessful) {
            updateTask(taskId) { it.copy(status = DownloadStatus.FAILED, errorMessage = "HTTP ${resp.code}") }
            return
        }
        if (resumeBytes == 0L && !resp.isSuccessful) {
            updateTask(taskId) { it.copy(status = DownloadStatus.FAILED, errorMessage = classifyHttpError(resp.code)) }
            return
        }

        val body = resp.body
        val bodyLength = body.contentLength()

        if (resumeBytes > 0 && resp.code == 200) {
            AppLogger.log("DownloadManager", "服务器不支持 Range(返回 200)，降级为全量重下: $filePath")
        }

        val isPartial = resp.code == 206
        val persistedTotal = totalBytesOf(taskId)
        val totalFromRange = resp.header("Content-Range")?.let {
            Regex("/(\\d+)$").find(it)?.groupValues?.get(1)?.toLongOrNull()
        }
        // 断点续传时若服务端总量与本地记录不符，说明源文件已变，旧前缀 + 新后缀会拼成坏文件
        if (isPartial && resumeBytes > 0 && totalFromRange != null &&
            persistedTotal > 0 && totalFromRange != persistedTotal
        ) {
            throw SourceChangedException(
                "源文件大小已变化：本地 $persistedTotal，服务端 $totalFromRange"
            )
        }
        val totalBytes = if (isPartial && resumeBytes > 0) {
            totalFromRange ?: (resumeBytes + bodyLength)
        } else {
            bodyLength
        }

        val outputFile = File(filePath)
        // 运行期目录可能被清理/卸载重挂，补一次父目录兜底重建
        outputFile.parentFile?.mkdirs()
        val isResume = resumeBytes > 0 && isPartial
        // 续传走 append 写，隐含要求「文件长度恰好等于 resumeBytes」，而分块写入是稀疏的：
        // 块被驱逐/重排后可能让 file.length() 大于「已完成块的连续前缀」(= resumeBytes)。
        // 此时 append 会落到 EOF，[resumeBytes, file.length()) 的空洞永不回填、后续数据整体错位，
        // 而收尾校验只看长度下界（done/total、disk>=total）→ 静默产出「能播但花屏」的损坏文件。
        // 故续传前显式把文件截断到 resumeBytes，把该不变量落到实处。
        if (isResume) {
            val onDisk = outputFile.length()
            if (onDisk > resumeBytes) {
                AppLogger.log(
                    "DownloadManager",
                    "单连接续传前截断稀疏文件：$onDisk -> $resumeBytes ($filePath)"
                )
                RandomAccessFile(outputFile, "rw").use { it.setLength(resumeBytes) }
            } else if (onDisk < resumeBytes) {
                // 起点越界说明隐含前提已被破坏（文件比续传点还短），继续 append 会写错偏移。
                // 宁可显式失败让上层重试/重下，也不写出看似完整实则错位的文件。
                throw IOException(
                    "单连接续传起点越界：resume=$resumeBytes 但文件只有 $onDisk 字节，丢弃本次续传"
                )
            }
        }
        val outputStream = if (isResume) {
            java.io.FileOutputStream(outputFile, true)
        } else {
            java.io.FileOutputStream(outputFile, false)
        }

        val buffer = ByteArray(SINGLE_PATH_BUFFER_SIZE)
        var downloadedBytes = if (isPartial) resumeBytes else 0L
        var lastUpdate = 0L

        // 校验通过后再取响应流：截断失败/起点越界会直接抛出，不留半开的输入流
        val inputStream = body.byteStream()
        inputStream.use { input ->
            outputStream.use { output ->
                while (true) {
                    if (currentCoroutineContext()[Job]?.isActive != true) break
                    val bytesRead = input.read(buffer)
                    if (bytesRead == -1) break

                    output.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead

                    val now = System.currentTimeMillis()
                    if (now - lastUpdate > 500) {
                        lastUpdate = now
                        updateTask(taskId) {
                            it.copy(
                                downloadedBytes = downloadedBytes,
                                totalBytes = totalBytes
                            )
                        }
                    }
                }
            }
        }

        updateTask(taskId) {
            it.copy(
                downloadedBytes = downloadedBytes,
                totalBytes = if (totalBytes > 0) totalBytes else downloadedBytes
            )
        }

        // 单连接路径写满后清理可能残留的分块位图表：其块布局与顺序写入的进度无关，
        // 留着会让 deriveResumeState 在下次续传时按错误的块边界推导出「带空洞的前缀」。
        if (totalBytes > 0 && downloadedBytes >= totalBytes) {
            val partmap = File(filePath + PARTMAP_SUFFIX)
            if (partmap.exists()) runCatching { partmap.delete() }
        }
    }
}
