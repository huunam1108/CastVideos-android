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

package com.google.sample.cast.refplayer.queue.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.sample.cast.refplayer.MySessionManagerListener
import com.google.sample.cast.refplayer.R
import com.google.sample.cast.refplayer.queue.QueueDataProvider
import com.google.sample.cast.refplayer.settings.CastPreference

/**
 * An activity to show the queue list
 */
class QueueListViewActivity : AppCompatActivity() {

    private val remoteMediaClientCallback = MyRemoteMediaClientCallback()
    private var castContext: CastContext? = null
    private var remoteMediaClient1: RemoteMediaClient? = null
    private var emptyView: View? = null
    private val remoteMediaClient: RemoteMediaClient?
        get() {
            val castSession = castContext?.sessionManager?.currentCastSession
            return if (castSession != null && castSession.isConnected)
                castSession.remoteMediaClient
            else
                null
        }
    private val sessionManagerListener = object : MySessionManagerListener() {
        override fun onSessionEnded(session: CastSession, error: Int) {
            if (remoteMediaClient1 != null) {
                remoteMediaClient1!!.registerCallback(remoteMediaClientCallback)
            }
            remoteMediaClient1 = null
            emptyView!!.visibility = View.VISIBLE
        }

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            remoteMediaClient1 = remoteMediaClient
            if (remoteMediaClient1 != null) {
                remoteMediaClient1!!.registerCallback(remoteMediaClientCallback)
            }
        }

        override fun onSessionStarted(session: CastSession, sessionId: String) {
            remoteMediaClient1 = remoteMediaClient
            if (remoteMediaClient1 != null) {
                remoteMediaClient1!!.registerCallback(remoteMediaClientCallback)
            }
        }

        override fun onSessionSuspended(session: CastSession, reason: Int) {
            if (remoteMediaClient1 != null) {
                remoteMediaClient1!!.unregisterCallback(remoteMediaClientCallback)
            }
            remoteMediaClient1 = null
        }
    }
    private inner class MyRemoteMediaClientCallback : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() {
            updateMediaQueue()
        }

        override fun onQueueStatusUpdated() {
            updateMediaQueue()
        }

        private fun updateMediaQueue() {
            val mediaStatus = remoteMediaClient1!!.mediaStatus
            val queueItems = mediaStatus?.queueItems
            if (queueItems == null || queueItems.isEmpty()) {
                emptyView!!.visibility = View.VISIBLE
            } else {
                emptyView!!.visibility = View.GONE
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.queue_activity)
        Log.d(TAG, "onCreate() was called")

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .add(R.id.container, QueueListViewFragment(), FRAGMENT_LIST_VIEW)
                .commit()
        }
        setupActionBar()
        emptyView = findViewById(R.id.empty)
        castContext = CastContext.getSharedInstance(this)
    }

    private fun setupActionBar() {
        val toolbar = findViewById<View>(R.id.toolbar) as Toolbar
        toolbar.setTitle(R.string.queue_list)
        setSupportActionBar(toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
    }

    override fun onPause() {
        if (remoteMediaClient1 != null) {
            remoteMediaClient1!!.unregisterCallback(remoteMediaClientCallback)
        }
        castContext!!.sessionManager
            .removeSessionManagerListener(sessionManagerListener, CastSession::class.java)
        super.onPause()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.queue_menu, menu)
        CastButtonFactory.setUpMediaRouteButton(applicationContext, menu,
            R.id.media_route_menu_item)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_settings -> startActivity(
                Intent(this@QueueListViewActivity, CastPreference::class.java))
            R.id.action_clear_queue -> QueueDataProvider.getInstance(applicationContext).removeAll()
            android.R.id.home -> finish()
        }
        return true
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        return castContext!!.onDispatchVolumeKeyEventBeforeJellyBean(
            event) || super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        castContext!!.sessionManager
            .addSessionManagerListener(sessionManagerListener, CastSession::class.java)
        if (remoteMediaClient1 == null) {
            remoteMediaClient1 = remoteMediaClient
        }
        if (remoteMediaClient1 != null) {
            remoteMediaClient1!!.registerCallback(remoteMediaClientCallback)
            val mediaStatus = remoteMediaClient1!!.mediaStatus
            val queueItems = mediaStatus?.queueItems
            if (queueItems != null && !queueItems.isEmpty()) {
                emptyView!!.visibility = View.GONE
            }
        }
        super.onResume()
    }

    companion object {

        private const val FRAGMENT_LIST_VIEW = "list view"
        private const val TAG = "QueueListViewActivity"
    }
}
