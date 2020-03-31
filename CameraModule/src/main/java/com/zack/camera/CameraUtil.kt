package com.zack.camera

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.media.MediaRecorder
import android.util.SparseArray
import android.view.MotionEvent
import android.view.TextureView
import androidx.annotation.MainThread
import androidx.annotation.RequiresPermission
import androidx.core.util.contains
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver

/**
 * @Author zack
 * @Date 2020/1/16
 * @Description 摄像机的相关方法
 * @Version 1.0
 */
object CameraUtil :LifecycleObserver {

    //camera实例队列
    private val cameraArray = SparseArray<Camera>()
    //录像实例
    private var mediaRecorder:MediaRecorder? = null

    /**
     * 获取camera对象
     */
    private fun getCamera(id:Int):Camera? = if (cameraArray.contains(id)) cameraArray.get(id) else null

    /**
     * 初始化相机实例
     */
    @MainThread
    @RequiresPermission(Manifest.permission.CAMERA)
    fun initCamera(lifecycle: Lifecycle,cameraId:Int = 0,openFrontCamera: Boolean = false,debug:Boolean = true){
        InitProvider.DEBUG = debug
        val camera = Camera()
        cameraArray.put(cameraId,camera)
        camera.initCamera(cameraId,lifecycle,openFrontCamera = openFrontCamera)
    }

    /**
     * 移除camera实例
     */
    @MainThread
    fun destroyCamera(cameraId:Int = 0){
        cameraArray.remove(cameraId)
    }

    /**
     * 设置展示面板为textureView
     */
    @MainThread
    @SuppressLint("ClickableViewAccessibility")
    fun setSurface(textureView: TextureView,cameraId:Int = 0){
        val camera = getCamera(cameraId)
        if (textureView.surfaceTexture != null){
            camera?.onSurfaceTextureAvailable(textureView.surfaceTexture,textureView.measuredWidth,textureView.measuredHeight)
        }
        textureView.surfaceTextureListener = camera
        textureView.setOnTouchListener { _, event ->
            if (event?.action == MotionEvent.ACTION_DOWN){
                val actionX = event.x
                val actionY = event.y
                camera?.startPreview(actionX.toInt(), actionY.toInt(),textureView.measuredWidth,textureView.measuredHeight)
            }
            false
        }
    }

    /**
     * 切换摄像头
     */
    @RequiresPermission(Manifest.permission.CAMERA)
    fun switchCamera(cameraId: Int = 0){
        getCamera(cameraId)?.switchCamera()
    }

    /**
     * 当前是否支持闪光灯
     */
    fun isFlashAvailable(cameraId:Int = 0) = getCamera(cameraId)?.checkFlashAvailable()?:false

    /**
     * 打开闪光灯
     */
    fun enableFlash(open:Boolean,cameraId:Int = 0){
        getCamera(cameraId)?.openFlash(open)
    }

    /**
     * 拍照
     */
    fun captureImage(activity: Activity,filePath:String,stateCallback:(Boolean,String)->Unit,cameraId:Int = 0){
        getCamera(cameraId)?.captureImage(activity,filePath,stateCallback)
    }

    /**
     * 开始录像
     */
    fun startRecordVideo(filePath:String,cameraId:Int = 0){
        val camera = getCamera(cameraId)?:return
        val mediaRecorder = MediaRecorder()
        mediaRecorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setVideoEncodingBitRate(1000000)
            setVideoFrameRate(30)
            setOutputFile(filePath)
            val videoSize = camera.getVideoOutputSize()
            setVideoSize(videoSize.height, videoSize.width)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            if (camera.isFrontCameraOpened()) {
                setOrientationHint(270)
            } else {
                setOrientationHint(90)
            }
        }
        mediaRecorder.prepare()
        this.mediaRecorder = mediaRecorder
        camera.startRecordVideo(mediaRecorder)
    }

    /**
     * 停止录像
     */
    fun stopRecordVideo(cameraId:Int = 0){
        mediaRecorder?.let {
            getCamera(cameraId)?.stopRecordVideo(it)
        }
    }

}

