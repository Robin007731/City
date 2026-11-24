package com.example.gl

import com.example.simulation.*
import kotlin.math.*

object DynamicEffectsGeometry {
    private fun hash(x: Int, y: Int): Float {
        val n = sin(x * 127.1 + y * 311.7) * 43758.5453
        return (n - floor(n)).toFloat()
    }

    fun addRiverAndLakeStatic(sim: CitySimulation, batch: MeshBatch, e: Int) {
        val waterCol = if (e == 4) 0xFF144B6E.toInt() else 0xFF2D76AA.toInt()
        val bankCol = if (e == 4) 0xFF193048.toInt() else 0xFFC2AE82.toInt()
        val waterEm = if (e == 4) 0.28f else 0.05f

        // Winding river ribbon (z from -560 to +560)
        val stepZ = 16f
        var z = -560f
        while (z < 560f) {
            val z1 = z + stepZ
            val x0 = riverX(z)
            val x1 = riverX(z1)
            // Sandy bank underneath
            batch.addQuad(
                x0 - 15.5f, -0.02f, z,
                x0 + 15.5f, -0.02f, z,
                x1 + 15.5f, -0.02f, z1,
                x1 - 15.5f, -0.02f, z1,
                0f, 1f, 0f, bankCol, 0f
            )
            // Water surface
            batch.addQuad(
                x0 - 11.2f, 0.01f, z,
                x0 + 11.2f, 0.01f, z,
                x1 + 11.2f, 0.01f, z1,
                x1 - 11.2f, 0.01f, z1,
                0f, 1f, 0f, waterCol, waterEm
            )
            z = z1
        }

        // Lake & shore
        batch.addCylinder(LAKE_X, -0.03f, LAKE_Z, LAKE_R + 7f, LAKE_R + 7f, 0.08f, 20, bankCol)
        batch.addCylinder(LAKE_X, 0.02f, LAKE_Z, LAKE_R, LAKE_R, 0.08f, 20, waterCol, waterEm)
    }

