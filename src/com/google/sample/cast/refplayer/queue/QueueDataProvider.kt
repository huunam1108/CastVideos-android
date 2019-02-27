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

package com.google.sample.cast.refplayer.queue

import android.content.Context
import android.util.Log
import android.view.View
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.sample.cast.refplayer.MySessionManagerListener
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A singleton to manage the queue. Upon instantiation, it syncs up its own copy of the queue with
 * the one that the VideoCastManager holds. After that point, it maintains an up-to-date version of
 * the queue. UI elements get their data from this class. A boolean field, `mDetachedQueue`
 * is used to manage whether this changes to the queue coming from the cast framework should be
 * reflected here or not; when in "detached" mode, it means that its own copy of the queue is not
 * kept up to date with the one that the cast framework has. This is needed to preserve the queue
 * when the media session ends.
 */
class QueueDataProvider private constructor(context: Context) {
    private val mAppContext: Context
    private val mQueue = CopyOnWriteArrayList<MediaQueueItem>()
    // Locks modification to the remove queue.
    private val mLock = Any()
    private val mSessionManagerListener = object : MySessionManagerListener() {
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            super.onSessionResumed(session, wasSuspended)
            syncWithRemoteQueue()
        }

        override fun onSessionStarted(session: CastSession, sessionId: String) {
            super.onSessionStarted(session, sessionId)
            syncWithRemoteQueue()
        }

