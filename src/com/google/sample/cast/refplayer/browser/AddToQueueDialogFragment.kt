package com.google.sample.cast.refplayer.browser

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastState
import com.google.android.gms.cast.framework.CastStateListener
import com.google.sample.cast.refplayer.R
import com.google.sample.cast.refplayer.queue.QueueDataProvider
import com.google.sample.cast.refplayer.utils.Utils
import kotlinx.android.synthetic.main.fragment_dialog_add_to_queue.*

class AddToQueueDialogFragment : DialogFragment() {
    private var selectedMedia: MediaInfo? = null
    private val castStateListener = CastStateListener { state -> doOnCastStateChanged(state) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_dialog_add_to_queue, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        arguments?.apply {
            selectedMedia = getParcelable("SELECTED_VIDEO")
        }

        val castContext = CastContext.getSharedInstance(requireContext())
        castContext?.apply {
            doOnCastStateChanged(castState)
            addCastStateListener(castStateListener)
        }

        btn_play_now.setOnClickListener {
            doOnButtonClicked(it.id)
            dismissAllowingStateLoss()
        }
        btn_add_to_queue.setOnClickListener {
            doOnButtonClicked(it.id)
            dismissAllowingStateLoss()
        }
    }

    override fun onPause() {
        super.onPause()
        val castContext = CastContext.getSharedInstance(requireContext())
        castContext?.apply {
            removeCastStateListener(castStateListener)
        }
    }

    private fun doOnCastStateChanged(state: Int) {
        pg_cast_connecting?.visibility = if (state == CastState.CONNECTING) View.VISIBLE else View.GONE

        if (state == CastState.CONNECTED) {
            btn_play_now?.visibility = View.VISIBLE
            btn_add_to_queue?.visibility = View.VISIBLE
        }
    }

    private fun doOnButtonClicked(id: Int) {
        val castSession = CastContext.getSharedInstance(requireContext()).sessionManager.currentCastSession
        if (castSession == null || !castSession.isConnected) {
            Log.w(TAG, "showQueuePopup(): not connected to a cast device")
            return
        }
        val remoteMediaClient = castSession.remoteMediaClient
        if (remoteMediaClient == null) {
            Log.w(TAG, "showQueuePopup(): null RemoteMediaClient")
            return
        }

        val queueProvider = QueueDataProvider.getInstance(requireContext())
        val queueItem = MediaQueueItem.Builder(selectedMedia).setAutoplay(true)
            .setPreloadTime(20.toDouble()).build()
        val newItemArray = arrayOf(queueItem)
        if (queueProvider.isQueueDetached && queueProvider.count > 0) {
            val items = Utils.rebuildQueueAndAppend(queueProvider.items, queueItem)
            remoteMediaClient.queueLoad(items, queueProvider.count,
                MediaStatus.REPEAT_MODE_REPEAT_OFF, null)
        } else {
            if (queueProvider.count == 0) {
                remoteMediaClient.queueLoad(newItemArray, 0, MediaStatus.REPEAT_MODE_REPEAT_OFF,
                    null)
            } else {
                val currentId = queueProvider.currentItemId
                when (id) {
                    R.id.btn_play_now -> {
                        remoteMediaClient.queueInsertAndPlayItem(queueItem, currentId, null)
                        Toast.makeText(context, "Add to Queue & Playing item",
                            Toast.LENGTH_SHORT).show()
                    }
                    R.id.btn_add_to_queue -> {
                        remoteMediaClient.queueAppendItem(queueItem, null)
                        Toast.makeText(context, getString(R.string.queue_item_added_to_queue),
                            Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    companion object {
        const val TAG = "AddToQueueDialog"
        fun newInstance(selectedVideo: MediaInfo) = AddToQueueDialogFragment().apply {
            arguments = Bundle().apply {
                putParcelable("SELECTED_VIDEO", selectedVideo)
            }
        }
    }
}
