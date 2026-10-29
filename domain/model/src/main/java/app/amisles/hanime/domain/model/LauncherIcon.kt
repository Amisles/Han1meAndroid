package app.amisles.hanime.domain.model

/** 桌面图标方案；[alias] 与 AndroidManifest 中 activity-alias 的类名后缀一一对应。 */
enum class LauncherIcon(val alias: String) {
    DEFAULT("LauncherDefault"),
    CALCULATOR("LauncherCalculator"),
    NOTES("LauncherNotes"),
    GALLERY("LauncherGallery"),
    MUSIC("LauncherMusic"),
    FILES("LauncherFiles")
}