    fun addSpecialRoad(sim: CitySimulation, batch: MeshBatch, x: Int, y: Int, cx: Float, cz: Float, e: Int, c: GridCell) {
        if (c.br) {
            // Bridge over river
            val deckY = 0.34f
            val deckCol = if (e >= 2) 0xFF6B7078.toInt() else 0xFF8C663F.toInt()
            val railCol = if (e == 4) 0xFF22E3F2.toInt() else if (e >= 2) 0xFFB0B6BE.toInt() else 0xFF6B4A2F.toInt()
            val railEm = if (e == 4) 0.9f else 0f
            batch.addBox(cx, deckY - 0.14f, cz, TILE_SIZE, 0.28f, TILE_SIZE, deckCol)
            val ew = sim.isRoad(x + 1, y) || sim.isRoad(x - 1, y)
            if (ew) {
                batch.addBox(cx, deckY + 0.38f, cz - 3.6f, TILE_SIZE, 0.6f, 0.22f, railCol, railEm)
                batch.addBox(cx, deckY + 0.38f, cz + 3.6f, TILE_SIZE, 0.6f, 0.22f, railCol, railEm)
            } else {
                batch.addBox(cx - 3.6f, deckY + 0.38f, cz, 0.22f, 0.6f, TILE_SIZE, railCol, railEm)
                batch.addBox(cx + 3.6f, deckY + 0.38f, cz, 0.22f, 0.6f, TILE_SIZE, railCol, railEm)
            }
            // Support pillars into water
            batch.addCylinder(cx - 2.8f, -0.25f, cz - 2.8f, 0.28f, 0.32f, 1.0f, 6, 0xFF4A4E54.toInt())
            batch.addCylinder(cx + 2.8f, -0.25f, cz + 2.8f, 0.28f, 0.32f, 1.0f, 6, 0xFF4A4E54.toInt())
            return
        }

        if (c.k == "ave") {
            val east = sim.isRoad(x + 1, y); val west = sim.isRoad(x - 1, y)
            val south = sim.isRoad(x, y + 1); val north = sim.isRoad(x, y - 1)
            val rd = if (e == 4) 0xFF0E182B.toInt() else 0xFF3E444B.toInt()
            val median = if (e == 4) 0xFF14283A.toInt() else 0xFF4D8C46.toInt()
            batch.addPlaneXZ(cx, 0.04f, cz, TILE_SIZE, TILE_SIZE, rd)
            if (!east && !west && (south || north)) {
                batch.addBox(cx, 0.14f, cz, 1.0f, 0.2f, TILE_SIZE - 0.5f, median)
            } else {
                batch.addBox(cx, 0.14f, cz, TILE_SIZE - 0.5f, 0.2f, 1.0f, median)
            }
            if ((x + y) % 2 == 0) {
                batch.addCylinder(cx, 0.65f, cz, 0.12f, 0.16f, 0.9f, 5, 0xFF5A4030.toInt())
                batch.addCone(cx, 1.5f, cz, 0.65f, 1.3f, 6, seasonCol(0xFF3F8A3A.toInt(), 0, hash(x, y), e, sim.wx))
            }
        } else if (c.k == "hwy") {
            val rd = if (e == 4) 0xFF0A1426.toInt() else 0xFF282D34.toInt()
            val dash = if (e == 4) 0xFF22E3F2.toInt() else 0xFFF2C84B.toInt()
            val railCol = if (e == 4) 0xFF22E3F2.toInt() else 0xFFB8BEC4.toInt()
            batch.addPlaneXZ(cx, 0.06f, cz, TILE_SIZE, TILE_SIZE, rd)
            val ew = sim.isRoad(x + 1, y) || sim.isRoad(x - 1, y)
            if (ew) {
                batch.addPlaneXZ(cx, 0.08f, cz, TILE_SIZE - 0.4f, 0.24f, dash, if (e == 4) 1f else 0.2f)
                batch.addBox(cx, 0.35f, cz - 3.7f, TILE_SIZE, 0.4f, 0.16f, railCol, if (e == 4) 0.7f else 0f)
                batch.addBox(cx, 0.35f, cz + 3.7f, TILE_SIZE, 0.4f, 0.16f, railCol, if (e == 4) 0.7f else 0f)
            } else {
                batch.addPlaneXZ(cx, 0.08f, cz, 0.24f, TILE_SIZE - 0.4f, dash, if (e == 4) 1f else 0.2f)
                batch.addBox(cx - 3.7f, 0.35f, cz, 0.16f, 0.4f, TILE_SIZE, railCol, if (e == 4) 0.7f else 0f)
                batch.addBox(cx + 3.7f, 0.35f, cz, 0.16f, 0.4f, TILE_SIZE, railCol, if (e == 4) 0.7f else 0f)
            }
        }

        if (c.rx.isNotEmpty()) {
            // Level crossing rails & red/white crossbucks
            val steel = if (e == 4) 0xFF22E3F2.toInt() else 0xFFB6BDC4.toInt()
            if (c.rx == "h") {
                batch.addBox(cx, 0.09f, cz - 0.75f, TILE_SIZE, 0.06f, 0.12f, steel, if (e == 4) 0.7f else 0f)
                batch.addBox(cx, 0.09f, cz + 0.75f, TILE_SIZE, 0.06f, 0.12f, steel, if (e == 4) 0.7f else 0f)
            } else {
                batch.addBox(cx - 0.75f, 0.09f, cz, 0.12f, 0.06f, TILE_SIZE, steel, if (e == 4) 0.7f else 0f)
                batch.addBox(cx + 0.75f, 0.09f, cz, 0.12f, 0.06f, TILE_SIZE, steel, if (e == 4) 0.7f else 0f)
            }
            batch.addCylinder(cx - 2.9f, 1.3f, cz - 2.9f, 0.08f, 0.08f, 2.6f, 5, 0xFF3A3E44.toInt())
            batch.addBox(cx - 2.9f, 2.4f, cz - 2.9f, 0.9f, 0.22f, 0.12f, 0xFFE63E32.toInt(), 0.4f, 0.78f, cx - 2.9f, cz - 2.9f)
            batch.addBox(cx - 2.9f, 2.4f, cz - 2.9f, 0.9f, 0.22f, 0.12f, 0xFFF4F4F0.toInt(), 0.4f, -0.78f, cx - 2.9f, cz - 2.9f)
        }
    }

