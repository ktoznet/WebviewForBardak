package com.example.webview.presentor.viewModel

import android.webkit.WebView
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.webview.repository.repositoryImpl.RepositoryImpl
import com.example.webview.utils.SharedPref
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
@ExperimentalCoroutinesApi
class MainViewModel @Inject constructor(
    private val repositoryImpl: RepositoryImpl,
    private val sharedPref: SharedPref,
    private val scope: CoroutineScope
): ViewModel() {
    private val _path = MutableStateFlow("")
    private val _data get() = repositoryImpl.data

    val data: Flow<String> get() = _data.transform { data ->
        _path.value = data

        emit(data)
    }
    val path get() = _path.value

    fun fetchSettings() = repositoryImpl.configSettings()


}