package com.chaijie.app

import com.tencent.kuikly.compose.ui.unit.Dp
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import kotlin.test.*

/**
 * 方隅页核心功能逻辑测试（不依赖设备/渲染，跑在 JVM 单测上）。
 * 覆盖：boundingBox 投影边界、buildClusters 屏幕空间聚合、GPS 格式化、城市名、分组 JSON 解析。
 */
class PlacePageLogicTest {

    private val page = PlacePage()

    @Test
    fun boundingBox_singleGroup_isPoint() {
        val g = PlacePage.LocationGroup("A", 3, true, 31.23, 121.47, emptyList(), false)
        val q = page.boundingBox(listOf(g))
        assertEquals(31.23, q.minLat)
        assertEquals(31.23, q.maxLat)
        assertEquals(121.47, q.minLng)
        assertEquals(121.47, q.maxLng)
    }

    @Test
    fun boundingBox_empty_returnsZeros() {
        val q = page.boundingBox(emptyList())
        assertEquals(0.0, q.minLat)
        assertEquals(0.0, q.maxLat)
    }

    @Test
    fun boundingBox_multiComputesExtent() {
        val g1 = PlacePage.LocationGroup("A", 1, true, 31.0, 121.0, emptyList(), false)
        val g2 = PlacePage.LocationGroup("B", 1, true, 32.0, 122.0, emptyList(), false)
        val q = page.boundingBox(listOf(g1, g2))
        assertEquals(31.0, q.minLat)
        assertEquals(32.0, q.maxLat)
        assertEquals(121.0, q.minLng)
        assertEquals(122.0, q.maxLng)
    }

    @Test
    fun buildClusters_nearbyMergeIntoOne() {
        val g1 = PlacePage.LocationGroup("A", 1, true, 31.0, 121.0, emptyList(), false)
        val g2 = PlacePage.LocationGroup("B", 1, true, 31.0, 121.0, emptyList(), false)
        val project: (Double, Double) -> Pair<Dp, Dp> = { _, _ -> Dp(10f) to Dp(10f) }
        val clusters = page.buildClusters(listOf(g1, g2), project)
        assertEquals(1, clusters.size)
        assertEquals(2, clusters[0].count)
    }

    @Test
    fun buildClusters_farApartStaySeparate() {
        val g1 = PlacePage.LocationGroup("A", 1, true, 31.0, 121.0, emptyList(), false)
        val g2 = PlacePage.LocationGroup("B", 1, true, 32.0, 122.0, emptyList(), false)
        val project: (Double, Double) -> Pair<Dp, Dp> = { lat, _ ->
            if (lat == 31.0) Dp(0f) to Dp(0f) else Dp(200f) to Dp(0f)
        }
        val clusters = page.buildClusters(listOf(g1, g2), project)
        assertEquals(2, clusters.size)
    }

    @Test
    fun buildClusters_labelAndCoverFromSingle() {
        val photo = PlacePage.Photo("p1.jpg", "2026-01-01", "thumbUrl", 31.0, 121.0, true)
        val g = PlacePage.LocationGroup("上海", 1, true, 31.0, 121.0, listOf(photo), false)
        val project: (Double, Double) -> Pair<Dp, Dp> = { _, _ -> Dp(50f) to Dp(50f) }
        val clusters = page.buildClusters(listOf(g), project)
        assertEquals(1, clusters.size)
        assertEquals("上海", clusters[0].label)
        assertEquals("thumbUrl", clusters[0].cover)
        assertEquals(1, clusters[0].count)
    }

    @Test
    fun fmtGps_northEast() {
        assertEquals("31.2304°N, 121.4737°E", page.fmtGps(31.2304, 121.4737))
    }

    @Test
    fun fmtGps_southWest() {
        assertEquals("33.8000°S, 151.2000°W", page.fmtGps(-33.8, -151.2))
    }

    @Test
    fun cityName_returnsAddress() {
        val g = PlacePage.LocationGroup("北京市朝阳区", 1, true, 39.9, 116.4, emptyList(), false)
        assertEquals("北京市朝阳区", 39.9.cityName(g))
    }

    @Test
    fun parseGroup_withGps() {
        val o = JSONObject().apply {
            put("address", "上海·外滩")
            put("count", 5)
            put("has_gps", true)
            put("primary_latitude", 31.24)
            put("primary_longitude", 121.49)
            put("images", JSONArray().apply {
                put(JSONObject().apply {
                    put("filename", "a.jpg")
                    put("has_gps", true)
                    put("latitude", 31.24)
                    put("longitude", 121.49)
                    put("shooting_time", "2026-05-01 10:00")
                })
            })
        }
        val g = page.parseGroup(o)
        assertNotNull(g)
        assertEquals("上海·外滩", g!!.address)
        assertEquals(5, g.count)
        assertTrue(g.hasGps)
        assertEquals(31.24, g.lat)
        assertEquals(1, g.photos.size)
        assertEquals("a.jpg", g.photos[0].filename)
        assertEquals("https://www.zhuyanyou.fun/api/optimized_image/a.jpg", g.photos[0].thumb)
        assertTrue(g.photos[0].hasGps)
    }

    @Test
    fun parseGroup_noGpsUsesFixedCoord() {
        val o = JSONObject().apply {
            put("address", "未知地点")
            put("count", 2)
            put("has_gps", false)
            put("primary_latitude", 0.0)
            put("primary_longitude", 0.0)
            put("images", JSONArray())
        }
        val g = page.parseGroup(o)
        assertNotNull(g)
        assertTrue(g!!.noGps)
        assertFalse(g.hasGps)
        assertEquals(0.0, g.lat)
    }
}
