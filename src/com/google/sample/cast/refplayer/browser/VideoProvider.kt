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

import android.net.Uri
import android.util.Log
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaTrack
import com.google.android.gms.common.images.WebImage
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.util.*

/**
 * Provider of the list of videos.
 */
class VideoProvider {

    protected fun parseUrl(urlString: String): JSONObject? {
        var inputStream: InputStream? = null
        try {
            val url = java.net.URL(urlString)
            val urlConnection = url.openConnection()
            inputStream = BufferedInputStream(urlConnection.getInputStream())
            val json = inputStream.bufferedReader(Charsets.ISO_8859_1).use {
                it.readText()
            }
            return JSONObject(json)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse the json for media list", e)
            return null
        } finally {
            if (null != inputStream) {
                try {
                    inputStream.close()
                } catch (e: IOException) {
                    // ignore
                }

            }
        }
    }

    companion object {

        private const val TAG = "VideoProvider"
        private const val TAG_VIDEOS = "videos"
        private const val TAG_HLS = "hls"
        private const val TAG_DASH = "dash"
        private const val TAG_MP4 = "mp4"
        private const val TAG_IMAGES = "images"
        private const val TAG_VIDEO_TYPE = "type"
        private const val TAG_VIDEO_URL = "url"
        private const val TAG_VIDEO_MIME = "mime"

        private const val TAG_CATEGORIES = "categories"
        private const val TAG_NAME = "name"
        private const val TAG_STUDIO = "studio"
        private const val TAG_SOURCES = "sources"
        private const val TAG_SUBTITLE = "subtitle"
        private const val TAG_DURATION = "duration"
        private const val TAG_TRACKS = "tracks"
        private const val TAG_TRACK_ID = "id"
        private const val TAG_TRACK_TYPE = "type"
        private const val TAG_TRACK_SUBTYPE = "subtype"
        private const val TAG_TRACK_CONTENT_ID = "contentId"
        private const val TAG_TRACK_NAME = "name"
        private const val TAG_TRACK_LANGUAGE = "language"
        private const val TAG_THUMB = "image-480x270" // "thumb";
        private const val TAG_IMG_780_1200 = "image-780x1200"
        private const val TAG_TITLE = "title"

        const val KEY_DESCRIPTION = "description"

        private const val TARGET_FORMAT = TAG_HLS
        private var mediaList: MutableList<MediaInfo>? = null

        @Throws(JSONException::class)
        fun buildMedia(url: String): List<MediaInfo>? {

            if (null != mediaList) {
                return mediaList
            }
            val urlPrefixMap = HashMap<String, String>()
            mediaList = ArrayList()
            val jsonObj = VideoProvider().parseUrl(url)
            val categories = jsonObj!!.getJSONArray(TAG_CATEGORIES)
            if (null != categories) {
                for (i in 0 until categories.length()) {
                    val category = categories.getJSONObject(i)
                    urlPrefixMap[TAG_HLS] = category.getString(TAG_HLS)
                    urlPrefixMap[TAG_DASH] = category.getString(TAG_DASH)
                    urlPrefixMap[TAG_MP4] = category.getString(TAG_MP4)
                    urlPrefixMap[TAG_IMAGES] = category.getString(TAG_IMAGES)
                    urlPrefixMap[TAG_TRACKS] = category.getString(TAG_TRACKS)
                    category.getString(TAG_NAME)
                    val videos = category.getJSONArray(TAG_VIDEOS)
                    if (null != videos) {
                        for (j in 0 until videos.length()) {
                            var videoUrl: String? = null
                            var mimeType: String? = null
                            val video = videos.getJSONObject(j)
                            val subTitle = video.getString(TAG_SUBTITLE)
                            val videoSpecs = video.getJSONArray(TAG_SOURCES)
                            if (null == videoSpecs || videoSpecs.length() == 0) {
                                continue
                            }
                            for (k in 0 until videoSpecs.length()) {
                                val videoSpec = videoSpecs.getJSONObject(k)
                                if (TARGET_FORMAT == videoSpec.getString(TAG_VIDEO_TYPE)) {
                                    videoUrl = urlPrefixMap[TARGET_FORMAT]!! + videoSpec
                                        .getString(TAG_VIDEO_URL)
                                    mimeType = videoSpec.getString(TAG_VIDEO_MIME)
                                }
                            }
                            if (videoUrl == null) {
                                continue
                            }
                            val imageUrl = urlPrefixMap[TAG_IMAGES]!! + video.getString(TAG_THUMB)
                            val bigImageUrl = urlPrefixMap[TAG_IMAGES]!! + video
                                .getString(TAG_IMG_780_1200)
                            val title = video.getString(TAG_TITLE)
                            val studio = video.getString(TAG_STUDIO)
                            val duration = video.getInt(TAG_DURATION)
                            var tracks: MutableList<MediaTrack>? = null
                            if (video.has(TAG_TRACKS)) {
                                val tracksArray = video.getJSONArray(TAG_TRACKS)
                                if (tracksArray != null) {
                                    tracks = ArrayList()
                                    for (k in 0 until tracksArray.length()) {
                                        val track = tracksArray.getJSONObject(k)
                                        tracks.add(buildTrack(track.getLong(TAG_TRACK_ID),
                                            track.getString(TAG_TRACK_TYPE),
                                            track.getString(TAG_TRACK_SUBTYPE),
                                            urlPrefixMap[TAG_TRACKS]!! + track
                                                .getString(TAG_TRACK_CONTENT_ID),
                                            track.getString(TAG_TRACK_NAME),
                                            track.getString(TAG_TRACK_LANGUAGE)
                                        ))
                                    }
                                }
                            }
                            mediaList!!.add(
                                buildMediaInfo(title, studio, subTitle, duration, videoUrl,
                                    mimeType, imageUrl, bigImageUrl, tracks))
                        }
                    }
                }
            }
            return mediaList
        }

        private fun buildMediaInfo(title: String, studio: String, subTitle: String,
            duration: Int, url: String, mimeType: String?, imgUrl: String, bigImageUrl: String,
            tracks: List<MediaTrack>?): MediaInfo {
            val movieMetadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE)

            movieMetadata.putString(MediaMetadata.KEY_SUBTITLE, studio)
            movieMetadata.putString(MediaMetadata.KEY_TITLE, title)
            movieMetadata.addImage(WebImage(Uri.parse(imgUrl)))
            movieMetadata.addImage(WebImage(Uri.parse(bigImageUrl)))
            var jsonObj: JSONObject? = null
            try {
                jsonObj = JSONObject()
                jsonObj.put(KEY_DESCRIPTION, subTitle)
            } catch (e: JSONException) {
                Log.e(TAG, "Failed to add description to the json object", e)
            }

            return MediaInfo.Builder(url)
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setContentType(mimeType)
                .setMetadata(movieMetadata)
                .setMediaTracks(tracks)
                .setStreamDuration((duration * 1000).toLong())
                .setCustomData(jsonObj)
                .build()
        }

        private fun buildTrack(id: Long, type: String, subType: String?, contentId: String,
            name: String, language: String): MediaTrack {
            var trackType = MediaTrack.TYPE_UNKNOWN
            when (type) {
                "text" -> trackType = MediaTrack.TYPE_TEXT
                "video" -> trackType = MediaTrack.TYPE_VIDEO
                "audio" -> trackType = MediaTrack.TYPE_AUDIO
            }

            var trackSubType = MediaTrack.SUBTYPE_NONE
            if (subType != null) {
                if ("captions" == type) {
                    trackSubType = MediaTrack.SUBTYPE_CAPTIONS
                } else if ("subtitle" == type) {
                    trackSubType = MediaTrack.SUBTYPE_SUBTITLES
                }
            }

            return MediaTrack.Builder(id, trackType)
                .setName(name)
                .setSubtype(trackSubType)
                .setContentId(contentId)
                .setLanguage(language).build()
        }
    }
}
