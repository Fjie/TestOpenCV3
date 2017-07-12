package me.fanjie.testopencv3

import android.content.Context
import android.hardware.Camera
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView

/**
 * Created by fanjie on 2017/7/12.
 */
class CvSurfaveView : SurfaceView {

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    private var cameraDevice: Camera? = null
    private var surfaceHolder: SurfaceHolder? = null


    private fun init() {
        cameraDevice = Camera.open()
        cameraDevice?.setPreviewCallback { _, _ -> cvCameraPreviewCallbackCv }

    }

    private val cvCameraPreviewCallbackCv: (data: ByteArray, camera: Camera) -> Unit = { bytes: ByteArray, camera: Camera ->

    }


    class CvSurfaceHolderCallback : SurfaceHolder.Callback {
        override fun surfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun surfaceDestroyed(holder: SurfaceHolder?) {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun surfaceCreated(holder: SurfaceHolder?) {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }


    }

}