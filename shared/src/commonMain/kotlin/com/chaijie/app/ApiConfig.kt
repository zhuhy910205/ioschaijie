package com.chaijie.app

/**
 * 后端 Base URL 配置。
 * chaijie 公网：https://zhuyanyou.fun
 * video_studio 经反代挂在 /video 下：https://zhuyanyou.fun/video
 * 若部署路径不同，改这里即可（单点配置）。
 */
object ApiConfig {
    const val CHAIJIE_BASE = "https://www.zhuyanyou.fun"
    const val VIDEO_BASE = "https://www.zhuyanyou.fun/video"

    /** chaijie 图片列表（分页） */
    const val CHAIJIE_IMAGES = "$CHAIJIE_BASE/api/images?per_page=60&page=1"

    /** 人脸聚类列表（人物 + 人脸数，降序）；后端 blueprint 前缀 /admin/persons */
    const val CLUSTERS = "$CHAIJIE_BASE/admin/persons/clusters"

    /** 搜索接口（POST {search_text}） */
    const val SEARCH = "$CHAIJIE_BASE/api/search"

    /** 方隅：按地址分组的位置数据（含每组坐标/缩略图），对齐 gps 规格 */
    const val GROUP_BY_LOCATION = "$CHAIJIE_BASE/api/images/group_by_location"

    /** 单图分析接口（POST {image_name}），返回 analysis_result 文本 */
    const val ANALYZE = "$CHAIJIE_BASE/api/analyze"

    /** video_studio 视频列表（顶层 JSON 数组） */
    const val VIDEO_LIST = "$VIDEO_BASE/api/videos"

    /** 由图片文件名拼出优化图直链 */
    fun chaijieImageUrl(filename: String): String = "$CHAIJIE_BASE/api/optimized_image/$filename"
}