    fun addRailCell(sim: CitySimulation, batch: MeshBatch, x: Int, y: Int, cx: Float, cz: Float, e: Int, c: GridCell) {
        val m = sim.rMask(x, y)
        val horiz = (m and 3) != 0 || (m == 0)
        val vert = (m and 12) != 0
        val ballast = if (e == 4) 0xFF141E30.toInt() else 0xFF6B655B.toInt()
        val tie = 0xFF5A3F28.toInt()
        val steel = if (e == 4) 0xFF22E3F2.toInt() else 0xFFC2C8CE.toInt()
        val em = if (e == 4) 0.8f else 0f

        batch.addPlaneXZ(cx, 0.04f, cz, if (horiz) TILE_SIZE else 3.2f, if (vert) TILE_SIZE else 3.2f, ballast)
        if (horiz) {
            for (i in -3..3) {
                batch.addBox(cx + i * 1.1f, 0.08f, cz, 0.26f, 0.08f, 2.4f, tie)
            }
            batch.addBox(cx, 0.15f, cz - 0.75f, TILE_SIZE, 0.10f, 0.14f, steel, em)
            batch.addBox(cx, 0.15f, cz + 0.75f, TILE_SIZE, 0.10f, 0.14f, steel, em)
        }
        if (vert) {
            for (i in -3..3) {
                batch.addBox(cx, 0.08f, cz + i * 1.1f, 2.4f, 0.08f, 0.26f, tie)
            }
            batch.addBox(cx - 0.75f, 0.15f, cz, 0.14f, 0.10f, TILE_SIZE, steel, em)
            batch.addBox(cx + 0.75f, 0.15f, cz, 0.14f, 0.10f, TILE_SIZE, steel, em)
        }

        if (c.st) {
            val platCol = if (e == 4) 0xFF192E48.toInt() else 0xFFC8C2B4.toInt()
            val roofCol = if (e == 4) 0xFF22E3F2.toInt() else if (e >= 3) 0xFF2A6FD6.toInt() else 0xFF8A4B3B.toInt()
            if (horiz) {
                batch.addBox(cx, 0.35f, cz - 2.6f, 7.6f, 0.6f, 2.0f, platCol)
                batch.addBox(cx, 0.35f, cz + 2.6f, 7.6f, 0.6f, 2.0f, platCol)
                batch.addBox(cx, 2.6f, cz - 2.6f, 7.4f, 0.22f, 2.2f, roofCol, if (e == 4) 0.6f else 0f)
                batch.addBox(cx, 2.6f, cz + 2.6f, 7.4f, 0.22f, 2.2f, roofCol, if (e == 4) 0.6f else 0f)
                batch.addCylinder(cx - 2.6f, 1.5f, cz - 2.6f, 0.1f, 0.1f, 2.2f, 5, 0xFF444A50.toInt())
                batch.addCylinder(cx + 2.6f, 1.5f, cz + 2.6f, 0.1f, 0.1f, 2.2f, 5, 0xFF444A50.toInt())
            } else {
                batch.addBox(cx - 2.6f, 0.35f, cz, 2.0f, 0.6f, 7.6f, platCol)
                batch.addBox(cx + 2.6f, 0.35f, cz, 2.0f, 0.6f, 7.6f, platCol)
                batch.addBox(cx - 2.6f, 2.6f, cz, 2.2f, 0.22f, 7.4f, roofCol, if (e == 4) 0.6f else 0f)
                batch.addBox(cx + 2.6f, 2.6f, cz, 2.2f, 0.22f, 7.4f, roofCol, if (e == 4) 0.6f else 0f)
            }
        }
    }

