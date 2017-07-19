package me.fanjie.testopencv3

import io.reactivex.Observable
import org.bytedeco.javacpp.opencv_core
import org.bytedeco.javacpp.opencv_videoio
import java.io.File

/**
 * Created by fanjie on 2017/7/18.
 */
class VideoFrameCapture(val callback: FrameCallback) {

    fun startCapture(file: File) {
        Observable.create<opencv_core.Mat> {
            val capture = opencv_videoio.VideoCapture(file.absolutePath)
            var mat:opencv_core.Mat = opencv_core.Mat()
            while (capture.read(mat)){
                callback.onFrameCapture(mat)
            }
        }
    }
}