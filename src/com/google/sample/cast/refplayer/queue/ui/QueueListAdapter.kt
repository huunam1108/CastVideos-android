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

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.IntDef
import androidx.core.view.MotionEventCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.toolbox.ImageLoader
import com.android.volley.toolbox.NetworkImageView
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.sample.cast.refplayer.R
import com.google.sample.cast.refplayer.queue.QueueDataProvider
import com.google.sample.cast.refplayer.utils.CustomVolleyRequest

/**
 * An adapter to show the list of queue items.
 */
class QueueListAdapter(context: Context,
    private val mDragStartListener: OnStartDragListener) : RecyclerView.Adapter<QueueListAdapter.QueueItemViewHolder>(), QueueItemTouchHelperCallback.ItemTouchHelperAdapter {
    private val mProvider: QueueDataProvider = QueueDataProvider.getInstance(context)
    private val mAppContext: Context = context.applicationContext
    private val mItemViewOnClickListener: View.OnClickListener
    private var mEventListener: EventListener? = null
    private var mImageLoader: ImageLoader? = null

    init {
        mProvider.setOnQueueDataChangedListener (object : QueueDataProvider.OnQueueDataChangedListener {
            override fun onQueueDataChanged() {
                notifyDataSetChanged()
            }
        })
        mItemViewOnClickListener = View.OnClickListener { view ->
            if (view.getTag(R.string.queue_tag_item) != null) {
                val item = view.getTag(R.string.queue_tag_item) as MediaQueueItem
                Log.d(TAG, item.itemId.toString())
            }
            onItemViewClick(view)
        }
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return mProvider.getItem(position).itemId.toLong()
    }

    private fun onItemViewClick(view: View) {
        if (mEventListener != null) {
            mEventListener!!.onItemViewClicked(view)
        }
    }

    override fun onItemDismiss(position: Int) {
        mProvider.removeFromQueue(position)
    }

    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        if (fromPosition == toPosition) {
            return false
        }
        mProvider.moveItem(fromPosition, toPosition)
        notifyItemMoved(fromPosition, toPosition)
        return true
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QueueItemViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view = inflater.inflate(R.layout.queue_row, parent, false)
        return QueueItemViewHolder(view)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: QueueItemViewHolder, position: Int) {
        Log.d(TAG, "[upcoming] onBindViewHolder() for position: $position")
        val item = mProvider.getItem(position)
        holder.mContainer.setTag(R.string.queue_tag_item, item)
        holder.mPlayPause.setTag(R.string.queue_tag_item, item)
        holder.mPlayUpcoming.setTag(R.string.queue_tag_item, item)
        holder.mStopUpcoming.setTag(R.string.queue_tag_item, item)

        // Set listeners
        holder.mContainer.setOnClickListener(mItemViewOnClickListener)
        holder.mPlayPause.setOnClickListener(mItemViewOnClickListener)
        holder.mPlayUpcoming.setOnClickListener(mItemViewOnClickListener)
        holder.mStopUpcoming.setOnClickListener(mItemViewOnClickListener)

        val info = item.media
        val metaData = info.metadata
        holder.mTitleView.text = metaData.getString(MediaMetadata.KEY_TITLE)
        holder.mDescriptionView.text = metaData.getString(MediaMetadata.KEY_SUBTITLE)
        if (!metaData.images.isEmpty()) {

            val url = metaData.images[0].url.toString()
            mImageLoader = CustomVolleyRequest.getInstance(mAppContext)
                .imageLoader
            mImageLoader!!.get(url, ImageLoader.getImageListener(holder.mImageView, 0, 0))
            holder.mImageView.setImageUrl(url, mImageLoader)

        }

        holder.mDragHandle.setOnTouchListener { view, event ->
            if (MotionEventCompat.getActionMasked(event) == MotionEvent.ACTION_DOWN) {
                mDragStartListener.onStartDrag(holder)
            }
            false
        }

        if (item === mProvider.currentItem) {
            holder.updateControlsStatus(QueueItemViewHolder.CURRENT)
            updatePlayPauseButtonImageResource(holder.mPlayPause)
        } else if (item === mProvider.upcomingItem) {
            holder.updateControlsStatus(QueueItemViewHolder.UPCOMING)
        } else {
            holder.updateControlsStatus(QueueItemViewHolder.NONE)
            holder.mPlayPause.visibility = View.GONE
        }

    }

    private fun updatePlayPauseButtonImageResource(button: ImageButton) {
        val castSession = CastContext.getSharedInstance(mAppContext)
            .sessionManager.currentCastSession
        val remoteMediaClient = castSession?.remoteMediaClient
        if (remoteMediaClient == null) {
            button.visibility = View.GONE
            return
        }
        val status = remoteMediaClient.playerState
        when (status) {
            MediaStatus.PLAYER_STATE_PLAYING -> button.setImageResource(PAUSE_RESOURCE)
            MediaStatus.PLAYER_STATE_PAUSED -> button.setImageResource(PLAY_RESOURCE)
            else -> button.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int {
        return QueueDataProvider.getInstance(mAppContext).count
    }

    /* package */ class QueueItemViewHolder(itemView: View) : RecyclerView.ViewHolder(
        itemView), ItemTouchHelperViewHolder {

        private val mContext: Context = itemView.context
        val mPlayPause: ImageButton
        val mControls: View
        val mUpcomingControls: View
        val mPlayUpcoming: ImageButton
        val mStopUpcoming: ImageButton
        var mImageView: NetworkImageView
        var mContainer: ViewGroup
        var mDragHandle: ImageView
        var mTitleView: TextView
        var mDescriptionView: TextView

        override fun onItemSelected() {
            // no-op
        }

        override fun onItemClear() {
            itemView.setBackgroundColor(0)
        }

        @Retention(AnnotationRetention.SOURCE)
        @IntDef(CURRENT, UPCOMING, NONE)
        private annotation class ControlStatus

        init {
            mContainer = itemView.findViewById(R.id.container)
            mDragHandle = itemView.findViewById(R.id.drag_handle)
            mTitleView = itemView.findViewById(R.id.textView1)
            mDescriptionView = itemView.findViewById(R.id.textView2)
            mImageView = itemView.findViewById(R.id.imageView1)
            mPlayPause = itemView.findViewById(R.id.play_pause)
            mControls = itemView.findViewById(R.id.controls)
            mUpcomingControls = itemView.findViewById(R.id.controls_upcoming)
            mPlayUpcoming = itemView.findViewById(R.id.play_upcoming)
            mStopUpcoming = itemView.findViewById(R.id.stop_upcoming)
        }

        fun updateControlsStatus(@ControlStatus status: Int) {
            var bgResId = R.drawable.bg_item_normal_state
            mTitleView.setTextAppearance(mContext, R.style.Base_TextAppearance_AppCompat_Subhead)
            mDescriptionView.setTextAppearance(mContext,
                R.style.Base_TextAppearance_AppCompat_Caption)
            when (status) {
                CURRENT -> {
                    bgResId = R.drawable.bg_item_normal_state
                    mControls.visibility = View.VISIBLE
                    mPlayPause.visibility = View.VISIBLE
                    mUpcomingControls.visibility = View.GONE
                    mDragHandle.setImageResource(DRAG_HANDLER_DARK_RESOURCE)
                }
                UPCOMING -> {
                    mControls.visibility = View.VISIBLE
                    mPlayPause.visibility = View.GONE
                    mUpcomingControls.visibility = View.VISIBLE
                    mDragHandle.setImageResource(DRAG_HANDLER_LIGHT_RESOURCE)
                    bgResId = R.drawable.bg_item_upcoming_state
                    mTitleView.setTextAppearance(mContext,
                        R.style.TextAppearance_AppCompat_Small_Inverse)
                    mTitleView.setTextAppearance(mTitleView.context,
                        R.style.Base_TextAppearance_AppCompat_Subhead_Inverse)
                    mDescriptionView.setTextAppearance(mContext,
                        R.style.Base_TextAppearance_AppCompat_Caption)
                }
                else -> {
                    mControls.visibility = View.GONE
                    mPlayPause.visibility = View.GONE
                    mUpcomingControls.visibility = View.GONE
                    mDragHandle.setImageResource(DRAG_HANDLER_DARK_RESOURCE)
                }
            }
            mContainer.setBackgroundResource(bgResId)
        }

        companion object {

            const val CURRENT = 0
            const val UPCOMING = 1
            const val NONE = 2
        }
    }

    fun setEventListener(eventListener: EventListener) {
        mEventListener = eventListener
    }

    /**
     * Interface for catching clicks on the ViewHolder items
     */
    interface EventListener {

        fun onItemViewClicked(view: View)
    }

    /**
     * Interface to notify an item ViewHolder of relevant callbacks from [ ].
     */
    interface ItemTouchHelperViewHolder {

        /**
         * Called when the [ItemTouchHelper] first registers an item as being moved or
         * swiped.
         * Implementations should update the item view to indicate it's active state.
         */
        fun onItemSelected()


        /**
         * Called when the [ItemTouchHelper] has completed the move or swipe, and the active
         * item state should be cleared.
         */
        fun onItemClear()
    }

    /**
     * Listener for manual initiation of a drag.
     */
    interface OnStartDragListener {

        /**
         * Called when a view is requesting a start of a drag.
         */
        fun onStartDrag(viewHolder: RecyclerView.ViewHolder)

    }

    companion object {

        private val TAG = "QueueListAdapter"
        private val IMAGE_THUMBNAIL_WIDTH = 64
        private val PLAY_RESOURCE = R.drawable.ic_play_arrow_grey600_48dp
        private val PAUSE_RESOURCE = R.drawable.ic_pause_grey600_48dp
        private val DRAG_HANDLER_DARK_RESOURCE = R.drawable.ic_drag_updown_grey_24dp
        private val DRAG_HANDLER_LIGHT_RESOURCE = R.drawable.ic_drag_updown_white_24dp
        private val ASPECT_RATIO = 1f
    }

}
