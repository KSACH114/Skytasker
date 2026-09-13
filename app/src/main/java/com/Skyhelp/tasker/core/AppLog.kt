package com.Skyhelp.tasker.core

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

data class LogLine(
    val id: Long,
    val time: String,
    /** 'I' / 'W' / 'E' */
    val level: Char,
    val text: String
)

/**
 * 进程内日志总线。
 *
 * Service 与 Activity 同进程，直接共享这个单例即可，无需 binder。
 * UI 通过 collectAsStateWithLifecycle() 订阅。
 */
object AppLog {

    private const val TAG = "SkyUhid"

    /** 只保留最近 N 行，防止无限增长 */
    private const val MAX = 300

    private val _lines = MutableStateFlow<List<LogLine>>(emptyList())
    val lines: StateFlow<List<LogLine>> = _lines.asStateFlow()

    private val idGen = AtomicLong()

    /** SimpleDateFormat 非线程安全，加锁保护（日志量小，开销可忽略） */
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun i(msg: String) = add('I', msg)
    fun w(msg: String) = add('W', msg)
    fun e(msg: String) = add('E', msg)

    private fun add(level: Char, msg: String) {
        // 同时写 logcat，方便 adb logcat -s SkyUhid 调试
        when (level) {
            'E' -> Log.e(TAG, msg)
            'W' -> Log.w(TAG, msg)
            else -> Log.i(TAG, msg)
        }
        val time = synchronized(fmt) { fmt.format(Date()) }
        val line = LogLine(idGen.incrementAndGet(), time, level, msg)
        // StateFlow.update 走 CAS 重试，多线程（Service worker / Shizuku 回调）写入安全
        _lines.update { (it + line).takeLast(MAX) }
    }

    fun clear() {
        _lines.update { emptyList() }
    }
}
