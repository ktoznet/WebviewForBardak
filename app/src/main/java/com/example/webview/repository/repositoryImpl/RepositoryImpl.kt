package com.example.webview.repository.repositoryImpl

import android.content.Context
import android.util.Log
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.android.installreferrer.api.ReferrerDetails
import com.example.webview.repository.model.Quadruple
import com.example.webview.repository.model.Quintuple
import com.example.webview.utils.Constant.URL
import com.example.webview.utils.SharedPref
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.zip
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class RepositoryImpl @Inject constructor(
    private val sharedPreferences: SharedPref,
    @ApplicationContext private val context: Context,
    private val scope: CoroutineScope,
    private val referrerClient :InstallReferrerClient
) {
    val gaidFlow: Flow<String?> = flow {
        val googleAdvertisingId = sharedPreferences.getParam("GAID")

        if (googleAdvertisingId.isNotEmpty()) {
            emit(googleAdvertisingId)
        } else {
            runCatching {
                val advertisingId: String?
                val idInfo: AdvertisingIdClient.Info =
                    AdvertisingIdClient.getAdvertisingIdInfo(context)
                advertisingId = idInfo.id
                advertisingId?.let {
                    sharedPreferences.setParam("GAID", advertisingId)
                }
                emit(advertisingId)
            }.onFailure { e ->
                val errorString = "not_collected: Failure ${e.message}"
                sharedPreferences.setParam("GAID", errorString)
                emit(errorString)
            }
        }

    }.flowOn(Dispatchers.IO)

    val installReferrerFlow: Flow<ReferrerDetails?> = callbackFlow {
        val listener = object : InstallReferrerStateListener {
            override fun onInstallReferrerSetupFinished(responseCode: Int) {
                when (responseCode) {
                    InstallReferrerClient.InstallReferrerResponse.OK -> {
                        val response = referrerClient.installReferrer
                        trySend(response).isSuccess
                    }
                    else -> {
                        trySend(null).isSuccess
                    }
                }
                referrerClient.endConnection()
            }

            override fun onInstallReferrerServiceDisconnected() {
                // #TODO Обработка отключения сервиса, если необходимо
            }
        }
        referrerClient.startConnection(listener)
        awaitClose { referrerClient.endConnection() }
    }.flowOn(Dispatchers.IO)



    val countryCodeFlow: Flow<String?> = flow {
        val countryCode = sharedPreferences.getParam("locale")
        if (countryCode.isNotEmpty()) {
            emit(countryCode)
        } else {
            runCatching {
                val cc: String? = Locale.getDefault().language
                cc?.let {
                    sharedPreferences.setParam("locale", cc)
                }
                emit(cc)
            }.onFailure { e ->
                val errorString = "not_collected: Failure ${e.message}"
                sharedPreferences.setParam("locale", errorString)
                emit(errorString)
            }
        }
    }.flowOn(Dispatchers.IO)


    val firebaseAnalyticsFlow: Flow<String> = flow {
        val instanceId = suspendCancellableCoroutine<String> { continuation ->
            FirebaseAnalytics.getInstance(context).appInstanceId.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val result = task.result ?: "Error: null result"
                    continuation.resume(result)
                } else {
                    continuation.resumeWithException(
                        task.exception ?: CancellationException("Unknown Firebase Analytics error")
                    )
                }
            }.addOnCanceledListener {
                continuation.cancel(CancellationException("Firebase Analytics request was cancelled"))
            }
        }
        emit(instanceId)
    }.flowOn(Dispatchers.IO)

    val fcmTokenFlow: Flow<String?> = flow {
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            emit(token)
        } catch (e: Exception) {
            emit(null)
        }
    }.flowOn(Dispatchers.IO)

    val combinedFlow = gaidFlow.zip(installReferrerFlow) { gaid, referrer ->
        Pair(gaid, referrer)
    }.zip(countryCodeFlow) { pair, countryCode ->
        Triple(pair.first, pair.second, countryCode)
    }.zip(firebaseAnalyticsFlow) { triple, firebaseAnalyticsId ->
        Quadruple(triple.first, triple.second, triple.third, firebaseAnalyticsId)
    }.zip(fcmTokenFlow) { quadruple, fcmToken ->
        Quintuple(quadruple.first, quadruple.second, quadruple.third, quadruple.fourth, fcmToken)
    }

    fun encodeForUrl(value: Any): String {
        return try {
            URLEncoder.encode(value.toString(), StandardCharsets.UTF_8.toString())
        } catch (e: Exception) {
            "unknown"
        }
    }

    fun configSettings() {
        scope.launch {
            combinedFlow.collect { combinedData ->
                val encodedGaid = encodeForUrl(combinedData.first ?: "unknown")
                val encodedInstallReferrer = encodeForUrl(combinedData.second ?: "unknown")
                val encodedCountryCode = encodeForUrl(combinedData.third ?: "unknown")
                val encodedFirebaseAnalyticsId = encodeForUrl(combinedData.fourth ?: "unknown")
                val encodedFcmToken = encodeForUrl(combinedData.fifth ?: "unknown")

                val url = "$URL?gaid=$encodedGaid&referrer=$encodedInstallReferrer&countryCode=$encodedCountryCode&firebaseId=$encodedFirebaseAnalyticsId&fcmToken=$encodedFcmToken"
                sharedPreferences.setParam("Url", url)
                Log.d("MayLogi", sharedPreferences.getParam("Url"))
            }
        }
    }



    val data: Flow<String> = flow {
        sharedPreferences.apply {
            if (getParam("Url").isEmpty()) {
                delay(5000)
                val link = getParam("Url")
                setParam("full_path", link)
                Log.d("MayLogin", getParam("full_path"))
                emit(link)
            } else {
                val link = getParam("Url")
                setParam("full_path", link)
                Log.d("MayLogin", getParam("full_path"))
                emit(link)
            }
        }

    }.flowOn(Dispatchers.IO)
}