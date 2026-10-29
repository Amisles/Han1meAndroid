package app.amisles.hanime.data.icon

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import app.amisles.hanime.domain.model.LauncherIcon
import app.amisles.hanime.domain.repository.AppIconRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LauncherIconRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : AppIconRepository {

    override fun current(): LauncherIcon {
        val pm = context.packageManager
        return LauncherIcon.entries.firstOrNull { icon ->
            runCatching {
                pm.getComponentEnabledSetting(component(icon)) ==
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            }.getOrDefault(false)
        } ?: LauncherIcon.DEFAULT
    }

    override fun select(icon: LauncherIcon): Boolean = runCatching {
        val pm = context.packageManager
        // 先启用目标再关闭其余：中途失败时桌面上至少还留着一个可启动的入口
        pm.setComponentEnabledSetting(
            component(icon),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
        LauncherIcon.entries.filter { it != icon }.forEach { variant ->
            pm.setComponentEnabledSetting(
                component(variant),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }
        true
    }.getOrDefault(false)

    private fun component(icon: LauncherIcon) =
        ComponentName(context.packageName, context.packageName + "." + icon.alias)
}
