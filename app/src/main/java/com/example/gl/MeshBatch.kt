package com.example.gl

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Dynamic interleaved vertex buffer:
 * [x, y, z, nx, ny, nz, r, g, b, emissive] = 10 floats per vertex, triangles (3 verts per tri).
 */
class MeshBatch(initialFloats: Int = 65536) {
    var data = FloatArray(initialFloats)
        private set
    var floatCount = 0
        private set
    val vertexCount: Int get() = floatCount / FLOATS_PER_VERTEX

    private var nativeBuffer: FloatBuffer = allocateDirect(initialFloats)

    fun clear() {
        floatCount = 0
    }

    private fun allocateDirect(floats: Int): FloatBuffer =
        ByteBuffer.allocateDirect(floats * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

    private fun ensureFloats(additional: Int) {
        val needed = floatCount + additional
        if (needed > data.size) {
            var next = data.size * 2
            while (next < needed) next *= 2
            data = data.copyOf(next)
        }
    }

    fun toFloatBuffer(): FloatBuffer {
        if (nativeBuffer.capacity() < floatCount) {
            nativeBuffer = allocateDirect(max(floatCount, data.size))
        }
        nativeBuffer.clear()
        nativeBuffer.put(data, 0, floatCount)
        nativeBuffer.flip()
        return nativeBuffer
    }

    fun pushVertex(
        x: Float, y: Float, z: Float,
        nx: Float, ny: Float, nz: Float,
        r: Float, g: Float, b: Float,
        em: Float
    ) {
        ensureFloats(FLOATS_PER_VERTEX)
        val i = floatCount
        data[i] = x; data[i + 1] = y; data[i + 2] = z
        data[i + 3] = nx; data[i + 4] = ny; data[i + 5] = nz
        data[i + 6] = r; data[i + 7] = g; data[i + 8] = b
        data[i + 9] = em
        floatCount = i + FLOATS_PER_VERTEX
    }

    fun pushTri(
        x1: Float, y1: Float, z1: Float,
        x2: Float, y2: Float, z2: Float,
        x3: Float, y3: Float, z3: Float,
        nx: Float, ny: Float, nz: Float,
        r: Float, g: Float, b: Float,
        em: Float
    ) {
        pushVertex(x1, y1, z1, nx, ny, nz, r, g, b, em)
        pushVertex(x2, y2, z2, nx, ny, nz, r, g, b, em)
        pushVertex(x3, y3, z3, nx, ny, nz, r, g, b, em)
    }

    fun addQuad(
        x1: Float, y1: Float, z1: Float,
        x2: Float, y2: Float, z2: Float,
        x3: Float, y3: Float, z3: Float,
        x4: Float, y4: Float, z4: Float,
        nx: Float, ny: Float, nz: Float,
        color: Int,
        em: Float = 0f
    ) {
        val r = ((color shr 16) and 0xFF) / 255f
        val g = ((color shr 8) and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        pushTri(x1, y1, z1, x2, y2, z2, x3, y3, z3, nx, ny, nz, r, g, b, em)
        pushTri(x1, y1, z1, x3, y3, z3, x4, y4, z4, nx, ny, nz, r, g, b, em)
    }

    fun addPlaneXZ(cx: Float, y: Float, cz: Float, w: Float, d: Float, color: Int, em: Float = 0f) {
        val hw = w * 0.5f
        val hd = d * 0.5f
        addQuad(
            cx - hw, y, cz + hd,
            cx + hw, y, cz + hd,
            cx + hw, y, cz - hd,
            cx - hw, y, cz - hd,
            0f, 1f, 0f, color, em
        )
    }

    /**
     * Adds an axis-aligned box rotated around Y by [yawRad] around local pivot ([px], [py], [pz]).
     */
    fun addBox(
        cx: Float, cy: Float, cz: Float,
        w: Float, h: Float, d: Float,
        color: Int,
        em: Float = 0f,
        yawRad: Float = 0f,
        pivotX: Float = 0f,
        pivotZ: Float = 0f,
        limbPitchX: Float = 0f,
        limbPivotY: Float = 0f
    ) {
        val r = ((color shr 16) and 0xFF) / 255f
        val g = ((color shr 8) and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        val hw = w * 0.5f
        val hh = h * 0.5f
        val hd = d * 0.5f

        val cosY = cos(yawRad)
        val sinY = sin(yawRad)
        val cosX = cos(limbPitchX)
        val sinX = sin(limbPitchX)

        fun tx(lx: Float, ly: Float, lz: Float): Triple<Float, Float, Float> {
            // Optional X-axis rotation around limbPivotY
            var x = cx + lx
            var y = cy + ly
            var z = cz + lz
            if (limbPitchX != 0f) {
                val dy = y - limbPivotY
                val dz = z - cz
                y = limbPivotY + dy * cosX - dz * sinX
                z = cz + dy * sinX + dz * cosX
            }
            if (yawRad != 0f) {
                val dx = x - pivotX
                val dz = z - pivotZ
                x = pivotX + dx * cosY + dz * sinY
                z = pivotZ - dx * sinY + dz * cosY
            }
            return Triple(x, y, z)
        }

        fun tn(nx: Float, ny: Float, nz: Float): Triple<Float, Float, Float> {
            var y = ny
            var z = nz
            if (limbPitchX != 0f) {
                val ny2 = y * cosX - z * sinX
                val nz2 = y * sinX + z * cosX
                y = ny2; z = nz2
            }
            var x = nx
            if (yawRad != 0f) {
                val nx2 = x * cosY + z * sinY
                val nz2 = -x * sinY + z * cosY
                x = nx2; z = nz2
            }
            return Triple(x, y, z)
        }

        fun emitFace(
            p1: Triple<Float, Float, Float>, p2: Triple<Float, Float, Float>,
            p3: Triple<Float, Float, Float>, p4: Triple<Float, Float, Float>,
            n: Triple<Float, Float, Float>
        ) {
            pushTri(p1.first, p1.second, p1.third, p2.first, p2.second, p2.third, p3.first, p3.second, p3.third, n.first, n.second, n.third, r, g, b, em)
            pushTri(p1.first, p1.second, p1.third, p3.first, p3.second, p3.third, p4.first, p4.second, p4.third, n.first, n.second, n.third, r, g, b, em)
        }

        val v000 = tx(-hw, -hh, -hd)
        val v100 = tx(hw, -hh, -hd)
        val v110 = tx(hw, hh, -hd)
        val v010 = tx(-hw, hh, -hd)
        val v001 = tx(-hw, -hh, hd)
        val v101 = tx(hw, -hh, hd)
        val v111 = tx(hw, hh, hd)
        val v011 = tx(-hw, hh, hd)

        emitFace(v001, v101, v111, v011, tn(0f, 0f, 1f))   // +Z front
        emitFace(v100, v000, v010, v110, tn(0f, 0f, -1f))  // -Z back
        emitFace(v101, v100, v110, v111, tn(1f, 0f, 0f))   // +X right
        emitFace(v000, v001, v011, v010, tn(-1f, 0f, 0f))  // -X left
        emitFace(v011, v111, v110, v010, tn(0f, 1f, 0f))   // +Y top
        emitFace(v000, v100, v101, v001, tn(0f, -1f, 0f))  // -Y bottom
    }

    fun addCylinder(
        cx: Float, cy: Float, cz: Float,
        rTop: Float, rBot: Float, h: Float,
        segs: Int, color: Int, em: Float = 0f,
        yawRad: Float = 0f, pivotX: Float = 0f, pivotZ: Float = 0f
    ) {
        val r = ((color shr 16) and 0xFF) / 255f
        val g = ((color shr 8) and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        val hh = h * 0.5f
        val cosY = cos(yawRad)
        val sinY = sin(yawRad)

        fun rot(x: Float, z: Float): Pair<Float, Float> {
            if (yawRad == 0f) return x to z
            val dx = x - pivotX
            val dz = z - pivotZ
            return (pivotX + dx * cosY + dz * sinY) to (pivotZ - dx * sinY + dz * cosY)
        }

        for (i in 0 until segs) {
            val a0 = (i.toFloat() / segs) * (PI.toFloat() * 2f)
            val a1 = ((i + 1).toFloat() / segs) * (PI.toFloat() * 2f)
            val am = (a0 + a1) * 0.5f
            val c0 = cos(a0); val s0 = sin(a0)
            val c1 = cos(a1); val s1 = sin(a1)
            val (nx, nz) = rot(cos(am), sin(am))

            val (bx0, bz0) = rot(cx + c0 * rBot, cz + s0 * rBot)
            val (bx1, bz1) = rot(cx + c1 * rBot, cz + s1 * rBot)
            val (tx0, tz0) = rot(cx + c0 * rTop, cz + s0 * rTop)
            val (tx1, tz1) = rot(cx + c1 * rTop, cz + s1 * rTop)
            val yBot = cy - hh
            val yTop = cy + hh

            pushTri(bx0, yBot, bz0, bx1, yBot, bz1, tx1, yTop, tz1, nx, 0.15f, nz, r, g, b, em)
            pushTri(bx0, yBot, bz0, tx1, yTop, tz1, tx0, yTop, tz0, nx, 0.15f, nz, r, g, b, em)

            if (rTop > 0.001f) {
                val (tcx, tcz) = rot(cx, cz)
                pushTri(tcx, yTop, tcz, tx0, yTop, tz0, tx1, yTop, tz1, 0f, 1f, 0f, r, g, b, em)
            }
        }
    }

    fun addCone(
        cx: Float, cy: Float, cz: Float,
        radius: Float, h: Float,
        segs: Int, color: Int, em: Float = 0f,
        yawRad: Float = 0f, pivotX: Float = 0f, pivotZ: Float = 0f
    ) {
        addCylinder(cx, cy, cz, 0f, radius, h, segs, color, em, yawRad, pivotX, pivotZ)
    }

    fun addGable(
        cx: Float, cyBase: Float, cz: Float,
        w: Float, h: Float, d: Float,
        color: Int, yawRad: Float = 0f, pivotX: Float = 0f, pivotZ: Float = 0f
    ) {
        val hw = w * 0.5f
        val hd = d * 0.5f
        val r = ((color shr 16) and 0xFF) / 255f
        val g = ((color shr 8) and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        val cosY = cos(yawRad)
        val sinY = sin(yawRad)

        fun pt(lx: Float, ly: Float, lz: Float): Triple<Float, Float, Float> {
            val x0 = cx + lx
            val y0 = cyBase + ly
            val z0 = cz + lz
            if (yawRad == 0f) return Triple(x0, y0, z0)
            val dx = x0 - pivotX
            val dz = z0 - pivotZ
            return Triple(pivotX + dx * cosY + dz * sinY, y0, pivotZ - dx * sinY + dz * cosY)
        }

        val fl = pt(-hw, 0f, hd)
        val fr = pt(hw, 0f, hd)
        val ft = pt(0f, h, hd)
        val bl = pt(-hw, 0f, -hd)
        val br = pt(hw, 0f, -hd)
        val bt = pt(0f, h, -hd)

        // Front & back gables
        pushTri(fl.first, fl.second, fl.third, fr.first, fr.second, fr.third, ft.first, ft.second, ft.third, 0f, 0.2f, 1f, r, g, b, 0f)
        pushTri(br.first, br.second, br.third, bl.first, bl.second, bl.third, bt.first, bt.second, bt.third, 0f, 0.2f, -1f, r, g, b, 0f)
        // Left slope
        pushTri(bl.first, bl.second, bl.third, fl.first, fl.second, fl.third, ft.first, ft.second, ft.third, -0.7f, 0.7f, 0f, r, g, b, 0f)
        pushTri(bl.first, bl.second, bl.third, ft.first, ft.second, ft.third, bt.first, bt.second, bt.third, -0.7f, 0.7f, 0f, r, g, b, 0f)
        // Right slope
        pushTri(fr.first, fr.second, fr.third, br.first, br.second, br.third, bt.first, bt.second, bt.third, 0.7f, 0.7f, 0f, r, g, b, 0f)
        pushTri(fr.first, fr.second, fr.third, bt.first, bt.second, bt.third, ft.first, ft.second, ft.third, 0.7f, 0.7f, 0f, r, g, b, 0f)
    }

    companion object {
        const val FLOATS_PER_VERTEX = 10
    }
}
