package com.starspace.app.data

/** 记录应用是否在前台，用来决定要不要弹通知，避免正在看的时候被打扰。 */
object AppLifecycle {
    @Volatile
    var foreground: Boolean = false
}
