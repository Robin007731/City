package com.example.gl

import com.example.simulation.*
import kotlin.math.*

object CityGeometry {
    private val ROAD_COLORS = intArrayOf(
        0xFFB99A6E.toInt(), 0xFFA58F78.toInt(), 0xFF8D8A84.toInt(), 0xFF3A4046.toInt(), 0xFF0A1020.toInt()
    )
    private val SIDEWALK_COLORS = intArrayOf(
        0xFFA88A60.toInt(), 0xFF8F7B63.toInt(), 0xFFB3ADA0.toInt(), 0xFF9AA0A5.toInt(), 0xFF18233A.toInt()
    )
    private val FOREST_COLORS = intArrayOf(
        0xFF3F6B35.toInt(), 0xFF3F6B35.toInt(), 0xFF3F6B35.toInt(), 0xFF3F6B35.toInt(), 0xFF070B14.toInt()
    )
    private val TREE_LEAF_A = intArrayOf(
        0xFF2F7A3A.toInt(), 0xFF3A8040.toInt(), 0xFF4A7A45.toInt(), 0xFF3F6F4A.toInt(), 0xFF0F4A5A.toInt()
    )
    private val TREE_LEAF_B = intArrayOf(
        0xFF3A8A3A.toInt(), 0xFF4A8A3A.toInt(), 0xFF5A8A50.toInt(), 0xFF4A7A55.toInt(), 0xFF16707A.toInt()
    )

    private fun hash(x: Int, y: Int): Float {
        val n = sin(x * 127.1 + y * 311.7) * 43758.5453
        return (n - floor(n)).toFloat()
    }

