package com.toktiklite.permission

import android.Manifest
import android.content.pm.PackageManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * Bridges WebView's page-triggered permission callbacks ([PermissionRequest],
 * [GeolocationPermissions.Callback]) to Android's runtime permission system. TikTok's web app
 * asks for camera/mic (recording, duets, live) and geolocation (nearby content, location
 * tagging) the same way it would in a real Chrome tab, so the app must be able to grant
 * whichever underlying OS permission that requires - never blanket-granting without checking.
 *
 * Must be constructed during the host Activity's onCreate, before it reaches STARTED, because
 * [ComponentActivity.registerForActivityResult] requires that.
 */
class PermissionManager(private val activity: ComponentActivity) {

    private var pendingWebRequest: PermissionRequest? = null
    private var pendingGeoRequest: PendingGeo? = null

    private data class PendingGeo(val origin: String, val callback: GeolocationPermissions.Callback)

    private val requestLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        pendingWebRequest?.let { request ->
            resolveWebRequest(request, grants)
            pendingWebRequest = null
        }
        pendingGeoRequest?.let { pending ->
            val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            pending.callback.invoke(pending.origin, granted, false)
            pendingGeoRequest = null
        }
    }

    fun handleWebViewPermissionRequest(request: PermissionRequest) {
        val androidPermissions = request.resources.mapNotNull { resource ->
            when (resource) {
                PermissionRequest.RESOURCE_VIDEO_CAPTURE -> Manifest.permission.CAMERA
                PermissionRequest.RESOURCE_AUDIO_CAPTURE -> Manifest.permission.RECORD_AUDIO
                else -> null // Protected media (RESOURCE_PROTECTED_MEDIA_ID) needs no OS permission.
            }
        }.distinct()

        if (androidPermissions.isEmpty()) {
            // Nothing we recognize needs an OS-level grant; deny rather than silently allow.
            request.deny()
            return
        }

        val missing = androidPermissions.filter { !isGranted(it) }
        if (missing.isEmpty()) {
            request.grant(androidPermissions.toTypedArray())
            return
        }

        pendingWebRequest = request
        requestLauncher.launch(missing.toTypedArray())
    }

    fun handleGeolocationPermissionRequest(origin: String, callback: GeolocationPermissions.Callback) {
        val fineGranted = isGranted(Manifest.permission.ACCESS_FINE_LOCATION)
        val coarseGranted = isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fineGranted || coarseGranted) {
            callback.invoke(origin, true, false)
            return
        }
        pendingGeoRequest = PendingGeo(origin, callback)
        requestLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    private fun resolveWebRequest(request: PermissionRequest, grants: Map<String, Boolean>) {
        val originallyRequested = request.resources.mapNotNull {
            when (it) {
                PermissionRequest.RESOURCE_VIDEO_CAPTURE -> Manifest.permission.CAMERA
                PermissionRequest.RESOURCE_AUDIO_CAPTURE -> Manifest.permission.RECORD_AUDIO
                else -> null
            }
        }
        val allGranted = originallyRequested.all { grants[it] == true || isGranted(it) }
        if (allGranted && originallyRequested.isNotEmpty()) {
            request.grant(request.resources)
        } else {
            request.deny()
        }
    }

    private fun isGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
    }
}
