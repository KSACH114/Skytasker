package com.Skyhelp.tasker

import android.content.Context
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import moe.shizuku.server.IRemoteProcess
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Shizuku 桥：免 root 方案的核心。
 *
 * App 自身不申请任何高危权限，通过 Shizuku 的 binder 以 shell（或 root）身份
 * 执行内嵌的 vkbd 二进制 —— shell 域自带 uhid 组（3011），Stage-1 已验证
 * 该域可以 open("/dev/uhid") 并创建虚拟键盘。
 *
 * 对最终用户：装 Shizuku App + 一次性授权即可，全程免 root。
 * 测试机（K）：SUI 兼容同一套 API，零改动。
 */
object ShizukuBridge {

    private const val TAG = "SkyUhid"
    /** vkbd 一次完整跑 create→Shift→destroy 约 1~2 秒 */
    private const val VKBD_TIMEOUT_SEC = 15L

    /** Shizuku 服务端是否活着（未装/未启动都是 false） */
    fun isAlive(): Boolean = try {
        Shizuku.pingBinder()
    } catch (t: Throwable) {
        Log.w(TAG, "[SHZ] pingBinder 异常: $t")
        false
    }

    /** 本 App 是否已获得 Shizuku 授权 */
    fun hasPermission(): Boolean = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (t: Throwable) {
        Log.w(TAG, "[SHZ] checkSelfPermission 异常: $t")
        false
    }

    /** Shizuku 版本过旧（v11 之前没有新权限模型） */
    fun isPreV11(): Boolean = try {
        Shizuku.isPreV11()
    } catch (t: Throwable) {
        true
    }

    fun requestPermission(requestCode: Int) {
        Shizuku.requestPermission(requestCode)
    }

    /** vkbd 在解压后的 native 库目录里的完整路径 */
    fun vkbdPath(ctx: Context): String =
        ctx.applicationInfo.nativeLibraryDir + "/libvkbd.so"

    /**
     * 通过 Shizuku 跑一次 vkbd（一次性 create→左 Shift→destroy）。
     * 返回 VKBD_OK / VKBD_FAIL 前缀 + 全部输出。
     */
    fun runVkbd(ctx: Context): String {
        val path = vkbdPath(ctx)
        Log.i(TAG, "[SHZ] runVkbd path=$path alive=${isAlive()} perm=${hasPermission()}")

        if (!isAlive()) return "VKBD_FAIL\nShizuku 服务未运行（未安装或未启动）。"
        if (!hasPermission()) return "VKBD_FAIL\nShizuku 未授权，请先点「⓪ 连接 Shizuku」完成授权。"
        if (isPreV11()) return "VKBD_FAIL\nShizuku 版本过旧（< v11），请升级 Shizuku。"

        val t0 = System.currentTimeMillis()
        val out = execAndWait(arrayOf(path), VKBD_TIMEOUT_SEC)
        val ms = System.currentTimeMillis() - t0

        val ok = out.startsWith("EXIT=0")
        return (if (ok) "VKBD_OK" else "VKBD_FAIL") + " (耗时 ${ms}ms)\n" + out
    }

    /**
     * 通过 Shizuku 执行命令并等待结束。返回 "EXIT=<code>" + stdout + stderr。
     * 在调用方线程阻塞执行，请勿在主线程调。
     *
     * v13 的 Shizuku.newProcess 是 private，标准姿势是拿 binder 转
     * IShizukuService AIDL 接口直接调 newProcess，返回 IRemoteProcess。
     */
    fun execAndWait(cmd: Array<String>, timeoutSec: Long): String {
        val rp: IRemoteProcess = try {
            val binder: IBinder = Shizuku.getBinder()
                ?: return "NOT_RUNNING\nShizuku binder 为 null（服务未运行）。"
            val svc = IShizukuService.Stub.asInterface(binder)
            svc.newProcess(cmd, null, null)
        } catch (t: Throwable) {
            Log.e(TAG, "[SHZ] newProcess 抛异常", t)
            return "EXC=${t.javaClass.simpleName}: ${t.message}"
        }

        val stdoutIs = pfdToStream(rp, stdout = true)
        val stderrIs = pfdToStream(rp, stdout = false)
        val stdout = readAll(stdoutIs, "stdout")
        val stderr = readAll(stderrIs, "stderr")

        val exit: Int = try {
            // AIDL 层的 waitForTimeout(long, TimeUnit名称字符串)
            val finished = rp.waitForTimeout(timeoutSec, TimeUnit.SECONDS.name)
            if (!finished) {
                rp.destroy()
                Log.e(TAG, "[SHZ] 进程超时 ${timeoutSec}s，已 destroy")
                return "TIMEOUT\n--stdout--\n$stdout\n--stderr--\n$stderr"
            }
            rp.exitValue()
        } catch (t: Throwable) {
            Log.e(TAG, "[SHZ] waitFor/exitValue 异常", t)
            return "EXC=${t.javaClass.simpleName}: ${t.message}\n--stdout--\n$stdout\n--stderr--\n$stderr"
        }

        Log.i(TAG, "[SHZ] exec exit=$exit cmd=${cmd.joinToString(" ")}")
        return "EXIT=$exit\n--stdout--\n$stdout\n--stderr--\n$stderr"
    }

    // ---------------------------------------------------------------- 内部工具

    private fun pfdToStream(rp: IRemoteProcess, stdout: Boolean): InputStream? = try {
        val pfd: ParcelFileDescriptor? = if (stdout) rp.inputStream else rp.errorStream
        pfd?.let { ParcelFileDescriptor.AutoCloseInputStream(it) }
    } catch (t: Throwable) {
        Log.w(TAG, "[SHZ] 获取 ${if (stdout) "stdout" else "stderr"} 流失败: $t")
        null
    }

    private fun readAll(`is`: InputStream?, tag: String): String {
        if (`is` == null) return ""
        val sb = StringBuilder()
        try {
            BufferedReader(InputStreamReader(`is`, Charsets.UTF_8)).use { r ->
                var line: String? = r.readLine()
                while (line != null) {
                    sb.append(line).append('\n')
                    line = r.readLine()
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "[SHZ] 读 $tag 流异常", t)
            sb.append("(读 $tag 出错: $t)\n")
        }
        return sb.toString().trim()
    }
}
