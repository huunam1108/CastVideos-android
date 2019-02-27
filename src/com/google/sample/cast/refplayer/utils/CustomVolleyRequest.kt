package com.google.sample.cast.refplayer.utils

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import androidx.collection.LruCache
import com.android.volley.RequestQueue
import com.android.volley.toolbox.BasicNetwork
import com.android.volley.toolbox.DiskBasedCache
import com.android.volley.toolbox.HurlStack
import com.android.volley.toolbox.ImageLoader


class CustomVolleyRequest private constructor(private val context: Context) {
    private var requestQueue: RequestQueue? = null
    val imageLoader: ImageLoader

    init {
        this.requestQueue = getRequestQueue()

        imageLoader = ImageLoader(requestQueue,
            object : ImageLoader.ImageCache {
                private val cache = LruCache<String, Bitmap>(20)

                override fun getBitmap(url: String): Bitmap? {
                    return cache.get(url)
                }

                override fun putBitmap(url: String, bitmap: Bitmap) {
                    cache.put(url, bitmap)
                }
            })
    }

    private fun getRequestQueue(): RequestQueue {
        if (requestQueue == null) {
            val cache = DiskBasedCache(context.cacheDir, 10 * 1024 * 1024)
            val network = BasicNetwork(HurlStack())
            requestQueue = RequestQueue(cache, network)
            requestQueue!!.start()
        }
        return requestQueue!!
    }

    companion object {

        @SuppressLint("StaticFieldLeak")
        private var customVolleyRequest: CustomVolleyRequest? = null

        @Synchronized
        fun getInstance(context: Context): CustomVolleyRequest {
            if (customVolleyRequest == null) {
                customVolleyRequest = CustomVolleyRequest(context)
            }
            return customVolleyRequest!!
        }
    }

}
