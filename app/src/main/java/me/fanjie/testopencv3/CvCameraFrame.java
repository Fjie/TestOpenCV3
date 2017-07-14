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
package me.fanjie.testopencv3;

import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.os.Build;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacv.FFmpegFrameFilter;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameFilter;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import static org.bytedeco.javacpp.avutil.AV_PIX_FMT_NV21;

// TODO: 2017/7/13 再试一次Kotlin重写；替换Frame为cv.Mat
public class CvCameraFrame {

    private final String LOG_TAG = "CvCameraPreview";


    public static final int CAMERA_BACK = 99;
    public static final int CAMERA_FRONT = 98;


    private static final int MAGIC_TEXTURE_ID = 10;


    private final float ASPECT_RATIO_W = 4.0f;
    private final float ASPECT_RATIO_H = 3.0f;

    /**
     * The maximum dimension (in pixels) of the preview frames that are produced
     * by the Camera object. Note that this should not be intended as
     * the final, exact, dimension because the device could not support
     * it and a lower value is required (but the aspect ratio should remain the same).<br />
     * See {@link CvCameraFrameOld#getBestSize(List, int)} for more information.
     */
    private final int PREVIEW_MAX_WIDTH = 640;

    /**
     * The maximum dimension (in pixels) of the images produced when a
     * {@link PictureCallback#onPictureTaken(byte[], Camera)} event is
     * fired. Again, this is a maximum value and could not be the
     * real one implemented by the device.
     */
    private final int PICTURE_MAX_WIDTH = 1280;

    public CameraCallback cameraCallback;
    public int cameraId = -1;

    /**
     * In this example we look at camera preview buffer functionality too.<br />
     * This is the array that will be filled everytime a single preview frame is
     * ready to be processed (for example when we want to show to the user
     * a transformed preview instead of the original one, or when we want to
     * make some image analysis in real-time without taking full-sized pictures).
     */
    private byte[] previewBuffer;


    private FFmpegFrameFilter filter;
    private int chainIdx = 0;
    private boolean stopThread = false;
    private boolean cameraFrameReady = false;

    private Thread thread;

    private Frame[] cameraFrame;
    private int cameraType = Camera.CameraInfo.CAMERA_FACING_BACK;
    private Camera cameraDevice;
    private int frameWidth, frameHeight;

    private SurfaceHolder surfaceHolder;

    private boolean cameraConnected;

    public CvCameraFrame(int camType, SurfaceHolder surfaceHolder) {
        this.surfaceHolder = surfaceHolder;
        this.cameraType = camType == CAMERA_BACK ? Camera.CameraInfo.CAMERA_FACING_BACK : Camera.CameraInfo.CAMERA_FACING_FRONT;
    }

    public void initCamera(){
        if(cameraConnected){
            return;
        }
        cameraConnected = connectCamera();
    }

    public void stop(){
        disconnectCamera();
        if (filter != null) {
            try {
                filter.release();
            } catch (FrameFilter.Exception e) {
                e.printStackTrace();
            }
        }
    }


    private boolean connectCamera() {
        /* 1. We need to instantiate camera
         * 2. We need to initCamera thread which will be getting frames
         */
        /* First step - initialize camera connection */
        Log.d(LOG_TAG, "Connecting to camera");
        if (!initializeCamera()) {
            return false;
        }

        /* now we can initCamera update thread */
        Log.d(LOG_TAG, "Starting processing thread");
        stopThread = false;
        thread = new Thread(new CameraWorker());
        thread.start();

        return cameraConnected = true;
    }

