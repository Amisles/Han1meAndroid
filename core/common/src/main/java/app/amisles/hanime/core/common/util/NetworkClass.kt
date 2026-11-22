package app.amisles.hanime.core.common.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** 网络类型枚举；用于下载并行连接数与播放器缓冲策略。 */
enum class NetworkClass { WIFI, CELLULAR, OTHER }

/** 读取当前网络类型；无权限 / 异常时回退 [NetworkClass.OTHER]。 */
fun getCurrentNetworkClass(context: Context): NetworkClass {
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
