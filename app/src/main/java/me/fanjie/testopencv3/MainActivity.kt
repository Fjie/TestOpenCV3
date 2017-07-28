package me.fanjie.testopencv3

import android.Manifest
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.widget.Toast
import com.tbruyelle.rxpermissions2.RxPermissions
import kotlinx.android.synthetic.main.activity_main.*



class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        btn_camera.setOnClickListener { CameraPreviewActivity.start(this) }
        btn_video.setOnClickListener { VideoPreviewActivity.start(this) }
        btn_permissions.setOnClickListener {
            val rxPermissions = RxPermissions(this)
            rxPermissions
                    .request(Manifest.permission.CAMERA,Manifest.permission.READ_EXTERNAL_STORAGE)
                    .subscribe {
                        if (it){
                            Toast.makeText(this,"per ok",Toast.LENGTH_SHORT).show()
                        }
                    }
        }
    }
}