    private boolean initializeCamera() {
        synchronized (this) {
            if (this.cameraDevice != null) {
                // do the job only if the camera is not already set
                Log.i(LOG_TAG, "initializeCamera(): camera is already set, nothing to do");
                return true;
            }

            // warning here! starting from API 9, we can retrieve one from the multiple
            // hardware cameras (ex. front/back)
            if (Build.VERSION.SDK_INT >= 9) {

                if (this.cameraId < 0) {
                    // at this point, it's the first time we request for a camera
                    Camera.CameraInfo camInfo = new Camera.CameraInfo();
                    for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
                        Camera.getCameraInfo(i, camInfo);

                        if (camInfo.facing == cameraType) {
                            // in this example we'll request specifically the back camera
                            try {
                                this.cameraDevice = Camera.open(i);
                                this.cameraId = i; // assign to cameraId this camera's ID (O RLY?)
                                break;
                            } catch (RuntimeException e) {
                                // something bad happened! this camera could be locked by other apps
                                Log.e(LOG_TAG, "initializeCamera(): trying to open camera #" + i + " but it's locked", e);
                            }
                        }
                    }
                } else {
                    // at this point, a previous camera was set, we try to re-instantiate it
                    try {
                        this.cameraDevice = Camera.open(this.cameraId);
                    } catch (RuntimeException e) {
                        Log.e(LOG_TAG, "initializeCamera(): trying to re-open camera #" + this.cameraId + " but it's locked", e);
                    }
                }
            }

            // we could reach this point in two cases:
            // - the API is lower than 9
            // - previous code block failed
            // hence, we try the classic method, that doesn't ask for a particular camera
            if (this.cameraDevice == null) {
                try {
                    this.cameraDevice = Camera.open();
                    this.cameraId = 0;
                } catch (RuntimeException e) {
                    // this is REALLY bad, the camera is definitely locked by the system.
                    Log.e(LOG_TAG,
                            "initializeCamera(): trying to open default camera but it's locked. "
                                    + "The camera is not available for this app at the moment.", e
                    );
                    return false;
                }
            }

            // here, the open() went good and the camera is available
            Log.i(LOG_TAG, "initializeCamera(): successfully set camera #" + this.cameraId);

            setupCamera();

            updateCameraDisplayOrientation();

            initFilter(frameWidth, frameHeight);

            startCameraPreview(this.surfaceHolder);
        }

