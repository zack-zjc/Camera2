package com.zack.camera

import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.Image
import android.util.Size
import androidx.annotation.WorkerThread
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import kotlin.math.abs


/**
 * @Author zack
 * @Date 2020/3/27
 * @Description 照相机的相关方法类
 * @Version 1.0
 */
object CameraFunction {

    /**
     * 获取摄像头预览尺寸
     * 返回尺寸width为短的height为长的默认竖屏size
     */
    @Throws(CameraAccessException::class)
    internal fun getCameraOutputSize(cameraId:String,maxWidth:Int = InitProvider.screenWidth,
                            maxHeight:Int = InitProvider.screenHeight,kClass:Class<*> = SurfaceTexture::class.java): Size {
        if (!StreamConfigurationMap.isOutputSupportedFor(kClass)) return Size(InitProvider.screenWidth,InitProvider.screenHeight)
        val characteristic = CameraDeviceInfo.cameraManager.getCameraCharacteristics(cameraId)
        val streamConfigurationMap = characteristic.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val supportedSizes = streamConfigurationMap?.getOutputSizes(kClass)
        supportedSizes?.let {
            for (size in supportedSizes){
                if (abs(size.width *1f / size.height - maxHeight * 1f / maxWidth) <= 0.1){
                    return@getCameraOutputSize Size(size.height,size.width)
                }
            }
            return@getCameraOutputSize Size(supportedSizes[0].height,supportedSizes[0].width)
        }
        return Size(InitProvider.screenWidth,InitProvider.screenHeight)
    }

    /**
     * 获取点击区域
     * @param pointX：手指触摸点x坐标
     * @param pointY: 手指触摸点y坐标
     * @param previewWidth: 预览surface的width
     * @param previewHeight: 预览surface的height
     */
    internal fun getFocusRect(cameraId: String,pointX: Int, pointY: Int, previewWidth:Int, previewHeight:Int): Rect {
        val characteristic = CameraDeviceInfo.cameraManager.getCameraCharacteristics(cameraId)
        val cameraRect = characteristic.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)!!
        val reflectX = pointY.toFloat()
        val reflectY = (previewWidth - pointX).toFloat()
        val focusX = (reflectX / previewHeight * cameraRect.width()).toInt()
        val focusY = (reflectY / previewWidth * cameraRect.height()).toInt()
        return Rect(getOptSize(focusX-20,cameraRect.width()), getOptSize(focusY-20,cameraRect.height()),
            getOptSize(focusX+20,cameraRect.width()),getOptSize(focusY+20,cameraRect.height()))
    }

    /**
     * 获取size大小
     */
    private fun getOptSize(size:Int,maxSize:Int,minSize:Int = 0) = minSize.coerceAtLeast(size.coerceAtMost(maxSize))

    /**
     * 保存图片到目标地址
     */
    @WorkerThread
    internal fun storeImageFile(image:Image,filePath:String){
        val buffer: ByteBuffer = image.planes[0].buffer
        val data = ByteArray(buffer.remaining())
        buffer.get(data)
        var fileOutputStream: FileOutputStream? = null
        try {
            val mImageFile = File(filePath)
            if (mImageFile.exists()){
                mImageFile.delete()
            }
            mImageFile.createNewFile()
            fileOutputStream = FileOutputStream(mImageFile)
            fileOutputStream.write(data, 0, data.size)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                fileOutputStream?.close()
                fileOutputStream = null
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

}