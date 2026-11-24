package com.example.gl

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.example.simulation.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*
import kotlin.random.Random

data class ScreenPoint(val x: Float, val y: Float, val visible: Boolean)

class CityRenderer(
    private val sim: CitySimulation,
    private val onFrameTick: (rawDt: Float) -> Unit
) : GLSurfaceView.Renderer {

    @Volatile var speedMultiplier: Int = 1
    @Volatile var hoverX: Int = -1
    @Volatile var hoverY: Int = -1
    @Volatile var hoverValid: Boolean = false
    @Volatile var hoverBig: Boolean = false
    @Volatile var showHover: Boolean = false

    private var program = 0
    private var uMvpLoc = 0
    private var uSunDirLoc = 0
    private var uSunColorLoc = 0
    private var uHemiIntensityLoc = 0
    private var uNightEmissiveLoc = 0
    private var uFogColorLoc = 0
    private var uFogStartLoc = 0
    private var uFogEndLoc = 0
    private var uCamPosLoc = 0

    private var aPosLoc = 0
    private var aNormLoc = 0
    private var aColLoc = 0
    private var aEmLoc = 0

    private var starProgram = 0
    private var uStarMvpLoc = 0
    private var uStarOpacityLoc = 0
    private var aStarPosLoc = 0
    private lateinit var starBuffer: FloatBuffer
    private val starCount = 600

    private val staticBatch = MeshBatch(262144)
    private val dynamicBatch = MeshBatch(131072)
    private var builtRevision = -1L
    private var lastSnowCover = -1f
    private var lastAutumn = -1f

    private val projMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val vpMatrix = FloatArray(16)
    private val invVpMatrix = FloatArray(16)

    @Volatile var viewportWidth = 1
    @Volatile var viewportHeight = 1
    private var lastTimeNs = System.nanoTime()
    private var clockSec = 0f

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)

        program = buildProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        uMvpLoc = GLES20.glGetUniformLocation(program, "uMvp")
        uSunDirLoc = GLES20.glGetUniformLocation(program, "uSunDir")
        uSunColorLoc = GLES20.glGetUniformLocation(program, "uSunColor")
        uHemiIntensityLoc = GLES20.glGetUniformLocation(program, "uHemiIntensity")
        uNightEmissiveLoc = GLES20.glGetUniformLocation(program, "uNightEmissive")
        uFogColorLoc = GLES20.glGetUniformLocation(program, "uFogColor")
        uFogStartLoc = GLES20.glGetUniformLocation(program, "uFogStart")
        uFogEndLoc = GLES20.glGetUniformLocation(program, "uFogEnd")
        uCamPosLoc = GLES20.glGetUniformLocation(program, "uCamPos")

        aPosLoc = GLES20.glGetAttribLocation(program, "aPos")
        aNormLoc = GLES20.glGetAttribLocation(program, "aNorm")
        aColLoc = GLES20.glGetAttribLocation(program, "aCol")
        aEmLoc = GLES20.glGetAttribLocation(program, "aEm")

        starProgram = buildProgram(STAR_VS, STAR_FS)
        uStarMvpLoc = GLES20.glGetUniformLocation(starProgram, "uMvp")
        uStarOpacityLoc = GLES20.glGetUniformLocation(starProgram, "uOpacity")
        aStarPosLoc = GLES20.glGetAttribLocation(starProgram, "aPos")

        val stars = FloatArray(starCount * 3)
        for (i in 0 until starCount) {
            val a = Random.nextFloat() * PI.toFloat() * 2f
            val b = acos(Random.nextFloat() * 0.9f + 0.06f)
            val r = 1400f
            stars[i * 3] = sin(b) * cos(a) * r
            stars[i * 3 + 1] = cos(b) * r
            stars[i * 3 + 2] = sin(b) * sin(a) * r
        }
        starBuffer = ByteBuffer.allocateDirect(stars.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        starBuffer.put(stars)
        starBuffer.flip()
        builtRevision = -1L
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportWidth = max(1, width)
        viewportHeight = max(1, height)
        GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
        val aspect = viewportWidth.toFloat() / viewportHeight.toFloat()
        Matrix.perspectiveM(projMatrix, 0, 54f, aspect, 0.8f, 2200f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val rawDt = ((now - lastTimeNs) / 1_000_000_000f).coerceIn(0f, 0.08f)
        lastTimeNs = now
        clockSec += rawDt * speedMultiplier.coerceAtLeast(1)

        onFrameTick(rawDt)

        val dayness: Float
        val sunAngle: Float
        val eraIdx: Int
        val lookAtX: Float
        val lookAtY: Float
        val lookAtZ: Float
        val camPosX: Float
        val camPosY: Float
        val camPosZ: Float
        val wxCloud: Float
        val wxRain: Float
        val wxFog: Float
        val wxTemp: Float
        val wxFlash: Float

        synchronized(sim.lock) {
            val cov = sim.wx.cover
            val aut = sim.wx.autumnWeight()
            if (builtRevision != sim.gridRevision || abs(cov - lastSnowCover) > 0.14f || abs(aut - lastAutumn) > 0.14f) {
                CityGeometry.buildStaticWorld(sim, staticBatch)
                builtRevision = sim.gridRevision
                lastSnowCover = cov
                lastAutumn = aut
            }
            CityGeometry.buildDynamicEntities(
                sim, dynamicBatch, clockSec,
                hoverX, hoverY, hoverValid, hoverBig, showHover && !sim.walkMode
            )

            dayness = sim.dayness()
            sunAngle = sim.sunAngle()
            eraIdx = eraOfCiv(sim.civ)
            wxCloud = sim.wx.cloud
            wxRain = sim.wx.rain
            wxFog = sim.wx.fog
            wxTemp = sim.wx.temp
            wxFlash = sim.wx.flash

            if (!sim.walkMode) {
                val cp = cos(sim.camPitch)
                val sp = sin(sim.camPitch)
                lookAtX = sim.camX; lookAtY = 0f; lookAtZ = sim.camZ
                camPosX = sim.camX + sim.camDist * sin(sim.camYaw) * cp
                camPosY = sim.camDist * sp
                camPosZ = sim.camZ + sim.camDist * cos(sim.camYaw) * cp
            } else {
                val wy = sim.walkY + sim.walkJy * 0.6f
                val cp = cos(sim.walkCamPitch)
                val sp = sin(sim.walkCamPitch)
                lookAtX = sim.walkX; lookAtY = wy + 1.65f; lookAtZ = sim.walkZ
                camPosX = sim.walkX + sim.walkCamDist * sin(sim.walkCamYaw) * cp
                camPosY = wy + 1.65f + sim.walkCamDist * sp
                camPosZ = sim.walkZ + sim.walkCamDist * cos(sim.walkCamYaw) * cp
            }
        }

        Matrix.setLookAtM(viewMatrix, 0, camPosX, camPosY, camPosZ, lookAtX, lookAtY, lookAtZ, 0f, 1f, 0f)
        Matrix.multiplyMM(vpMatrix, 0, projMatrix, 0, viewMatrix, 0)
        Matrix.invertM(invVpMatrix, 0, vpMatrix, 0)

        // Sky & weather modulation
        val skyHex = ERAS[eraIdx].skyHex
        var dayR = ((skyHex shr 16) and 0xFF) / 255f
        var dayG = ((skyHex shr 8) and 0xFF) / 255f
        var dayB = (skyHex and 0xFF) / 255f
        if (eraIdx != 4) {
            val ovF = max(wxCloud * 0.6f, wxRain * 0.85f).coerceIn(0f, 1f)
            dayR += (0.50f - dayR) * ovF
            dayG += (0.54f - dayG) * ovF
            dayB += (0.60f - dayB) * ovF
            val fgF = (wxFog * 0.9f).coerceIn(0f, 1f)
            dayR += (0.72f - dayR) * fgF
            dayG += (0.76f - dayG) * fgF
            dayB += (0.80f - dayB) * fgF
        }
        val nightR = 11f / 255f
        val nightG = 16f / 255f
        val nightB = 48f / 255f
        val kSky = dayness.pow(0.8f)
        var skyR = nightR + (dayR - nightR) * kSky
        var skyG = nightG + (dayG - nightG) * kSky
        var skyB = nightB + (dayB - nightB) * kSky
        if (wxFlash > 0.01f) {
            val fl = (wxFlash * 0.65f).coerceIn(0f, 0.75f)
            skyR = (skyR + (1f - skyR) * fl).coerceIn(0f, 1f)
            skyG = (skyG + (1f - skyG) * fl).coerceIn(0f, 1f)
            skyB = (skyB + (1f - skyB) * fl).coerceIn(0f, 1f)
        }

        GLES20.glClearColor(skyR, skyG, skyB, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        val starOp = ((1f - dayness * 1.75f) * (1f - wxCloud * 0.85f)).coerceIn(0f, 1f)
        if (starOp > 0.02f) {
            GLES20.glUseProgram(starProgram)
            GLES20.glUniformMatrix4fv(uStarMvpLoc, 1, false, vpMatrix, 0)
            GLES20.glUniform1f(uStarOpacityLoc, starOp)
            GLES20.glEnableVertexAttribArray(aStarPosLoc)
            GLES20.glVertexAttribPointer(aStarPosLoc, 3, GLES20.GL_FLOAT, false, 12, starBuffer)
            GLES20.glDrawArrays(GLES20.GL_POINTS, 0, starCount)
            GLES20.glDisableVertexAttribArray(aStarPosLoc)
        }

        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(uMvpLoc, 1, false, vpMatrix, 0)

        val sx = cos(sunAngle) * 0.7f
        val sy = abs(sin(sunAngle)) * 0.9f + 0.35f
        val sz = 0.45f
        val sLen = sqrt(sx * sx + sy * sy + sz * sz)
        GLES20.glUniform3f(uSunDirLoc, sx / sLen, sy / sLen, sz / sLen)

        val heat = if (wxTemp > 31f && eraIdx != 4) min(0.18f, (wxTemp - 31f) * 0.02f) else 0f
        val sunR = (0.45f + 0.55f * dayness + heat).coerceIn(0f, 1.2f)
        val sunG = 0.55f + 0.40f * dayness
        val sunB = (0.85f + 0.05f * dayness - heat * 0.6f).coerceIn(0.3f, 1f)
        val cloudDim = 1f - 0.52f * max(wxCloud, wxRain)
        val sunInt = (0.28f + 0.85f * dayness) * cloudDim + wxFlash * 0.9f
        GLES20.glUniform3f(uSunColorLoc, sunR * sunInt, sunG * sunInt, sunB * sunInt)
        GLES20.glUniform1f(uHemiIntensityLoc, (0.38f + 0.44f * dayness) * (1f - 0.18f * wxRain) + wxFlash * 0.4f)

        val nightEmissive = if (eraIdx == 4) 1f else ((1f - dayness * 1.25f).coerceIn(0.08f, 1f))
        GLES20.glUniform1f(uNightEmissiveLoc, nightEmissive)
        GLES20.glUniform3f(uFogColorLoc, skyR, skyG, skyB)
        val fgF = if (eraIdx == 4) 0f else wxFog.coerceIn(0f, 0.95f)
        GLES20.glUniform1f(uFogStartLoc, 240f - 205f * fgF)
        GLES20.glUniform1f(uFogEndLoc, 980f - 690f * fgF)
        GLES20.glUniform3f(uCamPosLoc, camPosX, camPosY, camPosZ)

        drawBatch(staticBatch)
        drawBatch(dynamicBatch)
    }

    private fun drawBatch(batch: MeshBatch) {
        val vCount = batch.vertexCount
        if (vCount <= 0) return
        val buf = batch.toFloatBuffer()
        val stride = MeshBatch.FLOATS_PER_VERTEX * 4

        buf.position(0)
        GLES20.glEnableVertexAttribArray(aPosLoc)
        GLES20.glVertexAttribPointer(aPosLoc, 3, GLES20.GL_FLOAT, false, stride, buf)

        buf.position(3)
        GLES20.glEnableVertexAttribArray(aNormLoc)
        GLES20.glVertexAttribPointer(aNormLoc, 3, GLES20.GL_FLOAT, false, stride, buf)

        buf.position(6)
        GLES20.glEnableVertexAttribArray(aColLoc)
        GLES20.glVertexAttribPointer(aColLoc, 3, GLES20.GL_FLOAT, false, stride, buf)

        buf.position(9)
        GLES20.glEnableVertexAttribArray(aEmLoc)
        GLES20.glVertexAttribPointer(aEmLoc, 1, GLES20.GL_FLOAT, false, stride, buf)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vCount)

        GLES20.glDisableVertexAttribArray(aPosLoc)
        GLES20.glDisableVertexAttribArray(aNormLoc)
        GLES20.glDisableVertexAttribArray(aColLoc)
        GLES20.glDisableVertexAttribArray(aEmLoc)
    }

    fun screenToWorldXZ(screenX: Float, screenY: Float): Pair<Float, Float>? {
        val w = viewportWidth.toFloat()
        val h = viewportHeight.toFloat()
        if (w < 2f || h < 2f) return null
        val ndcX = (screenX / w) * 2f - 1f
        val ndcY = 1f - (screenY / h) * 2f

        val nearNdc = floatArrayOf(ndcX, ndcY, -1f, 1f)
        val farNdc = floatArrayOf(ndcX, ndcY, 1f, 1f)
        val nearWorld = FloatArray(4)
        val farWorld = FloatArray(4)
        Matrix.multiplyMV(nearWorld, 0, invVpMatrix, 0, nearNdc, 0)
        Matrix.multiplyMV(farWorld, 0, invVpMatrix, 0, farNdc, 0)
        if (abs(nearWorld[3]) < 1e-5f || abs(farWorld[3]) < 1e-5f) return null

        val nx = nearWorld[0] / nearWorld[3]
        val ny = nearWorld[1] / nearWorld[3]
        val nz = nearWorld[2] / nearWorld[3]
        val fx = farWorld[0] / farWorld[3]
        val fy = farWorld[1] / farWorld[3]
        val fz = farWorld[2] / farWorld[3]

        val dy = fy - ny
        if (abs(dy) < 1e-4f) return null
        val t = -ny / dy
        if (t < 0f) return null

        return (nx + (fx - nx) * t) to (nz + (fz - nz) * t)
    }

    fun screenToTile(screenX: Float, screenY: Float): Pair<Int, Int>? {
        val (wx, wz) = screenToWorldXZ(screenX, screenY) ?: return null
        val gx = sim.x2g(wx)
        val gy = sim.x2g(wz)
        return if (sim.inBounds(gx, gy)) gx to gy else null
    }

    fun worldToScreen(wx: Float, wy: Float, wz: Float): ScreenPoint {
        val inV = floatArrayOf(wx, wy, wz, 1f)
        val outV = FloatArray(4)
        Matrix.multiplyMV(outV, 0, vpMatrix, 0, inV, 0)
        if (outV[3] <= 0.01f) return ScreenPoint(0f, 0f, false)
        val ndcX = outV[0] / outV[3]
        val ndcY = outV[1] / outV[3]
        val ndcZ = outV[2] / outV[3]
        if (ndcZ > 1f) return ScreenPoint(0f, 0f, false)
        val sx = (ndcX * 0.5f + 0.5f) * viewportWidth
        val sy = (-ndcY * 0.5f + 0.5f) * viewportHeight
        return ScreenPoint(sx, sy, true)
    }

    private fun buildProgram(vsCode: String, fsCode: String): Int {
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vsCode)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fsCode)
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, vs)
        GLES20.glAttachShader(prog, fs)
        GLES20.glLinkProgram(prog)
        return prog
    }

    private fun compileShader(type: Int, code: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, code)
        GLES20.glCompileShader(shader)
        return shader
    }

    companion object {
        private const val VERTEX_SHADER = """
            uniform mat4 uMvp;
            attribute vec3 aPos;
            attribute vec3 aNorm;
            attribute vec3 aCol;
            attribute float aEm;
            varying vec3 vPos;
            varying vec3 vNorm;
            varying vec3 vCol;
            varying float vEm;
            void main() {
                vPos = aPos;
                vNorm = aNorm;
                vCol = aCol;
                vEm = aEm;
                gl_Position = uMvp * vec4(aPos, 1.0);
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform vec3 uSunDir;
            uniform vec3 uSunColor;
            uniform float uHemiIntensity;
            uniform float uNightEmissive;
            uniform vec3 uFogColor;
            uniform float uFogStart;
            uniform float uFogEnd;
            uniform vec3 uCamPos;
            varying vec3 vPos;
            varying vec3 vNorm;
            varying vec3 vCol;
            varying float vEm;
            void main() {
                vec3 n = normalize(vNorm);
                float ndotl = max(dot(n, uSunDir), 0.0);
                float hemi = mix(0.65, 1.05, n.y * 0.5 + 0.5) * uHemiIntensity;
                vec3 lit = vCol * (hemi + uSunColor * ndotl);
                vec3 emissiveColor = vCol * 1.25;
                vec3 color = mix(lit, max(lit, emissiveColor), vEm * uNightEmissive);
                float dist = distance(vPos, uCamPos);
                float fog = clamp((dist - uFogStart) / (uFogEnd - uFogStart), 0.0, 1.0);
                gl_FragColor = vec4(mix(color, uFogColor, fog), 1.0);
            }
        """

        private const val STAR_VS = """
            uniform mat4 uMvp;
            attribute vec3 aPos;
            void main() {
                gl_Position = uMvp * vec4(aPos, 1.0);
                gl_PointSize = 3.0;
            }
        """

        private const val STAR_FS = """
            precision mediump float;
            uniform float uOpacity;
            void main() {
                gl_FragColor = vec4(1.0, 1.0, 1.0, uOpacity);
            }
        """
    }
}
