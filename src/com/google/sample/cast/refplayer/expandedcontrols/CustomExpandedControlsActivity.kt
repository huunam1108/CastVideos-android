package com.google.sample.cast.refplayer.expandedcontrols

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.cast.framework.media.uicontroller.UIMediaController
import com.google.android.gms.cast.framework.media.widget.ExpandedControllerActivity
import com.google.sample.cast.refplayer.R
import com.google.sample.cast.refplayer.queue.QueueDataProvider
import com.google.sample.cast.refplayer.queue.ui.QueueItemTouchHelperCallback
import com.google.sample.cast.refplayer.queue.ui.QueueListAdapter
import com.google.sample.cast.refplayer.utils.Utils
import kotlinx.android.synthetic.main.activity_expanded_controls.*

class CustomExpandedControlsActivity : ExpandedControllerActivity(), QueueListAdapter.OnStartDragListener {

    private lateinit var queueListAdapter: QueueListAdapter
    private lateinit var queueDataProvider: QueueDataProvider
    private lateinit var itemTouchHelper: ItemTouchHelper
    private val remoteMediaClient: RemoteMediaClient?
        get() {
            val castSession = CastContext.getSharedInstance(this).sessionManager
                .currentCastSession
            return if (castSession != null && castSession.isConnected) {
                castSession.remoteMediaClient
            } else null
        }

    private val uiMediaController: UIMediaController = UIMediaController(this)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_expanded_controls)
        initCast()
        initViews()
    }

    private fun initCast() {
        queueDataProvider = QueueDataProvider.getInstance(this)
    }

    private fun initViews() {
        rc_casting_list.apply {
            layoutManager = LinearLayoutManager(this@CustomExpandedControlsActivity)
            queueListAdapter = QueueListAdapter(this@CustomExpandedControlsActivity,
                this@CustomExpandedControlsActivity)
            adapter = queueListAdapter
            itemTouchHelper = ItemTouchHelper(QueueItemTouchHelperCallback(queueListAdapter))
            itemTouchHelper.attachToRecyclerView(this)

            queueListAdapter.setEventListener(object : QueueListAdapter.EventListener {
                override fun onItemViewClicked(view: View) {
                    when (view.id) {
                        R.id.container -> {
                            Log.d(TAG, "onItemViewClicked() container " + view.getTag(
                                R.string.queue_tag_item))
                            onContainerClicked(view)
                        }
                        R.id.play_pause -> {
                            Log.d(TAG, "onItemViewClicked() play-pause " + view.getTag(
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

        tv_title.text = queueDataProvider.currentItem?.media?.metadata?.getString(
            MediaMetadata.KEY_TITLE)

        setupActionBar()
        bindMediaViewControllers()
    }

    private fun bindMediaViewControllers() {
        val playDrawable = AppCompatResources.getDrawable(this, R.drawable.ic_av_play_dark)
        val pauseDrawable = AppCompatResources.getDrawable(this, R.drawable.ic_av_pause_dark)
        if (playDrawable == null || pauseDrawable == null) return
        uiMediaController.apply {
            bindImageViewToPlayPauseToggle(btn_play_pause, playDrawable, pauseDrawable, null, null,
                true)
            bindSeekBar(seekBar_player)
            bindViewToSkipNext(btn_next, View.INVISIBLE)
            bindViewToSkipPrev(btn_prev, View.INVISIBLE)
            bindTextViewToStreamPosition(startText, true)
            bindTextViewToStreamDuration(endText)
            bindTextViewToSmartSubtitle(tv_subtitle)
        }
    }


    private fun setupActionBar() {
        toolbar.setTitle(R.string.queue_list)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun onPlayPauseClicked(view: View) {
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
                val castSession = CastContext.getSharedInstance(this)
                    .sessionManager.currentCastSession
                if (castSession != null) {
                    val intent = Intent(applicationContext,
                        CustomExpandedControlsActivity::class.java)
                    startActivity(intent)
                }
            } else {
                // a different item in the queue was selected so we jump there
                remoteMediaClient.queueJumpToItem(item.itemId, null)
            }
        }
    }

    override fun onStartDrag(viewHolder: RecyclerView.ViewHolder) {
        itemTouchHelper.startDrag(viewHolder)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.expanded_controller, menu)
        CastButtonFactory.setUpMediaRouteButton(this, menu, R.id.media_route_menu_item)
        return true
    }

    companion object {
        const val TAG = "CustomExpandedActivity"
    }
}