    fun addDynamicWorldDetails(sim: CitySimulation, batch: MeshBatch, clock: Float, e: Int) {
        // 2 sailboats orbiting the lake
        for (i in 0..1) {
            val ang = clock * (0.14f + i * 0.05f) + i * PI.toFloat()
            val r = LAKE_R * (0.45f + i * 0.22f)
            val bx = LAKE_X + cos(ang) * r
            val bz = LAKE_Z + sin(ang) * r
            val yaw = -ang
            batch.addBox(bx, 0.22f, bz, 1.4f, 0.45f, 3.4f, 0xFFECEAE0.toInt(), 0f, yaw, bx, bz)
            batch.addCylinder(bx, 1.9f, bz, 0.06f, 0.08f, 3.2f, 5, 0xFF6B4A2F.toInt())
            batch.addBox(bx, 2.1f, bz + 0.5f, 0.08f, 2.4f, 1.5f, 0xFFFFFFFF.toInt(), 0f, yaw, bx, bz)
        }

        // Drifting river ducks / logs
        for (i in 0 until 8) {
            val rz = ((i * 110f + clock * 3.5f + 500f) % 1000f) - 500f
            val rx = riverX(rz) + sin(i * 2.1f) * 4.5f
            if (i % 2 == 0) {
                batch.addBox(rx, 0.14f, rz, 0.45f, 0.28f, 0.65f, 0xFFF2ECE0.toInt())
                batch.addBox(rx, 0.24f, rz + 0.26f, 0.22f, 0.18f, 0.22f, 0xFFE88A20.toInt())
            } else {
                batch.addCylinder(rx, 0.12f, rz, 0.2f, 0.2f, 1.8f, 6, 0xFF6B4A2F.toInt())
            }
        }

        // Flapping circling birds during daytime in Era < 4
        if (e < 4 && sim.dayness() > 0.3f) {
            for (i in 0 until 7) {
                val a = clock * 0.28f + i * 0.9f
                val rr = 55f + (i % 3) * 32f
                val bx = cos(a) * rr
                val bz = sin(a) * rr
                val by = 34f + (i % 4) * 6f + sin(clock * 2f + i) * 1.5f
                val flap = sin(clock * 9f + i) * 0.45f
                batch.addBox(bx - 0.55f, by + flap * 0.3f, bz, 0.9f, 0.08f, 0.35f, 0xFF282C34.toInt(), 0f, -a, bx, bz)
                batch.addBox(bx + 0.55f, by + flap * 0.3f, bz, 0.9f, 0.08f, 0.35f, 0xFF282C34.toInt(), 0f, -a, bx, bz)
            }
        }

        // Station waiting passengers
        for (p in sim.events.stationPax) {
            batch.addBox(p.wx, 1.0f, p.wz, 0.3f, 0.8f, 0.22f, p.color)
            batch.addBox(p.wx, 1.55f, p.wz, 0.2f, 0.22f, 0.2f, 0xFFE0AD82.toInt())
        }

        // Trains (3 cars)
        for (t in sim.events.trains) {
            addTrainConsist(batch, t, e)
        }

        // Incidents: wrecks, barriers, cones, casualties, responders
        for (inc in sim.events.incidents) {
            for ((cx, cz) in inc.cones) {
                batch.addCone(cx, 0.4f, cz, 0.22f, 0.7f, 6, 0xFFFF6A1A.toInt(), 0.2f)
            }
            for ((sx, sz, ang) in inc.skids) {
                batch.addBox(sx, 0.07f, sz, 0.35f, 0.03f, 2.8f, 0xFF1A1C20.toInt(), 0f, ang, sx, sz)
            }
            for (w in inc.wrecks) {
                batch.addBox(w.x, w.y + 0.55f, w.z, 1.5f, 0.6f, 3.0f, w.color, 0f, w.yaw, w.x, w.z)
                batch.addBox(w.x, w.y + 1.02f, w.z - 0.1f, 1.3f, 0.46f, 1.4f, 0xFF1B2A38.toInt(), 0f, w.yaw, w.x, w.z)
            }
            for (cas in inc.cas) {
                if (!cas.hidden) {
                    batch.addBox(cas.x, 0.22f, cas.z, 0.45f, 0.24f, 1.35f, cas.topCol, 0f, cas.yaw, cas.x, cas.z)
                    batch.addBox(cas.x, 0.24f, cas.z + 0.72f, 0.22f, 0.22f, 0.22f, SKIN_COLORS[cas.skinIdx], 0f, cas.yaw, cas.x, cas.z)
                }
            }
            for (st in inc.staff) {
                val suitCol = when (st.role) {
                    "police" -> 0xFF1E325C.toInt()
                    "fire" -> 0xFFC93028.toInt()
                    else -> 0xFFF2F4F8.toInt()
                }
                val hatCol = when (st.role) {
                    "police" -> 0xFF1E325C.toInt()
                    "fire" -> 0xFFF2C200.toInt()
                    else -> 0xFF2FA86A.toInt()
                }
                batch.addBox(st.x, 0.5f, st.z, 0.34f, 0.8f, 0.24f, suitCol, 0f, st.yaw, st.x, st.z)
                batch.addBox(st.x, 1.2f, st.z, 0.36f, 0.55f, 0.26f, suitCol, 0f, st.yaw, st.x, st.z)
                batch.addBox(st.x, 1.65f, st.z, 0.24f, 0.24f, 0.24f, 0xFFE0AD82.toInt(), 0f, st.yaw, st.x, st.z)
                batch.addBox(st.x, 1.82f, st.z, 0.28f, 0.12f, 0.28f, hatCol, 0f, st.yaw, st.x, st.z)
                if (st.role == "fire") {
                    batch.addBox(st.x + 0.2f, 1.0f, st.z + 0.4f, 0.14f, 0.14f, 0.9f, 0xFFE6C820.toInt(), 0f, st.yaw, st.x, st.z)
                }
            }
        }

        // Road Barriers
        for (b in sim.events.barriers) {
            val yaw = if (b.x != 0) 1.57f else 0f
            batch.addBox(b.cx, 0.55f, b.cz, 3.6f, 0.4f, 0.22f, 0xFFFF6A1A.toInt(), 0.2f, yaw, b.cx, b.cz)
            batch.addBox(b.cx, 0.85f, b.cz, 3.6f, 0.25f, 0.22f, 0xFFF4F4F0.toInt(), 0.2f, yaw, b.cx, b.cz)
        }

        // Zeus Lightning Bolts
        for (b in sim.events.bolts) {
            val r = 0.38f + (b.life * 0.6f)
            batch.addBox(b.x, 30f, b.z, r, 60f, r, 0xFFBCEEFF.toInt(), 1f)
            batch.addBox(b.x, 30f, b.z, r * 0.5f, 60f, r * 0.5f, 0xFFFFFFFF.toInt(), 1f)
        }

        // Particle Pools
        emitParticles(batch, sim.events.smokeParticles, 0f)
        emitParticles(batch, sim.events.fireParticles, 1f)
        emitParticles(batch, sim.events.waterParticles, 0.5f)
        emitParticles(batch, sim.events.sparkParticles, 1f)

        // Rain & Snow
        for (r in sim.events.rainDrops) {
            batch.addBox(r.x, r.y, r.z, 0.05f, r.len, 0.05f, 0xFFAAD8FF.toInt(), 0.35f)
        }
        for (s in sim.events.snowFlakes) {
            batch.addBox(s.x, s.y, s.z, s.size, s.size, s.size, 0xFFF6FAFF.toInt(), 0.5f)
        }
    }