        override fun onSessionEnded(session: CastSession, error: Int) {
            super.onSessionEnded(session, error)
            clearQueue()
            if (mListener != null) {
                mListener!!.onQueueDataChanged()
            }
        }
    }
    private val mRemoteMediaClientCallback = MyRemoteMediaClientCallback()
    var repeatMode: Int = 0
        private set
    val isShuffleOn: Boolean
    var currentItem: MediaQueueItem? = null
        private set
    private var mUpcomingItem: MediaQueueItem? = null
    private var mListener: OnQueueDataChangedListener? = null
    var isQueueDetached = true
        private set

    val count: Int
        get() = mQueue.size

    val currentItemId: Int
        get() = currentItem!!.itemId

    val upcomingItem: MediaQueueItem?
        get() {
            Log.d(TAG, "[upcoming] getUpcomingItem() returning $mUpcomingItem")
            return mUpcomingItem
        }

    val items: List<MediaQueueItem>
        get() = mQueue

    private val remoteMediaClient: RemoteMediaClient?
        get() {
            val castSession = CastContext.getSharedInstance(
                mAppContext).sessionManager.currentCastSession
            if (castSession == null || !castSession.isConnected) {
                Log.w(TAG, "Trying to get a RemoteMediaClient when no CastSession is started.")
                return null
            }
            return castSession.remoteMediaClient
        }

    init {
        mAppContext = context.applicationContext
        repeatMode = MediaStatus.REPEAT_MODE_REPEAT_OFF
        isShuffleOn = false
        currentItem = null
        CastContext.getSharedInstance(mAppContext)
            .sessionManager
            .addSessionManagerListener(mSessionManagerListener, CastSession::class.java)
        syncWithRemoteQueue()
    }

    fun onUpcomingStopClicked(view: View, upcomingItem: MediaQueueItem) {
        val remoteMediaClient = remoteMediaClient ?: return
        // need to truncate the queue on the remote device so that we can complete the playback of
        // the current item but not go any further. Alternatively, one could just stop the playback
        // here, if that was acceptable.
        val position = getPositionByItemId(upcomingItem.itemId)
        val itemIds = IntArray(count - position)
        for (i in itemIds.indices) {
            itemIds[i] = mQueue[i + position].itemId
        }
        remoteMediaClient.queueRemoveItems(itemIds, null)
    }

    fun onUpcomingPlayClicked(view: View, upcomingItem: MediaQueueItem) {
        val remoteMediaClient = remoteMediaClient ?: return
        remoteMediaClient.queueJumpToItem(upcomingItem.itemId, null)
    }

    fun getPositionByItemId(itemId: Int): Int {
        if (mQueue.isEmpty()) {
            return INVALID
        }
        for (i in mQueue.indices) {
            if (mQueue[i].itemId == itemId) {
                return i
            }
        }
        return INVALID
    }

    fun removeFromQueue(position: Int) {
        synchronized(mLock) {
            val remoteMediaClient = remoteMediaClient ?: return
            remoteMediaClient.queueRemoveItem(mQueue[position].itemId, null)
        }
    }

    fun removeAll() {
        synchronized(mLock) {
            if (mQueue.isEmpty()) {
                return
            }
            val remoteMediaClient = remoteMediaClient ?: return
            val itemIds = IntArray(mQueue.size)
            for (i in mQueue.indices) {
                itemIds[i] = mQueue[i].itemId
            }
            remoteMediaClient.queueRemoveItems(itemIds, null)
            mQueue.clear()
        }
    }

    fun moveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition == toPosition) {
            return
        }
        val remoteMediaClient = remoteMediaClient ?: return
        val itemId = mQueue[fromPosition].itemId

        remoteMediaClient.queueMoveItemToNewIndex(itemId, toPosition, null)
        val item = mQueue.removeAt(fromPosition)
        mQueue.add(toPosition, item)
    }

    fun getItem(position: Int): MediaQueueItem {
        return mQueue[position]
    }

    fun clearQueue() {
        mQueue.clear()
        isQueueDetached = true
        currentItem = null
    }

    fun setOnQueueDataChangedListener(listener: OnQueueDataChangedListener) {
        mListener = listener
    }

    /**
     * Listener notifies the data of the queue has changed.
     */
    interface OnQueueDataChangedListener {

        fun onQueueDataChanged()
    }

    private fun syncWithRemoteQueue() {
        val remoteMediaClient = remoteMediaClient
        if (remoteMediaClient != null) {
            remoteMediaClient.registerCallback(mRemoteMediaClientCallback)
            val mediaStatus = remoteMediaClient.mediaStatus
            if (mediaStatus != null) {
                val items = mediaStatus.queueItems
                if (items != null && !items.isEmpty()) {
                    mQueue.clear()
                    mQueue.addAll(items)
                    repeatMode = mediaStatus.queueRepeatMode
                    currentItem = mediaStatus.getQueueItemById(mediaStatus.currentItemId)
                    isQueueDetached = false
                    mUpcomingItem = mediaStatus.getQueueItemById(mediaStatus.preloadedItemId)
                }
            }
        }
    }

    private inner class MyRemoteMediaClientCallback : RemoteMediaClient.Callback() {

        override fun onPreloadStatusUpdated() {
            val remoteMediaClient = remoteMediaClient ?: return
            val mediaStatus = remoteMediaClient.mediaStatus ?: return
            mUpcomingItem = mediaStatus.getQueueItemById(mediaStatus.preloadedItemId)
            Log.d(TAG, "onRemoteMediaPreloadStatusUpdated() with item= $mUpcomingItem")
            if (mListener != null) {
                mListener!!.onQueueDataChanged()
            }
        }

        override fun onQueueStatusUpdated() {
            updateMediaQueue()
            if (mListener != null) {
                mListener!!.onQueueDataChanged()
            }
            Log.d(TAG, "Queue was updated")
        }

        override fun onStatusUpdated() {
            updateMediaQueue()
            if (mListener != null) {
                mListener!!.onQueueDataChanged()
            }
        }

        private fun updateMediaQueue() {
            val remoteMediaClient = remoteMediaClient
            val mediaStatus: MediaStatus?
            var queueItems: List<MediaQueueItem>? = null
            if (remoteMediaClient != null) {
                mediaStatus = remoteMediaClient.mediaStatus
                if (mediaStatus != null) {
                    queueItems = mediaStatus.queueItems
                    repeatMode = mediaStatus.queueRepeatMode
                    currentItem = mediaStatus.getQueueItemById(mediaStatus.currentItemId)
                }
            }
            mQueue.clear()
            if (queueItems == null) {
                Log.d(TAG, "Queue is cleared")
            } else {
                Log.d(TAG, "Queue is updated with a list of size: " + queueItems.size)
                isQueueDetached = if (queueItems.isNotEmpty()) {
                    mQueue.addAll(queueItems)
                    false
                } else {
                    true
                }
            }
        }
    }

    companion object {

        private const val TAG = "QueueDataProvider"
        const val INVALID = -1
        private var mInstance: QueueDataProvider? = null

        @Synchronized
        fun getInstance(context: Context): QueueDataProvider {
            if (mInstance == null) {
                mInstance = QueueDataProvider(context)
            }
            return mInstance!!
        }
    }
}
