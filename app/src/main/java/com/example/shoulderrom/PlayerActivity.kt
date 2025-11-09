package com.example.shoulderrom

import android.net.Uri
import android.os.Bundle
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.ComponentActivity

class PlayerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        val videoView = findViewById<VideoView>(R.id.videoView)
        val uriStr = intent.getStringExtra("uri")
        if (uriStr != null) {
            val uri = Uri.parse(uriStr)
            videoView.setVideoURI(uri)
            val controller = MediaController(this)
            controller.setAnchorView(videoView)
            videoView.setMediaController(controller)
            videoView.setOnPreparedListener { mp ->
                mp.isLooping = false
                videoView.start()
            }
        }
    }
}

