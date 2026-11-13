package app.amisles.hanime.domain.model

enum class DownloadErrorCode {
    STORAGE_FULL,
    NETWORK_TIMEOUT,
    NETWORK_DNS,
    NETWORK_TLS,
    NETWORK_CONNECT,
    NETWORK_IO,
    HTTP_REJECTED,
    HTTP_SERVER,
    NO_DATA,
    INCOMPLETE,
    FILE_MISSING,
    UNKNOWN
}
