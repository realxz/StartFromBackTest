package com.example.realxz.startfromback

import androidx.annotation.Keep
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * @author real xz
 * @date 2019-11-29
 */
class HookMain : IXposedHookLoadPackage {
    @Keep
    @Throws(Throwable::class)
    override fun handleLoadPackage(param: XC_LoadPackage.LoadPackageParam) {
        if ("com.slanissue.apps.mobile.erge" == param.packageName) {
            XposedHelpers.findAndHookMethod(
                "com.beva.BevaVideo.Bean.VIPInfoBean",
                param.classLoader,
                "getIs_vip",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        param.result = "Y"
                    }
                }

            )
            XposedHelpers.findAndHookMethod(
                "com.beva.BevaVideo.Bean.VIPInfoBean",
                param.classLoader,
                "getEnd_time",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        param.result = 1606634437
                    }
                }

            )
        }
    }

}