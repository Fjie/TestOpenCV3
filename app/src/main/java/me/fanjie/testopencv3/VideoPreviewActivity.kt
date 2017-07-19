package me.fanjie.testopencv3

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.CursorLoader
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.support.annotation.RequiresApi
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.Toast
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_video_preview.*
import org.bytedeco.javacpp.opencv_core
import org.bytedeco.javacpp.opencv_videoio
import permissions.dispatcher.NeedsPermission
import permissions.dispatcher.RuntimePermissions

@RuntimePermissions
class VideoPreviewActivity : AppCompatActivity() {

    private val TAG = "VideoPreviewActivity"
    private val REQUEST_TAKE_GALLERY_VIDEO = 10086

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, VideoPreviewActivity::class.java))
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_preview)
        btn_select_video.setOnClickListener {
            selectVideoByGallery()
        }
//        VideoPreviewActivityPermissionsDispatcher.showCameraWithCheck(this)
    }

    override fun onRequestPermissionsResult(requestCode: Int,  permissions: Array<String>,  grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // NOTE: delegate the permission handling to generated method
//        VideoPreviewActivityPermissionsDispatcher.onRequestPermissionsResult(this, requestCode, grantResults)
    }

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == REQUEST_TAKE_GALLERY_VIDEO) {
                if (data != null) {
                    val uri = data.data
//                    startCapture(getRealPathFromURI(uri))
                    startCapture(Utils.getPath(this,uri))
                }
            }
        }
    }


    @NeedsPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
    fun selectVideoByGallery() {
        val intent = Intent()
        intent.type = "video/*"
        intent.action = Intent.ACTION_GET_CONTENT
        startActivityForResult(Intent.createChooser(intent, "Select Video"), REQUEST_TAKE_GALLERY_VIDEO)
    }

    private fun getRealPathFromURI(contentUri: Uri): String? {
        val projection = arrayOf(MediaStore.Images.Media.DATA)
        val loader = CursorLoader(this, contentUri, projection, null, null, null)
        val cursor = loader.loadInBackground()
        val column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
        cursor.moveToFirst()
        val result = cursor.getString(column_index)
        cursor.close()
        return result
    }


    fun getPath(uri: Uri): String? {
        val projection = arrayOf(MediaStore.Video.Media.DATA)
        val cursor = contentResolver.query(uri, projection, null, null, null)
        if (cursor != null) {
            val column_index = cursor
                    .getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            cursor.moveToFirst()
            return cursor.getString(column_index)
        } else
            return null
    }


    fun startCapture(path: String?) {
        if (path == null || path.isEmpty()) {
            Toast.makeText(this, "path is null", Toast.LENGTH_SHORT).show()
            return
        }
        Log.d(TAG,path)
        Observable
                .create<opencv_core.Mat> {
                    val capture = opencv_videoio.VideoCapture()
                    capture.open(-1)
                    Log.d(TAG,"${capture.isOpened}")
                    val mat: opencv_core.Mat = opencv_core.Mat()
                    while (capture.read(mat)) {
                        it.onNext(mat)
                    }
                }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { Log.d(TAG, it.toString()) }
    }
}
