package com.example.webview.utils

import android.content.ClipDescription
import android.content.SharedPreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SharedPref @Inject constructor(
    private val sharedPreferences: SharedPreferences
) {

    suspend  fun setParam(param: String, value: String){
        sharedPreferences.edit().putString(param,value).apply()
    }
    suspend fun getParam(param: String):String{
        return sharedPreferences.getString(param,"")?: ""
    }
}