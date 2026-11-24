package app.amisles.hanime.data.translation

import android.content.Context
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.domain.model.TranslationModelState
import app.amisles.hanime.domain.repository.TranslationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request

@Singleton
class TranslationModelManager @Inject constructor(
    @ApplicationContext private val context: Context
) : TranslationRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val client = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val _modelState = MutableStateFlow<TranslationModelState>(TranslationModelState.NotDownloaded)
    override val modelStateFlow: StateFlow<TranslationModelState> = _modelState.asStateFlow()

    override val isModelPublished: Boolean get() = MODEL_URL.isNotBlank()

    @Volatile
    private var downloadJob: Job? = null

    @Volatile
    private var activeCall: Call? = null

    init {
        _modelState.value = installedState()
    }

    override fun downloadModel() {
        if (!isModelPublished || downloadJob?.isActive == true) return
        downloadJob = scope.launch {
            val temp = tempFile()
            _modelState.value = TranslationModelState.Downloading(0L, 0L)
            try {
                fetch(temp)
                if (!temp.renameTo(modelFile())) throw IOException("模型临时文件重命名失败")
                AppLogger.i(TAG, "模型已就绪: ${modelFile().name}")
                _modelState.value = TranslationModelState.Ready(modelFile().absolutePath)
            } catch (e: CancellationException) {
                temp.delete()
                throw e
            } catch (e: Exception) {
                temp.delete()
                // 取消下载也会让流读到一半抛 IOException，那条路径的状态已由 cancelDownload 复位
                if (!isActive) return@launch
                AppLogger.w(TAG, "模型下载失败: ${e.message}")
                _modelState.value = TranslationModelState.Failed
            } finally {
                activeCall = null
            }
        }
    }

    override fun cancelDownload() {
        val job = downloadJob ?: return
        downloadJob = null
        job.cancel()
        activeCall?.cancel()
        activeCall = null
        _modelState.value = installedState()
    }

    override fun deleteModel() {
        cancelDownload()
        scope.launch {
            withContext(Dispatchers.IO) { modelFile().delete() }
            _modelState.value = installedState()
        }
    }

    private suspend fun fetch(target: File) = withContext(Dispatchers.IO) {
        target.parentFile?.mkdirs()
        val call = client.newCall(Request.Builder().url(MODEL_URL).get().build())
        activeCall = call
        call.execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val totalBytes = response.body.contentLength().coerceAtLeast(0L)
            var downloadedBytes = 0L
            var reportedStep = -1
            response.body.byteStream().use { input ->
                target.outputStream().buffered().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        val step = if (totalBytes <= 0L) -1 else (downloadedBytes * PROGRESS_STEPS / totalBytes).toInt()
                        if (step != reportedStep) {
                            reportedStep = step
                            _modelState.value = TranslationModelState.Downloading(downloadedBytes, totalBytes)
                        }
                    }
                }
            }
        }
    }

    private fun modelFile(): File = File(File(context.filesDir, MODEL_DIR), MODEL_FILE_NAME)

    private fun tempFile(): File = File(File(context.filesDir, MODEL_DIR), MODEL_FILE_NAME + TEMP_SUFFIX)

    private fun installedState(): TranslationModelState {
        val file = modelFile()
        return if (file.isFile && file.length() > 0L) {
            TranslationModelState.Ready(file.absolutePath)
        } else {
            TranslationModelState.NotDownloaded
        }
    }

    private companion object {
        /** 模型直链；留空表示模型尚未发布，此时 [isModelPublished] 为 false，界面不提供下载入口。 */
        const val MODEL_URL = ""

        const val MODEL_FILE_NAME = "qwen2.5-0.5b-instruct-translate.gguf"
        const val MODEL_DIR = "translation"
        const val TEMP_SUFFIX = ".part"

        const val CONNECT_TIMEOUT_SECONDS = 15L

        /** 读超时按「多久没收到数据」计：模型体积远大于站点页面，套用接口那几秒超时会在弱网下反复中断。 */
        const val READ_TIMEOUT_SECONDS = 60L

        const val BUFFER_SIZE = 64 * 1024

        /** 进度按 1% 一档发布，避免每读一块都刷新 StateFlow 触发设置页重组。 */
        const val PROGRESS_STEPS = 100

        const val TAG = "TranslationModel"
    }
}
