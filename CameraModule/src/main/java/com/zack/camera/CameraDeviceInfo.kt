package com.zack.camera

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.SparseArray
import android.view.Surface

/**
 * @Author zack
 * @Date 2020/3/30
 * @Description 设备信息
 * @Version 1.0
 */
object CameraDeviceInfo {

    //获取cameraManager
    val cameraManager : CameraManager by lazy {
        InitProvider.applicationContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    //获取前置摄像头id
    val frontCameraId : String by lazy {
        var cameraId = ""
        try {
            for (id in cameraManager.cameraIdList){
                val characteristic = cameraManager.getCameraCharacteristics(id)
                //摄像头是否前置
                val facing = characteristic.get(CameraCharacteristics.LENS_FACING)
                if (facing == CameraCharacteristics.LENS_FACING_FRONT){
                    cameraId = id
                }
            }
        }catch (e:Exception){
            e.printStackTrace()
        }
        cameraId
    }

    //获取后置摄像头id
    val backCameraId : String by lazy {
        var cameraId = ""
        try {
            for (id in cameraManager.cameraIdList){
                val characteristic = cameraManager.getCameraCharacteristics(id)
                //摄像头是否前置
                val facing = characteristic.get(CameraCharacteristics.LENS_FACING)
                if (facing == CameraCharacteristics.LENS_FACING_BACK){
                    cameraId = id
                }
            }
        }catch (e:Exception){
            e.printStackTrace()
        }
        cameraId
    }

    //拍照旋转角度
    @SuppressLint("UseSparseArrays")
    val CAPTURE_ORIENTATION = SparseArray<Int>().apply{
        append(Surface.ROTATION_0,90)
        append(Surface.ROTATION_90,0)
        append(Surface.ROTATION_180,270)
        append(Surface.ROTATION_270,180)
    }

}