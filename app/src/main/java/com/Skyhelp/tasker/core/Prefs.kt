package com.Skyhelp.tasker.core

import android.content.Context
import android.content.SharedPreferences

/**
 * 轻量偏好存储。
 * 只有 OOBE 完成标记这类极少量的数据，用 SharedPreferences 足够（不必上 DataStore）。
 */
object Prefs {

    private const val FILE = "skytasker_prefs"

    /** 带版本号：将来引导流程改版时可换成 v2 重新触发 */
    private const val KEY_OOBE_DONE = "oobe_done_v1"

    /**
     * 欢迎页（界面1）是否已看过。
     * 用途：权限没给全就退出 App 时，下次直接停在权限页（界面2），不必重看欢迎页。
     */
    private const val KEY_WELCOME_SEEN = "welcome_seen_v1"

    private fun sp(c: Context): SharedPreferences =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** 引导流程是否全部走完（走完才进主界面） */
    fun isOobeDone(c: Context): Boolean = sp(c).getBoolean(KEY_OOBE_DONE, false)

    fun markOobeDone(c: Context) {
        sp(c).edit().putBoolean(KEY_OOBE_DONE, true).apply()
    }

    /** 欢迎页（界面1）是否看过 */
    fun isWelcomeSeen(c: Context): Boolean = sp(c).getBoolean(KEY_WELCOME_SEEN, false)

    fun markWelcomeSeen(c: Context) {
        sp(c).edit().putBoolean(KEY_WELCOME_SEEN, true).apply()
    }
}
