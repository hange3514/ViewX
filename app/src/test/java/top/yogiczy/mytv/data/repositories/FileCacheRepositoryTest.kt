package top.yogiczy.mytv.data.repositories

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import top.yogiczy.mytv.AppGlobal

class FileCacheRepositoryTest {
    @get:Rule
    val cacheFolder = TemporaryFolder()

    @Test
    fun newSourceCannotUseAnEarlierRequestsCache() = runBlocking {
        AppGlobal.cacheDir = cacheFolder.root
        val firstRequestStarted = CompletableDeferred<Unit>()
        val finishFirstRequest = CompletableDeferred<Unit>()
        val first = async {
            TestCache().refresh("source-a") {
                firstRequestStarted.complete(Unit)
                finishFirstRequest.await()
                "first playlist"
            }
        }

        firstRequestStarted.await()
        val second = async {
            runCatching {
                TestCache().refresh("source-b") { error("new source unavailable") }
            }
        }
        finishFirstRequest.complete(Unit)

        assertEquals("first playlist", first.await())
        assertEquals("new source unavailable", second.await().exceptionOrNull()?.message)
    }

    @Test
    fun aFailedRefreshKeepsTheSameSourcesLastValidCopy() = runBlocking {
        AppGlobal.cacheDir = cacheFolder.root
        val cache = TestCache()
        cache.refresh("source-a") { "valid playlist" }

        assertEquals("valid playlist", cache.refresh("source-a") { error("bad payload") })
    }

    private class TestCache : FileCacheRepository("cache-test.txt") {
        suspend fun refresh(source: String, fetch: suspend () -> String): String =
            getOrRefresh(cacheTime = 0L, cacheKey = source, refreshOp = fetch)
    }
}
