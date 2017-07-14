package me.fanjie.testopencv3

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.SurfaceHolder
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.FlowableEmitter
import io.reactivex.functions.BiFunction
import kotlinx.android.synthetic.main.activity_main.*
import org.bytedeco.javacpp.opencv_core
import org.bytedeco.javacv.AndroidFrameConverter
import org.bytedeco.javacv.Frame
import org.bytedeco.javacv.OpenCVFrameConverter
import org.reactivestreams.Subscription
import java.util.*

class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"

    private val converterToBitmap = AndroidFrameConverter()
    private val converterToMat = OpenCVFrameConverter.ToMat()

    private lateinit var emitter1: FlowableEmitter<Frame>
    private lateinit var emitter2: FlowableEmitter<Frame>

    private lateinit var cvCameraFrame1: CvCameraFrame
    private lateinit var cvCameraFrame2: CvCameraFrame

    private var permissionReady: Boolean = false
    private var subscription: Subscription? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        requirePermissions()
        cvCameraFrame1 = CvCameraFrame(CvCameraFrame.CAMERA_BACK, cvCameraPreview.holder)
        cvCameraFrame2 = CvCameraFrame(CvCameraFrame.CAMERA_FRONT, cvCameraPreview.holder)
        initEmitter()
        addCameraListener()
    }

    private fun initEmitter() {
        val observable1 = Flowable.create<Frame>({emitter1 = it}, BackpressureStrategy.LATEST)
        val observable2 = Flowable.create<Frame>({emitter2 = it}, BackpressureStrategy.LATEST)
        Flowable.zip(observable1, observable2, BiFunction<Frame, Frame, Frame> { frame1, frame2 ->
            val mat1 = converterToMat.convert(frame1)
            val mat2 = converterToMat.convert(frame2)
            val mat3 = opencv_core.Mat()
            opencv_core.hconcat(mat1, mat2, mat3)
            Log.d(TAG,mat3.toString())
            val frame = converterToMat.convert(mat3)
            mat1.release()
            mat2.release()
            mat3.release()
            frame
        }).subscribe({
            drawFrame(it)
        },{},{},{
            it.request(Long.MAX_VALUE)
            subscription = it
        })

    }

    private fun addCameraListener() {
        cvCameraFrame1.cameraCallback = CameraCallback { emitter1.onNext(it) }
        cvCameraFrame2.cameraCallback = CameraCallback { emitter2.onNext(it) }

        cvCameraPreview.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {

            }

            override fun surfaceDestroyed(holder: SurfaceHolder?) {
                cvCameraFrame1.stop()
                cvCameraFrame2.stop()
            }

            override fun surfaceCreated(holder: SurfaceHolder?) {
                cvCameraFrame1.initCamera()
                cvCameraFrame2.initCamera()
            }
        })
    }


    private fun drawFrame(frame: Frame) {
        val cacheBitmap = converterToBitmap.convert(frame)
        val width: Int
        val height: Int
        val canvas = cvCameraPreview.holder.lockCanvas()
        width = canvas.width
        height = cacheBitmap.height * canvas.width / cacheBitmap.width
        canvas.drawColor(0, android.graphics.PorterDuff.Mode.CLEAR)
        canvas.drawBitmap(cacheBitmap,
                Rect(0, 0, cacheBitmap.width, cacheBitmap.height),
                Rect(0, (canvas!!.height - height) / 2, width, (canvas.height - height) / 2 + height),
                null)
        cvCameraPreview.holder.unlockCanvasAndPost(canvas)
    }

    private fun requirePermissions() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE), 11)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        val perm = HashMap<String, Int>()
        perm.put(Manifest.permission.CAMERA, PackageManager.PERMISSION_DENIED)
        perm.put(Manifest.permission.WRITE_EXTERNAL_STORAGE, PackageManager.PERMISSION_DENIED)
        for (i in permissions.indices) {
            perm.put(permissions[i], grantResults[i])
        }
        if (perm[Manifest.permission.CAMERA] === PackageManager.PERMISSION_GRANTED && perm[Manifest.permission.WRITE_EXTERNAL_STORAGE] === PackageManager.PERMISSION_GRANTED) {
            permissionReady = true
        } else {
            if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA) || !ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                AlertDialog.Builder(this)
                        .setMessage("权限，兄弟")
                        .setPositiveButton("取消", null)
                        .show()
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
}
