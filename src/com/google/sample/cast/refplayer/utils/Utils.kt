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

package com.google.sample.cast.refplayer.utils

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Point
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.sample.cast.refplayer.R
import com.google.sample.cast.refplayer.expandedcontrols.ExpandedControlsActivity
import com.google.sample.cast.refplayer.queue.QueueDataProvider
import java.util.*

/**
 * A collection of utility methods, all static.
 */
object Utils {

    private const val TAG = "Utils"
    private const val PRELOAD_TIME_S = 20

    /**
     * Returns the screen/display size
     *
     */
    fun getDisplaySize(context: Context): Point {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display = wm.defaultDisplay
        val width = display.width
        val height = display.height
        return Point(width, height)
    }

    /**
     * Returns `true` if and only if the screen orientation is portrait.
     */
    fun isOrientationPortrait(context: Context): Boolean {
        return context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    }

    /**
     * Shows an error dialog with a given text message.
     */
    fun showErrorDialog(context: Context, errorString: String) {
        AlertDialog.Builder(context).setTitle(R.string.error)
            .setMessage(errorString)
            .setPositiveButton(R.string.ok) { dialog, _ -> dialog.cancel() }
            .create()
            .show()
    }

    /**
     * Shows an "Oops" error dialog with a text provided by a resource ID
     */
    fun showOopsDialog(context: Context, resourceId: Int) {
        AlertDialog.Builder(context).setTitle(R.string.oops)
            .setMessage(context.getString(resourceId))
            .setPositiveButton(R.string.ok) { dialog, _ -> dialog.cancel() }
            .setIcon(R.drawable.ic_action_alerts_and_states_warning)
            .create()
            .show()
    }

    /**
     * Gets the version of app.
     */
    fun getAppVersionName(context: Context): String? {
        var versionString: String? = null
        try {
            val info = context.packageManager.getPackageInfo(context.packageName,
                0 /* basic info */)
            versionString = info.versionName
        } catch (e: Exception) {
            // do nothing
        }

        return versionString
    }

    /**
     * Shows a (long) toast.
     */
    fun showToast(context: Context, resourceId: Int) {
        Toast.makeText(context, context.getString(resourceId), Toast.LENGTH_LONG).show()
    }

    /**
     * Formats time from milliseconds to hh:mm:ss string format.
     */
    fun formatMillis(millisec: Int): String {
        var seconds = millisec / 1000
        val hours = seconds / (60 * 60)
        seconds %= 60 * 60
        val minutes = seconds / 60
        seconds %= 60

        return if (hours > 0) {
            String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
        }
    }

    /**
     * Show a popup to select whether the selected item should play immediately, be added to the
     * end of queue or be added to the queue right after the current item.
     */
    fun showQueuePopup(context: Context, view: View, mediaInfo: MediaInfo) {
        val castSession = CastContext.getSharedInstance(context).sessionManager.currentCastSession
        if (castSession == null || !castSession.isConnected) {
            Log.w(TAG, "showQueuePopup(): not connected to a cast device")
            return
        }
        val remoteMediaClient = castSession.remoteMediaClient
        if (remoteMediaClient == null) {
            Log.w(TAG, "showQueuePopup(): null RemoteMediaClient")
            return
        }
        val provider = QueueDataProvider.getInstance(context)
        val popup = PopupMenu(context, view)
        popup.menuInflater.inflate(
            if (provider.isQueueDetached || provider.count == 0)
                R.menu.detached_popup_add_to_queue
            else
                R.menu.popup_add_to_queue, popup.menu)
        val clickListener = PopupMenu.OnMenuItemClickListener { menuItem ->
            val queueProvider = QueueDataProvider.getInstance(context)
            val queueItem = MediaQueueItem.Builder(mediaInfo).setAutoplay(
                true).setPreloadTime(PRELOAD_TIME_S.toDouble()).build()
            val newItemArray = arrayOf(queueItem)
            var toastMessage: String? = null
            if (queueProvider.isQueueDetached && queueProvider.count > 0) {
                if (menuItem.itemId == R.id.action_play_now || menuItem.itemId == R.id.action_add_to_queue) {
                    val items = Utils.rebuildQueueAndAppend(queueProvider.items, queueItem)
                    remoteMediaClient.queueLoad(items, queueProvider.count,
                        MediaStatus.REPEAT_MODE_REPEAT_OFF, null)
                } else {
                    return@OnMenuItemClickListener false
                }
            } else {
                if (queueProvider.count == 0) {
                    remoteMediaClient.queueLoad(newItemArray, 0,
                        MediaStatus.REPEAT_MODE_REPEAT_OFF, null)
                } else {
                    val currentId = queueProvider.currentItemId
                    when (menuItem.itemId) {
                        R.id.action_play_now -> remoteMediaClient.queueInsertAndPlayItem(
                            queueItem, currentId, null)
                        R.id.action_play_next -> {
                            val currentPosition = queueProvider.getPositionByItemId(currentId)
                            if (currentPosition == queueProvider.count - 1) {
                                //we are adding to the end of queue
                                remoteMediaClient.queueAppendItem(queueItem, null)
                            } else {
                                val nextItemId = queueProvider.getItem(currentPosition + 1).itemId
                                remoteMediaClient.queueInsertItems(newItemArray, nextItemId, null)
                            }
                            toastMessage = context.getString(
                                R.string.queue_item_added_to_play_next)
                        }
                        R.id.action_add_to_queue -> {
                            remoteMediaClient.queueAppendItem(queueItem, null)
                            toastMessage = context.getString(R.string.queue_item_added_to_queue)
                        }
                        else -> return@OnMenuItemClickListener false
                    }
                }
            }
            if (menuItem.itemId == R.id.action_play_now) {
                val intent = Intent(context, ExpandedControlsActivity::class.java)
                context.startActivity(intent)
            }
            if (!TextUtils.isEmpty(toastMessage)) {
                Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
            }
            true
        }
        popup.setOnMenuItemClickListener(clickListener)
        popup.show()
    }

    fun rebuildQueue(items: List<MediaQueueItem>?): Array<MediaQueueItem>? {
        if (items == null || items.isEmpty()) {
            return null
        }
        return items.map {
            rebuildQueueItem(it)
        }.toTypedArray()
    }

    fun rebuildQueueAndAppend(items: List<MediaQueueItem>?,
        currentItem: MediaQueueItem): Array<MediaQueueItem> {
        if (items == null || items.isEmpty()) {
            return arrayOf(currentItem)
        }
        val rebuiltQueue = items.map {
            rebuildQueueItem(it)
        }.toMutableList()

        rebuiltQueue.add(currentItem)

        return rebuiltQueue.toTypedArray()
    }

    private fun rebuildQueueItem(item: MediaQueueItem): MediaQueueItem {
        return MediaQueueItem.Builder(item).clearItemId().build()
    }
}
