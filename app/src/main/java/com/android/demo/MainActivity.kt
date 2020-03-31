package com.android.demo

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.zack.camera.CameraUtil
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        CameraUtil.initCamera(this.lifecycle)
        CameraUtil.setSurface(id_textture)
        id_capture.setOnClickListener {
//            CameraUtil.startRecordVideo("/sdcard/libs/11.mp4")
//            id_capture.postDelayed({
//                CameraUtil.stopRecordVideo()
//            },5000)
            CameraUtil.captureImage(this,"/sdcard/libs/12.jpg",::onCaptureResult)
        }
    }

    private fun onCaptureResult(success:Boolean,filePath:String){

    }

    override fun onDestroy() {
        super.onDestroy()
        CameraUtil.destroyCamera()
    }
}
