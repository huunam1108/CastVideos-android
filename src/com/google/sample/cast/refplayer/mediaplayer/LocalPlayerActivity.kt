/*
 * Copyright (C) 2016 Google LLC. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.sample.cast.refplayer.mediaplayer

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Point
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.*
import android.widget.*
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import com.android.volley.toolbox.ImageLoader
import com.android.volley.toolbox.NetworkImageView
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadOptions
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.MediaUtils
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.sample.cast.refplayer.R
import com.google.sample.cast.refplayer.browser.VideoProvider
import com.google.sample.cast.refplayer.expandedcontrols.CustomExpandedControlsActivity
import com.google.sample.cast.refplayer.queue.ui.QueueListViewActivity
import com.google.sample.cast.refplayer.settings.CastPreference
import com.google.sample.cast.refplayer.utils.CustomVolleyRequest
import com.google.sample.cast.refplayer.utils.Utils
import java.util.*

/**
 * Activity for the local media player.
 */
class LocalPlayerActivity : AppCompatActivity() {
    private var videoView: VideoView? = null
    private var titleView: TextView? = null
    private var descriptionView: TextView? = null
    private var startText: TextView? = null
    private var endText: TextView? = null
    private var seekbar: SeekBar? = null
    private var playPause: ImageView? = null
    private var loading: ProgressBar? = null
    private var controllers: View? = null
    private var container: View? = null
    private var coverArt: NetworkImageView? = null
    private var seekbarTimer: Timer? = null
    private var controllersTimer: Timer? = null
    private var playbackLocation: PlaybackLocation? = null
    private var playbackState: PlaybackState? = null
    private val handler = Handler()
    private val aspectRatio = 72f / 128
    private var selectedMedia: MediaInfo? = null
    private var controllersVisible: Boolean = false
    private var mDuration: Int = 0
    private var authorView: TextView? = null
    private var playCircle: ImageButton? = null
    private var castContext: CastContext? = null
    private var castSession: CastSession? = null
    private var sessionManagerListener: SessionManagerListener<CastSession>? = null
    private var queueMenuItem: MenuItem? = null
    private var imageLoader: ImageLoader? = null

    /**
     * indicates whether we are doing a local or a remote playback
     */
    enum class PlaybackLocation {
        LOCAL,
        REMOTE
    }

