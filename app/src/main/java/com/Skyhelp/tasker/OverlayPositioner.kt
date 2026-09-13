package com.Skyhelp.tasker

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Rect
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.PathInterpolator
import kotlin.math.roundToInt

/**
 * 悬浮窗位置管理。
 *
 * 以「相对可用区的比例」存储位置，屏幕尺寸变化时换算成新的绝对坐标并裁剪到屏内，
 * 避免横竖屏切换后悬浮窗跑到屏幕外。
 *
 * - 拖动过程中只改绝对坐标（跟手），松手时反算比例并持久化。
 * - 旋转/尺寸变化时用保存的比例重算坐标，平滑动画归位。
 */
class OverlayPositioner(
    private val wm: WindowManager,
    private val v: View,
    private val lp: WindowManager.LayoutParams
) {

    companion object {
        private const val TAG = "SkyUhid"
        private const val PREFS = "overlay_pos"
        private const val KEY_REL_X = "rel_x"
        private const val KEY_REL_Y = "rel_y"
        /** 默认位置：靠右、偏下（相对可用区的比例） */
        private const val DEFAULT_REL_X = 0.88f
        private const val DEFAULT_REL_Y = 0.60f
        /** 首次布局前拿不到 View 尺寸时的兜底值（dp） */
        private const val FALLBACK_SIZE_DP = 56
        private const val ANIM_MS = 280L
    }

    private val bounds = Rect()
    private val safeInsets = Rect()
    private var hasBounds = false
    private var animator: ValueAnimator? = null

    // ---------------------------------------------------------------- 尺寸缓存

    /** 刷新屏幕尺寸与 systemBars 安全区，返回是否发生变化（供去抖用） */
    fun refresh(): Boolean {
        val m = try {
            wm.currentWindowMetrics
        } catch (t: Throwable) {
            Log.w(TAG, "[POS] currentWindowMetrics 异常: $t")
            return false
        }
        val b = m.bounds
        val ins = m.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
        val changed = !hasBounds ||
                bounds.left != b.left || bounds.top != b.top ||
                bounds.right != b.right || bounds.bottom != b.bottom ||
                safeInsets.left != ins.left || safeInsets.top != ins.top ||
                safeInsets.right != ins.right || safeInsets.bottom != ins.bottom
        bounds.set(b.left, b.top, b.right, b.bottom)
        safeInsets.set(ins.left, ins.top, ins.right, ins.bottom)
        hasBounds = true
        return changed
    }

    private fun viewW(): Int = v.width.takeIf { it > 0 } ?: dp(FALLBACK_SIZE_DP)
    private fun viewH(): Int = v.height.takeIf { it > 0 } ?: dp(FALLBACK_SIZE_DP)

    private fun dp(value: Int): Int =
        (value * v.resources.displayMetrics.density).roundToInt()

    /** 可移动范围 = 可用区尺寸 - View 尺寸 */
    private fun rangeX(): Int =
        (bounds.width() - safeInsets.left - safeInsets.right - viewW()).coerceAtLeast(0)

    private fun rangeY(): Int =
        (bounds.height() - safeInsets.top - safeInsets.bottom - viewH()).coerceAtLeast(0)

    fun clampX(x: Int): Int = x.coerceIn(safeInsets.left, safeInsets.left + rangeX())
    fun clampY(y: Int): Int = y.coerceIn(safeInsets.top, safeInsets.top + rangeY())

    // ---------------------------------------------------------------- 比例换算

    /** 当前绝对坐标 → 相对可用区的比例 [0,1] */
    fun absToRel(): Pair<Float, Float> {
        val rx = if (rangeX() > 0) (lp.x - safeInsets.left).toFloat() / rangeX() else 0f
        val ry = if (rangeY() > 0) (lp.y - safeInsets.top).toFloat() / rangeY() else 0f
        return rx.coerceIn(0f, 1f) to ry.coerceIn(0f, 1f)
    }

    /** 相对比例 → 绝对坐标（已裁剪到屏内） */
    fun relToAbs(rx: Float, ry: Float): Pair<Int, Int> {
        val x = safeInsets.left + (rx.coerceIn(0f, 1f) * rangeX()).roundToInt()
        val y = safeInsets.top + (ry.coerceIn(0f, 1f) * rangeY()).roundToInt()
        return clampX(x) to clampY(y)
    }

    // ---------------------------------------------------------------- 应用位置

    /** 立即应用位置（拖动时用，无动画，保证跟手） */
    fun applyNow(x: Int, y: Int) {
        lp.x = x
        lp.y = y
        try {
            wm.updateViewLayout(v, lp)
        } catch (t: Throwable) {
            Log.w(TAG, "[POS] updateViewLayout 异常: $t")
        }
    }

    /** 平滑移动到目标位置（旋转归位/边界回弹用） */
    fun animateTo(tx: Int, ty: Int) {
        animator?.cancel()
        val sx = lp.x
        val sy = lp.y
        if (sx == tx && sy == ty) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ANIM_MS
            interpolator = PathInterpolator(0.2f, 0f, 0f, 1f) // FastOutSlowIn
            addUpdateListener { a ->
                val f = a.animatedFraction
                lp.x = (sx + (tx - sx) * f).roundToInt()
                lp.y = (sy + (ty - sy) * f).roundToInt()
                try {
                    wm.updateViewLayout(v, lp)
                } catch (_: Throwable) {
                    // 窗口可能已被移除，忽略
                }
            }
            start()
        }
    }

    fun cancelAnimation() {
        animator?.cancel()
        animator = null
    }

    // ---------------------------------------------------------------- 持久化

    /** 保存当前相对位置 */
    fun saveRel(ctx: Context) {
        val (rx, ry) = absToRel()
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putFloat(KEY_REL_X, rx)
            .putFloat(KEY_REL_Y, ry)
            .apply()
    }

    /** 读取上次保存的相对位置（无则默认值） */
    fun loadRel(ctx: Context): Pair<Float, Float> {
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return sp.getFloat(KEY_REL_X, DEFAULT_REL_X) to sp.getFloat(KEY_REL_Y, DEFAULT_REL_Y)
    }
}
