package com.junglerun.game.game

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.os.Build
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.junglerun.game.gfx.AndroidGfx
import kotlin.math.abs

/**
 * SurfaceView hosting the game loop: fixed-step simulation (120 Hz) + render every frame.
 * Multi-touch: on-screen JUMP / SLIDE / ability buttons, plus tap-anywhere = jump and swipes,
 * so two players can share one device.
 */
@SuppressLint("ViewConstructor")
class GameView(context: Context, private val controller: GameController) :
    SurfaceView(context), SurfaceHolder.Callback {

    private val renderer = GameRenderer()
    private val gfx = AndroidGfx()
    private val viewports = List(controller.worlds.size) { Box() }
    private val styles: List<ViewportStyle>
    @Volatile private var running = false
    private var thread: Thread? = null

    init {
        holder.addCallback(this)
        isFocusable = true
        styles = if (controller.worlds.size == 2) {
            listOf(
                ViewportStyle("P1 • ${controller.players[0].name}", 0xFF29B6F6.toInt(), showPause = true),
                ViewportStyle("P2 • ${controller.players[1].name}", 0xFFFF7043.toInt(), showPause = false),
            )
        } else {
            listOf(ViewportStyle())
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) = start()
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        controller.layout(width, height, viewports)
    }
    override fun surfaceDestroyed(holder: SurfaceHolder) = stop()

    private fun start() {
        if (running) return
        running = true
        controller.layout(width, height, viewports)
        thread = Thread(::loop, "JungleRun-GameLoop").also { it.start() }
    }

    fun stop() {
        running = false
        thread?.let {
            try {
                it.join(500)
            } catch (_: InterruptedException) {
            }
        }
        thread = null
    }

    private fun loop() {
        val step = 1f / 120f
        var last = System.nanoTime()
        var acc = 0f
        while (running) {
            val now = System.nanoTime()
            acc += ((now - last) / 1_000_000_000.0).toFloat().coerceAtMost(0.1f)
            last = now
            while (acc >= step) {
                controller.update(step)
                acc -= step
            }
            render()
            val frameNanos = System.nanoTime() - now
            val sleepMs = (16_000_000L - frameNanos) / 1_000_000L
            if (sleepMs > 0) {
                try {
                    Thread.sleep(sleepMs)
                } catch (_: InterruptedException) {
                }
            }
        }
    }

    private fun render() {
        val h = holder
        if (!h.surface.isValid) return
        val canvas: Canvas = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) h.lockHardwareCanvas() else h.lockCanvas()
        } catch (e: Exception) {
            null
        } ?: return
        try {
            canvas.drawColor(0xFF0B3D20.toInt())
            gfx.begin(canvas)
            controller.worlds.forEachIndexed { i, w ->
                val style = if (i == 0 && controller.ghost != null) {
                    ViewportStyle(styles[0].label, styles[0].labelColor, styles[0].showPause, controller.ghost)
                } else {
                    styles[i]
                }
                renderer.draw(gfx, w, viewports[i], style)
            }
        } finally {
            try {
                h.unlockCanvasAndPost(canvas)
            } catch (_: Exception) {
            }
        }
    }

    // ---------------------------------------------------------------- input

    private class TouchTrack(var x: Float, var y: Float, var player: Int, var consumed: Boolean)

    private val touches = HashMap<Int, TouchTrack>()
    private val hit = Box()

    private fun playerAt(y: Float): Int {
        for (i in viewports.indices) if (y >= viewports[i].t && y <= viewports[i].b) return i
        return 0
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val swipe = 28f * resources.displayMetrics.density
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = e.actionIndex
                val x = e.getX(idx)
                val y = e.getY(idx)
                val p = playerAt(y)
                val vp = viewports[p]
                when {
                    renderer.button(Button.JUMP, vp, hit).contains(x, y) -> controller.jump(p)
                    renderer.button(Button.SLIDE, vp, hit).contains(x, y) -> controller.slide(p)
                    renderer.button(Button.ABILITY, vp, hit).contains(x, y) -> controller.ability(p)
                    styles[p].showPause && renderer.button(Button.PAUSE, vp, hit).contains(x, y) -> {
                        controller.setPaused(true)
                        post { controllerCallbacks?.onPauseRequested() }
                    }
                    else -> {
                        val track = TouchTrack(x, y, p, false)
                        touches[e.getPointerId(idx)] = track
                        // A press that hasn't turned into a swipe within ~90 ms is a jump.
                        postDelayed({
                            if (!track.consumed) {
                                track.consumed = true
                                controller.jump(track.player)
                            }
                        }, TAP_DECIDE_MS)
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) {
                    val t = touches[e.getPointerId(i)] ?: continue
                    if (t.consumed) continue
                    val dy = e.getY(i) - t.y
                    val dx = e.getX(i) - t.x
                    if (abs(dy) > swipe && abs(dy) > abs(dx)) {
                        if (dy > 0) controller.slide(t.player) else controller.jump(t.player)
                        t.consumed = true
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val idx = e.actionIndex
                val t = touches.remove(e.getPointerId(idx))
                if (t != null && !t.consumed) {
                    t.consumed = true
                    controller.jump(t.player)
                }
            }
            MotionEvent.ACTION_CANCEL -> touches.clear()
        }
        return true
    }

    /** Set by the hosting screen to receive pause-button taps on the UI thread. */
    var controllerCallbacks: GameCallbacks? = null

    companion object {
        private const val TAP_DECIDE_MS = 90L
    }
}
