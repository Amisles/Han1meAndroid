package app.amisles.hanime.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Baseline Profile 生成器。
 *
 * 只覆盖冷启动与首页首屏滑动：这两段是所有用户每次打开都要走的路径，
 * 也是 macrobenchmark 里对首帧影响最大的部分。搜索、详情等次级页面在
 * 冷启动后再进，对 profile 的收益明显更小，故不纳入。
 *
 * 运行：./gradlew :baselineprofile:generateNonMinifiedReleaseBaselineProfile
 * 产物落在 app/src/main/baseline-prof.txt，随 release 打包。
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = "app.amisles.hanime",
        includeInStartupProfile = true
    ) {
        pressHome()
        startActivityAndWait()

        // 等首屏列表真正可滚动再开始：骨架屏阶段没有可滚动容器，
        // 直接滑会落到空对象上，profile 里就只剩启动阶段的采样
        device.wait(Until.hasObject(By.scrollable(true)), 15_000)

        // 下滑两屏：覆盖列表滚动、缩略图解码与卡片复用
        repeat(2) {
            device.findObject(By.scrollable(true))?.scroll(Direction.DOWN, 0.8f)
            device.waitForIdle()
        }

        // 回滚到顶部，覆盖反向滚动时的组合复用路径
        device.findObject(By.scrollable(true))?.scroll(Direction.UP, 0.8f)
        device.waitForIdle()
    }
}
