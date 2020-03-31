package com.zack.camera

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri


/**
 * @Author zack
 * @Date 2020/1/16
 * @Description 初始化类
 * @Version 1.0
 */
class InitProvider : ContentProvider() {

    companion object {
        lateinit var  applicationContext: Context

        var screenWidth:Int = 1080

        var screenHeight:Int = 1920

        var DEBUG = true
    }

    override fun onCreate(): Boolean {
        applicationContext = this.context!!.applicationContext
        screenWidth = this.context!!.resources.displayMetrics.widthPixels
        screenHeight = this.context!!.resources.displayMetrics.heightPixels
        return true
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun query(uri: Uri,projection: Array<out String>?,selection: String?,
                       selectionArgs: Array<out String>?,sortOrder: String?): Cursor? = null

    override fun update(uri: Uri,values: ContentValues?,
        selection: String?,selectionArgs: Array<out String>?): Int = 0

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun getType(uri: Uri): String? = null
}