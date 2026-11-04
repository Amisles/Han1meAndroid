package app.amisles.hanime.data.icon

import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
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

    override fun current(): LauncherIcon =
        LauncherIcon.entries.firstOrNull { it != LauncherIcon.DEFAULT && isEnabled(it) }
            ?: LauncherIcon.DEFAULT

    override fun select(icon: LauncherIcon): Boolean = runCatching {
        val pm = context.packageManager
        // 先启用目标再关闭其余：中途失败时桌面上至少还留着一个可启动的入口
        pm.setComponentEnabledSetting(
            component(icon),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
        LauncherIcon.entries
            .filter { it != icon }
            .filterNot { it == LauncherIcon.DEFAULT && keepDefaultAlias }
            .forEach { variant ->
                pm.setComponentEnabledSetting(
                    component(variant),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        isEnabled(icon)
    }.getOrDefault(false)

    private fun isEnabled(icon: LauncherIcon): Boolean =
        runCatching {
            context.packageManager.getComponentEnabledSetting(component(icon)) ==
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }.getOrDefault(false)

    // 调试包保留默认入口：别名一旦被禁用，Android Studio 的默认启动配置会指向已禁用的组件
    private val keepDefaultAlias: Boolean
        get() = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    private fun component(icon: LauncherIcon) =
        ComponentName(context.packageName, context.packageName + "." + icon.alias)

    companion object {
        fun ensureDefaultEntryEnabled(context: Context) {
            if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
            runCatching {
                context.packageManager.setComponentEnabledSetting(
                    ComponentName(
                        context.packageName,
                        context.packageName + "." + LauncherIcon.DEFAULT.alias
                    ),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        }
    }
}
