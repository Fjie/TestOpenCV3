package me.fanjie.testopencv3

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        btn_camera.setOnClickListener { CameraPreviewActivity.start(this) }
        btn_video.setOnClickListener { VideoPreviewActivity.start(this) }
    }
}