        return true;
    }

    private void disconnectCamera() {
        /* 1. We need to stop thread which updating the frames
         * 2. Stop camera and release it
         */
        Log.d(LOG_TAG, "Disconnecting from camera");
        try {
            stopThread = true;
            Log.d(LOG_TAG, "Notify thread");
            synchronized (this) {
                this.notify();
            }
            Log.d(LOG_TAG, "Wating for thread");
            if (thread != null)
                thread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            thread = null;
        }

        stopCameraPreview();

        /* Now release camera */
        releaseCamera();

        cameraFrameReady = false;
    }

    /**
     * It sets all the required parameters for the Camera object, like preview
     * and picture size, format, flash modes and so on.
     * In this particular example it initializes the {@link #previewBuffer} too.
     */
    private boolean setupCamera() {
        if (cameraDevice == null) {
            Log.e(LOG_TAG, "setupCamera(): warning, camera is null");
            return false;
        }
        try {
            Camera.Parameters parameters = cameraDevice.getParameters();
            List<Size> sizes = parameters.getSupportedPreviewSizes();
            if (sizes != null) {
                Size bestPreviewSize = getBestSize(sizes, PREVIEW_MAX_WIDTH);

                frameWidth = bestPreviewSize.width;
                frameHeight = bestPreviewSize.height;

                parameters.setPreviewSize(bestPreviewSize.width, bestPreviewSize.height);

                parameters.setPreviewFormat(ImageFormat.NV21); // NV21 is the most supported format for preview frames

                List<String> FocusModes = parameters.getSupportedFocusModes();
                if (FocusModes != null && FocusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                    parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
                }

                cameraDevice.setParameters(parameters); // save everything

                // print saved parameters
                int prevWidth = cameraDevice.getParameters().getPreviewSize().width;
                int prevHeight = cameraDevice.getParameters().getPreviewSize().height;
                int picWidth = cameraDevice.getParameters().getPictureSize().width;
                int picHeight = cameraDevice.getParameters().getPictureSize().height;

                Log.d(LOG_TAG, "setupCamera(): settings applied:\n\t"
                        + "preview size: " + prevWidth + "x" + prevHeight + "\n\t"
                        + "picture size: " + picWidth + "x" + picHeight
                );

                // here: previewBuffer initialization. It will host every frame that comes out
                // from the preview, so it must be big enough.
                // After that, it's linked to the camera with the setCameraCallback() method.
                try {
                    this.previewBuffer = new byte[prevWidth * prevHeight * ImageFormat.getBitsPerPixel(cameraDevice.getParameters().getPreviewFormat()) / 8];
                    setCameraCallback();
                } catch (IOException e) {
                    Log.e(LOG_TAG, "setupCamera(): error setting camera callback.", e);
                }

                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private void releaseCamera() {
        synchronized (this) {
            if (cameraDevice != null) {
                cameraDevice.release();
            }
            cameraDevice = null;
        }
    }

    /**
     * [IMPORTANT!] Sets the {@link #previewBuffer} to be the default buffer where the
     * preview frames will be copied. Also sets the callback function
     * when a frame is ready.
     *
     * @throws IOException
     */
    private void setCameraCallback() throws IOException {
        Log.d(LOG_TAG, "setCameraCallback()");
        cameraDevice.addCallbackBuffer(this.previewBuffer);
        cameraDevice.setPreviewCallbackWithBuffer(cvCameraPreviewCallback);
    }


    /**
     * [IMPORTANT!] This is a convenient function to determine what's the proper
     * preview/picture size to be assigned to the camera, by looking at
     * the list of supported sizes and the maximum value given
     *
     * @param sizes          sizes that are currently supported by the camera hardware,
     *                       retrived with {@link Camera.Parameters#getSupportedPictureSizes()} or {@link Camera.Parameters#getSupportedPreviewSizes()}
     * @param widthThreshold the maximum value we want to apply
     * @return an optimal size <= widthThreshold
     */
    private Size getBestSize(List<Size> sizes, int widthThreshold) {
        Size bestSize = null;

        for (Size currentSize : sizes) {
            boolean isDesiredRatio = ((currentSize.width / ASPECT_RATIO_W) == (currentSize.height / ASPECT_RATIO_H));
            boolean isBetterSize = (bestSize == null || currentSize.width > bestSize.width);
            boolean isInBounds = currentSize.width <= widthThreshold;

            if (isDesiredRatio && isInBounds && isBetterSize) {
                bestSize = currentSize;
            }
        }

        if (bestSize == null) {
            bestSize = sizes.get(0);
            Log.e(LOG_TAG, "determineBestSize(): can't find a good size. Setting to the very first...");
        }

        Log.i(LOG_TAG, "determineBestSize(): bestSize is " + bestSize.width + "x" + bestSize.height);
        return bestSize;
    }

    /**
     * In addition to calling {@link Camera#startPreview()}, it also
     * updates the preview display that could be changed in some situations
     *
     * @param holder the current {@link SurfaceHolder}
     */
    private synchronized void startCameraPreview(SurfaceHolder holder) {
        try {
            SurfaceTexture surfaceTexture = new SurfaceTexture(MAGIC_TEXTURE_ID);
            cameraDevice.setPreviewTexture(surfaceTexture);
            cameraDevice.startPreview();
        } catch (Exception e) {
            Log.e(LOG_TAG, "startCameraPreview(): error starting camera preview", e);
        }
    }

    /**
     * It "simply" calls {@link Camera#stopPreview()} and checks
     * for errors
     */
    private synchronized void stopCameraPreview() {
        try {
            cameraDevice.stopPreview();
            cameraDevice.setPreviewCallback(null);
//            filter.stop();
        } catch (Exception e) {
            // ignored: tried to stop a non-existent preview
            Log.i(LOG_TAG, "stopCameraPreview(): tried to stop a non-running preview, this is not an error");
        }
    }

    /**
     * Gets the current screen rotation in order to understand how much
     * the surface needs to be rotated
     */
    private void updateCameraDisplayOrientation() {
        if (cameraDevice == null) {
            Log.e(LOG_TAG, "updateCameraDisplayOrientation(): warning, camera is null");
            return;
        }

        int degree = getRotationDegree();

        cameraDevice.setDisplayOrientation(degree); // save settings
    }

    private int getRotationDegree() {
        int result = 0;
//        Activity parentActivity = (Activity) this.getContext();

//        int rotation = parentActivity.getWindowManager().getDefaultDisplay().getRotation();
        int rotation = Surface.ROTATION_0;
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }

        if (Build.VERSION.SDK_INT >= 9) {
            // on >= API 9 we can proceed with the CameraInfo method
            // and also we have to keep in mind that the camera could be the front one
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(cameraId, info);

            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                result = (info.orientation + degrees) % 360;
                result = (360 - result) % 360;  // compensate the mirror
            } else {
                // back-facing
                result = (info.orientation - degrees + 360) % 360;
            }
        } else {
            // TODO: on the majority of API 8 devices, this trick works good
            // and doesn't produce an upside-down preview.
            // ... but there is a small amount of devices that don't like it!
            result = Math.abs(degrees - 90);
        }
        return result;
    }

    private void initFilter(int width, int height) {
        int degree = getRotationDegree();
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);
        boolean isFrontFaceCamera = info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT;
        Log.i(LOG_TAG, "init filter with width = " + width + " and height = " + height + " and degree = "
                + degree + " and isFrontFaceCamera = " + isFrontFaceCamera);
        String transposeCode;
        String formatCode = "format=pix_fmts=rgba";
        /*
         0 = 90CounterCLockwise and Vertical Flip (default)
         1 = 90Clockwise
         2 = 90CounterClockwise
         3 = 90Clockwise and Vertical Flip
         */
        switch (degree) {
            case 0:
                transposeCode = isFrontFaceCamera ? "transpose=3,transpose=2" : "transpose=1,transpose=2";
                break;
            case 90:
                transposeCode = isFrontFaceCamera ? "transpose=3" : "transpose=1";
                break;
            case 180:
                transposeCode = isFrontFaceCamera ? "transpose=0,transpose=2" : "transpose=2,transpose=2";
                break;
            case 270:
                transposeCode = isFrontFaceCamera ? "transpose=0" : "transpose=2";
                break;
            default:
                transposeCode = isFrontFaceCamera ? "transpose=3,transpose=2" : "transpose=1,transpose=2";
        }

        if (cameraFrame == null) {
            cameraFrame = new Frame[2];
            cameraFrame[0] = new Frame(width, height, Frame.DEPTH_UBYTE, 2);
            cameraFrame[1] = new Frame(width, height, Frame.DEPTH_UBYTE, 2);
        }

        filter = new FFmpegFrameFilter(transposeCode + "," + formatCode, width, height);
        filter.setPixelFormat(AV_PIX_FMT_NV21);

        Log.i(LOG_TAG, "filter initialize success");
    }

    /**
     * [IMPORTANT!] It's the callback that's fired when a preview frame is ready. Here
     * we can do some real-time analysis of the preview's contents.
     * Just remember that the buffer array is a list of pixels represented in
     * Y'UV420sp (NV21) format, so you could have to convert it to RGB before.
     *
     * @param raw the preview buffer
     * @param cam the camera that filled the buffer
     * @see <a href="http://en.wikipedia.org/wiki/YUV#Y.27UV420sp_.28NV21.29_to_ARGB8888_conversion">YUV Conversion - Wikipedia</a>
     */
    private void processFrame(byte[] raw, Camera cam) {
        if (cameraFrame != null) {
            synchronized (this) {
                ((ByteBuffer) cameraFrame[chainIdx].image[0].position(0)).put(raw);
                cameraFrameReady = true;
                this.notify();
            }
        }
    }

    private class CameraWorker implements Runnable {
        public void run() {
            do {
                boolean hasFrame = false;
                synchronized (CvCameraFrame.this) {
                    try {
                        while (!cameraFrameReady && !stopThread) {
                            CvCameraFrame.this.wait();
                        }
                    } catch (InterruptedException e) {
                        Log.e(LOG_TAG, "CameraWorker interrupted", e);
                    }
                    if (cameraFrameReady) {
                        chainIdx = 1 - chainIdx;
                        cameraFrameReady = false;
                        hasFrame = true;
                    }
                }

                if (!stopThread && hasFrame) {
                    if (cameraFrame[1 - chainIdx] != null) {
                        try {
                            Frame frame;
                            filter.start();
                            filter.push(cameraFrame[1 - chainIdx]);
                            while ((frame = filter.pull()) != null) {
                                cameraCallback.onCameraFrame(frame);
                            }
                            filter.stop();
                        } catch (FrameFilter.Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            } while (!stopThread);
            Log.d(LOG_TAG, "Finish processing thread");
        }
    }

    private PreviewCallback cvCameraPreviewCallback = new PreviewCallback(){

        @Override
        public void onPreviewFrame(byte[] raw, Camera cam) {
            opencv_core.Mat mat = new opencv_core.Mat(raw);
            processFrame(previewBuffer, cam);
            // [IMPORTANT!] remember to reset the CallbackBuffer at the end of every onPreviewFrame event.
            // Seems weird, but it works
            cam.addCallbackBuffer(previewBuffer);
        }
    };




}
