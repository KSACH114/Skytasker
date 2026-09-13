package com.Skyhelp.tasker.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.Skyhelp.tasker.MainViewModel
import com.Skyhelp.tasker.R
import com.Skyhelp.tasker.core.Prefs
import com.Skyhelp.tasker.ui.screens.AboutScreen
import com.Skyhelp.tasker.ui.screens.HomeScreen
import com.Skyhelp.tasker.ui.screens.PermissionOnboardingScreen
import com.Skyhelp.tasker.ui.screens.TutorialScreen
import com.Skyhelp.tasker.ui.screens.WelcomeScreen

private enum class HomeTab(val labelRes: Int, val icon: ImageVector) {
    HOME(R.string.tab_home, Icons.Filled.Home),
    TUTORIAL(R.string.tab_tutorial, Icons.AutoMirrored.Filled.List),
    ABOUT(R.string.tab_about, Icons.Filled.Info)
}

/** App 根：引导流程 / 权限页 / 主界面 */
@Composable
fun SkytaskerApp(vm: MainViewModel) {
    val context = LocalContext.current
    var oobeDone by rememberSaveable { mutableStateOf(Prefs.isOobeDone(context)) }
    var welcomeSeen by rememberSaveable { mutableStateOf(Prefs.isWelcomeSeen(context)) }
    // 从主界面主动进权限页（主页的「返回获取权限」按钮）
    var showPermPage by rememberSaveable { mutableStateOf(false) }

    // 进主界面就自动尝试开启悬浮键盘（首次走完引导、以及之后每次冷启动各一次）。
    // key 用 oobeDone：从权限页返回不会重跑，避免把用户手动关掉的悬浮窗又强行打开。
    LaunchedEffect(oobeDone) {
        if (oobeDone) vm.startOverlay()
    }

    when {
        !oobeDone -> OobeFlow(
            vm = vm,
            // 欢迎页看过了却没走完引导（比如权限没给全就退出 App）→ 下次直接停在权限页
            startAtPermission = welcomeSeen,
            onWelcomeNext = {
                Prefs.markWelcomeSeen(context)
                welcomeSeen = true
            },
            onFinish = {
                Prefs.markOobeDone(context)
                oobeDone = true
            }
        )

        showPermPage -> {
            // 系统返回键 = 回主界面（而不是退出 App）
            BackHandler { showPermPage = false }
            PermissionOnboardingScreen(
                vm = vm,
                onFinish = { showPermPage = false }
            )
        }

        else -> MainScaffold(
            vm = vm,
            onOpenPermissions = { showPermPage = true }
        )
    }
}

/** OOBE：欢迎页 → 权限引导页 */
@Composable
private fun OobeFlow(
    vm: MainViewModel,
    startAtPermission: Boolean,
    onWelcomeNext: () -> Unit,
    onFinish: () -> Unit
) {
    // 欢迎页看过了就跳过它，直接停在权限页
    var step by rememberSaveable { mutableIntStateOf(if (startAtPermission) 1 else 0) }
    AnimatedContent(
        targetState = step,
        label = "oobe",
        // edge-to-edge 下避开状态栏/导航栏
        modifier = Modifier.safeDrawingPadding()
    ) { s ->
        if (s == 0) {
            WelcomeScreen(onNext = {
                onWelcomeNext()
                step = 1
            })
        } else {
            PermissionOnboardingScreen(vm = vm, onFinish = onFinish)
        }
    }
}

/** 主界面：底栏 3 个 tab */
@Composable
private fun MainScaffold(vm: MainViewModel, onOpenPermissions: () -> Unit) {
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tabs = HomeTab.entries

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = tabIndex == index,
                        onClick = { tabIndex = index },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.labelRes)) }
                    )
                }
            }
        }
    ) { pad ->
        Box(modifier = Modifier.padding(pad)) {
            when (tabs[tabIndex]) {
                HomeTab.HOME -> HomeScreen(
                    vm = vm,
                    onGoTutorial = { tabIndex = HomeTab.TUTORIAL.ordinal },
                    onOpenPermissions = onOpenPermissions
                )

                HomeTab.TUTORIAL -> TutorialScreen()
                HomeTab.ABOUT -> AboutScreen()
            }
        }
    }
}
