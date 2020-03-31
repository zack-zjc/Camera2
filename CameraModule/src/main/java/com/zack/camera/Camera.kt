package com.zack.camera

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.params.MeteringRectangle
import android.media.ImageReader
import android.media.MediaRecorder
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import androidx.annotation.MainThread
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent

/**
 * @Author zack
 * @Date 2020/3/30
 * @Description 照相机操作方法
 * @Version 1.0
 */
internal class Camera : LifecycleObserver,TextureView.SurfaceTextureListener{

    //主线程handler
    private var mainHandler: Handler = Handler(Looper.getMainLooper())
    //相机线程
    private var cameraThread: HandlerThread? = null
    //相机处理方法
    private var cameraHandler: Handler? = null
    //之前默认的摄像头id
    private var currentCameraId:String = CameraDeviceInfo.backCameraId
    //是否打开闪光灯
    private var openFlash:Boolean = false
    //照相机实例
    private var mCameraDeviceFuture: CameraFuture<CameraDevice>? = null
    //surfaceTexture实例
    private var mPreviewSurfaceTextureFuture: CameraFuture<SurfaceTexture> = CameraFuture()
    //surface实例
    private var mPreviewSurfaceFuture: CameraFuture<Surface> = CameraFuture()
    //surface实例
    private var mPreviewSurfaceSizeFuture: CameraFuture<Size> = CameraFuture()
    //预览session实例
    private var mCameraPreviewSessionFuture:CameraFuture<CameraCaptureSession>? = null
    //拍照reader
    private var mCaptureImageReaderFuture: CameraFuture<ImageReader>? = null

