package com.Skyhelp.tasker.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 悬浮键盘服务（UhidService）的运行状态总线。
 *
 * 为什么需要它：主页那个开关按钮要显示**真实状态**，而不是「上次点了什么」——
 * 服务可能被系统回收、也可能被通知栏关掉，只靠 UI 本地状态会不准。
 * 所以由服务自己在 onCreate / onDestroy 里写，UI 侧 collect。
 */
object ServiceState {

    private val _running = MutableStateFlow(false)

    /** true = 悬浮键盘服务正在运行（悬浮窗已挂上） */
    val running: StateFlow<Boolean> = _running.asStateFlow()

    fun setRunning(value: Boolean) {
        _running.value = value
    }
}
