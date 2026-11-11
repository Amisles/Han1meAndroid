package app.amisles.hanime.baselineprofile

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class DetailOpenBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun openDetailAndBack() = rule.measureRepeated(
        packageName = "app.amisles.hanime",
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            check(device.wait(Until.hasObject(By.res("video_card")), 15_000))
        }
    ) {
        checkNotNull(device.findObject(By.res("video_card"))).click()
        check(device.wait(Until.hasObject(By.res("detail_screen")), 20_000))
        device.waitForIdle()
        device.pressBack()
        check(device.wait(Until.hasObject(By.res("video_card")), 10_000))
    }
}