    //------------------------------surface状态处理-start--------------------------------
    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture?, width: Int, height: Int) {
        mPreviewSurfaceTextureFuture.set(surface)
        mPreviewSurfaceSizeFuture.set(Size(width,height))
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) = Unit

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?): Boolean { 
        mPreviewSurfaceTextureFuture.reset()
        mPreviewSurfaceFuture.reset()
        mPreviewSurfaceSizeFuture.reset()
        return false
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture?, width: Int, height: Int) {
        mPreviewSurfaceTextureFuture.set(surface)
        mPreviewSurfaceFuture.set(Surface(surface))
        mPreviewSurfaceSizeFuture.set(Size(width,height))
    }
    //----------------------------surface状态处理-end------------------------------

    /**
     * 初始化拍照的imageReader
     */
    private fun initCaptureImageReader(previewSize:Size){
        mCaptureImageReaderFuture = CameraFuture()
        val mImageReader = ImageReader.newInstance(previewSize.width, previewSize.height, ImageFormat.JPEG, 1)
        mCaptureImageReaderFuture?.set(mImageReader)
    }

    /**
     * 生成预览session
     */
    private fun createCaptureSession(){
        if (InitProvider.DEBUG){
            Log.e("camera","createCaptureSession")
        }
        cameraHandler?.post {
            mCameraPreviewSessionFuture = CameraFuture()
            val cameraDevice = mCameraDeviceFuture?.get()!!
            val size = mPreviewSurfaceSizeFuture.get()!!
            val surfaceTexture = mPreviewSurfaceTextureFuture.get()
            val previewSize = CameraFunction.getCameraOutputSize(cameraDevice.id,
                maxWidth = size.width,maxHeight = size.height,kClass = SurfaceTexture::class.java)
            if (InitProvider.DEBUG){
                Log.e("camera","previewSize:${previewSize.width},${previewSize.height}")
            }
            surfaceTexture?.setDefaultBufferSize(previewSize.width, previewSize.height)
            initCaptureImageReader(previewSize)
            val imageReaderSurface = mCaptureImageReaderFuture?.get()?.surface
            val previewSurface = mPreviewSurfaceFuture.get()
            cameraDevice.createCaptureSession(listOf(imageReaderSurface,previewSurface),object: CameraCaptureSession.StateCallback() {
                override fun onConfigureFailed(session: CameraCaptureSession) {
                    if (InitProvider.DEBUG){
                        Log.e("camera","on camera session configure failed")
                    }
                    mCameraPreviewSessionFuture?.set(session)
                    closeCamera()
                }

                override fun onConfigured(session: CameraCaptureSession) {
                    if (InitProvider.DEBUG){
                        Log.e("camera","on camera session configured")
                    }
                    mCameraPreviewSessionFuture?.set(session)
                    startPreview()
                }

            }, mainHandler)
        }
    }

    /**
     * 开启preview,可聚焦于某个区域
     */
    internal fun startPreview(pointX:Int = 0,pointY:Int = 0,viewWidth:Int = 0,viewHeight:Int = 0){
        if (InitProvider.DEBUG){
            Log.e("camera","startPreview")
        }
        cameraHandler?.post {
            val cameraDevice = mCameraDeviceFuture?.get()!!
            val surface = mPreviewSurfaceFuture.get()!!
            val session = mCameraPreviewSessionFuture?.get()
            session?.stopRepeating()
            val previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            previewRequestBuilder.apply {
                addTarget(surface)
                if (pointX != 0 && pointY != 0){
                    val rect = CameraFunction.getFocusRect(currentCameraId,pointX,pointY,viewWidth,viewHeight)
                    set(CaptureRequest.CONTROL_AF_REGIONS,arrayOf(MeteringRectangle(rect, 1000)))
                    set(CaptureRequest.CONTROL_AE_REGIONS,arrayOf(MeteringRectangle(rect, 1000)))
                    set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_AUTO)
                    set(CaptureRequest.CONTROL_AF_TRIGGER,CameraMetadata.CONTROL_AF_TRIGGER_START)
                }else{
                    set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                }
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                set(CaptureRequest.FLASH_MODE, if (openFlash) CameraMetadata.FLASH_MODE_TORCH else CameraMetadata.FLASH_MODE_OFF)
            }
            session?.setRepeatingRequest(previewRequestBuilder.build(),object:
                CameraCaptureSession.CaptureCallback() {

                override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                    super.onCaptureCompleted(session, request, result)
                    val afState = result.get(CaptureResult.CONTROL_AF_STATE)
                    val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                    //聚焦稳定就切换到连续图像模式
                    if (afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED &&
                        aeState == CaptureRequest.CONTROL_AE_STATE_CONVERGED){
                        if (InitProvider.DEBUG){
                            Log.e("camera","focus complete")
                        }
                    }
                }
            }, mainHandler)
        }
    }

    /**
     * 关闭摄像头
     */
    @MainThread
    private fun closeCamera(){
        if (InitProvider.DEBUG){
            Log.e("camera","close camera")
        }
        cameraHandler?.post {
            val cameraPreviewSession = mCameraPreviewSessionFuture?.get()
            cameraPreviewSession?.close()
            mCameraPreviewSessionFuture = null
            val cameraDevice = mCameraDeviceFuture?.get()
            cameraDevice?.close()
            mCameraDeviceFuture = null
        }
    }

    /**
     * 初始化相机线程和部分参数
     */
    @MainThread
    internal fun initCamera(id:Int,lifecycle: Lifecycle,openFrontCamera: Boolean = false){
        cameraThread = HandlerThread("cameraThread-$id")
        cameraThread?.start()
        cameraHandler = Handler(cameraThread!!.looper, Handler.Callback { false })
        lifecycle.addObserver(this)
        currentCameraId = if (openFrontCamera) CameraDeviceInfo.frontCameraId else CameraDeviceInfo.backCameraId
    }

    /**
     * 释放camera参数
     */
    private fun destroyCamera(){
        mCaptureImageReaderFuture?.reset()
        cameraThread?.quitSafely()
        cameraThread?.join()
        cameraThread = null
        cameraHandler?.removeCallbacksAndMessages(null)
        mainHandler.removeCallbacksAndMessages(null)
        cameraHandler = null
    }


    /**
     * 切换相机开关开关
     */
    @RequiresPermission(android.Manifest.permission.CAMERA)
    internal fun switchCamera(){
        if (InitProvider.DEBUG){
            Log.e("camera","switchCamera")
        }
        closeCamera()
        openCamera(openFrontCamera = currentCameraId != CameraDeviceInfo.frontCameraId)
    }

    /**
     * 打开摄像头
     */
    @RequiresPermission(android.Manifest.permission.CAMERA)
    internal fun openCamera(openFrontCamera:Boolean = false){
        if (InitProvider.DEBUG){
            Log.e("camera","open front camera:$openFrontCamera")
        }
        currentCameraId = if (openFrontCamera) CameraDeviceInfo.frontCameraId else CameraDeviceInfo.backCameraId
        cameraHandler?.post {
            mCameraDeviceFuture = CameraFuture()
            CameraDeviceInfo.cameraManager.openCamera(currentCameraId,object :CameraDevice.StateCallback(){
                override fun onOpened(camera: CameraDevice) {
                    if (InitProvider.DEBUG){
                        Log.e("camera","on camera opened")
                    }
                    mCameraDeviceFuture?.set(camera)
                    createCaptureSession()
                }

                override fun onClosed(camera: CameraDevice) {
                    if (InitProvider.DEBUG){
                        Log.e("camera","on camera closed")
                    }
                }

                override fun onDisconnected(camera: CameraDevice) {
                    if (InitProvider.DEBUG){
                        Log.e("camera","on camera disconnected")
                    }
                    mCameraDeviceFuture?.set(camera)
                    closeCamera()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    if (InitProvider.DEBUG){
                        Log.e("camera","on camera error:$error")
                    }
                    mCameraDeviceFuture?.set(camera)
                    closeCamera()
                }
            }, mainHandler)
        }
    }

    /**
     * 检测是否支持闪光灯
     */
    @Throws(CameraAccessException::class)
    internal fun checkFlashAvailable():Boolean{
        val characteristic = CameraDeviceInfo.cameraManager.getCameraCharacteristics(currentCameraId)
        return characteristic.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)?:false
    }

    /**
     * 拍照
     */
    @MainThread
    internal fun captureImage(activity: Activity, storeFilePath:String,stateCallback:(Boolean,String)->Unit){
        val cameraDevice = mCameraDeviceFuture?.get()?:return
        val previewSession = mCameraPreviewSessionFuture?.get()?:return
        val mImageReader = mCaptureImageReaderFuture?.get()
        mImageReader?.setOnImageAvailableListener({
            val image = it.acquireLatestImage()
            CameraFunction.storeImageFile(image,storeFilePath)
            it.close()
            stateCallback(true,storeFilePath)
            previewSession.close()
            createCaptureSession()
        }, cameraHandler)
        val captureRequest = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
        captureRequest.apply {
            set(CaptureRequest.JPEG_ORIENTATION, CameraDeviceInfo.CAPTURE_ORIENTATION[activity.windowManager.defaultDisplay.rotation])
            set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            set(CaptureRequest.FLASH_MODE, if (openFlash) CameraMetadata.FLASH_MODE_TORCH else CameraMetadata.FLASH_MODE_OFF)
            addTarget(mImageReader!!.surface)
        }
        previewSession.capture(captureRequest.build(),object:CameraCaptureSession.CaptureCallback() {
            //拍照成功
            override fun onCaptureCompleted(session: CameraCaptureSession,request: CaptureRequest,result: TotalCaptureResult) {
                if (InitProvider.DEBUG){
                    Log.e("camera","capture image complete")
                }
            }

            //拍照失败
            override fun onCaptureFailed(session: CameraCaptureSession, request: CaptureRequest, failure: CaptureFailure) {
                if (InitProvider.DEBUG){
                    Log.e("camera","capture image fail reason:${failure.reason}")
                }
                stateCallback(false,storeFilePath)
            }
        }, mainHandler)
    }

    /**
     * 开始录制视频
     */
    internal fun startRecordVideo(videoRecorder: MediaRecorder){
        val cameraDevice = mCameraDeviceFuture?.get()?:return
        val previewSurface = mPreviewSurfaceFuture.get()?:return
        val previewSession = mCameraPreviewSessionFuture?.get()?:return
        previewSession.close()
        mCameraPreviewSessionFuture?.reset()
        cameraDevice.createCaptureSession(listOf(previewSurface,videoRecorder.surface),object: CameraCaptureSession.StateCallback(){
            override fun onConfigureFailed(session: CameraCaptureSession) {
                if (InitProvider.DEBUG){
                    Log.e("camera","on camera session configure failed")
                }
                session.close()
                createCaptureSession()
            }

            override fun onConfigured(session: CameraCaptureSession) {
                mCameraPreviewSessionFuture?.set(session)
                val previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
                previewRequestBuilder.apply {
                    addTarget(previewSurface)
                    addTarget(videoRecorder.surface)
                    set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                    set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                    set(CaptureRequest.FLASH_MODE, if (openFlash) CameraMetadata.FLASH_MODE_TORCH else CameraMetadata.FLASH_MODE_OFF)
                }
                session.setRepeatingRequest(previewRequestBuilder.build(),null,
                    cameraHandler
                )
                videoRecorder.start()
            }
        }, cameraHandler)
    }

    /**
     * 停止视频录制
     */
    internal fun stopRecordVideo(videoRecorder: MediaRecorder){
        videoRecorder.stop()
        videoRecorder.reset()
        mCameraPreviewSessionFuture?.get()?.close()
        createCaptureSession()
    }

    /**
     * 打开或关闭闪光灯
     */
    @Throws(IllegalAccessException::class)
    internal fun openFlash(open:Boolean){
        if (!checkFlashAvailable()) throw IllegalAccessException("access flash fail")
        openFlash = open
        startPreview()
    }

    /**
     * 是否前置摄像头开启
     */
    internal fun isFrontCameraOpened() = currentCameraId == CameraDeviceInfo.frontCameraId

    /**
     * 获取视频录制大小
     */
    internal fun getVideoOutputSize():Size{
        val previewSize = mPreviewSurfaceSizeFuture.get()?: Size(InitProvider.screenWidth,InitProvider.screenHeight)
        return CameraFunction.getCameraOutputSize(currentCameraId,previewSize.width,previewSize.height,MediaRecorder::class.java)
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    fun resume(){
        if (ActivityCompat.checkSelfPermission(InitProvider.applicationContext, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED){
            openCamera(currentCameraId == CameraDeviceInfo.frontCameraId)
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    fun pause(){
        closeCamera()
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun destroy(){
        destroyCamera()
    }

}