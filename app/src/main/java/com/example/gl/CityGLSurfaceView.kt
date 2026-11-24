package com.example.gl

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import com.example.simulation.*
import kotlin.math.*

@SuppressLint("ViewConstructor")
class CityGLSurfaceView(
    context: Context,
    private val sim: CitySimulation,
    onFrameTick: (rawDt: Float) -> Unit,
    private val onTileAction: (gx: Int, gy: Int, isTap: Boolean) -> Unit,
    private val onZeusTap: (wx: Float, wz: Float) -> Unit
) : GLSurfaceView(context) {

    val renderer: CityRenderer

    var activeTool: BuildTool = BuildTool.ROAD

    private var lastX1 = 0f
    private var lastY1 = 0f
    private var downX1 = 0f
    private var downY1 = 0f
    private var movedTooFar = false
    private var lastTileKey = -1

    private var prevPinchDist = 0f
    private var prevPinchAngle = 0f
    private var prevMidX = 0f
    private var prevMidY = 0f
    private var twoFingerActive = false

    init {
        setEGLContextClientVersion(2)
        renderer = CityRenderer(sim, onFrameTick)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX1 = event.x
                lastY1 = event.y
                downX1 = event.x
                downY1 = event.y
                movedTooFar = false
                twoFingerActive = false
                lastTileKey = -1

                if (!sim.walkMode && activeTool == BuildTool.ZEUS) {
                    val world = renderer.screenToWorldXZ(event.x, event.y)
                    if (world != null) {
                        onZeusTap(world.first, world.second)
                    }
                    return true
                }

                if (!sim.walkMode && activeTool != BuildTool.CAMERA) {
                    val tile = renderer.screenToTile(event.x, event.y)
                    if (tile != null) {
                        updateHover(tile.first, tile.second)
                        lastTileKey = tile.second * GRID_SIZE + tile.first
                        onTileAction(tile.first, tile.second, false)
                    }
                }
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) {
                    twoFingerActive = true
                    prevPinchDist = hypot(event.getX(1) - event.getX(0), event.getY(1) - event.getY(0))
                    prevPinchAngle = atan2(event.getY(1) - event.getY(0), event.getX(1) - event.getX(0))
                    prevMidX = (event.getX(0) + event.getX(1)) * 0.5f
                    prevMidY = (event.getY(0) + event.getY(1)) * 0.5f
                    renderer.showHover = false
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2 && twoFingerActive) {
                    val dist = hypot(event.getX(1) - event.getX(0), event.getY(1) - event.getY(0))
                    val ang = atan2(event.getY(1) - event.getY(0), event.getX(1) - event.getX(0))
                    val mx = (event.getX(0) + event.getX(1)) * 0.5f
                    val my = (event.getY(0) + event.getY(1)) * 0.5f

                    synchronized(sim.lock) {
                        if (!sim.walkMode) {
                            if (prevPinchDist > 10f && dist > 10f) {
                                sim.camDist = (sim.camDist * prevPinchDist / dist).coerceIn(18f, 340f)
                            }
                            var dAng = ang - prevPinchAngle
                            while (dAng > Math.PI) dAng -= (Math.PI * 2.0).toFloat()
                            while (dAng < -Math.PI) dAng += (Math.PI * 2.0).toFloat()
                            sim.camYaw -= dAng
                            val dy = my - prevMidY
                            val dx = mx - prevMidX
                            if (abs(dAng) < 0.06f) {
                                panOrbital(dx, dy)
                            }
                        } else if (prevPinchDist > 10f && dist > 10f) {
                            sim.walkCamDist = (sim.walkCamDist * prevPinchDist / dist).coerceIn(3f, 18f)
                        }
                    }

                    prevPinchDist = dist
                    prevPinchAngle = ang
                    prevMidX = mx
                    prevMidY = my
                    return true
                }

                val dx = event.x - lastX1
                val dy = event.y - lastY1
                lastX1 = event.x
                lastY1 = event.y
                if (hypot(event.x - downX1, event.y - downY1) > 14f) {
                    movedTooFar = true
                }

                if (sim.walkMode) {
                    synchronized(sim.lock) {
                        sim.walkCamYaw -= dx * 0.006f
                        sim.walkCamPitch = (sim.walkCamPitch + dy * 0.004f).coerceIn(0.05f, 1.2f)
                    }
                    return true
                }

                if (activeTool == BuildTool.CAMERA || activeTool == BuildTool.ZEUS) {
                    synchronized(sim.lock) {
                        panOrbital(dx, dy)
                    }
                    return true
                }

                val tile = renderer.screenToTile(event.x, event.y)
                if (tile != null) {
                    updateHover(tile.first, tile.second)
                    val key = tile.second * GRID_SIZE + tile.first
                    if (key != lastTileKey) {
                        lastTileKey = key
                        onTileAction(tile.first, tile.second, false)
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!twoFingerActive && !movedTooFar && !sim.walkMode && activeTool == BuildTool.CAMERA) {
                    val tile = renderer.screenToTile(event.x, event.y)
                    if (tile != null) {
                        onTileAction(tile.first, tile.second, true)
                    }
                }
                twoFingerActive = false
                renderer.showHover = false
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                if (event.pointerCount <= 2) {
                    twoFingerActive = false
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun panOrbital(dx: Float, dy: Float) {
        val k = sim.camDist * 0.0018f
        val s = sin(sim.camYaw)
        val c = cos(sim.camYaw)
        sim.camX = (sim.camX - c * dx * k - s * dy * k).coerceIn(-280f, 280f)
        sim.camZ = (sim.camZ + s * dx * k - c * dy * k).coerceIn(-280f, 280f)
    }

    private fun updateHover(gx: Int, gy: Int) {
        synchronized(sim.lock) {
            val isBig = activeTool.key in BIG_TOOLS
            val c = sim.grid[gy][gx]
            val eraOk = eraOfCiv(sim.civ) >= activeTool.minEra
            val valid = when {
                activeTool == BuildTool.ERASE -> c != null
                isBig -> eraOk && sim.bigFootprintValid(gx, gy) != null && sim.coins >= activeTool.cost
                activeTool == BuildTool.ROAD && sim.isWater(gx, gy) -> c == null && sim.inUsable(gx, gy) && sim.coins >= BRIDGE_COST
                activeTool == BuildTool.TREN -> eraOk && sim.inUsable(gx, gy) && !sim.isWater(gx, gy) && sim.coins >= activeTool.cost &&
                    (c == null || (c.t == "road" && c.k == "" && !c.br && c.rx.isEmpty()))
                activeTool == BuildTool.ESTACION -> eraOk && sim.inUsable(gx, gy) && !sim.isWater(gx, gy) && sim.coins >= activeTool.cost &&
                    (c == null || (c.t == "rail" && !c.st))
                else -> eraOk && c == null && sim.inUsable(gx, gy) && !sim.isWater(gx, gy) && sim.coins >= activeTool.cost
            }
            renderer.hoverX = gx
            renderer.hoverY = gy
            renderer.hoverValid = valid
            renderer.hoverBig = isBig
            renderer.showHover = activeTool != BuildTool.CAMERA && activeTool != BuildTool.ZEUS
        }
    }
}
