package app.amisles.hanime.baselineprofile

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class HomeScrollBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun homeScroll() = rule.measureRepeated(
        packageName = "app.amisles.hanime",
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            device.wait(Until.hasObject(By.scrollable(true)), 15_000)
        }
    ) {
        repeat(3) {
            device.findObject(By.scrollable(true))?.scroll(Direction.DOWN, 0.8f)
            device.waitForIdle()
        }
        repeat(3) {
            device.findObject(By.scrollable(true))?.scroll(Direction.UP, 0.8f)
            device.waitForIdle()
        }
    }
}
