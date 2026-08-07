package com.chaijie.app

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.PlaybackParameters
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView

/**
 * 抖音式全屏播放器：传入视频列表 + 起始 index。
 * - 自动播放当前视频，循环
 * - 上滑下一个 / 下滑上一个（循环）
 * - 单击暂停/播放，右下倍速切换
 * - 顶部标题栏（index/总数 + 标题 + 关闭），底部进度条 + 时间
 * - 磁盘缓存走全局 VideoCacheHolder（300MB LRU）
 */
class VideoPlayActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private var urls: List<String> = emptyList()
    private var currentIndex = 0
    private var speedIdx = 1
    private var isPlaying = true

    private lateinit var titleView: TextView
    private lateinit var posView: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var speedBtn: TextView
    private lateinit var playerView: PlayerView

    private val handler = Handler(Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            updateProgress()
            handler.postDelayed(this, 500)
        }
    }

    private val gestureDetector by lazy {
        GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                togglePlay()
                return true
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                val dy = e2.y - (e1?.y ?: e2.y)
                val dx = e2.x - (e1?.x ?: e2.x)
                if (Math.abs(dy) > Math.abs(dx) && Math.abs(dy) > 120) {
                    if (dy < 0) switchVideo(1) else switchVideo(-1)
                    return true
                }
                return false
            }
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        urls = intent.getStringArrayListExtra(EXTRA_URLS) ?: emptyList()
        if (urls.isEmpty()) {
            intent.getStringExtra(EXTRA_URL)?.let { urls = listOf(it) }
        }
        currentIndex = intent.getIntExtra(EXTRA_INDEX, 0).coerceIn(0, (urls.size - 1).coerceAtLeast(0))
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        if (urls.isEmpty()) {
            finish()
            return
        }

        // 全屏沉浸
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility =
            (View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)

        player = ExoPlayer.Builder(this).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    runOnUiThread {
                        Toast.makeText(this@VideoPlayActivity, "视频播放失败：${error.errorCodeName}", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            })
        }

        // 根布局
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

        playerView = PlayerView(this).apply {
            this.player = this@VideoPlayActivity.player
            useController = false
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            setBackgroundColor(Color.BLACK)
        }
        root.addView(playerView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        ))

        // 顶部标题栏
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundColor(Color.parseColor("#99000000"))
        }
        titleView = TextView(this).apply {
            text = "${currentIndex + 1} / ${urls.size} · $title"
            setTextColor(Color.WHITE)
            textSize = 15f
            maxLines = 1
        }
        topBar.addView(titleView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        topBar.addView(TextView(this).apply {
            text = "✕ 关闭"
            setTextColor(Color.WHITE)
            textSize = 14f
            setOnClickListener { finish() }
        })
        root.addView(topBar, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP,
        ))

        // 右下倍速
        speedBtn = TextView(this).apply {
            text = "1.0x"
            setTextColor(Color.WHITE)
            textSize = 15f
            gravity = Gravity.CENTER
            setBackgroundResource(android.R.drawable.screen_background_dark_transparent)
            setOnClickListener {
                speedIdx = (speedIdx + 1) % SPEEDS.size
                val s = SPEEDS[speedIdx]
                text = "${s}x"
                player?.playbackParameters = PlaybackParameters(s)
            }
        }
        val speedLp = FrameLayout.LayoutParams(dp(72), dp(44), Gravity.BOTTOM or Gravity.END)
        speedLp.setMargins(0, 0, dp(16), dp(56))
        root.addView(speedBtn, speedLp)

        // 底部进度条 + 时间
        posView = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 12f
        }
        val posLp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END)
        posLp.setMargins(0, 0, dp(16), dp(28))
        root.addView(posView, posLp)

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progress = 0
        }
        val progLp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(3), Gravity.BOTTOM)
        progLp.setMargins(0, 0, 0, dp(20))
        root.addView(progressBar, progLp)

        // 手势：上下滑切换 / 单击暂停播放
        root.setOnTouchListener { _, event -> gestureDetector.onTouchEvent(event) }

        setContentView(root)
        playUrl(urls[currentIndex])
        handler.post(progressRunnable)
    }

    private fun playUrl(url: String) {
        player?.let { exo ->
            val httpFactory = DefaultHttpDataSource.Factory()
            val cacheFactory = CacheDataSource.Factory()
                .setCache(VideoCacheHolder.get(this))
                .setUpstreamDataSourceFactory(httpFactory)
            val source = ProgressiveMediaSource.Factory(cacheFactory)
                .createMediaSource(MediaItem.fromUri(url))
            exo.setMediaSource(source, true)
            exo.prepare()
            exo.playWhenReady = true
            isPlaying = true
        }
    }

    private fun switchVideo(delta: Int) {
        if (urls.isEmpty()) return
        val newIndex = (currentIndex + delta + urls.size) % urls.size
        currentIndex = newIndex
        playUrl(urls[newIndex])
        progressBar.progress = 0
        updateTitle()
        updateProgress()
    }

    private fun togglePlay() {
        val exo = player ?: return
        isPlaying = !isPlaying
        if (isPlaying) exo.play() else exo.pause()
    }

    private fun updateTitle() {
        val t = titleView.text.toString()
        titleView.text = "${currentIndex + 1} / ${urls.size} · ${t.substringAfter("· ")}"
    }

    private fun updateProgress() {
        val exo = player ?: return
        val d = exo.duration.takeIf { it > 0 } ?: 0L
        val c = exo.currentPosition
        progressBar.progress = if (d > 0) (c * 1000 / d).toInt() else 0
        posView.text = "${fmtMs(c)} / ${fmtMs(d)}"
    }

    private fun fmtMs(ms: Long): String {
        val s = ms / 1000
        val m = s / 60
        val sec = s % 60
        return "$m:${if (sec < 10) "0$sec" else "$sec"}"
    }

    override fun onStop() {
        super.onStop()
        player?.playWhenReady = false
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(progressRunnable)
        player?.release()
        player = null
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val EXTRA_URLS = "urls"
        private const val EXTRA_URL = "url"
        private const val EXTRA_INDEX = "index"
        private const val EXTRA_TITLE = "title"
        private val SPEEDS = floatArrayOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

        fun start(context: Context, urls: List<String>, index: Int = 0, title: String = "") {
            val intent = Intent(context, VideoPlayActivity::class.java)
                .putStringArrayListExtra(EXTRA_URLS, ArrayList(urls))
                .putExtra(EXTRA_INDEX, index)
                .putExtra(EXTRA_TITLE, title)
            if (context !is android.app.Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }

        @JvmStatic
        fun startSingle(context: Context, url: String, title: String = "") {
            start(context, listOf(url), 0, title)
        }
    }
}
