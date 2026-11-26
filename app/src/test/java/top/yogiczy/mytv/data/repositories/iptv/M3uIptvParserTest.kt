package top.yogiczy.mytv.data.repositories.iptv

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import top.yogiczy.mytv.data.repositories.iptv.parser.M3uIptvParser

class M3uIptvParserTest {
    private val parser = M3uIptvParser()

    @Test
    fun handlesBomAndOptionsBeforeTheStream() = runBlocking {
        val data = "\uFEFF\n" + """
            #EXTM3U catchup="append" catchup-source="?begin={start}"
            #EXTINF:-1 tvg-id="demo",Demo
            #EXTVLCOPT:http-user-agent=Fixture

            #KODIPROP:inputstream.adaptive.manifest_type=hls
            https://example.test/live.ts
        """.trimIndent()

        assertTrue(parser.isSupport("https://example.test/iptv", data))
        val channel = parser.parse(data).iptvList.single()
        assertEquals("demo", channel.tvgId)
        assertEquals("append", channel.catchup)
        assertEquals("?begin={start}", channel.catchupSource)
        assertEquals(listOf("https://example.test/live.ts"), channel.urlList)
    }

    @Test
    fun keepsCommasInAttributesAndTitlesAndDropsDuplicateUrls() = runBlocking {
        val data = """
            #EXTM3U
            #EXTINF:-1 group-title="News, Local" tvg-name="City, Evening",News, Local
            http://example.test/first.ts
            #EXTINF:-1 group-title="News, Local" tvg-name="City, Evening",News, Local
            http://example.test/first.ts
            #EXTINF:-1 group-title="News, Local" tvg-name="City, Evening",News, Local
            http://example.test/backup.ts
        """.trimIndent()

        val group = parser.parse(data).single()
        val channel = group.iptvList.single()
        assertEquals("News, Local", group.name)
        assertEquals("News, Local", channel.name)
        assertEquals("City, Evening", channel.channelName)
        assertEquals(listOf("http://example.test/first.ts", "http://example.test/backup.ts"), channel.urlList)
    }

    @Test
    fun newMetadataReplacesATruncatedEntry() = runBlocking {
        val data = """
            #EXTM3U
            #EXTINF:-1,Missing stream
            #EXTINF:-1 tvg-name="" group-title="Demo",Present stream
            http://example.test/live.ts
            #EXTINF:-1,Pending stream
        """.trimIndent()

        val channel = parser.parse(data).iptvList.single()
        assertEquals("Present stream", channel.name)
        assertEquals("Present stream", channel.channelName)
    }
}
