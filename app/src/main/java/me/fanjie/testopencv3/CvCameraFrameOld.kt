/*
* Copyright 2007-2011 the original author or authors
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package me.fanjie.testopencv3

import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.hardware.Camera.PreviewCallback
import android.hardware.Camera.Size
import android.os.Build
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import org.bytedeco.javacpp.avutil.AV_PIX_FMT_NV21
import org.bytedeco.javacv.FFmpegFrameFilter
import org.bytedeco.javacv.Frame
import org.bytedeco.javacv.FrameFilter
import java.io.IOException
import java.nio.ByteBuffer

/**
 * This is the graphical object used to display a real-time preview of the Camera.
 * It MUST be an extension of the [SurfaceView] class.<br></br>
 * It also needs to implement some other interfaces like [SurfaceHolder.Callback]
 * (to react to SurfaceView events)

 * @author alessandrofrancesconi, hunghd
 */
class CvCameraFrameOld(camType: Int, private val surfaceHolder: SurfaceHolder) {

    companion object {
        val CAMERA_BACK = 99
        val CAMERA_FRONT = 98
        private val LOG_TAG = "CvCameraPreview"
        private val ASPECT_RATIO_W = 4.0f
        private val ASPECT_RATIO_H = 3.0f
        private val PREVIEW_MAX_WIDTH = 640
        private val MAGIC_TEXTURE_ID = 10
    }



    var cameraCallback: CameraCallback? = null

    private var cameraId = -1
    private var previewBuffer: ByteArray? = null
    private var filter: FFmpegFrameFilter? = null
    private var chainIdx = 0
    private var stopThread = false
    private var cameraFrameReady = false
    private var thread: Thread? = null
    private var frames: Array<Frame>? = null
    private var cameraType = Camera.CameraInfo.CAMERA_FACING_BACK
    private var cameraDevice: Camera? = null
    private var frameWidth: Int = 0
    private var frameHeight: Int = 0
    private var cameraConnected: Boolean = false

    init {
        this.cameraType = if (camType == CAMERA_BACK) Camera.CameraInfo.CAMERA_FACING_BACK else Camera.CameraInfo.CAMERA_FACING_FRONT
    }

    fun initCamera() {
        if (cameraConnected) {
            return
        }
        if (connectCamera()) {
            cameraConnected = true
        } else {
            onError("没相机？")
        }
    }

    fun stop() {
        disconnectCamera()
        if (filter != null) {
            try {
                filter!!.release()
            } catch (e: FrameFilter.Exception) {
                e.printStackTrace()
            }

        }
    }

    fun onError(msg: String) {
        Log.e(LOG_TAG, msg)
    }


    private fun connectCamera(): Boolean {
        if (!initializeCamera()) {
            return false
        }
        stopThread = false
        thread = Thread(cameraRunnable)
        thread?.start()
        return true
    }


    private fun initializeCamera(): Boolean {
        synchronized(this) {
            if (this.cameraDevice != null) {
                return true
            }
            this.cameraDevice = Camera.open(cameraId)
            this.cameraId = cameraId
            if (this.cameraId < 0) {
                val camInfo = Camera.CameraInfo()
                for (i in 0..Camera.getNumberOfCameras() - 1) {
                    Camera.getCameraInfo(i, camInfo)
                    if (camInfo.facing == cameraType) {
                        try {
                            this.cameraDevice = Camera.open(i)
                            this.cameraId = i
                            break
                        } catch (e: RuntimeException) {
                            Log.e(LOG_TAG, "initializeCamera(): trying to open camera #$i but it's locked", e)
                        }

                    }
                }
            }
//            如果失败换个方式再来一次
            if (this.cameraDevice == null) {
                try {
                    this.cameraDevice = Camera.open()
                    this.cameraId = 0
                } catch (e: RuntimeException) {
                    Log.e(LOG_TAG,
                            "initializeCamera(): trying to open default camera but it's locked. " + "The camera is not available for this app at the moment.", e
                    )
                    return false
                }

            }


            setupCamera()

            updateCameraDisplayOrientation()

            initFilter(frameWidth, frameHeight)

            startCameraPreview(this.surfaceHolder)
        }

        return true
    }

    private fun disconnectCamera() {
        /* 1. We need to stop thread which updating the frames
         * 2. Stop camera and release it
         */
        Log.d(LOG_TAG, "Disconnecting from camera")
        try {
            stopThread = true
            Log.d(LOG_TAG, "Notify thread")
            synchronized(this) {
                (this as Object).notify()
            }
            Log.d(LOG_TAG, "Wating for thread")
            if (thread != null)
                thread!!.join()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        } finally {
            thread = null
        }

        stopCameraPreview()

        /* Now release camera */
        releaseCamera()

        cameraFrameReady = false
    }

