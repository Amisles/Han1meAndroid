package app.amisles.hanime.domain.repository

import app.amisles.hanime.domain.model.DiagnosticResult

/**
 * 站点可用性诊断。
 *
 * 诊断页需要它，但诊断的实现（OkHttp + SSL + DNS 探测）属于数据层细节；
 * 界面只关心「拿到一组结果并展示」。
 */
interface DiagnosticsRepository {
    /** 运行全部诊断项，按 DNS → 连接 → SSL → 镜像状态 → 登录状态 的顺序返回。 */
    suspend fun runAll(): List<DiagnosticResult>
}
