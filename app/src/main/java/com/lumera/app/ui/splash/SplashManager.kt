package com.lumera.app.ui.splash

import android.app.Activity
import android.media.MediaPlayer
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import com.lumera.app.R

class SplashManager(private val activity: Activity, private val onDismiss: () -> Unit) {
    var splashPlayer: MediaPlayer? = null
    var splashOverlay: View? = null
    var splashIndicator: ProgressBar? = null
    var splashPausedAtLogo = false
    var splashAppReady = false

    fun prepare() {
        splashPlayer = try {
            MediaPlayer().apply {
                activity.resources.openRawResourceFd(R.raw.splash_video).use { afd ->
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                }
                isLooping = false
                prepare()
            }
        } catch (_: Exception) { null }
    }

    fun attachOverlay() {
        val player = splashPlayer ?: return
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val density = activity.resources.displayMetrics.density

        val container = FrameLayout(activity).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
        }

        val surfaceView = SurfaceView(activity)
        container.addView(surfaceView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        val indicator = ProgressBar(activity).apply {
            indeterminateTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
            visibility = View.GONE
        }
        container.addView(indicator, FrameLayout.LayoutParams((36 * density).toInt(), (36 * density).toInt()).apply {
            gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
            bottomMargin = (80 * density).toInt()
        })
        splashIndicator = indicator

        player.setOnCompletionListener { dismiss() }

        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                player.setDisplay(holder)
                player.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT)
                player.start()
                val pollRunnable = object : Runnable {
                    override fun run() {
                        try {
                            if (player.isPlaying && player.currentPosition >= 4500) {
                                if (!splashAppReady) {
                                    player.pause()
                                    splashPausedAtLogo = true
                                    indicator.visibility = View.VISIBLE
                                }
                            } else if (player.isPlaying) {
                                handler.postDelayed(this, 50)
                            }
                        } catch (_: IllegalStateException) {}
                    }
                }
                handler.postDelayed(pollRunnable, 50)
            }
            override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, height: Int) {}
            override fun surfaceDestroyed(h: SurfaceHolder) {}
        })

        activity.addContentView(container, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        splashOverlay = container
    }

    fun onAppReady() {
        if (splashAppReady) return
        splashAppReady = true
        val player = splashPlayer ?: return
        if (splashPausedAtLogo) {
            splashPausedAtLogo = false
            splashIndicator?.visibility = View.GONE
            player.start()
        }
    }

    fun dismiss() {
        splashOverlay?.let { (it.parent as? ViewGroup)?.removeView(it) }
        splashOverlay = null
        splashIndicator = null
        splashPlayer?.release()
        splashPlayer = null
        onDismiss()
    }
}
