package app.amisles.hanime.data.download

import app.amisles.hanime.data.BuildConfig

/**
 * 下载子系统的调参常量。
 *
 * 从 `DownloadManager` 的 companion 拆出：分块粒度、慢块判据、探测窗口、缓冲大小、
 * 并发预算等常量由下载链路多个文件共用，放在包级作用域后各文件可直接引用，
 * 无需为了跨文件访问而放宽类内可见性。
 */
internal const val MAX_CONCURRENT = 5
internal const val PROGRESS_PERSIST_INTERVAL_MS = 3000L

// 单个文件名（UTF-8 字节）上限：ext4/大多数文件系统为 255 字节，留出余量
internal const val MAX_FILE_NAME_BYTES = 200
internal const val DOWNLOAD_SERVICE_CLASS_NAME = "app.amisles.hanime.service.DownloadService"
// 取消/删除任务的通知通道，必须与 DownloadService.ACTION_REMOVE_TASK 手工保持一致
internal const val ACTION_REMOVE_TASK = "app.amisles.hanime.service.action.REMOVE_TASK"
internal const val EXTRA_TASK_ID = "extra_task_id"
internal const val EXTRA_TITLE = "extra_title"
internal const val EXTRA_PROGRESS = "extra_progress"
internal const val EXTRA_STATUS = "extra_status"
internal const val MAX_REQUESTS_PER_HOST = 16
internal const val MAX_IDLE_CONNECTIONS = 16
internal const val KEEP_ALIVE_SECONDS = 60L
internal const val HIGH_SINGLE_BPS = 8L * 1024 * 1024
internal const val LOW_SINGLE_BPS = 1_500_000L
// 探测窗口取 2MB 以跨出服务端 TCP 初始突发区间；只统计丢弃前 PROBE_WARMUP_BYTES
// 之后的字节，并用 PROBE_MAX_DURATION_MS 封顶耗时。
internal const val PROBE_WINDOW = 2 * 1024 * 1024
internal const val PROBE_WARMUP_BYTES = 512 * 1024
internal const val PROBE_READ_BUFFER_SIZE = 64 * 1024
internal const val PROBE_MAX_DURATION_MS = 3000L
internal const val PARTMAP_SUFFIX = ".partmap"
// 由 1s 放宽到 5s（块完成时仍强制落盘，见 reporter）
internal const val PARTMAP_PERSIST_MS = 5000L
// 位图表块数的合法上界（防损坏文件撑爆数组分配），同时作为分块总数上限
internal const val MAX_PARTMAP_CHUNKS = 4096

