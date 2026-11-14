package app.amisles.hanime.domain.preferences

enum class PlayerResizeMode {
    FIT,
    CROP,
    STRETCH,
    RATIO_16_9;

    companion object {
        fun fromNameOrNull(name: String?): PlayerResizeMode? =
            name?.let { runCatching { valueOf(it) }.getOrNull() }
    }
}