    /**
     * It sets all the required parameters for the Camera object, like preview
     * and picture size, format, flash modes and so on.
     * In this particular example it initializes the [.previewBuffer] too.
     */
    private fun setupCamera(): Boolean {
        if (cameraDevice == null) {
            Log.e(LOG_TAG, "setupCamera(): warning, camera is null")
            return false
        }
        try {
            val parameters = cameraDevice!!.parameters
            val sizes = parameters.supportedPreviewSizes
            if (sizes != null) {
                val bestPreviewSize = getBestSize(sizes, PREVIEW_MAX_WIDTH)

                frameWidth = bestPreviewSize.width
                frameHeight = bestPreviewSize.height

                parameters.setPreviewSize(bestPreviewSize.width, bestPreviewSize.height)

                parameters.previewFormat = ImageFormat.NV21 // NV21 is the most supported format for preview frames

                val FocusModes = parameters.supportedFocusModes
                if (FocusModes != null && FocusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                    parameters.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO
                }

                cameraDevice!!.parameters = parameters // save everything

                // print saved parameters
                val prevWidth = cameraDevice!!.parameters.previewSize.width
                val prevHeight = cameraDevice!!.parameters.previewSize.height
                val picWidth = cameraDevice!!.parameters.pictureSize.width
                val picHeight = cameraDevice!!.parameters.pictureSize.height

                Log.d(LOG_TAG, "setupCamera(): settings applied:\n\t"
                        + "preview size: " + prevWidth + "x" + prevHeight + "\n\t"
                        + "picture size: " + picWidth + "x" + picHeight
                )

                // here: previewBuffer initialization. It will host every frame that comes out
                // from the preview, so it must be big enough.
                // After that, it's linked to the camera with the setCameraCallback() method.
                try {
                    this.previewBuffer = ByteArray(prevWidth * prevHeight * ImageFormat.getBitsPerPixel(cameraDevice!!.parameters.previewFormat) / 8)
                    setCameraCallback()
                } catch (e: IOException) {
                    Log.e(LOG_TAG, "setupCamera(): error setting camera callback.", e)
                }

                return true
            } else {
                return false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }

    }

    private fun releaseCamera() {
        synchronized(this) {
            if (cameraDevice != null) {
                cameraDevice!!.release()
            }
            cameraDevice = null
        }
    }

    /**
     * [IMPORTANT!] Sets the [.previewBuffer] to be the default buffer where the
     * preview frames will be copied. Also sets the callback function
     * when a frame is ready.

     * @throws IOException
     */
    @Throws(IOException::class)
    private fun setCameraCallback() {
        Log.d(LOG_TAG, "setCameraCallback()")
        cameraDevice!!.addCallbackBuffer(this.previewBuffer)
        cameraDevice!!.setPreviewCallbackWithBuffer(cvCameraPreviewCallback)
    }


    /**
     * [IMPORTANT!] This is a convenient function to determine what's the proper
     * preview/picture size to be assigned to the camera, by looking at
     * the list of supported sizes and the maximum value given

     * @param sizes          sizes that are currently supported by the camera hardware,
     * *                       retrived with [Camera.Parameters.getSupportedPictureSizes] or [Camera.Parameters.getSupportedPreviewSizes]
     * *
     * @param widthThreshold the maximum value we want to apply
     * *
     * @return an optimal size <= widthThreshold
     */
    private fun getBestSize(sizes: List<Size>, widthThreshold: Int): Size {
        var bestSize: Size? = null

        for (currentSize in sizes) {
            val isDesiredRatio = currentSize.width / ASPECT_RATIO_W == currentSize.height / ASPECT_RATIO_H
            val isBetterSize = bestSize == null || currentSize.width > bestSize.width
            val isInBounds = currentSize.width <= widthThreshold

            if (isDesiredRatio && isInBounds && isBetterSize) {
                bestSize = currentSize
            }
        }

        if (bestSize == null) {
            bestSize = sizes[0]
            Log.e(LOG_TAG, "determineBestSize(): can't find a good size. Setting to the very first...")
        }

        Log.i(LOG_TAG, "determineBestSize(): bestSize is " + bestSize.width + "x" + bestSize.height)
        return bestSize
    }

    /**
     * In addition to calling [Camera.startPreview], it also
     * updates the preview display that could be changed in some situations

     * @param holder the current [SurfaceHolder]
     */
    @Synchronized private fun startCameraPreview(holder: SurfaceHolder) {
        try {
            val surfaceTexture = SurfaceTexture(MAGIC_TEXTURE_ID)
            cameraDevice!!.setPreviewTexture(surfaceTexture)
            cameraDevice!!.startPreview()
        } catch (e: Exception) {
            Log.e(LOG_TAG, "startCameraPreview(): error starting camera preview", e)
        }

    }

    /**
     * It "simply" calls [Camera.stopPreview] and checks
     * for errors
     */
    @Synchronized private fun stopCameraPreview() {
        try {
            cameraDevice!!.stopPreview()
            cameraDevice!!.setPreviewCallback(null)
            //            filter.stop();
        } catch (e: Exception) {
            // ignored: tried to stop a non-existent preview
            Log.i(LOG_TAG, "stopCameraPreview(): tried to stop a non-running preview, this is not an error")
        }

    }

    /**
     * Gets the current screen rotation in order to understand how much
     * the surface needs to be rotated
     */
    private fun updateCameraDisplayOrientation() {
        if (cameraDevice == null) {
            Log.e(LOG_TAG, "updateCameraDisplayOrientation(): warning, camera is null")
            return
        }

        val degree = rotationDegree

        cameraDevice!!.setDisplayOrientation(degree) // save settings
    }

    private //        Activity parentActivity = (Activity) this.getContext();
            //        int rotation = parentActivity.getWindowManager().getDefaultDisplay().getRotation();
            // on >= API 9 we can proceed with the CameraInfo method
            // and also we have to keep in mind that the camera could be the front one
            // compensate the mirror
            // back-facing
            // TODO: on the majority of API 8 devices, this trick works good
            // and doesn't produce an upside-down preview.
            // ... but there is a small amount of devices that don't like it!
    val rotationDegree: Int
        get() {
            var result = 0
            val rotation = Surface.ROTATION_0
            var degrees = 0
            when (rotation) {
                Surface.ROTATION_0 -> degrees = 0
                Surface.ROTATION_90 -> degrees = 90
                Surface.ROTATION_180 -> degrees = 180
                Surface.ROTATION_270 -> degrees = 270
            }

            if (Build.VERSION.SDK_INT >= 9) {
                val info = Camera.CameraInfo()
                Camera.getCameraInfo(cameraId, info)

                if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                    result = (info.orientation + degrees) % 360
                    result = (360 - result) % 360
                } else {
                    result = (info.orientation - degrees + 360) % 360
                }
            } else {
                result = Math.abs(degrees - 90)
            }
            return result
        }

