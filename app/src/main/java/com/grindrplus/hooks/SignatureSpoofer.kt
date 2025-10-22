package com.grindrplus.hooks

import android.content.ContextWrapper
import com.grindrplus.core.Constants.GRINDR_PACKAGE_NAME
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.callbacks.XC_LoadPackage

private const val packageSignature = "823f5a17c33b16b4775480b31607e7df35d67af8"
private const val firebaseInstallationServiceClient =
    "com.google.firebase.installations.remote.FirebaseInstallationServiceClient"
private const val configRealtimeHttpClient =
    "com.google.firebase.remoteconfig.internal.ConfigRealtimeHttpClient"
private const val configFetchHttpClient =
    "com.google.firebase.remoteconfig.internal.ConfigFetchHttpClient"

fun spoofSignatures(param: XC_LoadPackage.LoadPackageParam) {

    listOf(
        firebaseInstallationServiceClient,
        configRealtimeHttpClient,
        configFetchHttpClient
    ).forEach { className ->
        findAndHookMethod(
            className,
            param.classLoader,
            "getFingerprintHashForPackage",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.result = packageSignature
                }
            })
    }

    findAndHookMethod(
        "ly.img.android.c",
        param.classLoader,
        "d", // getPackageName
        object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                param.result = GRINDR_PACKAGE_NAME
            }
        })

    findAndHookMethod(
        "com.facebook.login.KatanaProxyLoginMethodHandler",
        param.classLoader,
        "tryAuthorize",
        XposedHelpers.findClass("com.facebook.login.LoginClient\$Request", param.classLoader),
        object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                param.result = 0
            }
        }
    )

    if (param.packageName != GRINDR_PACKAGE_NAME) {
        fun isFirebaseInstallationServiceClient() = Thread.currentThread().stackTrace.any {
            it.className.startsWith("com.google.firebase.installations.remote.FirebaseInstallationServiceClient")
        }

        findAndHookMethod(
            ContextWrapper::class.java,
            "getPackageName",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (isFirebaseInstallationServiceClient()) {
                        param.result = GRINDR_PACKAGE_NAME
                    }
                }
            }
        )

        findAndHookMethod(
            "com.google.firebase.messaging.Metadata",
            param.classLoader,
            "getPackageInfo",
            String::class.java,  // packageName
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if ((param.args[0] as String).contains("grindr")) {
                        param.args[0] = GRINDR_PACKAGE_NAME
                    }
                }
            }
        )
    }
}