    /**
     * List of various states that we can be in
     */
    enum class PlaybackState {
        PLAYING, PAUSED, BUFFERING, IDLE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.player_activity)
        loadViews()
        setupControlsCallbacks()
        setupCastListener()
        castContext = CastContext.getSharedInstance(this)
        castSession = castContext!!.sessionManager.currentCastSession
        // see what we need to play and where
        val bundle = intent.extras
        if (bundle != null) {
            selectedMedia = intent.getParcelableExtra("media")
            setupActionBar()
            val shouldStartPlayback = bundle.getBoolean("shouldStart")
            val startPosition = bundle.getInt("startPosition", 0)
            videoView!!.setVideoURI(Uri.parse(selectedMedia!!.contentId))
            Log.d(TAG, "Setting url of the VideoView to: " + selectedMedia!!.contentId)
            if (shouldStartPlayback) {
                // this will be the case only if we are coming from the
                // CastControllerActivity by disconnecting from a device
                playbackState = PlaybackState.PLAYING
                updatePlaybackLocation(PlaybackLocation.LOCAL)
                updatePlayButton(playbackState!!)
                if (startPosition > 0) {
                    videoView!!.seekTo(startPosition)
                }
                videoView!!.start()
                startControllersTimer()
            } else {
                // we should load the video but pause it
                // and show the album art.
                if (castSession != null && castSession!!.isConnected) {
                    updatePlaybackLocation(PlaybackLocation.REMOTE)
                } else {
                    updatePlaybackLocation(PlaybackLocation.LOCAL)
                }
                playbackState = PlaybackState.IDLE
                updatePlayButton(playbackState!!)
            }
        }
        if (titleView != null) {
            updateMetadata(true)
        }
    }

    private fun setupCastListener() {
        sessionManagerListener = object : SessionManagerListener<CastSession> {

            override fun onSessionEnded(session: CastSession, error: Int) {
                onApplicationDisconnected()
            }

            override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
                onApplicationConnected(session)
            }

            override fun onSessionResumeFailed(session: CastSession, error: Int) {
                onApplicationDisconnected()
            }

            override fun onSessionStarted(session: CastSession, sessionId: String) {
                onApplicationConnected(session)
            }

            override fun onSessionStartFailed(session: CastSession, error: Int) {
                onApplicationDisconnected()
            }

            override fun onSessionStarting(session: CastSession) {}

            override fun onSessionEnding(session: CastSession) {}

            override fun onSessionResuming(session: CastSession, sessionId: String) {}

            override fun onSessionSuspended(session: CastSession, reason: Int) {}

            private fun onApplicationConnected(session: CastSession) {
                castSession = session
                if (null != selectedMedia) {

                    if (playbackState == PlaybackState.PLAYING) {
                        videoView!!.pause()
                        loadRemoteMedia(seekbar!!.progress, true)
                        return
                    } else {
                        playbackState = PlaybackState.IDLE
                        updatePlaybackLocation(PlaybackLocation.REMOTE)
                    }
                }
                updatePlayButton(playbackState!!)
                invalidateOptionsMenu()
            }

            private fun onApplicationDisconnected() {
                updatePlaybackLocation(PlaybackLocation.LOCAL)
                playbackState = PlaybackState.IDLE
                playbackLocation = PlaybackLocation.LOCAL
                updatePlayButton(playbackState!!)
                invalidateOptionsMenu()
            }
        }
    }

    private fun updatePlaybackLocation(location: PlaybackLocation) {
        playbackLocation = location
        if (location == PlaybackLocation.LOCAL) {
            if (playbackState == PlaybackState.PLAYING || playbackState == PlaybackState.BUFFERING) {
                setCoverArtStatus(null)
                startControllersTimer()
            } else {
                stopControllersTimer()
                setCoverArtStatus(MediaUtils.getImageUrl(selectedMedia, 0))
            }
        } else {
            stopControllersTimer()
            setCoverArtStatus(MediaUtils.getImageUrl(selectedMedia, 0))
            updateControllersVisibility(false)
        }
    }

    private fun play(position: Int) {
        startControllersTimer()
        when (playbackLocation) {
            LocalPlayerActivity.PlaybackLocation.LOCAL -> {
                videoView!!.seekTo(position)
                videoView!!.start()
            }
            LocalPlayerActivity.PlaybackLocation.REMOTE -> {
                playbackState = PlaybackState.BUFFERING
                updatePlayButton(playbackState!!)
                castSession!!.remoteMediaClient.seek(position.toLong())
            }
            else -> {
            }
        }
        restartTrickplayTimer()
    }

    private fun togglePlayback() {
        stopControllersTimer()
        when (playbackState) {
            LocalPlayerActivity.PlaybackState.PAUSED -> when (playbackLocation) {
                LocalPlayerActivity.PlaybackLocation.LOCAL -> {
                    videoView!!.start()
                    Log.d(TAG, "Playing locally...")
                    playbackState = PlaybackState.PLAYING
                    startControllersTimer()
                    restartTrickplayTimer()
                    updatePlaybackLocation(PlaybackLocation.LOCAL)
                }
                LocalPlayerActivity.PlaybackLocation.REMOTE -> {
                    loadRemoteMedia(0, true)
                    finish()
                }
                else -> {
                }
            }

            LocalPlayerActivity.PlaybackState.PLAYING -> {
                playbackState = PlaybackState.PAUSED
                videoView!!.pause()
            }

            LocalPlayerActivity.PlaybackState.IDLE -> when (playbackLocation) {
                LocalPlayerActivity.PlaybackLocation.LOCAL -> {
                    videoView!!.setVideoURI(Uri.parse(selectedMedia!!.contentId))
                    videoView!!.seekTo(0)
                    videoView!!.start()
                    playbackState = PlaybackState.PLAYING
                    restartTrickplayTimer()
                    updatePlaybackLocation(PlaybackLocation.LOCAL)
                }
                LocalPlayerActivity.PlaybackLocation.REMOTE -> if (castSession != null && castSession!!.isConnected) {
                    Utils.showQueuePopup(this, playCircle!!, selectedMedia!!)
                }
                else -> {
                }
            }
            else -> {
            }
        }
        updatePlayButton(playbackState!!)
    }

    private fun loadRemoteMedia(position: Int, autoPlay: Boolean) {
        if (castSession == null) {
            return
        }
        val remoteMediaClient = castSession!!.remoteMediaClient ?: return
        remoteMediaClient.registerCallback(object : RemoteMediaClient.Callback() {
            override fun onStatusUpdated() {
                val intent = Intent(this@LocalPlayerActivity,
                    CustomExpandedControlsActivity::class.java)
                startActivity(intent)
                remoteMediaClient.unregisterCallback(this)
            }
        })
        remoteMediaClient.load(selectedMedia,
            MediaLoadOptions.Builder()
                .setAutoplay(autoPlay)
                .setPlayPosition(position.toLong()).build())
    }

    private fun setCoverArtStatus(url: String?) {
        if (url != null) {
            imageLoader = CustomVolleyRequest.getInstance(this.applicationContext)
                .imageLoader
            imageLoader!!.get(url, ImageLoader.getImageListener(coverArt, 0, 0))
            coverArt!!.setImageUrl(url, imageLoader)

            coverArt!!.visibility = View.VISIBLE
            videoView!!.visibility = View.INVISIBLE
        } else {
            coverArt!!.visibility = View.GONE
            videoView!!.visibility = View.VISIBLE
        }
    }

    private fun stopTrickplayTimer() {
        Log.d(TAG, "Stopped TrickPlay Timer")
        if (seekbarTimer != null) {
            seekbarTimer!!.cancel()
        }
    }

    private fun restartTrickplayTimer() {
        stopTrickplayTimer()
        seekbarTimer = Timer()
        seekbarTimer!!.scheduleAtFixedRate(UpdateSeekbarTask(), 100, 1000)
        Log.d(TAG, "Restarted TrickPlay Timer")
    }

    private fun stopControllersTimer() {
        if (controllersTimer != null) {
            controllersTimer!!.cancel()
        }
    }

    private fun startControllersTimer() {
        if (controllersTimer != null) {
            controllersTimer!!.cancel()
        }
        if (playbackLocation == PlaybackLocation.REMOTE) {
            return
        }
        controllersTimer = Timer()
        controllersTimer!!.schedule(HideControllersTask(), 5000)
    }

    // should be called from the main thread
    private fun updateControllersVisibility(show: Boolean) {
        if (show) {
            supportActionBar!!.show()
            controllers!!.visibility = View.VISIBLE
        } else {
            if (!Utils.isOrientationPortrait(this)) {
                supportActionBar!!.hide()
            }
            controllers!!.visibility = View.INVISIBLE
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause() was called")
        if (playbackLocation == PlaybackLocation.LOCAL) {

            if (seekbarTimer != null) {
                seekbarTimer!!.cancel()
                seekbarTimer = null
            }
            if (controllersTimer != null) {
                controllersTimer!!.cancel()
            }
            // since we are playing locally, we need to stop the playback of
            // video (if user is not watching, pause it!)
            videoView!!.pause()
            playbackState = PlaybackState.PAUSED
            updatePlayButton(PlaybackState.PAUSED)
        }
        castContext!!.sessionManager.removeSessionManagerListener(
            sessionManagerListener, CastSession::class.java!!)
    }

    override fun onStop() {
        Log.d(TAG, "onStop() was called")
        super.onStop()
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy() is called")
        stopControllersTimer()
        stopTrickplayTimer()
        super.onDestroy()
    }

    override fun onStart() {
        Log.d(TAG, "onStart was called")
        super.onStart()
    }

    override fun onResume() {
        Log.d(TAG, "onResume() was called")
        castContext!!.sessionManager.addSessionManagerListener(
            sessionManagerListener!!, CastSession::class.java)
        if (castSession != null && castSession!!.isConnected) {
            updatePlaybackLocation(PlaybackLocation.REMOTE)
        } else {
            updatePlaybackLocation(PlaybackLocation.LOCAL)
        }
        if (queueMenuItem != null) {
            queueMenuItem!!.isVisible = castSession != null && castSession!!.isConnected
        }
        super.onResume()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        return castContext!!.onDispatchVolumeKeyEventBeforeJellyBean(
            event) || super.dispatchKeyEvent(event)
    }

    private inner class HideControllersTask : TimerTask() {

        override fun run() {
            handler.post {
                updateControllersVisibility(false)
                controllersVisible = false
            }

        }
    }

    private inner class UpdateSeekbarTask : TimerTask() {

        override fun run() {
            handler.post {
                if (playbackLocation == PlaybackLocation.LOCAL) {
                    val currentPos = videoView!!.currentPosition
                    updateSeekbar(currentPos, mDuration)
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupControlsCallbacks() {
        videoView!!.setOnErrorListener { mp, what, extra ->
            Log.e(TAG, "OnErrorListener.onError(): VideoView encountered an "
                + "error, what: " + what + ", extra: " + extra)
            val msg: String = when {
                extra == MediaPlayer.MEDIA_ERROR_TIMED_OUT -> getString(
                    R.string.video_error_media_load_timeout)
                what == MediaPlayer.MEDIA_ERROR_SERVER_DIED -> getString(
                    R.string.video_error_server_unaccessible)
                else -> getString(R.string.video_error_unknown_error)
            }
            Utils.showErrorDialog(this@LocalPlayerActivity, msg)
            videoView!!.stopPlayback()
            playbackState = PlaybackState.IDLE
            updatePlayButton(playbackState!!)
            true
        }

        videoView!!.setOnPreparedListener { mp ->
            Log.d(TAG, "onPrepared is reached")
            mDuration = mp.duration
            endText!!.text = Utils.formatMillis(mDuration)
            seekbar!!.max = mDuration
            restartTrickplayTimer()
        }

        videoView!!.setOnCompletionListener {
            stopTrickplayTimer()
            Log.d(TAG, "setOnCompletionListener()")
            playbackState = PlaybackState.IDLE
            updatePlayButton(playbackState!!)
        }

        videoView!!.setOnTouchListener { v, event ->
            if (!controllersVisible) {
                updateControllersVisibility(true)
            }
            startControllersTimer()
            false
        }

        seekbar!!.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                if (playbackState == PlaybackState.PLAYING) {
                    play(seekBar.progress)
                } else if (playbackState != PlaybackState.IDLE) {
                    videoView!!.seekTo(seekBar.progress)
                }
                startControllersTimer()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                stopTrickplayTimer()
                videoView!!.pause()
                stopControllersTimer()
            }

            override fun onProgressChanged(seekBar: SeekBar, progress: Int,
                fromUser: Boolean) {
                startText!!.text = Utils.formatMillis(progress)
            }
        })

        playPause!!.setOnClickListener {
            if (playbackLocation == PlaybackLocation.LOCAL) {
                togglePlayback()
            }
        }
    }

    private fun updateSeekbar(position: Int, duration: Int) {
        seekbar!!.progress = position
        seekbar!!.max = duration
        startText!!.text = Utils.formatMillis(position)
        endText!!.text = Utils.formatMillis(duration)
    }

    private fun updatePlayButton(state: PlaybackState) {
        Log.d(TAG, "Controls: PlayBackState: $state")
        val isConnected = castSession != null && (castSession!!.isConnected || castSession!!.isConnecting)
        controllers!!.visibility = if (isConnected) View.GONE else View.VISIBLE
        playCircle!!.visibility = if (isConnected) View.GONE else View.VISIBLE
        when (state) {
            LocalPlayerActivity.PlaybackState.PLAYING -> {
                loading!!.visibility = View.INVISIBLE
                playPause!!.visibility = View.VISIBLE
                playPause!!.setImageDrawable(
                    resources.getDrawable(R.drawable.ic_av_pause_dark))
                playCircle!!.visibility = if (isConnected) View.VISIBLE else View.GONE
            }
            LocalPlayerActivity.PlaybackState.IDLE -> {
                playCircle!!.visibility = View.VISIBLE
                controllers!!.visibility = View.GONE
                coverArt!!.visibility = View.VISIBLE
                videoView!!.visibility = View.INVISIBLE
            }
            LocalPlayerActivity.PlaybackState.PAUSED -> {
                loading!!.visibility = View.INVISIBLE
                playPause!!.visibility = View.VISIBLE
                playPause!!.setImageDrawable(
                    resources.getDrawable(R.drawable.ic_av_play_dark))
                playCircle!!.visibility = if (isConnected) View.VISIBLE else View.GONE
            }
            LocalPlayerActivity.PlaybackState.BUFFERING -> {
                playPause!!.visibility = View.INVISIBLE
                loading!!.visibility = View.VISIBLE
            }
        }
    }

    @SuppressLint("NewApi")
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        supportActionBar!!.show()
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN)
            window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN)
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LOW_PROFILE
            updateMetadata(false)
            container!!.setBackgroundColor(resources.getColor(R.color.black))

        } else {
            window.setFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN)
            window.clearFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN)
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            updateMetadata(true)
            container!!.setBackgroundColor(resources.getColor(R.color.white))
        }
    }

    private fun updateMetadata(visible: Boolean) {
        val displaySize: Point
        if (!visible) {
            descriptionView!!.visibility = View.GONE
            titleView!!.visibility = View.GONE
            authorView!!.visibility = View.GONE
            displaySize = Utils.getDisplaySize(this)
            val lp = RelativeLayout.LayoutParams(displaySize.x,
                displaySize.y + supportActionBar!!.height)
            lp.addRule(RelativeLayout.CENTER_IN_PARENT)
            videoView!!.layoutParams = lp
            videoView!!.invalidate()
        } else {
            val mm = selectedMedia!!.metadata
            descriptionView!!.text = selectedMedia!!.customData.optString(
                VideoProvider.KEY_DESCRIPTION)
            titleView!!.text = mm.getString(MediaMetadata.KEY_TITLE)
            authorView!!.text = mm.getString(MediaMetadata.KEY_SUBTITLE)
            descriptionView!!.visibility = View.VISIBLE
            titleView!!.visibility = View.VISIBLE
            authorView!!.visibility = View.VISIBLE
            displaySize = Utils.getDisplaySize(this)
            val lp = RelativeLayout.LayoutParams(displaySize.x,
                (displaySize.x * aspectRatio).toInt())
            lp.addRule(RelativeLayout.BELOW, R.id.toolbar)
            videoView!!.layoutParams = lp
            videoView!!.invalidate()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.player, menu)
        CastButtonFactory.setUpMediaRouteButton(applicationContext, menu,
            R.id.media_route_menu_item)
        queueMenuItem = menu.findItem(R.id.action_show_queue)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(
            R.id.action_show_queue).isVisible = castSession != null && castSession!!.isConnected
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val intent: Intent
        if (item.itemId == R.id.action_settings) {
            intent = Intent(this@LocalPlayerActivity, CastPreference::class.java)
            startActivity(intent)
        } else if (item.itemId == R.id.action_show_queue) {
            intent = Intent(this@LocalPlayerActivity, QueueListViewActivity::class.java)
            startActivity(intent)
        } else if (item.itemId == android.R.id.home) {
            ActivityCompat.finishAfterTransition(this)
        }
        return true
    }

    private fun setupActionBar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        toolbar.title = selectedMedia!!.metadata.getString(MediaMetadata.KEY_TITLE)
        setSupportActionBar(toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
    }

    private fun loadViews() {
        videoView = findViewById(R.id.videoView)
        titleView = findViewById(R.id.titleTextView)
        descriptionView = findViewById(R.id.descriptionTextView)
        descriptionView!!.movementMethod = ScrollingMovementMethod()
        authorView = findViewById(R.id.authorTextView)
        startText = findViewById(R.id.startText)
        startText!!.text = Utils.formatMillis(0)
        endText = findViewById(R.id.endText)
        seekbar = findViewById(R.id.seekBar1)
        playPause = findViewById(R.id.playPauseImageView)
        loading = findViewById(R.id.progressBar1)
        controllers = findViewById(R.id.controllers)
        container = findViewById(R.id.container)
        coverArt = findViewById(R.id.coverArtView)
        ViewCompat.setTransitionName(coverArt!!, getString(R.string.transition_image))
        playCircle = findViewById(R.id.play_circle)
        playCircle!!.setOnClickListener { togglePlayback() }
    }

    companion object {

        private const val TAG = "LocalPlayerActivity"
    }
}