// 慢块判定 = max(下限, min(实测每块速率 × SLOW_RELATIVE_PERCENT%, 上限))
internal const val SLOW_THRESHOLD_FLOOR_BPS = 16 * 1024L    // 绝对下限：低于此值基本可断定为停滞连接
internal const val SLOW_THRESHOLD_CAP_BPS = 512 * 1024L     // 上限：避免高速链路上阈值过大导致频繁驱逐
internal const val SLOW_RELATIVE_PERCENT = 15L              // 相对判据：低于每块基准速率的 15%
internal const val SLOW_SAMPLE_MS = 2000L            // 逐块吞吐采样周期 2s
internal const val SLOW_DURATION_MS = 15000L         // 持续低于阈值 15s 才驱逐，避免抖动误杀
// 宽限期按「本次连接取块时刻」计，否则被驱逐过的块重连后毫无宽限；
// 15s 宽限 + 15s 持续判慢 ⟹ 最早 30s 后才可能驱逐，足以覆盖 TCP 慢启动。
internal const val SLOW_GRACE_MS = 15000L
internal const val MAX_CHUNK_ATTEMPTS = 5            // 单块最大「真实 IO 失败」次数（驱逐不计入）
// 驱逐总预算。超限即判定分块路径无收益，降级单连接续传（不再判任务失败）。
// 该值放宽，仅作为「防止病态服务器下无限重连」的兜底。
internal const val MAX_EVICTIONS_PER_TASK = 50
internal const val EVICTION_BACKOFF_BASE_MS = 800L   // 驱逐后重连退避基数（按次数线性递增）
internal const val EVICTION_BACKOFF_MAX_MS = 4000L   // 退避上限
internal const val EVICTION_BACKOFF_STAGGER_MS = 150L// 按块序号错峰，避免数块同时重连
internal const val REPORT_INTERVAL_MS = 500L         // 进度上报周期
internal const val ZERO_PROGRESS_TRIGGER_MS = 30000L // 整任务零进度看门狗触发阈值
// 诊断开关：仅 debug 包启用。诊断日志约 0.5 行/秒且会写入应用日志文件，
// release 常开会持续占用主线程 IO、增加耗电，并让日志更快触及轮转上限。
internal val DIAG_ENABLED = BuildConfig.DEBUG
// 单次 write 超过此耗时即计一次「慢写」（用于判别本地写入阻塞）
internal const val SLOW_WRITE_WARN_MS = 300L
// 诊断里逐块速率最多列出多少条（单任务并行度上限为 8，正常不会截断）
internal const val DIAG_MAX_CHUNK_RATES = 8
// 聚合持续低于健康阈值多久，触发一次「全量重建连接」（自动化用户手动的「暂停再恢复」）。
// 实际周期由冷却时间决定。
internal const val AGGREGATE_LOW_DURATION_MS = 10000L
// 两次全量重建之间的最小间隔：换一批新请求有效，但不能变成重连风暴
internal const val GLOBAL_REBUILD_COOLDOWN_MS = 15000L
// 单个任务全量重建的次数上限：该动作有效故放宽，仍保留上限以防服务端为硬配额时做无效重连。
internal const val MAX_GLOBAL_REBUILDS_PER_TASK = 12
// 聚合健康判据：聚合速率仍在此之上即认为「瓶颈不在连接数」，此时不驱逐「在动但慢」的连接。
// 多连接共享同一瓶颈（服务端限速 / 本机存储写入受限）时，每个连接分到的份额本来就低，
// 此时驱逐重连毫无收益，只会把连接反复打回慢启动。
internal const val AGGREGATE_HEALTHY_FLOOR_BPS = 256 * 1024L
internal const val AGGREGATE_HEALTHY_PERCENT = 30L
// 相对判据：低于「本任务观测峰值速率」的 1/4 即视为不健康（触发全量重建 / 允许逐块驱逐）。
internal const val AGGREGATE_HEALTHY_RATIO = 4L
// 单次 write 越大，写回节流下的阻塞窗口越长（阻塞期间读线程停止读 socket，接收窗口归零并
// 触发发送端 idle-restart，把本地卡顿放大成数十秒的网络低速）。分块路径 128KB、单连接 256KB。
internal const val CHUNK_PATH_BUFFER_SIZE = 128 * 1024
internal const val SINGLE_PATH_BUFFER_SIZE = 256 * 1024
// 真正的分块粒度。块数 = ceil(总量 / CHUNK_SIZE)，与并行连接数解耦。
// 必须是 Long：ceilDiv(value: Long, divisor: Long) 无隐式类型提升。
internal const val CHUNK_SIZE = 16 * 1024 * 1024L
internal const val MIN_CHUNK_TOTAL_BYTES = 12_000_000L
internal const val MAX_PARALLEL_CONNECTIONS = 8      // 单任务并行连接数上限
internal const val MAX_TOTAL_CONNECTIONS = 8         // 全局软预算（多任务时按任务数摊分）
// 每个任务至少保留的并行连接数（避免并发任务多时全部退化为单连接）
internal const val MIN_PARALLEL_PER_TASK = 2
// 全局连接硬上限（信号量许可数）。OkHttp 对同步 call.execute() 不限流，必须自建预算；
// 取略高于软预算以容纳在途重连与探测。
internal const val MAX_CONCURRENT_CONNECTIONS = 12
internal const val DOWNLOAD_UA = "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36"
