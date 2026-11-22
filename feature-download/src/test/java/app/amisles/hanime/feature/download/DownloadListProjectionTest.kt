package app.amisles.hanime.feature.download

import app.amisles.hanime.domain.model.DownloadStatus
import app.amisles.hanime.domain.model.DownloadTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadListProjectionTest {

    @Test
    fun `groups collect every status exactly once`() {
        val groups = groupDownloadTasks(
            listOf(
                task(1, DownloadStatus.DOWNLOADING),
                task(2, DownloadStatus.PENDING),
                task(3, DownloadStatus.PAUSED),
                task(4, DownloadStatus.FAILED),
                task(5, DownloadStatus.COMPLETED)
            )
        )
        assertEquals(listOf(1, 2), groups.downloading.map { it.id })
        assertEquals(listOf(3), groups.paused.map { it.id })
        assertEquals(listOf(4), groups.failed.map { it.id })
        assertEquals(listOf(5), groups.completed.map { it.id })
    }

    @Test
    fun `filter search and sort produce a flat list`() {
        val tasks = listOf(
            task(1, DownloadStatus.COMPLETED, "Alpha", total = 100),
            task(2, DownloadStatus.DOWNLOADING, "Beta", total = 300),
            task(3, DownloadStatus.COMPLETED, "Gamma", total = 200)
        )
        val projection = projection(tasks, DownloadFilter.COMPLETED, DownloadSort.SIZE, "")
        assertFalse(projection.grouped)
        assertEquals(listOf(3, 1), projection.visibleTasks.map { it.id })

        val searched = projection(tasks, DownloadFilter.ALL, DownloadSort.NAME, "a")
        assertEquals(listOf(1, 2, 3), searched.visibleTasks.map { it.id })
        assertEquals(setOf(1, 3), projection.selectableIds())
    }

    @Test
    fun `default query keeps grouped order`() {
        val projection = projection(
            listOf(task(2, DownloadStatus.COMPLETED), task(1, DownloadStatus.DOWNLOADING)),
            DownloadFilter.ALL,
            DownloadSort.ADDED,
            " "
        )
        assertTrue(projection.grouped)
        assertEquals(listOf(2, 1), projection.visibleTasks.map { it.id })
    }

    @Test
    fun `highlight index follows grouped headers`() {
        val projection = projection(
            listOf(
                task(1, DownloadStatus.DOWNLOADING),
                task(2, DownloadStatus.FAILED),
                task(3, DownloadStatus.COMPLETED)
            ),
            DownloadFilter.ALL,
            DownloadSort.ADDED,
            ""
        )
        assertEquals(2, projection.highlightIndex(1, leadingItems = 1))
        assertEquals(4, projection.highlightIndex(2, leadingItems = 1))
        assertEquals(6, projection.highlightIndex(3, leadingItems = 1))
    }

    private fun projection(
        tasks: List<DownloadTask>,
        filter: DownloadFilter,
        sort: DownloadSort,
        query: String
    ): DownloadListProjection {
        val groups = groupDownloadTasks(tasks)
        val ids = orderDownloadTaskIds(downloadOrderKeys(tasks, sort), filter, sort, query)
        return DownloadListProjection(
            groups = groups,
            visibleTasks = ids.mapNotNull { groups.byId[it] },
            grouped = filter == DownloadFilter.ALL && sort == DownloadSort.ADDED && query.isBlank()
        )
    }

    private fun task(
        id: Int,
        status: DownloadStatus,
        title: String = "Task $id",
        total: Long = 0
    ) = DownloadTask(id = id, title = title, quality = "1080p", url = "u$id", status = status, totalBytes = total)
}