    private fun emitParticles(batch: MeshBatch, list: List<SimpleParticle>, em: Float) {
        for (p in list) {
            val k = (p.life / p.maxLife).coerceIn(0.15f, 1f)
            val sz = p.size * (0.6f + 0.5f * k)
            batch.addBox(p.x, p.y, p.z, sz, sz, sz, p.color, em)
        }
    }

    private fun addTrainConsist(batch: MeshBatch, t: TrainAgent, e: Int) {
        val gap = 3.7f
        for (car in 0 until 3) {
            val ox = t.wx - sin(t.yaw) * (car * gap)
            val oz = t.wz - cos(t.yaw) * (car * gap)
            if (car == 0 && e <= 2) {
                // Steam locomotive
                batch.addBox(ox, 0.65f, oz, 1.55f, 0.45f, 3.2f, 0xFF1C2024.toInt(), 0f, t.yaw, ox, oz)
                batch.addBox(ox, 1.25f, oz + 0.25f, 1.2f, 1.0f, 2.1f, 0xFF283038.toInt(), 0f, t.yaw, ox, oz)
                batch.addBox(ox, 1.55f, oz - 0.9f, 1.45f, 1.4f, 1.1f, 0xFF7A2820.toInt(), 0f, t.yaw, ox, oz)
                batch.addCylinder(ox, 2.0f, oz + 0.95f, 0.22f, 0.26f, 0.9f, 6, 0xFF14181C.toInt())
                batch.addBox(ox, 1.1f, oz + 1.65f, 0.5f, 0.3f, 0.1f, 0xFFFFE590.toInt(), 1f, t.yaw, ox, oz)
                batch.addCone(ox, 0.45f, oz + 1.75f, 0.65f, 0.5f, 4, 0xFFD9302A.toInt(), 0f, t.yaw, ox, oz)
            } else if (car == 0) {
                // Sleek electric locomotive
                val body = if (e == 4) 0xFF102840.toInt() else 0xFF1F5FA8.toInt()
                val trim = if (e == 4) 0xFF22E3F2.toInt() else 0xFFE8C26A.toInt()
                batch.addBox(ox, 1.1f, oz, 1.6f, 1.4f, 3.3f, body, 0f, t.yaw, ox, oz)
                batch.addBox(ox, 1.3f, oz, 1.64f, 0.45f, 2.6f, 0xFF9AD0EE.toInt(), 0.7f, t.yaw, ox, oz)
                batch.addBox(ox, 0.65f, oz, 1.64f, 0.2f, 3.32f, trim, if (e == 4) 1f else 0.2f, t.yaw, ox, oz)
            } else {
                // Passenger coach
                val coach = if (e <= 2) 0xFF3E6846.toInt() else if (e == 4) 0xFF162C46.toInt() else 0xFFD9E0E6.toInt()
                val roof = if (e == 4) 0xFF22E3F2.toInt() else 0xFF5A626A.toInt()
                batch.addBox(ox, 1.05f, oz, 1.55f, 1.25f, 3.1f, coach, 0f, t.yaw, ox, oz)
                batch.addBox(ox, 1.72f, oz, 1.48f, 0.16f, 3.0f, roof, if (e == 4) 0.7f else 0f, t.yaw, ox, oz)
                batch.addBox(ox, 1.2f, oz, 1.58f, 0.42f, 2.5f, 0xFFFFE4A0.toInt(), 0.7f, t.yaw, ox, oz)
            }
        }
    }

