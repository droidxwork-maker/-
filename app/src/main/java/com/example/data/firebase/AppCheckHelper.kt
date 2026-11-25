package com.example.data.firebase

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory

private const val TAG = "AppCheckInit"

private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val PROVIDED_DEBUG_TOKEN_FLAG = "ai_studio_debug_token_provided"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }

        if (BuildConfig.DEBUG) {
            val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)
            val persistenceKey = FirebaseApp.getInstance().persistenceKey
            val prefs = context.getSharedPreferences(
                "$DEBUG_STORE_PREFIX$persistenceKey",
                Context.MODE_PRIVATE
            )

            if (!debugToken.isNullOrEmpty()) {
                // CRITICAL: synchronous commit() ensures token is written before DebugAppCheckProviderFactory reads it
                prefs.edit()
                    .putString(DEBUG_SECRET_KEY, debugToken)
                    .putBoolean(PROVIDED_DEBUG_TOKEN_FLAG, true)
                    .commit()
                Log.d(TAG, "App Check debug secret configured successfully.")
            }

            if (prefs.getBoolean(PROVIDED_DEBUG_TOKEN_FLAG, false)) {
                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
                Log.d(TAG, "DebugAppCheckProviderFactory installed.")
            } else {
                // Real device debug builds launched outside the test harness do not receive
                // FIREBASE_APPCHECK_DEBUG_TOKEN; use reCAPTCHA Enterprise so AI Logic calls succeed.
                Log.d(TAG, "No harness debug token provided; falling back to reCAPTCHA App Check.")
                installRecaptchaAppCheck(context)
            }
        } else {
            installRecaptchaAppCheck(context)
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}

private fun installRecaptchaAppCheck(context: Context) {
    val resId = context.resources.getIdentifier(
        RECAPTCHA_SITE_KEY_RES,
        "string",
        context.packageName
    )
    if (resId != 0) {
        val siteKey = context.getString(resId)
        if (siteKey.isNotBlank()) {
            val appCheck = FirebaseAppCheck.getInstance()
            appCheck.installAppCheckProviderFactory(
                RecaptchaAppCheckProviderFactory.getInstance(siteKey)
            )
            // Pre-warm reCAPTCHA Enterprise client and token in background so the first AI request does not stall.
            appCheck.getAppCheckToken(false).addOnFailureListener { e ->
                Log.w(TAG, "Warmup reCAPTCHA App Check token failed: ${e.message}")
            }
            Log.d(TAG, "RecaptchaAppCheckProviderFactory installed and warmup started.")
        } else {
            Log.e(TAG, "recaptcha_site_key resource string is blank.")
        }
    } else {
        Log.e(TAG, "recaptcha_site_key string resource not found in package.")
    }
}
