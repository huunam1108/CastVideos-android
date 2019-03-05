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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.sample.cast.refplayer.R
import com.google.sample.cast.refplayer.expandedcontrols.CustomExpandedControlsActivity
import com.google.sample.cast.refplayer.queue.QueueDataProvider
import com.google.sample.cast.refplayer.utils.Utils

/**
 * A fragment to show the list of queue items.
 */
class QueueListViewFragment : Fragment(), QueueListAdapter.OnStartDragListener {
    private lateinit var queueDataProvider: QueueDataProvider
    private var itemTouchHelper: ItemTouchHelper? = null
    private val remoteMediaClient: RemoteMediaClient?
        get() {
            val castSession = CastContext.getSharedInstance(context!!).sessionManager
                .currentCastSession
            return if (castSession != null && castSession.isConnected) {
                castSession.remoteMediaClient
            } else null
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_recycler_list_view, container, false)
    }

    override fun onStartDrag(viewHolder: RecyclerView.ViewHolder) {
        itemTouchHelper!!.startDrag(viewHolder)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val recyclerView = getView()!!.findViewById<View>(R.id.recycler_view) as RecyclerView
        queueDataProvider = QueueDataProvider.getInstance(requireContext())

        val adapter = QueueListAdapter(activity!!, this)
        recyclerView.setHasFixedSize(true)
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(activity)

        val callback = QueueItemTouchHelperCallback(adapter)
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper?.attachToRecyclerView(recyclerView)

        adapter.setEventListener(object : QueueListAdapter.EventListener {
            override fun onItemViewClicked(view: View) {
                when (view.id) {
                    R.id.container -> {
                        Log.d(TAG,
                            "onItemViewClicked() container " + view.getTag(R.string.queue_tag_item))
                        onContainerClicked(view)
                    }
                    R.id.play_pause -> {
                        Log.d(TAG,
                            "onItemViewClicked() play-pause " + view.getTag(
                                R.string.queue_tag_item))
                        onPlayPauseClicked(view)
                    }
                    R.id.play_upcoming -> queueDataProvider.onUpcomingPlayClicked(view,
                        view.getTag(R.string.queue_tag_item) as MediaQueueItem)
                    R.id.stop_upcoming -> queueDataProvider.onUpcomingStopClicked(view,
                        view.getTag(R.string.queue_tag_item) as MediaQueueItem)
                }
            }
        })
    }

    private fun onPlayPauseClicked(view: View) {
        val remoteMediaClient = remoteMediaClient
        remoteMediaClient?.togglePlayback()
    }

    private fun onContainerClicked(view: View) {
        val remoteMediaClient = remoteMediaClient ?: return
        val item = view.getTag(R.string.queue_tag_item) as MediaQueueItem
        if (queueDataProvider.isQueueDetached) {
            Log.d(TAG, "Is detached: itemId = " + item.itemId)

            val currentPosition = queueDataProvider.getPositionByItemId(item.itemId)
            val items = Utils.rebuildQueue(queueDataProvider.items)
            remoteMediaClient.queueLoad(items, currentPosition,
                MediaStatus.REPEAT_MODE_REPEAT_OFF, null)
        } else {
            val currentItemId = queueDataProvider.currentItemId
            if (currentItemId == item.itemId) {
                // We selected the one that is currently playing so we take the user to the
                // full screen controller
                val castSession = CastContext.getSharedInstance(requireContext())
                    .sessionManager.currentCastSession
                if (castSession != null) {
                    val intent = Intent(activity, CustomExpandedControlsActivity::class.java)
                    startActivity(intent)
                }
            } else {
                // a different item in the queue was selected so we jump there
                remoteMediaClient.queueJumpToItem(item.itemId, null)
            }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        retainInstance = true
    }

    companion object {

        private const val TAG = "QueueListViewFragment"
    }
}
