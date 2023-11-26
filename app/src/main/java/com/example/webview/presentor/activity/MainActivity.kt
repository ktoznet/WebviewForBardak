package com.example.webview.presentor.activity

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PorterDuff
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.example.webview.R
import com.example.webview.presentor.viewModel.MainViewModel
import com.example.webview.utils.SharedPref
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val mainViewModel: MainViewModel by viewModels()

    @Inject
    lateinit var scope: CoroutineScope

    @Inject
    lateinit var shared: SharedPref

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private var mFilePathCallback: ValueCallback<Array<Uri>>? = null
    private val launcher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data: Intent? = result.data
        var results: Array<Uri>? = null

        when (result.resultCode) {
            Activity.RESULT_OK -> {

                data?.let {
                    val dataString = it.dataString
                    dataString?.let { string ->
                        results = arrayOf(Uri.parse(string))
                    }
                }
            }

            Activity.RESULT_CANCELED -> {
                // The user canceled the operation.
            }
        }
        mFilePathCallback?.onReceiveValue(results)
        mFilePathCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val splashLayout = FrameLayout(this)
        splashLayout.setBackgroundResource(R.drawable.background)
        val progressBar = ProgressBar(this)
        val progressBarLayoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        )
        progressBar.layoutParams = progressBarLayoutParams
        splashLayout.addView(progressBar)
        setContentView(splashLayout)
        mainViewModel.fetchSettings()
        mainViewModel
            .data
            .take(1)
            .onEach { data ->
                setContentView(web())
            }
            .launchIn(lifecycleScope)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun web(): FrameLayout {
        handleOnBackPressed()
        val frameLayout = FrameLayout(applicationContext)

        webView = WebView(this@MainActivity).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setupWebView()
            progressBar = ProgressBar(
                context,
                null,
                android.R.attr.progressBarStyleHorizontal
            ).apply {

                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    16
                )
                progressDrawable.setColorFilter(android.graphics.Color.BLUE, PorterDuff.Mode.SRC_IN)
                isIndeterminate = true

            }
            webViewClient = object : WebViewClient() {

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)

                    progressBar.visibility = View.VISIBLE
                }

                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    if (url != null) {
                        view?.loadUrl(url)
                        return true
                    }
                    return false
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)

                    progressBar.visibility = View.GONE

                }
            }
            webChromeClient = object : WebChromeClient() {

                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {

                    if (mFilePathCallback != null) {
                        mFilePathCallback?.onReceiveValue(null)
                    }
                    mFilePathCallback = filePathCallback

                    val contentSelectionIntent = Intent(Intent.ACTION_GET_CONTENT)
                    contentSelectionIntent.addCategory(Intent.CATEGORY_OPENABLE)
                    contentSelectionIntent.type = "*/*"

                    val chooserIntent = Intent(Intent.ACTION_CHOOSER)
                    chooserIntent.putExtra(Intent.EXTRA_INTENT, contentSelectionIntent)
                    chooserIntent.putExtra(Intent.EXTRA_TITLE, "File Chooser")
                    launcher.launch(chooserIntent)
                    return true
                }
            }
        }
        frameLayout.addView(webView)
        frameLayout.addView(progressBar)
        loadUrl(mainViewModel.path.toString())
        return frameLayout
    }

    private fun loadUrl(url: String) {
        scope.launch {
            val newPath = shared.getParam("urlNew")
            withContext(Dispatchers.Main) {
                if (newPath.isEmpty()) {
                    webView.loadUrl(url)
                } else {
                    webView.loadUrl(newPath)
                }
            }
        }
    }


    private fun handleOnBackPressed() {

        onBackPressedDispatcher?.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (webView.canGoBack()) {
                        webView.goBack()
                    }
                }
            }
        )
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun WebView.setupWebView() {

        CookieManager
            .getInstance()
            .setAcceptThirdPartyCookies(
                this,
                true
            )

        this.settings.apply {
            javaScriptEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            domStorageEnabled = true
            databaseEnabled = true
            allowContentAccess = true
            allowFileAccess = true
            javaScriptCanOpenWindowsAutomatically = true
            mediaPlaybackRequiresUserGesture = false
        }
    }

    override fun onStop() {
        super.onStop()
        if (::webView.isInitialized) {
            webView.url?.let {
                try {
                    val url = URL(it)
                    val baseUrl = "${url.protocol}://${url.authority}"
                    scope.launch {
                        shared.setParam("pathNew", baseUrl)
                    }
                } catch (e: Exception) {
                    Log.d("D", "Error parsing URL in onStop: ${e.message}")
                }
            }
        }
    }


    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.clearHistory()
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.removeAllViews()
            webView.destroy()
        }
        super.onDestroy()
    }
}