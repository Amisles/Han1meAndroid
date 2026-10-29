package app.amisles.hanime.feature.settings

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import app.amisles.hanime.core.ui.R

internal object LauncherShortcut {

    private const val SHORTCUT_ID = "custom_launcher_icon"
    private const val ICON_SIZE = 432

    /** 桌面不支持固定快捷方式时返回 false；图片无法解码时抛出异常。 */
    fun pin(context: Context, uri: Uri): Boolean {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return false
        val bitmap = loadSquareBitmap(context, uri)
            ?: error("decode image failed")
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: error("launch intent missing")
        launch.action = Intent.ACTION_MAIN
        launch.addCategory(Intent.CATEGORY_LAUNCHER)
        val label = context.getString(R.string.app_name)
        val shortcut = ShortcutInfoCompat.Builder(context, SHORTCUT_ID)
            .setShortLabel(label)
            .setLongLabel(label)
            .setIcon(IconCompat.createWithAdaptiveBitmap(bitmap))
            .setIntent(launch)
            .setLongLived(true)
            .build()
        // 同时作为动态快捷方式发布：应用更新后系统才不会回收已固定的快捷方式
        ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
        return ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    }

    private fun loadSquareBitmap(context: Context, uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while (bounds.outWidth / (sampleSize * 2) >= ICON_SIZE &&
            bounds.outHeight / (sampleSize * 2) >= ICON_SIZE
        ) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val source = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null
        val side = minOf(source.width, source.height)
        val square = Bitmap.createBitmap(
            source,
            (source.width - side) / 2,
            (source.height - side) / 2,
            side,
            side
        )
        return Bitmap.createScaledBitmap(square, ICON_SIZE, ICON_SIZE, true)
    }
}
