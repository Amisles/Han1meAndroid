package app.amisles.hanime.feature.detail.comment

internal fun shouldSkipCommentReset(currentVideoId: String, nextVideoId: String): Boolean =
    nextVideoId == currentVideoId && nextVideoId.isNotEmpty()
