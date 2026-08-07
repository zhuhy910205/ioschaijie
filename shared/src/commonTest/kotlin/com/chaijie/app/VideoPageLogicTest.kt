package com.chaijie.app

import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import kotlin.test.*

/**
 * 视频页核心功能逻辑测试（不依赖设备/渲染，跑在 JVM 单测上）。
 * 覆盖：时长格式化、后端 JSON 解析（来源映射/标签拆分/渐变索引/拍摄时间截取）、演示数据完整性、兜底渐变。
 */
class VideoPageLogicTest {

    private val page = VideoPage()

    @Test
    fun fmtDur_zeroAndNegative() {
        assertEquals("0:00", page.fmtDur(0))
        assertEquals("0:00", page.fmtDur(-5))
    }

    @Test
    fun fmtDur_formatsCorrectly() {
        assertEquals("0:45", page.fmtDur(45))
        assertEquals("1:16", page.fmtDur(76))
        assertEquals("2:05", page.fmtDur(125))
        assertEquals("5:42", page.fmtDur(342))
        assertEquals("9:59", page.fmtDur(599))
    }

    @Test
    fun parseVideos_growthMapsTo精选() {
        val data = JSONObject().apply {
            put("videos", JSONArray().apply {
                put(JSONObject().apply {
                    put("key", "k1")
                    put("name", "成长视频")
                    put("type", "growth")
                    put("last_modified", "2026-03-18T12:00:00")
                    put("tags", "成长,家庭")
                    put("poster_url", "http://x/p.jpg")
                    put("url", "http://x/v.mp4")
                    put("dur", 76)
                })
                put(JSONObject().apply {
                    put("key", "k2")
                    put("name", "我的视频")
                    put("type", "mine")
                    put("last_modified", "2026-05-02T08:30:00")
                    put("tags", "")
                    put("poster_url", "")
                    put("url", "")
                    put("dur", 0)
                })
            })
        }
        val list = page.parseVideos(data)
        assertEquals(2, list.size)
        assertEquals("成长精选", list[0].who)
        assertEquals("我上传的", list[1].who)
        assertEquals("2026-03-18", list[0].shootTime)
        assertEquals(listOf("成长", "家庭"), list[0].tags)
        assertEquals(0, list[1].dur)
        assertEquals("", list[1].thumb)
        assertTrue(list[0].g in 0..7)
    }

    @Test
    fun parseVideos_ignoresEmptyKey() {
        val data = JSONObject().apply {
            put("videos", JSONArray().apply {
                put(JSONObject().apply { put("name", "no key") })
                put(JSONObject().apply { put("key", "ok"); put("type", "growth") })
            })
        }
        val list = page.parseVideos(data)
        assertEquals(1, list.size)
        assertEquals("ok", list[0].id)
    }

    @Test
    fun demoVideos_integrity() {
        val demo = page.DEMO_VIDEOS
        assertEquals(12, demo.size)
        val ids = demo.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        for (v in demo) {
            assertTrue(v.who == "我上传的" || v.who == "成长精选")
            assertTrue(v.g in 0..7)
            assertTrue(v.dur > 0)
        }
    }

    @Test
    fun palettes_areEightWarmPairs() {
        assertEquals(8, VideoPage.PALETTES.size)
        for (p in VideoPage.PALETTES) {
            assertEquals(2, p.size)
            assertTrue(p[0] is Color)
            assertTrue(p[1] is Color)
        }
    }
}
