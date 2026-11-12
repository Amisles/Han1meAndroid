package app.amisles.hanime.domain.model

sealed interface SiteProbeResult {
    data class Reachable(val latencyMs: Long) : SiteProbeResult
    data class Unreachable(val reason: Reason) : SiteProbeResult

    enum class Reason {
        INVALID_URL,
        DNS,
        TIMEOUT,
        IO
    }
}
