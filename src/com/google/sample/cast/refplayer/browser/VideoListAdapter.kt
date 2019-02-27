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

package com.google.sample.cast.refplayer.browser

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.toolbox.ImageLoader
import com.android.volley.toolbox.NetworkImageView
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.sample.cast.refplayer.R
import com.google.sample.cast.refplayer.utils.CustomVolleyRequest

/**
 * An [ArrayAdapter] to populate the list of videos.
 */
class VideoListAdapter(private val itemClickListener: ItemClickListener,
    context: Context) : RecyclerView.Adapter<VideoListAdapter.ViewHolder>() {
    private val mAppContext: Context = context.applicationContext
    private var videos: List<MediaInfo>? = null

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        val context = viewGroup.context
        val parent = LayoutInflater.from(context).inflate(R.layout.browse_row, viewGroup, false)
        return ViewHolder.newInstance(parent)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val item = videos!![position]
        val mm = item.metadata
        viewHolder.setTitle(mm.getString(MediaMetadata.KEY_TITLE))
        viewHolder.setDescription(mm.getString(MediaMetadata.KEY_SUBTITLE))
        viewHolder.setImage(mm.images[0].url.toString(), mAppContext)

        val castSession = CastContext.getSharedInstance(mAppContext).sessionManager
            .currentCastSession
        viewHolder.mMenu.apply {
            visibility = if (castSession != null && castSession.isConnected) View.VISIBLE else View.GONE
            setOnClickListener { view ->
                itemClickListener.itemClicked(view, item, position)
            }
        }
        viewHolder.mParent.setOnClickListener { view ->
            itemClickListener.itemClicked(view, item, position)
        }
    }

    override fun getItemCount(): Int {
        return if (videos == null) 0 else videos!!.size
    }

    /**
     * A [RecyclerView.ViewHolder] that displays a single video in
     * the video list.
     */
    class ViewHolder private constructor(val mParent: View,
        val mImgView: NetworkImageView, val mTitleView: TextView, val mDescriptionView: TextView,
        val mMenu: View) : RecyclerView.ViewHolder(mParent) {
        private var mImageLoader: ImageLoader? = null

        val imageView: ImageView
            get() = mImgView

        fun setTitle(title: String) {
            mTitleView.text = title
        }

        fun setDescription(description: String) {
            mDescriptionView.text = description
        }

        fun setImage(imgUrl: String, context: Context) {
            mImageLoader = CustomVolleyRequest.getInstance(context)
                .imageLoader

            mImageLoader!!.get(imgUrl, ImageLoader.getImageListener(mImgView, 0, 0))
            mImgView.setImageUrl(imgUrl, mImageLoader)
        }

        companion object {

            fun newInstance(parent: View): ViewHolder {
                val imgView = parent.findViewById<NetworkImageView>(R.id.imageView1)
                val titleView = parent.findViewById<TextView>(R.id.textView1)
                val descriptionView = parent.findViewById<TextView>(R.id.textView2)
                val menu = parent.findViewById<View>(R.id.menu)
                return ViewHolder(parent, imgView, titleView, descriptionView, menu)
            }
        }
    }

    fun setData(data: List<MediaInfo>?) {
        videos = data
        notifyDataSetChanged()
    }

    /**
     * A listener called when an item is clicked in the video list.
     */
    interface ItemClickListener {
        fun itemClicked(view: View, item: MediaInfo, position: Int)
    }
}
