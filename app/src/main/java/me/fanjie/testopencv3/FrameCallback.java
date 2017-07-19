package me.fanjie.testopencv3;

import org.bytedeco.javacpp.opencv_core;

/**
 * Created by fanjie on 2017/7/18.
 */

public interface FrameCallback {
    void onFrameCapture(opencv_core.Mat mat);
}
