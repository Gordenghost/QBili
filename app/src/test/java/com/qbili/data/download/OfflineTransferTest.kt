package com.qbili.data.download

import com.qbili.domain.download.DownloadTrack
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OfflineTransferTest {
    @get:Rule val temporary = TemporaryFolder()
    private val bytes = byteArrayOf(0, 0, 0, 24, 102, 116, 121, 112) + ByteArray(100)
    @Test fun downloadsCompleteTrackWithoutCookies() = runBlocking {
        var last = DownloadTrack()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            assertNull(chain.request().header("Cookie"))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .header("ETag", "\"track\"").body(bytes.toResponseBody("video/mp4".toMediaType())).build()
        }.build()
        val file = temporary.newFile()
        OfflineTransfer(client).download("https://a.bilivideo.com/v", file, DownloadTrack(), { true }) { last = it }
        assertTrue(last.complete)
        assertEquals(bytes.size.toLong(), last.total)
        assertArrayEquals(bytes, file.readBytes())
    }
    @Test fun resumesOnlyMatchingEntity() = runBlocking {
        val file = temporary.newFile().apply { writeBytes(bytes.copyOfRange(0, 50)) }
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            assertEquals("bytes=50-", chain.request().header("Range"))
            assertEquals("\"track\"", chain.request().header("If-Range"))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(206).message("Partial")
                .header("ETag", "\"track\"").header("Content-Range", "bytes 50-107/108")
                .body(bytes.copyOfRange(50, bytes.size).toResponseBody()).build()
        }.build()
        OfflineTransfer(client).download("https://a.bilivideo.com/v", file, DownloadTrack(50, 108, "\"track\""), { true }) { }
        assertArrayEquals(bytes, file.readBytes())
    }
    @Test fun changedEntityRestartsAndWeakValidatorNeverAppends() = runBlocking {
        val file = temporary.newFile().apply { writeBytes(ByteArray(40)) }
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            assertNull(chain.request().header("Range"))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(bytes.toResponseBody()).build()
        }.build()
        OfflineTransfer(client).download("https://a.bilivideo.com/v", file, DownloadTrack(40, 108, "W/\"old\""), { true }) { }
        assertArrayEquals(bytes, file.readBytes())
    }
    @Test fun rejectsHtmlInsteadOfMarkingComplete() = runBlocking {
        var complete = false
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("<html>permission denied</html>".toResponseBody()).build()
        }.build()
        try { OfflineTransfer(client).download("https://a.bilivideo.com/v", temporary.newFile(), DownloadTrack(), { true }) { complete = it.complete }; fail() }
        catch (_: IllegalStateException) { }
        assertFalse(complete)
    }
}
