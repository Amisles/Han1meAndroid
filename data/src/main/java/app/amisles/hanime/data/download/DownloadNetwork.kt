package app.amisles.hanime.data.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * 当前网络类型判定，用于自适应并行连接数决策。
 */
// 用于自适应分块数决策
internal enum class NetworkClass { WIFI, CELLULAR, OTHER }

internal fun getCurrentNetworkClass(context: Context): NetworkClass {
    // minSdk 30，直接用 NetworkCapabilities，无需已废弃的 activeNetworkInfo
    val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return NetworkClass.OTHER
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return NetworkClass.OTHER
    return when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI_AWARE) -> NetworkClass.WIFI
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkClass.CELLULAR
        else -> NetworkClass.OTHER
    }
}
