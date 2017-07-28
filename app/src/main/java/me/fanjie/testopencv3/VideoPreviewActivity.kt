package me.fanjie.testopencv3

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.support.annotation.RequiresApi
import android.support.v7.app.AppCompatActivity
import android.util.Log
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.BiFunction
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_video_preview.*
import org.bytedeco.javacpp.opencv_core
import org.bytedeco.javacv.AndroidFrameConverter
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame
import org.bytedeco.javacv.OpenCVFrameConverter


class VideoPreviewActivity : AppCompatActivity() {

    private val TAG = "VideoPreviewActivity"
    private val REQUEST_TAKE_GALLERY_VIDEO_1 = 10086
    private val REQUEST_TAKE_GALLERY_VIDEO_2 = 10087

    private val converterToBitmap = AndroidFrameConverter()
    private val converterToMat = OpenCVFrameConverter.ToMat()

    private lateinit var uri1: Uri
    private lateinit var uri2: Uri

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, VideoPreviewActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_preview)
        btn_select_video1.setOnClickListener {
            selectVideoByGallery(REQUEST_TAKE_GALLERY_VIDEO_1)
        }
        btn_select_video2.setOnClickListener {
            selectVideoByGallery(REQUEST_TAKE_GALLERY_VIDEO_2)
        }
        btn_frame_concat.setOnClickListener {
            startByFrameConcat()
        }
        btn_video_concat.setOnClickListener {
            startByVideoConcat()
        }

    }



    @RequiresApi(Build.VERSION_CODES.KITKAT)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == REQUEST_TAKE_GALLERY_VIDEO_1) {
                if (data != null) {
                    uri1 = data.data
                    btn_select_video1.setBackgroundColor(Color.WHITE)
                }
            } else if (requestCode == REQUEST_TAKE_GALLERY_VIDEO_2) {
                if (data != null) {
                    uri2 = data.data
                    btn_select_video2.setBackgroundColor(Color.WHITE)
                }
            }

        }
    }

    private fun startByVideoConcat() {

        Observable.create<Bitmap> {
            val stream1 = contentResolver.openInputStream(uri1)
            val grabber1 = FFmpegFrameGrabber(stream1)
            val stream2 = contentResolver.openInputStream(uri2)
            val grabber2 = FFmpegFrameGrabber(stream2)
            grabber1.start()
            grabber2.start()
            val frame_count = grabber1.lengthInFrames
            for (i in 0..frame_count) {
                grabber1.frameNumber = i
                val frame = grabber1.grabImage()
//                it.onNext()
            }
            stream1.close()
            grabber1.close()
            stream2.close()
            grabber2.close()

        }



    }
    private fun startByFrameConcat() {
        val flowable1 = getCaptureFlowable(uri1)
        val flowable2 = getCaptureFlowable(uri2)
        Flowable.zip(flowable1, flowable2, BiFunction<Frame, Frame, Bitmap> { frame1, frame2 ->
            convertFrame(frame1,frame2)
        })
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    preview.setImageBitmap(it)
                }
    }

    private fun convertFrame(frame1: Frame,frame2: Frame):Bitmap{
        val mat1 = converterToMat.convert(frame1)
        val mat2 = converterToMat.convert(frame2)
        val mat3 = opencv_core.Mat()
        opencv_core.vconcat(mat1, mat2, mat3)
        val frame = converterToMat.convert(mat3)
        val bitmap = converterToBitmap.convert(frame)
        mat1.release()
        mat2.release()
        mat3.release()
        return bitmap
    }


    fun selectVideoByGallery(requestCode: Int) {
        val intent = Intent()
        intent.type = "video/*"
        intent.action = Intent.ACTION_GET_CONTENT
        startActivityForResult(Intent.createChooser(intent, "Select Video"), requestCode)
    }



    fun getCaptureFlowable(uri: Uri):Flowable<Frame> = Flowable.create<Frame>({
        val stream = contentResolver.openInputStream(uri)
        val grabber = FFmpegFrameGrabber(stream)
        grabber.start()
        val frame_count = grabber.lengthInFrames
        Log.d(TAG, "length frame = ${grabber.lengthInFrames}")
        for (i in 0..frame_count) {
            grabber.frameNumber = i
            val frame = grabber.grabImage()
            Log.d(TAG, "frame = ${frame?.imageWidth ?: "null"}")
            it.onNext(frame)
        }
        stream.close()
        grabber.close()
    }, BackpressureStrategy.BUFFER)


}
