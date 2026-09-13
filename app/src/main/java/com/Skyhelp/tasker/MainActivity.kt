package com.Skyhelp.tasker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.Skyhelp.tasker.core.AppLog
import com.Skyhelp.tasker.ui.navigation.SkytaskerApp
import com.Skyhelp.tasker.ui.theme.SkytaskerTheme

/**
 * 唯一的 Activity：Compose 承载 OOBE 与主界面。
 * 悬浮窗仍在 [UhidService]（保持 View 体系）。
 */
class MainActivity : ComponentActivity() {

    /** Activity 级 ViewModel：onResume 与 Compose 共用同一实例 */
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // targetSdk 35+ 强制 edge-to-edge，不开会让内容顶到状态栏底下
        enableEdgeToEdge()
        AppLog.i("App 启动")

        setContent {
            SkytaskerTheme {
                SkytaskerApp(vm)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 从系统设置页（悬浮窗授权等）返回时刷新 Shizuku / 权限状态
        vm.refresh()
    }
}