    private fun initFilter(width: Int, height: Int) {
        val degree = rotationDegree
        val info = Camera.CameraInfo()
        Camera.getCameraInfo(cameraId, info)
        val isFrontFaceCamera = info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT
        Log.i(LOG_TAG, "init filter with width = " + width + " and height = " + height + " and degree = "
                + degree + " and isFrontFaceCamera = " + isFrontFaceCamera)
        val transposeCode: String
        val formatCode = "format=pix_fmts=rgba"
        /*
         0 = 90CounterCLockwise and Vertical Flip (default)
         1 = 90Clockwise
         2 = 90CounterClockwise
         3 = 90Clockwise and Vertical Flip
         */
        when (degree) {
            0 -> transposeCode = if (isFrontFaceCamera) "transpose=3,transpose=2" else "transpose=1,transpose=2"
            90 -> transposeCode = if (isFrontFaceCamera) "transpose=3" else "transpose=1"
            180 -> transposeCode = if (isFrontFaceCamera) "transpose=0,transpose=2" else "transpose=2,transpose=2"
            270 -> transposeCode = if (isFrontFaceCamera) "transpose=0" else "transpose=2"
            else -> transposeCode = if (isFrontFaceCamera) "transpose=3,transpose=2" else "transpose=1,transpose=2"
        }

        if (frames == null) {
            frames = arrayOf(Frame(width, height, Frame.DEPTH_UBYTE, 2),
                    Frame(width, height, Frame.DEPTH_UBYTE, 2))
        }

        filter = FFmpegFrameFilter(transposeCode + "," + formatCode, width, height)
        filter?.pixelFormat = AV_PIX_FMT_NV21

        Log.i(LOG_TAG, "filter initialize success")
    }

    private fun processFrame(raw: ByteArray?) {
        if (frames != null) {
            synchronized(this) {
                (frames!![chainIdx].image[0].position(0) as ByteBuffer).put(raw)
                cameraFrameReady = true
                (this as Object).notify()
            }
        }
    }

    private val cvCameraPreviewCallback = PreviewCallback { raw, cam ->
        processFrame(previewBuffer)
        // [IMPORTANT!] remember to reset the CallbackBuffer at the end of every onPreviewFrame event.
        // Seems weird, but it works
        cam.addCallbackBuffer(previewBuffer)
    }
    private val cameraRunnable: Runnable = Runnable {
        do {
            var hasFrame = false
            synchronized(this@CvCameraFrameOld) {
                try {
                    while (!cameraFrameReady && !stopThread) {
                        (this@CvCameraFrameOld as Object).wait()
                    }
                } catch (e: InterruptedException) {
                    Log.e(LOG_TAG, "CameraWorker interrupted", e)
                }

                if (cameraFrameReady) {
                    chainIdx = 1 - chainIdx
                    cameraFrameReady = false
                    hasFrame = true
                }
            }

            if (!stopThread && hasFrame) {
                try {
                    filter!!.start()
                    filter!!.push(frames!![1 - chainIdx])
                    val frame = filter!!.pull()
                    while (frame != null) {
                        cameraCallback?.onCameraFrame(frame)
                    }
                    filter!!.stop()
                } catch (e: FrameFilter.Exception) {
                    e.printStackTrace()
                }

            }
        } while (!stopThread)
        Log.d(LOG_TAG, "Finish processing thread")
    }

}
