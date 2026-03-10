package com.reelsplayer

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.RecyclerView

class VideoAdapter(
    private val videos: List<Pair<String, Uri>>
) : RecyclerView.Adapter<VideoAdapter.VideoViewHolder>() {

    private val attachedHolders = mutableMapOf<Int, VideoViewHolder>()
    private var currentPosition = -1
    private val handler = Handler(Looper.getMainLooper())
    private var progressRunnable: Runnable? = null
    private var userPaused = false

    class VideoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val playerView: PlayerView = view.findViewById(R.id.playerView)
        val pauseIcon: ImageView = view.findViewById(R.id.pauseIcon)
        val speedLabel: TextView = view.findViewById(R.id.speedLabel)
        val progressFill: View = view.findViewById(R.id.progressFill)
        var player: ExoPlayer? = null
        var pendingHideRunnable: Runnable? = null
        var attachedPosition: Int = RecyclerView.NO_POSITION
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_video, parent, false)
        return VideoViewHolder(view)
    }

    @OptIn(UnstableApi::class)
    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        // no-op, managed in attach/detach
    }

    override fun getItemCount() = videos.size

    @OptIn(UnstableApi::class)
    override fun onViewAttachedToWindow(holder: VideoViewHolder) {
        super.onViewAttachedToWindow(holder)
        val position = holder.bindingAdapterPosition
        if (position == RecyclerView.NO_POSITION) return
        val (_, uri) = videos[position]

        attachedHolders[position]?.let { old ->
            old.player?.release()
            old.player = null
            old.playerView.player = null
        }

        val player = ExoPlayer.Builder(holder.itemView.context).build()
        holder.player = player
        holder.playerView.player = player

        player.setMediaItem(MediaItem.fromUri(uri))
        player.repeatMode = ExoPlayer.REPEAT_MODE_ONE
        player.prepare()
        player.playWhenReady = (position == currentPosition)

        holder.attachedPosition = position
        attachedHolders[position] = holder

        setupTouchListener(holder)
    }

    private fun setupTouchListener(holder: VideoViewHolder) {
        var isSpeedMode = false
        var longPressRunnable: Runnable? = null

        val gestureDetector = GestureDetector(holder.itemView.context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    handleTap(holder)
                    return true
                }
            }
        )

        holder.playerView.setOnTouchListener { v, event ->
            gestureDetector.onTouchEvent(event)

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isSpeedMode = false
                    val inRightZone = event.x > v.width * 0.67f
                    if (inRightZone) {
                        longPressRunnable = Runnable {
                            isSpeedMode = true
                            // Tell ViewPager2 to not intercept while in speed mode
                            v.parent?.requestDisallowInterceptTouchEvent(true)
                            holder.player?.playbackParameters = PlaybackParameters(2f)
                            holder.speedLabel.visibility = View.VISIBLE
                        }
                        handler.postDelayed(longPressRunnable!!, 300)
                    }
                    // Return false so ViewPager2 can still intercept for swipes
                    false
                }

                MotionEvent.ACTION_MOVE -> {
                    if (!isSpeedMode) {
                        // Cancel long press if still pending — ViewPager2 handles the swipe
                        longPressRunnable?.let { handler.removeCallbacks(it) }
                        longPressRunnable = null
                    }
                    false
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    longPressRunnable?.let { handler.removeCallbacks(it) }
                    longPressRunnable = null

                    if (isSpeedMode) {
                        holder.player?.playbackParameters = PlaybackParameters(1f)
                        holder.speedLabel.visibility = View.GONE
                        isSpeedMode = false
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                    false
                }

                else -> false
            }
        }
    }

    private fun handleTap(holder: VideoViewHolder) {
        val p = holder.player ?: return
        holder.pendingHideRunnable?.let { r -> handler.removeCallbacks(r) }
        if (p.isPlaying) {
            p.pause()
            userPaused = true
            holder.pauseIcon.setImageResource(android.R.drawable.ic_media_pause)
            holder.pauseIcon.visibility = View.VISIBLE
        } else {
            p.play()
            userPaused = false
            holder.pauseIcon.setImageResource(android.R.drawable.ic_media_play)
            holder.pauseIcon.visibility = View.VISIBLE
            val hideRunnable = Runnable { holder.pauseIcon.visibility = View.GONE }
            holder.pendingHideRunnable = hideRunnable
            handler.postDelayed(hideRunnable, 400)
        }
    }

    @OptIn(UnstableApi::class)
    override fun onViewDetachedFromWindow(holder: VideoViewHolder) {
        super.onViewDetachedFromWindow(holder)
        val position = holder.attachedPosition
        holder.pendingHideRunnable?.let { handler.removeCallbacks(it) }
        holder.pendingHideRunnable = null
        holder.player?.release()
        holder.player = null
        holder.playerView.player = null
        holder.pauseIcon.visibility = View.GONE
        holder.speedLabel.visibility = View.GONE
        holder.progressFill.scaleX = 0f
        if (position != RecyclerView.NO_POSITION) attachedHolders.remove(position)
        holder.attachedPosition = RecyclerView.NO_POSITION
    }

    fun onPageSelected(position: Int) {
        val previousPosition = currentPosition
        currentPosition = position
        userPaused = false
        for ((pos, holder) in attachedHolders) {
            val player = holder.player ?: continue
            if (pos == position) {
                if (previousPosition != position) {
                    player.seekTo(0)
                }
                player.playWhenReady = true
                player.playbackParameters = PlaybackParameters(1f)
                holder.pauseIcon.visibility = View.GONE
                holder.speedLabel.visibility = View.GONE
            } else {
                player.playWhenReady = false
            }
        }
        startProgressUpdates()
    }

    private fun startProgressUpdates() {
        progressRunnable?.let { handler.removeCallbacks(it) }
        val runnable = object : Runnable {
            override fun run() {
                val holder = attachedHolders[currentPosition] ?: return
                val player = holder.player ?: return
                val duration = player.duration
                if (duration > 0) {
                    val progress = player.currentPosition.toFloat() / duration.toFloat()
                    holder.progressFill.scaleX = progress
                }
                handler.postDelayed(this, 200)
            }
        }
        progressRunnable = runnable
        handler.post(runnable)
    }

    fun pauseCurrent() {
        attachedHolders[currentPosition]?.player?.pause()
        progressRunnable?.let { handler.removeCallbacks(it) }
    }

    fun resumeCurrent() {
        val holder = attachedHolders[currentPosition] ?: return
        if (!userPaused) {
            holder.player?.playWhenReady = true
            holder.pauseIcon.visibility = View.GONE
        }
        startProgressUpdates()
    }

    fun releaseAll() {
        progressRunnable?.let { handler.removeCallbacks(it) }
        progressRunnable = null
        for ((_, holder) in attachedHolders) {
            holder.pendingHideRunnable?.let { handler.removeCallbacks(it) }
            holder.player?.release()
            holder.player = null
            holder.playerView.player = null
        }
        attachedHolders.clear()
        handler.removeCallbacksAndMessages(null)
    }
}
