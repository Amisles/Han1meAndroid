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
        // 始终保持恰好一个启用项：全部禁用会让应用从桌面彻底消失
        LauncherIcon.entries.forEach { variant ->
            pm.setComponentEnabledSetting(
                component(variant),
                if (variant == icon) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                },
                PackageManager.DONT_KILL_APP
            )
        }
        true
    }.getOrDefault(false)

    private fun component(icon: LauncherIcon) =
        ComponentName(context.packageName, context.packageName + "." + icon.alias)
}
