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

package com.google.sample.cast.refplayer

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.gms.cast.framework.*
import com.google.sample.cast.refplayer.queue.ui.QueueListViewActivity
import com.google.sample.cast.refplayer.settings.CastPreference

/**
 * The main activity that displays the list of videos.
 */
class VideoBrowserActivity : AppCompatActivity() {
    private var castContext: CastContext? = null
    private var castSession: CastSession? = null
    private var mediaRouteMenuItem: MenuItem? = null
    private var queueMenuItem: MenuItem? = null
    private var toolbar: Toolbar? = null
    private var introductoryOverlay: IntroductoryOverlay? = null
    private var castStateListener: CastStateListener? = null
    private val sessionManagerListener = object : MySessionManagerListener() {
        override fun onSessionEnded(session: CastSession, error: Int) {
            super.onSessionEnded(session, error)
            if (session === castSession) {
                castSession = null
            }
            invalidateOptionsMenu()
        }

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            super.onSessionResumed(session, wasSuspended)
            castSession = session
            invalidateOptionsMenu()
        }

        override fun onSessionStarted(session: CastSession, sessionId: String) {
            super.onSessionStarted(session, sessionId)
            castSession = session
            invalidateOptionsMenu()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.video_browser)
        setupActionBar()

        castStateListener = CastStateListener { newState ->
            if (newState != CastState.NO_DEVICES_AVAILABLE) {
                showIntroductoryOverlay()
            }
        }
        castContext = CastContext.getSharedInstance(this)
    }

    private fun setupActionBar() {
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.browse, menu)
        mediaRouteMenuItem = CastButtonFactory.setUpMediaRouteButton(applicationContext, menu,
            R.id.media_route_menu_item)
        queueMenuItem = menu.findItem(R.id.action_show_queue)
        showIntroductoryOverlay()
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
            intent = Intent(this@VideoBrowserActivity, CastPreference::class.java)
            startActivity(intent)
        } else if (item.itemId == R.id.action_show_queue) {
            intent = Intent(this@VideoBrowserActivity, QueueListViewActivity::class.java)
            startActivity(intent)
        }
        return true
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        return castContext!!.onDispatchVolumeKeyEventBeforeJellyBean(
            event) || super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        castContext?.apply {
            addCastStateListener(castStateListener)
            sessionManager.addSessionManagerListener(sessionManagerListener,
                CastSession::class.java)
        }
        if (castSession == null) {
            castSession = CastContext.getSharedInstance(this).sessionManager.currentCastSession
        }
        if (queueMenuItem != null) {
            queueMenuItem?.isVisible = castSession != null && castSession!!.isConnected
        }
        super.onResume()
    }

    override fun onPause() {
        castContext?.apply {
            removeCastStateListener(castStateListener)
            sessionManager.removeSessionManagerListener(sessionManagerListener,
                CastSession::class.java)
        }
        super.onPause()
    }

    private fun showIntroductoryOverlay() {
        if (introductoryOverlay != null) {
            introductoryOverlay!!.remove()
        }
        if (mediaRouteMenuItem != null && mediaRouteMenuItem!!.isVisible) {
            Handler().post {
                introductoryOverlay = IntroductoryOverlay.Builder(this@VideoBrowserActivity,
                    mediaRouteMenuItem!!).setTitleText(getString(R.string.introducing_cast))
                    .setOverlayColor(R.color.primary)
                    .setSingleTime()
                    .setOnOverlayDismissedListener { introductoryOverlay = null }
                    .build()
                introductoryOverlay!!.show()
            }
        }
    }
}