    private fun tintColor(base: Int, delta: Float): Int {
        val r = (((base shr 16) and 0xFF) * (1f + delta)).toInt().coerceIn(0, 255)
        val g = (((base shr 8) and 0xFF) * (1f + delta)).toInt().coerceIn(0, 255)
        val b = ((base and 0xFF) * (1f + delta)).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun faceRoadYaw(sim: CitySimulation, x: Int, y: Int, seed: Double): Float {
        if (sim.isRoad(x + 1, y)) return PI.toFloat() / 2f
        if (sim.isRoad(x - 1, y)) return -PI.toFloat() / 2f
        if (sim.isRoad(x, y + 1)) return 0f
        if (sim.isRoad(x, y - 1)) return PI.toFloat()
        return (floor(seed * 4.0) * (PI / 2.0)).toFloat()
    }

    fun buildStaticWorld(sim: CitySimulation, batch: MeshBatch) {
        batch.clear()
        val e = eraOfCiv(sim.civ)
        val lo = sim.boundsLo()
        val hi = sim.boundsHi()
        val groundCol = seasonCol(ERAS[e].groundHex, 0, 0.5f, e, sim.wx)
        val forestCol = seasonCol(FOREST_COLORS[e], 0, 0.5f, e, sim.wx)

        // Large outer plane & 70x70 forest base
        batch.addPlaneXZ(0f, -0.25f, 0f, 3200f, 3200f, forestCol)
        val mapSpan = GRID_SIZE * TILE_SIZE
        batch.addPlaneXZ(0f, -0.05f, 0f, mapSpan, mapSpan, forestCol)

        // Winding river and lake
        DynamicEffectsGeometry.addRiverAndLakeStatic(sim, batch, e)

        // Playable territory ground tiles (skip river tiles so water shows cleanly)
        for (y in lo..hi) {
            for (x in lo..hi) {
                if (sim.isWater(x, y)) continue
                val h = hash(x, y) - 0.5f
                val cx = sim.gx2x(x)
                val cz = sim.gx2x(y)
                batch.addPlaneXZ(cx, 0f, cz, TILE_SIZE - 0.08f, TILE_SIZE - 0.08f, tintColor(groundCol, h * 0.10f))
            }
        }

        // Playable boundary frame
        val bSize = (hi - lo + 1) * TILE_SIZE
        val bCenter = sim.gx2x(lo) + bSize * 0.5f - TILE_SIZE * 0.5f
        val borderCol = if (e == 4) 0xFFFF4FD0.toInt() else 0xFFF0E2A8.toInt()
        val borderEm = if (e == 4) 0.9f else 0f
        val halfB = bSize * 0.5f
        batch.addBox(bCenter, 0.1f, bCenter - halfB, bSize, 0.18f, 0.35f, borderCol, borderEm)
        batch.addBox(bCenter, 0.1f, bCenter + halfB, bSize, 0.18f, 0.35f, borderCol, borderEm)
        batch.addBox(bCenter - halfB, 0.1f, bCenter, 0.35f, 0.18f, bSize, borderCol, borderEm)
        batch.addBox(bCenter + halfB, 0.1f, bCenter, 0.35f, 0.18f, bSize, borderCol, borderEm)

        // Distant mountain ring (16 peaks) + snow caps
        for (i in 0 until 16) {
            val a = i / 16f * PI.toFloat() * 2f + hash(i, 1) * 0.3f
            val r = 560f + hash(i, 2) * 140f
            val h = 130f + hash(i, 3) * 150f
            val mx = cos(a) * r
            val mz = sin(a) * r
            val col = when {
                e == 4 -> 0xFF1A223A.toInt()
                i % 3 != 0 -> 0xFF6F8795.toInt()
                else -> 0xFF7D93A0.toInt()
            }
            batch.addCone(mx, h * 0.5f - 8f, mz, h * 0.8f, h, 6, col)
            if (h > 220f && e != 4) {
                batch.addCone(mx, h * 0.82f - 8f, mz, h * 0.25f, h * 0.32f, 6, 0xFFF4F8FC.toInt())
            }
        }

        // Surrounding forest trees outside playable bounds & non-water
        for (gy in 0 until GRID_SIZE) {
            for (gx in 0 until GRID_SIZE) {
                if ((gx in lo..hi && gy in lo..hi) || sim.isWater(gx, gy)) continue
                val h = hash(gx * 2, gy * 3)
                if (h > 0.52f) {
                    val s = 0.85f + hash(gx * 3, gy) * 0.85f
                    val tx = sim.gx2x(gx) + (hash(gx, gy * 5) - 0.5f) * 6f
                    val tz = sim.gx2x(gy) + (hash(gy, gx * 7) - 0.5f) * 6f
                    if (nearWater(tx, tz, 4f)) continue
                    batch.addCylinder(tx, 1.1f * s, tz, 0.26f * s, 0.36f * s, 2.2f * s, 5, 0xFF5A4030.toInt())
                    val baseLeaf = if (h > 0.72f) TREE_LEAF_B[e] else TREE_LEAF_A[e]
                    val leaf = seasonCol(baseLeaf, if (h > 0.72f) 2 else 1, h, e, sim.wx)
                    batch.addCone(tx, 4.1f * s, tz, 1.85f * s, 4.4f * s, 6, leaf)
                }
            }
        }

        // Built cells
        for (y in 0 until GRID_SIZE) {
            for (x in 0 until GRID_SIZE) {
                val c = sim.grid[y][x] ?: continue
                val cx = sim.gx2x(x)
                val cz = sim.gx2x(y)
                when (c.t) {
                    "road" -> addRoad(sim, batch, x, y, cx, cz, e, c)
                    "rail" -> DynamicEffectsGeometry.addRailCell(sim, batch, x, y, cx, cz, e, c)
                    "house" -> addHouse(batch, cx, cz, e, c.s, faceRoadYaw(sim, x, y, c.s))
                    "b" -> {
                        if (c.big == 2) {
                            val bx = sim.gx2x(c.ox) + TILE_SIZE * 0.5f
                            val bz = sim.gx2x(c.oy) + TILE_SIZE * 0.5f
                            addBuilding(batch, bx, bz, c.k, e, c.s, faceRoadYaw(sim, x, y, c.s), scale = 1.9f)
                        } else {
                            addBuilding(batch, cx, cz, c.k, e, c.s, faceRoadYaw(sim, x, y, c.s), scale = 1f)
                        }
                    }
                }
            }
        }
    }

    private fun addRoad(sim: CitySimulation, batch: MeshBatch, x: Int, y: Int, cx: Float, cz: Float, e: Int, c: GridCell) {
        if (c.br || c.k == "ave" || c.k == "hwy") {
            DynamicEffectsGeometry.addSpecialRoad(sim, batch, x, y, cx, cz, e, c)
            return
        }
        val east = sim.isRoad(x + 1, y)
        val west = sim.isRoad(x - 1, y)
        val south = sim.isRoad(x, y + 1)
        val north = sim.isRoad(x, y - 1)
        val sw = SIDEWALK_COLORS[e]
        val rd = ROAD_COLORS[e]
        val lineCol = if (e == 4) 0xFF22E3F2.toInt() else if (e == 3) 0xFFE8E4D0.toInt() else 0xFFD8D2BF.toInt()
        val lineEm = if (e == 4) 1f else 0f

        // Sidewalk base
        batch.addPlaneXZ(cx, 0.03f, cz, TILE_SIZE, TILE_SIZE, sw)
        // Center road surface
        val rw = 5.4f
        batch.addPlaneXZ(cx, 0.05f, cz, rw, rw, rd)
        if (east) batch.addPlaneXZ(cx + 2.65f, 0.05f, cz, 2.7f, rw, rd)
        if (west) batch.addPlaneXZ(cx - 2.65f, 0.05f, cz, 2.7f, rw, rd)
        if (south) batch.addPlaneXZ(cx, 0.05f, cz + 2.65f, rw, 2.7f, rd)
        if (north) batch.addPlaneXZ(cx, 0.05f, cz - 2.65f, rw, 2.7f, rd)

        if (e >= 2) {
            if (east || west) {
                val lx = (if (west) -2f else 0f) + (if (east) 2f else 0f)
                val lw = (if (east && west) 7.4f else 4.2f)
                batch.addPlaneXZ(cx + lx * 0.45f, 0.07f, cz, lw, 0.22f, lineCol, lineEm)
            }
            if (south || north) {
                val lz = (if (north) -2f else 0f) + (if (south) 2f else 0f)
                val ld = (if (south && north) 7.4f else 4.2f)
                batch.addPlaneXZ(cx, 0.07f, cz + lz * 0.45f, 0.22f, ld, lineCol, lineEm)
            }
            // Streetlamps
            if ((x * 7 + y * 3) % 4 == 0) {
                val hh = if (e == 2) 2.6f else 5.0f
                val postCol = if (e == 4) 0xFF22E3F2.toInt() else 0xFF444A50.toInt()
                batch.addCylinder(cx + 3.2f, hh * 0.5f, cz + 3.2f, 0.08f, 0.11f, hh, 6, postCol, if (e == 4) 0.8f else 0f)
                batch.addBox(cx + 3.2f, hh + 0.2f, cz + 3.2f, 0.42f, 0.42f, 0.42f, 0xFFFFE9B0.toInt(), 1f)
            }
        }
        if (c.rx.isNotEmpty()) {
            DynamicEffectsGeometry.addSpecialRoad(sim, batch, x, y, cx, cz, e, c)
        }
    }

    private fun addHouse(batch: MeshBatch, cx: Float, cz: Float, e: Int, s: Double, yaw: Float) {
        val warmWin = 0xFFFFB85A.toInt()
        when (e) {
            0 -> {
                batch.addCylinder(cx, 1.1f, cz, 2.3f, 2.3f, 2.2f, 10, 0xFFA8794C.toInt())
                batch.addCone(cx, 3.5f, cz, 3.4f, 2.7f, 10, 0xFFD6B04C.toInt())
                batch.addBox(cx, 0.75f, cz + 2.28f, 0.9f, 1.5f, 0.14f, 0xFF4A3320.toInt(), 0f, yaw, cx, cz)
                batch.addBox(cx + 1.5f, 1.4f, cz + 1.7f, 0.45f, 0.45f, 0.12f, warmWin, 1f, yaw, cx, cz)
            }
            1 -> {
                batch.addBox(cx, 1.4f, cz, 5.2f, 2.8f, 4.4f, 0xFFE0CFAE.toInt(), 0f, yaw, cx, cz)
                batch.addGable(cx, 2.8f, cz, 6.2f, 2.3f, 5.2f, 0xFFB4503A.toInt(), yaw, cx, cz)
                batch.addBox(cx + 1.8f, 3.7f, cz - 1f, 0.7f, 1.8f, 0.7f, 0xFF7A5A48.toInt(), 0f, yaw, cx, cz)
                batch.addBox(cx + 1.2f, 0.8f, cz + 2.22f, 0.9f, 1.6f, 0.12f, 0xFF6B4A2F.toInt(), 0f, yaw, cx, cz)
                batch.addBox(cx - 1.3f, 1.6f, cz + 2.22f, 0.9f, 0.9f, 0.12f, warmWin, 1f, yaw, cx, cz)
            }
            2 -> {
                batch.addBox(cx, 2.3f, cz, 6.0f, 4.6f, 5.0f, 0xFFC4BDAE.toInt(), 0f, yaw, cx, cz)
                batch.addBox(cx, 0.45f, cz, 6.1f, 0.9f, 5.1f, 0xFF9A9484.toInt(), 0f, yaw, cx, cz)
                batch.addGable(cx, 4.6f, cz, 7.2f, 3.0f, 6.2f, 0xFF8A4B3B.toInt(), yaw, cx, cz)
                batch.addBox(cx, 1.0f, cz + 2.52f, 1.0f, 2.0f, 0.14f, 0xFF5B3D28.toInt(), 0f, yaw, cx, cz)
                batch.addBox(cx - 1.8f, 1.9f, cz + 2.52f, 0.9f, 1.0f, 0.12f, warmWin, 1f, yaw, cx, cz)
                batch.addBox(cx + 1.8f, 1.9f, cz + 2.52f, 0.9f, 1.0f, 0.12f, warmWin, 1f, yaw, cx, cz)
                batch.addBox(cx - 1.8f, 3.7f, cz + 2.52f, 0.9f, 1.0f, 0.12f, warmWin, 1f, yaw, cx, cz)
                batch.addBox(cx + 1.8f, 3.7f, cz + 2.52f, 0.9f, 1.0f, 0.12f, warmWin, 1f, yaw, cx, cz)
            }
            3 -> {
                val h = 9f + floor(s * 10.0).toFloat()
                val bodyCol = if (s > 0.5) 0xFFAAB4BE.toInt() else 0xFFB8B0A6.toInt()
                batch.addBox(cx, h * 0.5f, cz, 6.0f, h, 6.0f, bodyCol, 0f, yaw, cx, cz)
                batch.addBox(cx, h + 0.25f, cz, 6.4f, 0.5f, 6.4f, 0xFF7F8890.toInt(), 0f, yaw, cx, cz)
                batch.addBox(cx, 2.6f, cz + 3.5f, 2.4f, 0.18f, 1.2f, 0xFFE8A53A.toInt(), 0f, yaw, cx, cz)
                val floors = (h / 3f).toInt()
                for (fl in 1 until floors) {
                    val wy = fl * 3f
                    batch.addBox(cx - 1.6f, wy, cz + 3.04f, 1.2f, 0.8f, 0.08f, 0xFFFFD98A.toInt(), 1f, yaw, cx, cz)
                    batch.addBox(cx + 1.6f, wy, cz + 3.04f, 1.2f, 0.8f, 0.08f, 0xFFFFD98A.toInt(), 1f, yaw, cx, cz)
                }
            }
            else -> {
                val h = 20f + floor(s * 22.0).toFloat()
                batch.addBox(cx, h * 0.5f, cz, 5.0f, h, 5.0f, 0xFF0C1A2A.toInt(), 0f, yaw, cx, cz)
                batch.addBox(cx, h + 1.5f, cz, 3.0f, 3.0f, 3.0f, 0xFF142840.toInt(), 0f, yaw, cx, cz)
                // Glowing vertical neon strips
                batch.addBox(cx - 2.45f, h * 0.5f, cz + 2.45f, 0.18f, h, 0.18f, 0xFF22E3F2.toInt(), 1f, yaw, cx, cz)
                batch.addBox(cx + 2.45f, h * 0.5f, cz + 2.45f, 0.18f, h, 0.18f, 0xFF22E3F2.toInt(), 1f, yaw, cx, cz)
                batch.addBox(cx, h + 3.1f, cz, 3.2f, 0.22f, 3.2f, 0xFFFF4FD0.toInt(), 1f, yaw, cx, cz)
                batch.addCylinder(cx, h + 6.0f, cz, 0.08f, 0.08f, 6.0f, 5, 0xFF22E3F2.toInt(), 1f)
                val floors = (h / 3.5f).toInt()
                for (fl in 1 until floors) {
                    val col = if (fl % 3 == 0) 0xFFFF4FD0.toInt() else 0xFF7FF3FF.toInt()
                    batch.addBox(cx, fl * 3.5f, cz + 2.55f, 3.6f, 0.45f, 0.08f, col, 1f, yaw, cx, cz)
                }
            }
        }
    }

    private class ScaledBatch(val b: MeshBatch, val ox: Float, val oz: Float, val sc: Float) {
        fun addBox(cx: Float, cy: Float, cz: Float, w: Float, h: Float, d: Float, color: Int, em: Float = 0f, yawRad: Float = 0f, pivotX: Float = 0f, pivotZ: Float = 0f) {
            b.addBox(ox + (cx - ox) * sc, cy * sc, oz + (cz - oz) * sc, w * sc, h * sc, d * sc, color, em, yawRad, ox + (pivotX - ox) * sc, oz + (pivotZ - oz) * sc)
        }
        fun addCylinder(cx: Float, cy: Float, cz: Float, rTop: Float, rBot: Float, h: Float, segs: Int, color: Int, em: Float = 0f, yawRad: Float = 0f, pivotX: Float = 0f, pivotZ: Float = 0f) {
            b.addCylinder(ox + (cx - ox) * sc, cy * sc, oz + (cz - oz) * sc, rTop * sc, rBot * sc, h * sc, segs, color, em, yawRad, ox + (pivotX - ox) * sc, oz + (pivotZ - oz) * sc)
        }
        fun addCone(cx: Float, cy: Float, cz: Float, radius: Float, h: Float, segs: Int, color: Int, em: Float = 0f, yawRad: Float = 0f, pivotX: Float = 0f, pivotZ: Float = 0f) {
            b.addCone(ox + (cx - ox) * sc, cy * sc, oz + (cz - oz) * sc, radius * sc, h * sc, segs, color, em, yawRad, ox + (pivotX - ox) * sc, oz + (pivotZ - oz) * sc)
        }
        fun addGable(cx: Float, cyBase: Float, cz: Float, w: Float, h: Float, d: Float, color: Int, yawRad: Float = 0f, pivotX: Float = 0f, pivotZ: Float = 0f) {
            b.addGable(ox + (cx - ox) * sc, cyBase * sc, oz + (cz - oz) * sc, w * sc, h * sc, d * sc, color, yawRad, ox + (pivotX - ox) * sc, oz + (pivotZ - oz) * sc)
        }
    }

    private fun addBuilding(rawBatch: MeshBatch, cx: Float, cz: Float, k: String, e: Int, s: Double, yaw: Float, scale: Float = 1f) {
        val batch = ScaledBatch(rawBatch, cx, cz, scale)
        val f = e == 4
        val neon = if (f) 1f else 0f
        if (k == "huerto") {
            when (e) {
                0 -> {
                    batch.addBox(cx, 0.1f, cz, 6.8f, 0.2f, 6.8f, 0xFF7A5230.toInt())
                    for (i in 0..4) batch.addBox(cx, 0.45f, cz - 2.6f + i * 1.3f, 6.0f, 0.5f, 0.55f, 0xFF5FA13A.toInt())
                }
                1 -> {
                    batch.addBox(cx, 0.08f, cz, 7.0f, 0.15f, 7.0f, 0xFF74A653.toInt())
                    for ((ox, oz) in listOf(-1.9f to -1.9f, 1.9f to -1.9f, -1.9f to 1.9f, 1.9f to 1.9f)) {
                        batch.addCylinder(cx + ox, 0.8f, cz + oz, 0.18f, 0.25f, 1.6f, 5, 0xFF5A4030.toInt())
                        batch.addCone(cx + ox, 2.4f, cz + oz, 1.2f, 2.2f, 7, 0xFF3F8A3A.toInt())
                    }
                }
                2 -> {
                    batch.addBox(cx, 0.12f, cz, 7.2f, 0.25f, 7.2f, 0xFFCFC8B8.toInt())
                    batch.addCylinder(cx, 0.6f, cz, 2.2f, 2.3f, 0.7f, 12, 0xFFB8B2A2.toInt())
                    batch.addCylinder(cx, 0.64f, cz, 1.85f, 1.85f, 0.72f, 12, 0xFF6FB0D6.toInt())
                    batch.addCylinder(cx, 1.4f, cz, 0.25f, 0.3f, 1.8f, 6, 0xFFD8D2C0.toInt())
                }
                3 -> {
                    batch.addBox(cx, 0.1f, cz, 7.2f, 0.2f, 7.2f, 0xFF6FAE5A.toInt())
                    batch.addBox(cx, 0.22f, cz, 1.0f, 0.05f, 7.2f, 0xFFD8D2BF.toInt())
                    batch.addBox(cx, 0.23f, cz, 7.2f, 0.05f, 1.0f, 0xFFD8D2BF.toInt())
                    for ((ox, oz) in listOf(-2.5f to -2.5f, 2.5f to -2.5f, -2.5f to 2.5f, 2.5f to 2.5f)) {
                        batch.addCylinder(cx + ox, 0.9f, cz + oz, 0.2f, 0.28f, 1.8f, 5, 0xFF5A4030.toInt())
                        batch.addCone(cx + ox, 2.6f, cz + oz, 1.35f, 2.5f, 7, 0xFF2F7A3A.toInt())
                    }
                }
                else -> {
                    batch.addCylinder(cx, 0.25f, cz, 3.4f, 3.6f, 0.5f, 14, 0xFF14283A.toInt())
                    batch.addCylinder(cx, 1.8f, cz, 1.2f, 3.0f, 2.6f, 12, 0xFF22E3F2.toInt(), 0.55f)
                    batch.addCone(cx, 1.2f, cz, 1.5f, 2.0f, 8, 0xFF5AF08A.toInt(), 1f)
                }
            }
            return
        }

        if (k == "comercio") {
            when (e) {
                0 -> {
                    batch.addBox(cx, 0.5f, cz + 1.2f, 5.0f, 1.0f, 1.2f, 0xFF9A6C3F.toInt(), 0f, yaw, cx, cz)
                    for (i in 0..5) {
                        batch.addBox(cx - 2.4f + i * 0.96f, 2.5f, cz, 0.92f, 0.14f, 3.8f, if (i % 2 == 1) 0xFFF1EAD8.toInt() else 0xFFC0504D.toInt(), 0f, yaw, cx, cz)
                    }
                    batch.addBox(cx - 2.5f, 1.2f, cz - 1.6f, 0.2f, 2.4f, 0.2f, 0xFF6B4A2F.toInt(), 0f, yaw, cx, cz)
                    batch.addBox(cx + 2.5f, 1.2f, cz - 1.6f, 0.2f, 2.4f, 0.2f, 0xFF6B4A2F.toInt(), 0f, yaw, cx, cz)
                }
                1 -> {
                    batch.addBox(cx, 1.6f, cz, 5.6f, 3.2f, 4.6f, 0xFFD9C08E.toInt(), 0f, yaw, cx, cz)
                    batch.addBox(cx, 3.4f, cz + 2.4f, 4.4f, 0.9f, 0.2f, 0xFF2F6B4F.toInt(), 0f, yaw, cx, cz)
                    batch.addBox(cx + 1.4f, 1.4f, cz + 2.34f, 1.8f, 1.0f, 0.1f, 0xFFFFB85A.toInt(), 1f, yaw, cx, cz)
                }
                2 -> {
                    batch.addBox(cx, 2.0f, cz, 6.4f, 4.0f, 5.4f, 0xFFCDBF9F.toInt(), 0f, yaw, cx, cz)
                    batch.addGable(cx, 4.0f, cz, 7.2f, 2.6f, 6.2f, 0xFF8A4B3B.toInt(), yaw, cx, cz)
                    batch.addBox(cx, 3.3f, cz + 2.74f, 2.0f, 0.9f, 0.1f, 0xFFFFB85A.toInt(), 1f, yaw, cx, cz)
                }
                3 -> {
                    batch.addBox(cx, 2.3f, cz, 6.4f, 4.6f, 5.4f, 0xFF4A5560.toInt(), 0f, yaw, cx, cz)
                    batch.addBox(cx, 1.4f, cz + 2.74f, 5.2f, 2.2f, 0.12f, 0xFFBCD9FF.toInt(), 1f, yaw, cx, cz)
                    batch.addBox(cx, 3.8f, cz + 2.78f, 5.2f, 0.9f, 0.2f, 0xFFE8A53A.toInt(), 0.7f, yaw, cx, cz)
                }
                else -> {
                    batch.addBox(cx, 2.0f, cz, 6.2f, 4.0f, 5.2f, 0xFF1A1033.toInt(), 0f, yaw, cx, cz)
                    batch.addBox(cx, 2.4f, cz + 2.65f, 5.0f, 0.5f, 0.12f, 0xFF22E3F2.toInt(), 1f, yaw, cx, cz)
                    batch.addBox(cx, 4.1f, cz, 6.3f, 0.2f, 5.3f, 0xFFFF4FD0.toInt(), 1f, yaw, cx, cz)
                    batch.addCone(cx, 6.5f, cz, 2.0f, 4.2f, 10, 0xFF22E3F2.toInt(), 0.6f)
                }
            }
            return
        }

        if (k == "torre") {
            when (e) {
                0 -> {
                    batch.addBox(cx, 2.0f, cz, 3.0f, 4.0f, 0.35f, 0xFF6B4A2F.toInt())
                    batch.addBox(cx, 4.0f, cz, 3.6f, 0.25f, 3.6f, 0xFF8A6A48.toInt())
                    batch.addBox(cx, 4.8f, cz, 3.2f, 1.2f, 3.2f, 0xFFA8794C.toInt())
                    batch.addCone(cx, 6.3f, cz, 2.6f, 1.8f, 4, 0xFFD6B04C.toInt())
                }
                1 -> {
                    batch.addCylinder(cx, 3.0f, cz, 1.7f, 1.9f, 6.0f, 10, 0xFF9D9585.toInt())
                    batch.addCylinder(cx, 6.4f, cz, 2.3f, 2.1f, 0.9f, 10, 0xFF8A8272.toInt())
                    batch.addCone(cx, 7.9f, cz, 2.4f, 2.2f, 10, 0xFF7A4A3A.toInt())
                }
                2 -> {
                    batch.addBox(cx, 3.5f, cz, 3.2f, 7.0f, 3.2f, 0xFFC9C2B2.toInt(), 0f, yaw, cx, cz)
                    batch.addCone(cx, 9.2f, cz, 2.4f, 4.5f, 4, 0xFF7A4A3A.toInt())
                    batch.addBox(cx, 12.0f, cz, 0.16f, 1.2f, 0.16f, 0xFFD8C070.toInt())
                    batch.addBox(cx, 12.2f, cz, 0.7f, 0.16f, 0.16f, 0xFFD8C070.toInt())
                }
                3 -> {
                    val h = 18f + floor(s * 10.0).toFloat()
                    batch.addBox(cx, h * 0.5f, cz, 5.4f, h, 5.4f, 0xFF5A7088.toInt(), 0f, yaw, cx, cz)
                    batch.addCylinder(cx, h + 2.5f, cz, 0.1f, 0.1f, 5.0f, 5, 0xFF9AA6B0.toInt())
                    for (fl in 1 until (h / 3f).toInt()) {
                        batch.addBox(cx, fl * 3f, cz + 2.74f, 4.0f, 0.7f, 0.08f, 0xFFBCD9FF.toInt(), 1f, yaw, cx, cz)
                    }
                }
                else -> {
                    batch.addCylinder(cx, 15f, cz, 0.7f, 1.7f, 30f, 8, 0xFF102A3D.toInt())
                    batch.addCone(cx, 35f, cz, 0.7f, 10f, 8, 0xFF22E3F2.toInt(), 1f)
                    batch.addBox(cx, 40.5f, cz, 0.9f, 0.9f, 0.9f, 0xFFFF4FD0.toInt(), 1f)
                }
            }
            return
        }

        // Civic & special buildings
        batch.addBox(cx, 0.1f, cz, 6.4f, 0.2f, 5.6f, 0xFF8A8F94.toInt(), 0f, yaw, cx, cz)
        when (k) {
            "gimnasio" -> {
                val body = intArrayOf(0xFF6B6F78.toInt(), 0xFF6B6F78.toInt(), 0xFF5A6470.toInt(), 0xFF4A5560.toInt(), 0xFF14283A.toInt())[e]
                batch.addBox(cx, 1.7f, cz, 5.6f, 3.0f, 4.8f, body, 0f, yaw, cx, cz)
                batch.addBox(cx, 3.35f, cz, 6.0f, 0.3f, 5.2f, if (f) 0xFF22E3F2.toInt() else 0xFFC0392B.toInt(), neon, yaw, cx, cz)
                batch.addBox(cx, 4.1f, cz, 2.4f, 0.22f, 0.22f, 0xFF222222.toInt(), 0f, yaw, cx, cz)
                batch.addBox(cx - 1.3f, 4.1f, cz, 0.6f, 0.8f, 0.8f, 0xFF222222.toInt(), 0f, yaw, cx, cz)
                batch.addBox(cx + 1.3f, 4.1f, cz, 0.6f, 0.8f, 0.8f, 0xFF222222.toInt(), 0f, yaw, cx, cz)
            }
            "escuela" -> {
                val body = intArrayOf(0xFFE8C26A.toInt(), 0xFFE0B86A.toInt(), 0xFFD9A88A.toInt(), 0xFFCFD8E0.toInt(), 0xFF14283A.toInt())[e]
                batch.addBox(cx, 1.7f, cz, 6.2f, 3.0f, 4.4f, body, 0f, yaw, cx, cz)
                batch.addBox(cx, 3.35f, cz, 6.6f, 0.3f, 4.8f, if (f) 0xFF22E3F2.toInt() else 0xFFA8452F.toInt(), neon, yaw, cx, cz)
                batch.addBox(cx - 2.0f, 2.0f, cz + 2.24f, 1.1f, 0.9f, 0.1f, 0xFF9AD0EE.toInt(), 0.7f, yaw, cx, cz)
                batch.addBox(cx + 2.0f, 2.0f, cz + 2.24f, 1.1f, 0.9f, 0.1f, 0xFF9AD0EE.toInt(), 0.7f, yaw, cx, cz)
                batch.addBox(cx + 2.8f, 5.4f, cz, 0.9f, 0.6f, 0.08f, 0xFF2A6FD6.toInt(), 0f, yaw, cx, cz)
            }
            "terminal" -> {
                batch.addBox(cx, 3.4f, cz - 1.0f, 6.6f, 0.3f, 3.2f, if (f) 0xFF22E3F2.toInt() else 0xFF2A6FD6.toInt(), neon, yaw, cx, cz)
                batch.addBox(cx, 1.4f, cz - 2.5f, 6.0f, 2.2f, 0.14f, 0xFF9AD0EE.toInt(), 0.5f, yaw, cx, cz)
                batch.addBox(cx - 3.0f, 1.8f, cz - 2.4f, 0.22f, 3.2f, 0.22f, 0xFF9AA0A8.toInt(), 0f, yaw, cx, cz)
                batch.addBox(cx + 3.0f, 1.8f, cz - 2.4f, 0.22f, 3.2f, 0.22f, 0xFF9AA0A8.toInt(), 0f, yaw, cx, cz)
                batch.addBox(cx + 3.0f, 4.6f, cz + 2.6f, 1.4f, 1.0f, 0.14f, 0xFF2A6FD6.toInt(), 0.5f, yaw, cx, cz)
            }
            "empresa" -> {
                val h = floatArrayOf(4f, 5f, 6.5f, 8f, 10f)[e]
                val col = tintColor(VEHICLE_COLORS[(s * VEHICLE_COLORS.size).toInt().coerceIn(0, VEHICLE_COLORS.lastIndex)], 0f)
                batch.addBox(cx, h * 0.5f + 0.2f, cz, 5.2f, h, 4.4f, col, 0f, yaw, cx, cz)
                var r = 1.4f
                while (r < h) {
                    batch.addBox(cx, r, cz, 5.3f, 0.55f, 4.5f, if (f) 0xFF22E3F2.toInt() else 0xFF9AD0EE.toInt(), if (f) 1f else 0.7f, yaw, cx, cz)
                    r += 1.6f
                }
                batch.addBox(cx, 1.4f, cz + 2.25f, 2.0f, 0.8f, 0.12f, 0xFFFFFFFF.toInt(), 0.4f, yaw, cx, cz)
            }
            "hospital" -> {
                batch.addBox(cx, 2.2f, cz, 6.0f, 4.0f, 4.6f, if (f) 0xFF14283A.toInt() else 0xFFF2F4F6.toInt(), 0f, yaw, cx, cz)
                batch.addBox(cx, 4.3f, cz, 6.2f, 0.3f, 4.8f, 0xFFC9CED4.toInt(), 0f, yaw, cx, cz)
                batch.addBox(cx, 3.2f, cz + 2.35f, 1.6f, 0.45f, 0.14f, 0xFFD9302A.toInt(), 0.8f, yaw, cx, cz)
                batch.addBox(cx, 3.2f, cz + 2.35f, 0.45f, 1.6f, 0.14f, 0xFFD9302A.toInt(), 0.8f, yaw, cx, cz)
                batch.addBox(cx, 1.9f, cz + 2.9f, 2.4f, 0.2f, 1.2f, 0xFFD9302A.toInt(), 0f, yaw, cx, cz)
            }
            "comisaria" -> {
                batch.addBox(cx, 1.7f, cz, 5.6f, 3.0f, 4.2f, 0xFF4A6FA5.toInt(), 0f, yaw, cx, cz)
                batch.addBox(cx, 3.35f, cz, 6.0f, 0.3f, 4.6f, 0xFF2B3A55.toInt(), 0f, yaw, cx, cz)
                batch.addBox(cx, 2.4f, cz + 2.15f, 1.2f, 1.2f, 0.14f, 0xFFE8C26A.toInt(), 0.4f, yaw, cx, cz)
                batch.addCylinder(cx + 1.8f, 3.8f, cz + 1.0f, 0.25f, 0.25f, 0.5f, 6, 0xFF2A6FD6.toInt(), 1f)
            }
            "bomberos" -> {
                batch.addBox(cx, 1.7f, cz, 6.2f, 3.0f, 4.6f, 0xFFC0392B.toInt(), 0f, yaw, cx, cz)
                batch.addBox(cx, 3.35f, cz, 6.5f, 0.3f, 4.9f, 0xFF7A2018.toInt(), 0f, yaw, cx, cz)
                batch.addBox(cx - 1.4f, 1.3f, cz + 2.35f, 2.2f, 2.2f, 0.14f, 0xFFECECE6.toInt(), 0f, yaw, cx, cz)
                batch.addBox(cx + 1.4f, 1.3f, cz + 2.35f, 2.2f, 2.2f, 0.14f, 0xFFECECE6.toInt(), 0f, yaw, cx, cz)
                batch.addBox(cx - 2.3f, 2.7f, cz - 1.5f, 1.3f, 5.0f, 1.3f, 0xFFA8322A.toInt(), 0f, yaw, cx, cz)
            }
            "ayuntamiento" -> {
                batch.addBox(cx, 1.7f, cz, 6.8f, 3.0f, 4.8f, 0xFFD8D2C0.toInt(), 0f, yaw, cx, cz)
                batch.addBox(cx, 3.3f, cz + 2.4f, 7.0f, 0.4f, 1.0f, 0xFFF0ECE0.toInt(), 0f, yaw, cx, cz)
                for (px in listOf(-2.4f, -0.8f, 0.8f, 2.4f)) {
                    batch.addBox(cx + px, 1.5f, cz + 2.6f, 0.38f, 2.6f, 0.38f, 0xFFF0ECE0.toInt(), 0f, yaw, cx, cz)
                }
                batch.addBox(cx, 4.8f, cz - 0.5f, 1.8f, 3.0f, 1.8f, 0xFFD8D2C0.toInt(), 0f, yaw, cx, cz)
                batch.addCone(cx, 7.1f, cz - 0.5f, 1.3f, 1.6f, 8, 0xFF3A6B8A.toInt())
            }
            "iglesia" -> {
                batch.addBox(cx, 1.7f, cz, 4.2f, 3.0f, 5.4f, 0xFFE8E0D0.toInt(), 0f, yaw, cx, cz)
                batch.addBox(cx, 3.3f, cz, 4.6f, 0.3f, 5.8f, 0xFF8A4A3A.toInt(), 0f, yaw, cx, cz)
                batch.addBox(cx, 3.0f, cz + 2.4f, 1.6f, 5.0f, 1.6f, 0xFFE8E0D0.toInt(), 0f, yaw, cx, cz)
                batch.addCone(cx, 6.5f, cz + 2.4f, 1.1f, 2.0f, 4, 0xFF6A3A2A.toInt(), 0f, yaw, cx, cz)
                batch.addBox(cx, 7.9f, cz + 2.4f, 0.14f, 0.9f, 0.14f, 0xFFE8C26A.toInt(), 0.4f, yaw, cx, cz)
                batch.addBox(cx, 8.0f, cz + 2.4f, 0.5f, 0.14f, 0.14f, 0xFFE8C26A.toInt(), 0.4f, yaw, cx, cz)
            }
            "estadio" -> {
                batch.addBox(cx, 0.4f, cz, 4.6f, 0.12f, 4.6f, 0xFF3A9A4A.toInt(), 0f, yaw, cx, cz)
                batch.addBox(cx, 0.9f, cz - 2.9f, 7.0f, 1.6f, 1.2f, 0xFFB8B8B0.toInt(), 0f, yaw, cx, cz)
                batch.addBox(cx, 0.9f, cz + 2.9f, 7.0f, 1.6f, 1.2f, 0xFFB8B8B0.toInt(), 0f, yaw, cx, cz)
                batch.addBox(cx - 2.9f, 0.9f, cz, 1.2f, 1.6f, 5.0f, 0xFFB8B8B0.toInt(), 0f, yaw, cx, cz)
                batch.addBox(cx + 2.9f, 0.9f, cz, 1.2f, 1.6f, 5.0f, 0xFFB8B8B0.toInt(), 0f, yaw, cx, cz)
                for ((lx, lz) in listOf(-3.2f to -3.2f, 3.2f to 3.2f, -3.2f to 3.2f, 3.2f to -3.2f)) {
                    batch.addCylinder(cx + lx, 2.3f, cz + lz, 0.09f, 0.09f, 4.5f, 5, 0xFF888888.toInt())
                    batch.addBox(cx + lx, 4.6f, cz + lz, 0.6f, 0.5f, 0.6f, 0xFFFFFFE0.toInt(), 1f)
                }
            }
            "biblioteca" -> {
                batch.addBox(cx, 1.7f, cz, 5.8f, 3.0f, 4.4f, 0xFFA8754A.toInt(), 0f, yaw, cx, cz)
                batch.addBox(cx, 3.35f, cz, 6.2f, 0.3f, 4.8f, 0xFF5A3A2A.toInt(), 0f, yaw, cx, cz)
                batch.addBox(cx, 2.8f, cz + 2.25f, 2.0f, 0.5f, 0.14f, 0xFFE8C26A.toInt(), 0.4f, yaw, cx, cz)
                batch.addBox(cx - 2.0f, 1.9f, cz + 2.24f, 1.1f, 1.0f, 0.1f, 0xFF9AD0EE.toInt(), 0.9f, yaw, cx, cz)
                batch.addBox(cx + 2.0f, 1.9f, cz + 2.24f, 1.1f, 1.0f, 0.1f, 0xFF9AD0EE.toInt(), 0.9f, yaw, cx, cz)
            }
        }
        if (f) {
            batch.addBox(cx, 0.25f, cz, 6.4f, 0.12f, 5.0f, 0xFF22E3F2.toInt(), 1f, yaw, cx, cz)
        }
    }

    fun buildDynamicEntities(
        sim: CitySimulation,
        batch: MeshBatch,
        clock: Float,
        hoverX: Int,
        hoverY: Int,
        hoverValid: Boolean,
        hoverBig: Boolean,
        showHover: Boolean
    ) {
        batch.clear()
        val e = eraOfCiv(sim.civ)

        // Construction sites (single & 2x2)
        for (y in 0 until GRID_SIZE) {
            for (x in 0 until GRID_SIZE) {
                val c = sim.grid[y][x] ?: continue
                if (c.t != "site") continue
                val sx = if (c.big == 2) sim.gx2x(c.ox) + TILE_SIZE * 0.5f else sim.gx2x(x)
                val sz = if (c.big == 2) sim.gx2x(c.oy) + TILE_SIZE * 0.5f else sim.gx2x(y)
                addSite(batch, sx, sz, e, c.p.toFloat().coerceIn(0.03f, 1f), clock, if (c.big == 2) 1.9f else 1f)
            }
        }

        // Traffic lights at intersections (Era >= 2)
        if (e >= 2) {
            val (nsCol, ewCol) = sim.trafficSignalStates()
            for (y in 0 until GRID_SIZE) {
                for (x in 0 until GRID_SIZE) {
                    if (!sim.isRoad(x, y)) continue
                    var n = 0
                    for (d in DIRS) if (sim.isRoad(x + d[0], y + d[1])) n++
                    if (n < 3) continue
                    val cx = sim.gx2x(x)
                    val cz = sim.gx2x(y)
                    val px = cx + 3.4f
                    val pz = cz + 3.4f
                    batch.addCylinder(px, 1.7f, pz, 0.08f, 0.08f, 3.4f, 5, 0xFF333333.toInt())
                    batch.addBox(px, 3.3f, pz, 0.45f, 0.45f, 0.45f, nsCol, 1f)
                    batch.addBox(px, 2.7f, pz, 0.45f, 0.45f, 0.45f, ewCol, 1f)
                }
            }
        }

        // Villagers
        for (v in sim.villagers) {
            if (!v.visible) continue
            addHuman(
                batch = batch,
                wx = v.wx, wy = v.wy, wz = v.wz,
                yaw = v.yaw,
                look = v.look,
                e = e,
                isPlayer = false,
                mv = v.mv,
                work = if (v.working) v.act else null,
                clock = clock,
                ph = v.ph,
                blessed = v.blessed
            )
        }

        // Player walker avatar
        if (sim.walkMode) {
            val playerLook = VillagerLook(
                female = false, skinIdx = 1, hairIdx = 1, heightScale = 1.04f,
                longHair = false, beard = false, c1 = 0.5f, c2 = 0.5f, c3 = 0.1f,
                hatSeed = 0f, name = "Vos"
            )
            addHuman(
                batch = batch,
                wx = sim.walkX, wy = 0.05f + sim.walkY + sim.walkJy * 0.6f, wz = sim.walkZ,
                yaw = sim.walkHeading,
                look = playerLook,
                e = e,
                isPlayer = true,
                mv = sim.walkMv,
                work = null,
                clock = clock,
                ph = sim.walkPh,
                blessed = false
            )
        }

        // Vehicles (horse-drawn in Era 0-1, vintage in Era 2, modern/futuristic in Era 3-4)
        for (c in sim.vehicles) {
            if (!c.visible) continue
            DynamicEffectsGeometry.addEraVehicle(batch, c, e, clock)
        }

        // Dynamic world details: lake sailboats, river ducks/logs, birds, trains, incidents, weather & Zeus FX
        DynamicEffectsGeometry.addDynamicWorldDetails(sim, batch, clock, e)

        // Drifting clouds (Era < 4)
        if (e < 4) {
            val cloudAlpha = 0.25f + sim.wx.cloud * 0.25f
            val cloudCol = if (sim.wx.storm) 0xFFB0B8C4.toInt() else 0xFFFFFFFF.toInt()
            for (i in 0 until 10) {
                val speed = (1.8f + (i % 3) * 0.9f) * (1f + sim.wx.windNow())
                val cx = ((hash(i, 5) - 0.5f) * 800f + clock * speed + 450f) % 900f - 450f
                val cy = 92f + hash(i, 6) * 35f
                val cz = (hash(i, 7) - 0.5f) * 800f
                batch.addBox(cx, cy, cz, 26f, 5.5f, 14f, cloudCol, cloudAlpha)
                batch.addBox(cx + 11f, cy + 2f, cz + 2f, 16f, 4.5f, 10f, 0xFFF4F6FA.toInt(), cloudAlpha)
            }
        }

        // Hover placement tile preview (1x1 or 2x2)
        if (showHover && hoverX in 0 until GRID_SIZE && hoverY in 0 until GRID_SIZE) {
            val hx = if (hoverBig) sim.gx2x(hoverX) + TILE_SIZE * 0.5f else sim.gx2x(hoverX)
            val hz = if (hoverBig) sim.gx2x(hoverY) + TILE_SIZE * 0.5f else sim.gx2x(hoverY)
            val sizeSpan = if (hoverBig) TILE_SIZE * 2f - 0.45f else TILE_SIZE - 0.35f
            val col = if (hoverValid) 0xFFFFFFFF.toInt() else 0xFFE6463C.toInt()
            batch.addBox(hx, 0.28f, hz, sizeSpan, 0.4f, sizeSpan, col, 0.65f)
        }
    }

    private fun addSite(batch: MeshBatch, cx: Float, cz: Float, e: Int, p: Float, clock: Float, sc: Float = 1f) {
        val f = e == 4
        val hMax = (if (f) 10f else if (e >= 2) 7f else 5f) * sc
        val h = hMax * p
        val pad = 7.6f * sc
        batch.addPlaneXZ(cx, 0.06f, cz, pad, pad, if (f) 0xFF0A1424.toInt() else 0xFF8A7A62.toInt())
        val fHalf = 3.65f * sc
        batch.addBox(cx, 0.5f, cz - fHalf, 7.3f * sc, 0.7f, 0.1f, 0xFFE8702A.toInt())
        batch.addBox(cx - fHalf, 0.5f, cz, 0.1f, 0.7f, 7.3f * sc, 0xFFE8702A.toInt())
        batch.addBox(cx + fHalf, 0.5f, cz, 0.1f, 0.7f, 7.3f * sc, 0xFFE8702A.toInt())
        batch.addBox(cx, 0.3f, cz, 5.8f * sc, 0.5f, 5.8f * sc, 0xFF9A9A96.toInt())
        batch.addBox(cx, 0.55f + h * 0.5f, cz, 4.8f * sc, max(0.3f, h), 4.8f * sc, if (f) 0xFF22E3F2.toInt() else 0xFFD6B04C.toInt(), if (f) 0.7f else 0f)
        if (e >= 1) {
            val craneH = hMax + 3.5f
            batch.addBox(cx - 3.1f * sc, craneH * 0.5f, cz - 3.1f * sc, 0.32f, craneH, 0.32f, 0xFFF2C200.toInt())
            val jibYaw = sin(clock * 0.4f) * 1.1f
            batch.addBox(cx - 1.2f * sc, craneH + 0.15f, cz - 3.1f * sc, 5.2f * sc, 0.25f, 0.25f, 0xFFF2C200.toInt(), 0f, jibYaw, cx - 3.1f * sc, cz - 3.1f * sc)
        }
        if (p < 0.45f) {
            val excYaw = sin(clock * 0.6f) * 0.5f
            batch.addBox(cx + 2.4f, 0.55f, cz + 2.4f, 1.3f, 0.6f, 1.5f, 0xFFF2C200.toInt(), 0f, excYaw, cx + 2.4f, cz + 2.4f)
            batch.addBox(cx + 2.4f, 1.2f, cz + 3.2f, 0.25f, 0.25f, 1.6f, 0xFFE0B000.toInt(), 0f, excYaw, cx + 2.4f, cz + 2.4f)
        }
    }

    private fun addHuman(
        batch: MeshBatch,
        wx: Float, wy: Float, wz: Float, yaw: Float,
        look: VillagerLook, e: Int, isPlayer: Boolean,
        mv: Float, work: String?, clock: Float, ph: Float,
        blessed: Boolean
    ) {
        val sc = look.heightScale
        val skin = SKIN_COLORS[look.skinIdx]
        val hair = HAIR_COLORS[look.hairIdx]
        val topCol = if (isPlayer) 0xFFD9742A.toInt() else when (e) {
            0 -> intArrayOf(0xFF8A6B4A.toInt(), 0xFFA87C4F.toInt(), 0xFF6B7A45.toInt(), 0xFF9A5A3A.toInt())[(look.c1 * 4).toInt().coerceIn(0, 3)]
            1 -> intArrayOf(0xFFC2B08A.toInt(), 0xFF7A8C5A.toInt(), 0xFFA65A4A.toInt(), 0xFF6A7F9A.toInt())[(look.c1 * 4).toInt().coerceIn(0, 3)]
            2 -> intArrayOf(0xFFA03A3A.toInt(), 0xFF2F5A8A.toInt(), 0xFF3A7A4A.toInt(), 0xFFC28A2A.toInt())[(look.c1 * 4).toInt().coerceIn(0, 3)]
            3 -> intArrayOf(0xFFE04A4A.toInt(), 0xFF3A8AD6.toInt(), 0xFFF0F0E8.toInt(), 0xFF2FA86A.toInt())[(look.c1 * 4).toInt().coerceIn(0, 3)]
            else -> 0xFF101828.toInt()
        }
        val botCol = if (isPlayer) 0xFF3A3A46.toInt() else if (e == 4) 0xFF0A0F1C.toInt() else 0xFF3C3A32.toInt()
        val s = sin(ph)
        val bounce = when {
            work == "gym" -> abs(sin(clock * 5f + ph)) * 0.12f
            mv > 0.1f -> abs(s) * 0.04f
            else -> 0f
        }
        val legSwing = s * 0.65f * mv
        val armL = -s * 0.55f * mv
        val armR = when (work) {
            "build" -> -1.4f + sin(clock * 9f + ph) * 0.55f
            "gym" -> -2.2f + sin(clock * 5f + ph) * 0.65f
            "look" -> -1.25f
            else -> s * 0.55f * mv
        }

        val y0 = wy + bounce
        batch.addBox(wx - 0.1f * sc, y0 + 0.44f * sc, wz, 0.12f * sc, 0.84f * sc, 0.13f * sc, botCol, 0f, yaw, wx, wz, legSwing, y0 + 0.85f * sc)
        batch.addBox(wx + 0.1f * sc, y0 + 0.44f * sc, wz, 0.12f * sc, 0.84f * sc, 0.13f * sc, botCol, 0f, yaw, wx, wz, -legSwing, y0 + 0.85f * sc)
        batch.addBox(wx, y0 + 1.15f * sc, wz, 0.34f * sc, 0.58f * sc, 0.24f * sc, topCol, 0f, yaw, wx, wz)
        if (work == "build") {
            batch.addBox(wx, y0 + 1.18f * sc, wz, 0.37f * sc, 0.42f * sc, 0.27f * sc, 0xFFFF7A1A.toInt(), 0f, yaw, wx, wz)
        } else if (isPlayer) {
            batch.addBox(wx, y0 + 1.15f * sc, wz - 0.17f * sc, 0.28f * sc, 0.38f * sc, 0.14f * sc, 0xFFC0602A.toInt(), 0f, yaw, wx, wz)
        }
        batch.addBox(wx - 0.23f * sc, y0 + 1.12f * sc, wz, 0.10f * sc, 0.56f * sc, 0.10f * sc, topCol, 0f, yaw, wx, wz, armL, y0 + 1.38f * sc)
        batch.addBox(wx + 0.23f * sc, y0 + 1.12f * sc, wz, 0.10f * sc, 0.56f * sc, 0.10f * sc, topCol, 0f, yaw, wx, wz, armR, y0 + 1.38f * sc)
        batch.addBox(wx, y0 + 1.62f * sc, wz, 0.23f * sc, 0.25f * sc, 0.23f * sc, skin, 0f, yaw, wx, wz)
        batch.addBox(wx, y0 + 1.75f * sc, wz - 0.02f * sc, 0.25f * sc, 0.12f * sc, 0.25f * sc, hair, 0f, yaw, wx, wz)
        if (work == "build") {
            batch.addBox(wx, y0 + 1.80f * sc, wz, 0.30f * sc, 0.12f * sc, 0.30f * sc, 0xFFF2C200.toInt(), 0f, yaw, wx, wz)
        } else if (isPlayer) {
            batch.addBox(wx, y0 + 1.80f * sc, wz, 0.38f * sc, 0.05f * sc, 0.38f * sc, 0xFF7A5A3A.toInt(), 0f, yaw, wx, wz)
            batch.addBox(wx, y0 + 1.86f * sc, wz, 0.22f * sc, 0.10f * sc, 0.22f * sc, 0xFF7A5A3A.toInt(), 0f, yaw, wx, wz)
        } else if (e == 4) {
            batch.addBox(wx, y0 + 1.64f * sc, wz + 0.11f * sc, 0.24f * sc, 0.06f * sc, 0.05f * sc, 0xFF22E3F2.toInt(), 1f, yaw, wx, wz)
        }
        if (blessed) {
            val hy = y0 + 2.12f * sc + sin(clock * 4f + ph) * 0.06f
            batch.addBox(wx, hy, wz, 0.38f, 0.07f, 0.38f, 0xFFFFD84A.toInt(), 1f, clock * 1.5f, wx, wz)
        }
    }
}
