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
 * 覆盖冷启动、首页滑动、进入视频详情页与打开搜索页四段主路径，
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

        checkNotNull(device.findObject(By.res("video_card"))).click()
        check(device.wait(Until.hasObject(By.res("detail_screen")), 20_000))
        device.waitForIdle()
        device.pressBack()
        check(device.wait(Until.hasObject(By.res("video_card")), 10_000))

        checkNotNull(device.findObject(By.res("home_search"))).click()
        check(device.wait(Until.hasObject(By.res("search_screen")), 10_000))
        device.waitForIdle()
    }
}
