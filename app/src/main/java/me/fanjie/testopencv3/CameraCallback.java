package me.fanjie.testopencv3;

import org.bytedeco.javacv.Frame;

/**
 * Created by fanjie on 2017/7/13.
 */

public interface CameraCallback {
    void onCameraFrame(Frame frame);
}
