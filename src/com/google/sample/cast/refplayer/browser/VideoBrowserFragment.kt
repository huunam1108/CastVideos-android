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

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityOptionsCompat
import androidx.core.util.Pair
import androidx.fragment.app.Fragment
import androidx.loader.app.LoaderManager
import androidx.loader.content.Loader
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.CastState
import com.google.sample.cast.refplayer.MySessionManagerListener
import com.google.sample.cast.refplayer.R
import com.google.sample.cast.refplayer.mediaplayer.LocalPlayerActivity
import com.google.sample.cast.refplayer.utils.Utils
import kotlinx.android.synthetic.main.video_browser_fragment.*

/**
 * A fragment to host a list view of the video catalog.
 */
class VideoBrowserFragment : Fragment(), VideoListAdapter.ItemClickListener, LoaderManager.LoaderCallbacks<List<MediaInfo>> {
    private var videoListAdapter: VideoListAdapter? = null
    private val sessionManagerListener = object : MySessionManagerListener() {
        override fun onSessionEnded(session: CastSession, error: Int) {
            super.onSessionEnded(session, error)
            videoListAdapter?.notifyDataSetChanged()
        }

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            super.onSessionResumed(session, wasSuspended)
            videoListAdapter?.notifyDataSetChanged()
        }

        override fun onSessionStarted(session: CastSession, sessionId: String) {
            super.onSessionStarted(session, sessionId)
            videoListAdapter?.notifyDataSetChanged()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.video_browser_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val layoutManager = LinearLayoutManager(context)
        layoutManager.orientation = RecyclerView.VERTICAL
        list.layoutManager = layoutManager
        videoListAdapter = VideoListAdapter(this, requireContext())
        list.adapter = videoListAdapter
        loaderManager.initLoader(0, null, this)
    }

    override fun itemClicked(view: View, item: MediaInfo, position: Int) {
        if (view is ImageButton) {
            Utils.showQueuePopup(requireContext(), view, item)
        } else {
            val castContext = CastContext.getSharedInstance(requireContext())
            if (castContext.castState == CastState.NO_DEVICES_AVAILABLE
                || castContext.castState == CastState.NOT_CONNECTED) {

                val transitionName = getString(R.string.transition_image)
                val viewHolder = list.findViewHolderForLayoutPosition(
                    position) as VideoListAdapter.ViewHolder?
                val imagePair = Pair.create(viewHolder!!.imageView as View, transitionName)
                val options = ActivityOptionsCompat
                    .makeSceneTransitionAnimation(requireActivity(), imagePair)

                val intent = Intent(context, LocalPlayerActivity::class.java)
                intent.putExtra("media", item)
                intent.putExtra("shouldStart", false)
                ActivityCompat.startActivity(requireActivity(), intent, options.toBundle())
            } else {
                AddToQueueDialogFragment.newInstance(item).show(fragmentManager,
                    "AddToQueueDialogFragment")
            }
        }
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<List<MediaInfo>> {
        return VideoItemLoader(requireContext(), CATALOG_URL)
    }

    override fun onLoadFinished(loader: Loader<List<MediaInfo>>, data: List<MediaInfo>?) {
        videoListAdapter?.setData(data)
        progress_indicator.visibility = View.GONE
        empty_view.visibility = if (null == data || data.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onLoaderReset(loader: Loader<List<MediaInfo>>) {
        videoListAdapter!!.setData(null)
    }

    override fun onStart() {
        CastContext.getSharedInstance(requireContext()).sessionManager
            .addSessionManagerListener(sessionManagerListener, CastSession::class.java)
        super.onStart()
    }

    override fun onStop() {
        CastContext.getSharedInstance(requireContext()).sessionManager
            .removeSessionManagerListener(sessionManagerListener, CastSession::class.java)
        super.onStop()
    }

    companion object {
        private const val CATALOG_URL = "https://commondatastorage.googleapis.com/gtv-videos-bucket/CastVideos/f.json"
    }
}