    fun addEraVehicle(batch: MeshBatch, c: VehicleAgent, e: Int, clock: Float) {
        val y0 = c.wy
        val wx = c.wx
        val wz = c.wz
        val yaw = c.yaw
        val headLight = 0xFFFFF6C8.toInt()
        val tailLight = 0xFFD01818.toInt()

        // Era 0-1: Horse riders & horse-drawn carriages / carts
        if (e <= 1) {
            val bob = abs(sin(c.amp)) * 0.08f
            val hCol = if ((c.color and 1) == 0) 0xFF6B4226.toInt() else 0xFF42291A.toInt()
            if (c.type == "moto" || c.type == "policia") {
                // Rider on horse
                batch.addBox(wx, y0 + 0.85f + bob, wz, 0.48f, 0.55f, 1.4f, hCol, 0f, yaw, wx, wz)
                batch.addBox(wx, y0 + 1.25f + bob, wz + 0.65f, 0.24f, 0.55f, 0.3f, hCol, 0f, yaw, wx, wz)
                batch.addBox(wx, y0 + 1.45f + bob, wz - 0.05f, 0.36f, 0.55f, 0.28f, c.color, 0f, yaw, wx, wz)
                batch.addBox(wx, y0 + 1.85f + bob, wz - 0.05f, 0.22f, 0.22f, 0.22f, 0xFFE0AD82.toInt(), 0f, yaw, wx, wz)
            } else {
                // Horse pulling carriage / freight wagon / fire cart
                batch.addBox(wx, y0 + 0.8f + bob, wz + 1.5f, 0.45f, 0.5f, 1.25f, hCol, 0f, yaw, wx, wz)
                batch.addBox(wx, y0 + 1.15f + bob, wz + 2.05f, 0.22f, 0.45f, 0.26f, hCol, 0f, yaw, wx, wz)
                val bodyCol = if (c.type == "bombero") 0xFFC93028.toInt() else if (c.type == "ambulancia") 0xFFECEAE0.toInt() else c.color
                val cartL = if (c.type == "bus" || c.type == "camion") 2.6f else 2.0f
                batch.addBox(wx, y0 + 0.9f, wz - 0.5f, 1.4f, 0.85f, cartL, bodyCol, 0f, yaw, wx, wz)
                if (c.type == "bus" || c.type == "auto") {
                    batch.addBox(wx, y0 + 1.45f, wz - 0.5f, 1.35f, 0.25f, cartL - 0.1f, 0xFF5A3F28.toInt(), 0f, yaw, wx, wz)
                }
            }
            return
        }

        // Era 2+: Motorized vehicles
        when (c.type) {
            "moto" -> {
                batch.addBox(wx, y0 + 0.65f, wz, 0.4f, 0.45f, 1.3f, c.color, 0f, yaw, wx, wz)
                batch.addBox(wx, y0 + 1.25f, wz - 0.2f, 0.4f, 0.65f, 0.32f, 0xFF2A2F3A.toInt(), 0f, yaw, wx, wz)
                batch.addBox(wx, y0 + 0.8f, wz + 0.7f, 0.2f, 0.16f, 0.08f, headLight, 1f, yaw, wx, wz)
            }
            "camion", "bombero" -> {
                batch.addBox(wx, y0 + 1.15f, wz + 1.8f, 1.9f, 1.5f, 1.4f, c.color, 0f, yaw, wx, wz)
                batch.addBox(wx, y0 + 1.55f, wz - 0.7f, 2.0f, 2.1f, 3.6f, if (c.type == "bombero") 0xFFD9302A.toInt() else 0xFFECECE6.toInt(), 0f, yaw, wx, wz)
                batch.addBox(wx, y0 + 0.5f, wz, 1.7f, 0.35f, 5.2f, 0xFF222222.toInt(), 0f, yaw, wx, wz)
                batch.addBox(wx - 0.6f, y0 + 0.85f, wz + 2.52f, 0.35f, 0.18f, 0.08f, headLight, 1f, yaw, wx, wz)
                batch.addBox(wx + 0.6f, y0 + 0.85f, wz + 2.52f, 0.35f, 0.18f, 0.08f, headLight, 1f, yaw, wx, wz)
                if (c.type == "bombero") {
                    val siren = if (((clock * 6f).toInt() % 2) == 0) 0xFFFF2020.toInt() else 0xFF2A6FD6.toInt()
                    batch.addBox(wx, y0 + 2.05f, wz + 1.8f, 0.8f, 0.2f, 0.35f, siren, if (c.siren) 1f else 0.7f, yaw, wx, wz)
                }
            }
            "bus" -> {
                batch.addBox(wx, y0 + 1.4f, wz, 2.0f, 2.1f, 6.2f, c.color, 0f, yaw, wx, wz)
                batch.addBox(wx, y0 + 1.8f, wz, 2.04f, 0.75f, 5.6f, 0xFF1B2A38.toInt(), 0f, yaw, wx, wz)
                batch.addBox(wx - 0.6f, y0 + 0.85f, wz + 3.12f, 0.38f, 0.18f, 0.08f, headLight, 1f, yaw, wx, wz)
                batch.addBox(wx + 0.6f, y0 + 0.85f, wz + 3.12f, 0.38f, 0.18f, 0.08f, headLight, 1f, yaw, wx, wz)
                batch.addBox(wx - 0.6f, y0 + 0.9f, wz - 3.12f, 0.38f, 0.18f, 0.08f, tailLight, 1f, yaw, wx, wz)
                batch.addBox(wx + 0.6f, y0 + 0.9f, wz - 3.12f, 0.38f, 0.18f, 0.08f, tailLight, 1f, yaw, wx, wz)
            }
            else -> { // auto, ambulancia, policia
                batch.addBox(wx, y0 + 0.6f, wz, 1.55f, 0.58f, 3.2f, c.color, 0f, yaw, wx, wz)
                batch.addBox(wx, y0 + 1.12f, wz - 0.1f, 1.45f, 0.52f, 1.5f, 0xFF1B2A38.toInt(), 0f, yaw, wx, wz)
                batch.addBox(wx, y0 + 1.38f, wz - 0.1f, 1.38f, 0.08f, 1.3f, c.color, 0f, yaw, wx, wz)
                if (e == 2) {
                    // Vintage brass radiator accent
                    batch.addBox(wx, y0 + 0.65f, wz + 1.61f, 0.6f, 0.45f, 0.08f, 0xFFE8C26A.toInt(), 0.2f, yaw, wx, wz)
                }
                batch.addBox(wx - 0.5f, y0 + 0.62f, wz + 1.62f, 0.32f, 0.15f, 0.06f, headLight, 1f, yaw, wx, wz)
                batch.addBox(wx + 0.5f, y0 + 0.62f, wz + 1.62f, 0.32f, 0.15f, 0.06f, headLight, 1f, yaw, wx, wz)
                val tlEm = if (c.brake > 0.2f) 1f else 0.6f
                batch.addBox(wx - 0.5f, y0 + 0.68f, wz - 1.62f, 0.32f, 0.15f, 0.06f, tailLight, tlEm, yaw, wx, wz)
                batch.addBox(wx + 0.5f, y0 + 0.68f, wz - 1.62f, 0.32f, 0.15f, 0.06f, tailLight, tlEm, yaw, wx, wz)
                if (c.type == "policia" || c.type == "ambulancia") {
                    val siren = if (((clock * 6f).toInt() % 2) == 0) 0xFFFF2020.toInt() else 0xFF2A6FD6.toInt()
                    batch.addBox(wx, y0 + 1.48f, wz - 0.1f, 0.8f, 0.16f, 0.3f, siren, 1f, yaw, wx, wz)
                }
            }
        }
        if (e == 4) {
            batch.addBox(wx, y0 + 0.15f, wz, 1.2f, 0.08f, 2.4f, 0xFF22E3F2.toInt(), 1f, yaw, wx, wz)
        }
    }
}
