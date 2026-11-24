package com.example.simulation

import java.util.ArrayDeque
import kotlin.math.*
import kotlin.random.Random

data class StationPax(val wx: Float, val wz: Float, val x: Int, val y: Int, val color: Int)

data class TrainAgent(
    var path: MutableList<IntArray>,
    var fx: Int,
    var fy: Int,
    var tx: Int,
    var ty: Int,
    var p: Float = 0f,
    var spd: Float = 2.6f,
    var wait: Float = 0f,
    var dx: Float = 0f,
    var dz: Float = 1f,
    var yaw: Float = 0f,
    var pax: Int = 6,
    var srcX: Int = 0,
    var srcY: Int = 0,
    var dstX: Int = 0,
    var dstY: Int = 0,
    var dead: Boolean = false,
    var wx: Float = 0f,
    var wy: Float = 0.08f,
    var wz: Float = 0f,
    var puffAcc: Float = 0f
)

data class BarrItem(val cx: Float, val cz: Float, val x: Int, val y: Int)

data class WreckItem(
    var x: Float,
    var y: Float,
    var z: Float,
    var yaw: Float,
    val color: Int,
    val type: String,
    var vx: Float = 0f,
    var vz: Float = 0f,
    var spin: Float = 0f
)

data class Casualty(
    var x: Float,
    var z: Float,
    var yaw: Float,
    val skinIdx: Int,
    val topCol: Int,
    val botCol: Int,
    val isRider: Boolean = false,
    var onStretcher: Boolean = false,
    var hidden: Boolean = false
)

data class EmergencyStaff(
    val role: String, // "police", "fire", "medic", "civil"
    var x: Float,
    var z: Float,
    var yaw: Float,
    var tx: Float,
    var tz: Float,
    var wait: Float = 0f,
    var pose: String = "notes",
    var ph: Float = 0f,
    var mv: Float = 0f,
    var carry: Boolean = false,
    var carryIdx: Int = -1
)

data class Incident(
    val id: Int,
    val kind: String, // "crash", "fire", "medical"
    val x: Int,
    val y: Int,
    var cx: Float,
    var cz: Float,
    val bx: Int = -1,
    val by: Int = -1,
    val blkKeys: MutableList<Int> = mutableListOf(),
    val units: MutableList<VehicleAgent> = mutableListOf(),
    val staff: MutableList<EmergencyStaff> = mutableListOf(),
    val wrecks: MutableList<WreckItem> = mutableListOf(),
    val cas: MutableList<Casualty> = mutableListOf(),
    val cones: MutableList<Pair<Float, Float>> = mutableListOf(),
    val skids: MutableList<Triple<Float, Float, Float>> = mutableListOf(),
    var fireInt: Float = 0f,
    var phase: String = "approach",
    var timer: Float = 0f,
    var arrived: Int = 0,
    val startClock: Float = 0f,
    var dead: Boolean = false,
    var emitAcc: Float = 0f
) {
    fun statusLabel(): String = when (phase) {
        "approach" -> "En camino (${units.size})"
        "work" -> "Atendiendo (${timer.roundToInt()}s)"
        else -> "Finalizando"
    }

    fun emoji(): String = when (kind) {
        "fire" -> "🔥"
        "crash" -> "💥"
        else -> "🚑"
    }

    fun title(): String = when (kind) {
        "fire" -> "Incendio"
        "crash" -> "Accidente vial"
        else -> "Emergencia médica"
    }
}

data class SimpleParticle(
    var x: Float,
    var y: Float,
    var z: Float,
    var vx: Float,
    var vy: Float,
    var vz: Float,
    var life: Float,
    val maxLife: Float,
    val size: Float,
    val color: Int
)

data class RainDrop(
    var x: Float,
    var y: Float,
    var z: Float,
    var len: Float
)

data class SnowFlake(
    var x: Float,
    var y: Float,
    var z: Float,
    var size: Float,
    var seed: Float
)

data class BoltFx(val x: Float, val z: Float, var life: Float)
data class DivRainFx(val x: Float, val z: Float, var life: Float, var acc: Float = 0f)

class WeatherEngine {
    var auto: Boolean = true
    var reg: String = "clr"
    var dur: Float = 90f
    var tAcc: Float = 0f
    var cloud: Float = 0.05f
    var rain: Float = 0f
    var fog: Float = 0f
    var wind: Float = 0.25f
    var windDir: Float = 0.6f
    var storm: Boolean = false
    var temp: Float = 22f
    var snow: Float = 0f
    var cover: Float = 0f
    var wet: Float = 0f
    var wave: String? = null
    var waveT: Float = 0f
    var waveA: Float = 0f
    var ltT: Float = 8f
    var flash: Float = 0f
    var yearClock: Float = 0f
    var rainLock: Boolean = false

    fun reset() {
        auto = true
        reg = "clr"
        dur = 90f
        tAcc = 0f
        cloud = 0.05f
        rain = 0f
        fog = 0f
        wind = 0.25f
        windDir = 0.6f
        storm = false
        temp = 22f
        snow = 0f
        cover = 0f
        wet = 0f
        wave = null
        waveT = 0f
        waveA = 0f
        ltT = 8f
        flash = 0f
        yearClock = 0f
        rainLock = false
    }

    fun yt(): Float = ((yearClock % (4f * YARD_DAYS)) / (4f * YARD_DAYS)).coerceIn(0f, 0.9999f)

    fun seasonIndex(): Int = (yt() * 4f).toInt().coerceIn(0, 3)

    fun seasonName(): String = SEASON_NAMES[seasonIndex()]

    fun windNow(): Float = wind * (1f + 0.35f * sin(yearClock * 1.4f))

    fun autumnWeight(): Float {
        val u = yt()
        val f = abs(u - 0.625f)
        return max(0f, 1f - min(f, 1f - f) / 0.16f)
    }

    fun winterWeight(): Float {
        val u = yt()
        val f = abs(u - 0.875f)
        return max(0f, 1f - min(f, 1f - f) / 0.16f)
    }

    fun setRegime(k: String, sim: CitySimulation) {
        reg = k
        val r = WEATHER_REGIMES[k] ?: WEATHER_REGIMES.getValue("clr")
        dur = (50f + Random.nextFloat() * 65f) * if (k == "clr") 1.5f else 1f
        tAcc = 0f
        rainLock = false
        val msg = when {
            r.storm -> "⛈ Tormenta en camino"
            k == "rn" -> "🌧 Empezó a llover"
            k == "fg" -> "🌫 Banco de niebla"
            k == "wd" -> "💨 Ráfagas de viento"
            else -> null
        }
        if (msg != null) sim.addLogMsg(msg)
        if (r.storm) sim.triggerSound("thunder")
    }

    fun toggleRain(sim: CitySimulation) {
        auto = false
        val next = if (rain > 0.35f) "clr" else if (temp <= 2f) "rn" else "stm"
        setRegime(next, sim)
    }

    fun toggleAuto(): Boolean {
        auto = !auto
        return auto
    }

    fun step(dt: Float, hourNow: Float, era: Int, sim: CitySimulation) {
        yearClock += dt / 220f
        if (flash > 0f) flash = max(0f, flash - dt * 3.8f)

        if (waveT > 0f) {
            waveT -= dt
            if (waveT <= 0f) {
                wave = null
                waveA = 0f
            }
        } else if (Random.nextFloat() < dt / 180f) {
            val s = seasonIndex()
            if (s == 1) {
                wave = "calor"
                waveA = 7f + Random.nextFloat() * 5f
                waveT = 40f + Random.nextFloat() * 40f
                sim.addLogMsg("🌡 Ola de calor")
            } else if (s == 3) {
                wave = "frio"
                waveA = -(6f + Random.nextFloat() * 5f)
                waveT = 40f + Random.nextFloat() * 40f
                sim.addLogMsg("🥶 Ola de frío")
            }
        }

        if (auto) {
            tAcc += dt
            if (tAcc >= dur) {
                val list = SEASON_PROBS[seasonIndex()]
                var r = Random.nextDouble()
                var picked = "clr"
                for ((k, p) in list) {
                    r -= p
                    if (r <= 0.0) {
                        picked = k
                        break
                    }
                }
                setRegime(picked, sim)
            }
        }

        val target = WEATHER_REGIMES[reg] ?: WEATHER_REGIMES.getValue("clr")
        val k = min(1f, dt * 0.25f)
        cloud += (target.cloud - cloud) * k
        rain += (target.rain - rain) * k
        fog += (target.fog - fog) * k
        wind += (target.wind - wind) * k
        windDir += sin(yearClock * 0.8f) * 0.04f * dt
        storm = target.storm

        val u = yt()
        val seasT = 18.5f + 9f * sin((u - 0.125f) * PI.toFloat() * 2f)
        val diurT = 4f * sin((hourNow - 9f) * PI.toFloat() / 12f)
        val tgT = seasT + diurT - rain * 4f - cloud * 2f + waveA
        temp += (tgT - temp) * min(1f, dt * 0.18f)

        val tgtSnow = if (temp <= 2f) rain * ((2f - temp) / 3f).coerceIn(0f, 1f) else 0f
        snow += (tgtSnow - snow) * min(1f, dt * 0.6f)

        val winW = winterWeight()
        if (snow > 0.08f) {
            cover = min(1f, cover + snow * dt * 0.055f)
        } else {
            val melt = max(0f, temp - 1f) * 0.008f + (1f - winW) * 0.01f
            cover = max(0f, cover - dt * (0.004f + melt))
        }

        val liqRain = rain * (1f - snow)
        wet = if (liqRain > 0.08f) {
            min(1f, wet + liqRain * dt * 0.25f)
        } else {
            max(0f, wet - dt * (0.014f + max(0f, temp - 15f) * 0.001f))
        }

        if (storm && era != 4) {
            ltT -= dt
            if (ltT <= 0f) {
                ltT = 5f + Random.nextFloat() * 14f
                flash = 1f
                sim.triggerSound("thunder")
            }
        }
    }

    fun wxIcon(hour: Float): String {
        val nt = hour >= 20f || hour < 6f
        return when {
            snow > 0.25f -> "❄️"
            storm -> "⛈"
            rain > 0.5f -> "🌧"
            rain > 0.15f -> "🌦"
            fog > 0.5f -> "🌫"
            cloud > 0.7f -> "☁️"
            cloud > 0.3f -> if (nt) "☁️" else "⛅"
            else -> if (nt) "🌙" else "☀️"
        }
    }
}

fun lerpColorRgb(c1: Int, c2: Int, t: Float): Int {
    val k = t.coerceIn(0f, 1f)
    val r1 = (c1 shr 16) and 0xFF
    val g1 = (c1 shr 8) and 0xFF
    val b1 = c1 and 0xFF
    val r2 = (c2 shr 16) and 0xFF
    val g2 = (c2 shr 8) and 0xFF
    val b2 = c2 and 0xFF
    val r = (r1 + (r2 - r1) * k).roundToInt().coerceIn(0, 255)
    val g = (g1 + (g2 - g1) * k).roundToInt().coerceIn(0, 255)
    val b = (b1 + (b2 - b1) * k).roundToInt().coerceIn(0, 255)
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}

fun seasonCol(baseHex: Int, treeKind: Int, seed: Float, era: Int, wx: WeatherEngine): Int {
    if (era == 4) return baseHex
    var col = baseHex
    val aut = wx.autumnWeight()
    if (aut > 0.05f && treeKind != 1) {
        val pal = intArrayOf(0xFFD9822B.toInt(), 0xFFC2462A.toInt(), 0xFFE6B438.toInt(), 0xFFA6522A.toInt())
        val tc = pal[((seed * 4f).toInt()).coerceIn(0, 3)]
        col = lerpColorRgb(col, tc, aut * 0.85f)
    }
    if (treeKind == 2) {
        val win = wx.winterWeight()
        if (win > 0.1f) {
            col = lerpColorRgb(col, 0xFF6B5A46.toInt(), win * 0.7f)
        }
    }
    if (wx.cover > 0.05f) {
        col = lerpColorRgb(col, 0xFFF2F6FA.toInt(), wx.cover * if (treeKind == 1) 0.45f else 0.7f)
    }
    return col
}

class WorldEventsController(private val sim: CitySimulation) {
    val trains = mutableListOf<TrainAgent>()
    val stationPax = mutableListOf<StationPax>()
    val incidents = mutableListOf<Incident>()
    val blk = HashMap<Int, Incident>()
    val barriers = mutableListOf<BarrItem>()
    val trainBlocks = HashSet<Int>()

    val smokeParticles = ArrayList<SimpleParticle>(260)
    val fireParticles = ArrayList<SimpleParticle>(200)
    val waterParticles = ArrayList<SimpleParticle>(320)
    val sparkParticles = ArrayList<SimpleParticle>(80)

    val rainDrops = ArrayList<RainDrop>(600)
    val snowFlakes = ArrayList<SnowFlake>(450)
    val bolts = mutableListOf<BoltFx>()
    val divRain = mutableListOf<DivRainFx>()

    var incAuto = true
    var incIdSeq = 0
    private var incTimer = 28f + Random.nextFloat() * 20f
    private var trAcc = 0f
    private var stnCache = mutableListOf<Pair<Int, Int>>()

    fun clearAll() {
        trains.clear()
        stationPax.clear()
        incidents.clear()
        blk.clear()
        barriers.clear()
        trainBlocks.clear()
        smokeParticles.clear()
        fireParticles.clear()
        waterParticles.clear()
        sparkParticles.clear()
        rainDrops.clear()
        snowFlakes.clear()
        bolts.clear()
        divRain.clear()
        stnCache.clear()
        incTimer = 28f + Random.nextFloat() * 20f
    }

    fun rebuildBarriers() {
        barriers.clear()
        for ((k, _) in blk) {
            val x = k % GRID_SIZE
            val y = k / GRID_SIZE
            for (d in DIRS) {
                val nx = x + d[0]
                val ny = y + d[1]
                if (!sim.isRoad(nx, ny)) continue
                val nk = ny * GRID_SIZE + nx
                if (!blk.containsKey(nk)) {
                    barriers.add(BarrItem(sim.gx2x((x + nx) * 0.5f), sim.gx2x((y + ny) * 0.5f), x - nx, y - ny))
                }
            }
        }
    }

    fun refreshStationsCache() {
        stnCache.clear()
        for (y in 0 until GRID_SIZE) {
            for (x in 0 until GRID_SIZE) {
                val c = sim.grid[y][x]
                if (c != null && c.t == "rail" && c.st) {
                    stnCache.add(x to y)
                }
            }
        }
        rebuildStationPax()
    }

    private fun rebuildStationPax() {
        stationPax.clear()
        val colors = intArrayOf(
            0xFFD9483A.toInt(), 0xFF2A6FD6.toInt(), 0xFFE8C26A.toInt(),
            0xFF3A8A40.toInt(), 0xFFF0F0EA.toInt(), 0xFF8A3AD6.toInt()
        )
        for ((sx, sy) in stnCache) {
            val h = sim.isRail(sx + 1, sy) || sim.isRail(sx - 1, sy)
            val cx = sim.gx2x(sx)
            val cz = sim.gx2x(sy)
            val n = if (sim.era >= 2) (2 + Random.nextInt(4)) else 0
            for (i in 0 until n) {
                val along = (Random.nextFloat() - 0.5f) * 5.5f
                val side = if (Random.nextBoolean()) 1f else -1f
                val lat = side * (2.6f + Random.nextFloat() * 0.8f)
                stationPax.add(
                    StationPax(
                        wx = cx + if (h) along else lat,
                        wz = cz + if (h) lat else along,
                        x = sx,
                        y = sy,
                        color = colors.random()
                    )
                )
            }
        }
    }

    private fun rbfs(sx: Int, sy: Int, gx: Int, gy: Int): MutableList<IntArray>? {
        val prev = HashMap<Int, Int>(64)
        val q = ArrayDeque<Int>(64)
        val start = sy * GRID_SIZE + sx
        prev[start] = -1
        q.addLast(start)
        while (q.isNotEmpty()) {
            val c = q.removeFirst()
            val x = c % GRID_SIZE
            val y = c / GRID_SIZE
            if (x == gx && y == gy) {
                val path = ArrayList<IntArray>()
                var k = c
                while (prev[k] != null && prev[k] != -1) {
                    path.add(0, intArrayOf(k % GRID_SIZE, k / GRID_SIZE))
                    k = prev[k]!!
                }
                return path
            }
            for (d in DIRS) {
                val nx = x + d[0]
                val ny = y + d[1]
                val nk = ny * GRID_SIZE + nx
                if (sim.rLink(x, y, nx, ny) && !prev.containsKey(nk)) {
                    prev[nk] = c
                    q.addLast(nk)
                }
            }
        }
        return null
    }

    fun stepTrains(dt: Float, pop: Int) {
        trAcc += dt
        if (trAcc > 0.7f) {
            trAcc = 0f
            manageTrains(pop)
        }
        trainBlocks.clear()
        for (i in trains.indices.reversed()) {
            val t = trains[i]
            updateTrain(t, dt)
            if (t.dead) {
                trains.removeAt(i)
            } else {
                for (dx in -1..1) {
                    for (dy in -1..1) {
                        val x = t.fx + dx
                        val y = t.fy + dy
                        val c = if (sim.inBounds(x, y)) sim.grid[y][x] else null
                        if (c != null && c.t == "road" && c.rx.isNotEmpty()) {
                            trainBlocks.add(y * GRID_SIZE + x)
                        }
                    }
                }
            }
        }
    }

    private fun manageTrains(pop: Int) {
        if (stnCache.size < 2 || pop < 4) {
            trains.clear()
            return
        }
        val want = min(4, max(1, stnCache.size / 2))
        while (trains.size > want) trains.removeAt(trains.lastIndex)
        if (trains.size < want) {
            val a = stnCache.random()
            var b = stnCache.random()
            if (a == b) b = stnCache.firstOrNull { it != a } ?: return
            val path = rbfs(a.first, a.second, b.first, b.second) ?: return
            val cx = sim.gx2x(a.first)
            val cz = sim.gx2x(a.second)
            trains.add(
                TrainAgent(
                    path = path,
                    fx = a.first, fy = a.second,
                    tx = a.first, ty = a.second,
                    spd = if (sim.era >= 3) 3.4f else 2.5f,
                    pax = max(4, min(40, pop / max(2, stnCache.size) + 4)),
                    srcX = a.first, srcY = a.second,
                    dstX = b.first, dstY = b.second,
                    wx = cx, wz = cz
                )
            )
            sim.triggerSound("train")
        }
    }

    private fun updateTrain(t: TrainAgent, dt: Float) {
        if (!sim.isRail(t.fx, t.fy) || !sim.isRail(t.tx, t.ty)) {
            t.dead = true
            return
        }
        if (t.wait > 0f) {
            t.wait -= dt
            return
        }
        if (t.p >= 1f) {
            t.fx = t.tx
            t.fy = t.ty
            if (t.path.isNotEmpty()) {
                val n = t.path.removeAt(0)
                t.tx = n[0]
                t.ty = n[1]
                t.p = 0f
            } else {
                // Arrived at destination station
                val c = sim.grid[t.fy][t.fx]
                if (c != null && c.t == "rail" && c.st) {
                    val earn = max(4, (t.pax * 0.7f).roundToInt())
                    sim.coins += earn
                    sim.triggerSound("train")
                }
                val nextDst = stnCache.filter { it.first != t.fx || it.second != t.fy }.randomOrNull()
                if (nextDst == null) {
                    t.dead = true
                    return
                }
                val newPath = rbfs(t.fx, t.fy, nextDst.first, nextDst.second)
                if (newPath == null) {
                    t.dead = true
                    return
                }
                t.path = newPath
                t.srcX = t.fx; t.srcY = t.fy
                t.dstX = nextDst.first; t.dstY = nextDst.second
                t.spd = if (sim.era >= 3) 3.4f else 2.5f
                t.wait = 2.5f
            }
            return
        }
        t.p = min(1f, t.p + dt * t.spd)
        val gxf = t.fx + (t.tx - t.fx) * t.p
        val gyf = t.fy + (t.ty - t.fy) * t.p
        val ddx = (t.tx - t.fx).toFloat()
        val ddz = (t.ty - t.fy).toFloat()
        if (ddx != 0f || ddz != 0f) {
            t.dx = ddx; t.dz = ddz
            t.yaw = CitySimulation.angLerp(t.yaw, atan2(t.dx, t.dz), dt * 8f)
        }
        t.wx = sim.gx2x(gxf)
        t.wz = sim.gx2x(gyf)
        t.wy = 0.08f
        if (sim.era <= 2) {
            t.puffAcc += dt
            if (t.puffAcc > 0.14f) {
                t.puffAcc = 0f
                val px = t.wx + sin(t.yaw) * 1.6f
                val pz = t.wz + cos(t.yaw) * 1.6f
                addSmoke(px, 3.0f, pz, (Random.nextFloat() - 0.5f) * 0.5f, 1.4f, (Random.nextFloat() - 0.5f) * 0.5f, 1.6f, 0.45f, 0xFFDDDDD8.toInt())
            }
        }
    }

    fun stepIncidents(dt: Float, st: CityStats) {
        if (incAuto) {
            incTimer -= dt
            if (incTimer <= 0f) {
                val base = 52f + Random.nextFloat() * 44f
                incTimer = base / (1f + sim.wx.rain * 0.45f)
                maybeIncident(st)
            }
        }
        for (i in incidents.indices.reversed()) {
            val inc = incidents[i]
            updateIncident(inc, dt)
            if (inc.dead) {
                for (k in inc.blkKeys) blk.remove(k)
                for (u in inc.units) {
                    u.em = null
                    u.emHold = false
                    u.siren = false
                }
                incidents.removeAt(i)
                rebuildBarriers()
            }
        }
    }

    private fun maybeIncident(st: CityStats) {
        if (incidents.size >= 2 || st.roads < 8) return
        val r = Random.nextFloat()
        when {
            r < 0.40f -> triggerIncident("crash")
            r < 0.72f -> triggerIncident("fire")
            else -> triggerIncident("medical")
        }
    }

    fun triggerIncident(kind: String): Incident? {
        if (incidents.size >= 3) return null
        return when (kind) {
            "crash" -> startCrash()
            "fire" -> startFire()
            else -> startMedical()
        }
    }

    private fun startCrash(): Incident? {
        val cands = ArrayList<Pair<Int, Int>>()
        for (y in 0 until GRID_SIZE) {
            for (x in 0 until GRID_SIZE) {
                val cell = sim.grid[y][x]
                if (cell != null && cell.t == "road" && !cell.br && !blk.containsKey(y * GRID_SIZE + x)) {
                    cands.add(x to y)
                }
            }
        }
        if (cands.isEmpty()) return null
        val (gx, gy) = cands.random()
        val cx = sim.gx2x(gx)
        val cz = sim.gx2x(gy)
        val inc = Incident(
            id = ++incIdSeq,
            kind = "crash",
            x = gx, y = gy,
            cx = cx, cz = cz,
            startClock = sim.clockSec
        )
        inc.blkKeys.add(gy * GRID_SIZE + gx)
        blk[gy * GRID_SIZE + gx] = inc

        val c1 = VEHICLE_COLORS.random()
        val c2 = VEHICLE_COLORS.random()
        inc.wrecks.add(WreckItem(cx - 1.1f, 0.08f, cz - 0.5f, 0.4f, c1, "auto"))
        inc.wrecks.add(WreckItem(cx + 1.0f, 0.08f, cz + 0.6f, 2.5f, c2, "auto"))
        inc.cones.add(cx - 2.6f to cz - 2.6f)
        inc.cones.add(cx + 2.6f to cz + 2.6f)
        inc.cones.add(cx - 2.6f to cz + 2.6f)
        inc.skids.add(Triple(cx - 0.5f, cz - 1.0f, 0.35f))
        val topC = intArrayOf(0xFFD9483A.toInt(), 0xFF2A6FD6.toInt(), 0xFFE8C26A.toInt()).random()
        inc.cas.add(Casualty(cx + 1.9f, cz - 0.9f, 0.8f, Random.nextInt(SKIN_COLORS.size), topC, 0xFF333840.toInt()))
        inc.timer = 14f

        incidents.add(inc)
        rebuildBarriers()
        dispatchUnits(inc, police = 1, fire = 0, medic = 1)
        sim.triggerSound("crash")
        sim.addLogMsg("💥 Accidente de tránsito en la ciudad.")
        return inc
    }

    private fun startFire(): Incident? {
        val cands = ArrayList<Pair<Int, Int>>()
        for (y in 0 until GRID_SIZE) {
            for (x in 0 until GRID_SIZE) {
                val c = sim.grid[y][x] ?: continue
                if ((c.t == "house" || c.t == "b") && sim.adjRoad(x, y)) {
                    for (d in DIRS) {
                        val rx = x + d[0]; val ry = y + d[1]
                        if (sim.isRoad(rx, ry) && !blk.containsKey(ry * GRID_SIZE + rx)) {
                            cands.add(x to y)
                            break
                        }
                    }
                }
            }
        }
        if (cands.isEmpty()) return null
        val (bx, by) = cands.random()
        var rx = bx; var ry = by
        for (d in DIRS) {
            val nx = bx + d[0]; val ny = by + d[1]
            if (sim.isRoad(nx, ny)) { rx = nx; ry = ny; break }
        }
        val inc = Incident(
            id = ++incIdSeq,
            kind = "fire",
            x = rx, y = ry,
            cx = sim.gx2x(bx), cz = sim.gx2x(by),
            bx = bx, by = by,
            fireInt = 0.7f,
            startClock = sim.clockSec
        )
        inc.blkKeys.add(ry * GRID_SIZE + rx)
        blk[ry * GRID_SIZE + rx] = inc
        inc.cones.add(sim.gx2x(rx) - 2.3f to sim.gx2x(ry) - 2.3f)
        inc.cones.add(sim.gx2x(rx) + 2.3f to sim.gx2x(ry) + 2.3f)
        inc.timer = 16f

        incidents.add(inc)
        rebuildBarriers()
        dispatchUnits(inc, police = 1, fire = 2, medic = 0)
        sim.addLogMsg("🔥 ¡Incendio reportado en un edificio!")
        return inc
    }

    private fun startMedical(): Incident? {
        val walkers = sim.villagers.filter { !it.dead && it.visible && !it.working }
        val (gx, gy, wx, wz, yaw) = if (walkers.isNotEmpty()) {
            val v = walkers.random()
            arrayOf<Any>(v.fx, v.fy, v.wx, v.wz, v.yaw)
        } else {
            val rds = ArrayList<Pair<Int, Int>>()
            for (y in 0 until GRID_SIZE) {
                for (x in 0 until GRID_SIZE) {
                    if (sim.isRoad(x, y) && !blk.containsKey(y * GRID_SIZE + x)) rds.add(x to y)
                }
            }
            if (rds.isEmpty()) return null
            val p = rds.random()
            arrayOf<Any>(p.first, p.second, sim.gx2x(p.first), sim.gx2x(p.second), 0.5f)
        }
        val ix = gx as Int
        val iy = gy as Int
        val cx = wx as Float
        val cz = wz as Float
        val inc = Incident(
            id = ++incIdSeq,
            kind = "medical",
            x = ix, y = iy,
            cx = cx, cz = cz,
            startClock = sim.clockSec
        )
        inc.blkKeys.add(iy * GRID_SIZE + ix)
        blk[iy * GRID_SIZE + ix] = inc
        val topC = intArrayOf(0xFFE04A4A.toInt(), 0xFF3A8AD6.toInt(), 0xFF2FA86A.toInt()).random()
        inc.cas.add(Casualty(cx, cz, yaw as Float, Random.nextInt(SKIN_COLORS.size), topC, 0xFF333840.toInt()))
        inc.timer = 12f

        incidents.add(inc)
        rebuildBarriers()
        dispatchUnits(inc, police = 0, fire = 0, medic = 1)
        sim.addLogMsg("🚑 Emergencia médica en la vía pública.")
        return inc
    }

    private fun dispatchUnits(inc: Incident, police: Int, fire: Int, medic: Int) {
        fun assign(type: String, count: Int) {
            repeat(count) {
                val existing = sim.vehicles.firstOrNull { it.type == type && it.em == null && !it.dead }
                val unit = existing ?: sim.spawnEmergencyVehicle(type, inc.x, inc.y)
                if (unit != null) {
                    unit.em = inc
                    unit.siren = true
                    unit.emHold = false
                    val path = sim.bfsPath(unit.tx, unit.ty, emergency = true, ped = false) { gx, gy ->
                        abs(gx - inc.x) + abs(gy - inc.y) <= 1
                    }
                    if (path != null) unit.path = path
                    inc.units.add(unit)
                }
            }
        }
        assign("policia", police)
        assign("bombero", fire)
        assign("ambulancia", medic)
    }

    private fun updateIncident(inc: Incident, dt: Float) {
        inc.emitAcc += dt
        if (inc.kind == "fire" && inc.fireInt > 0.05f && inc.emitAcc > 0.09f) {
            inc.emitAcc = 0f
            addFire(
                inc.cx + (Random.nextFloat() - 0.5f) * 3.4f,
                1.5f + Random.nextFloat() * 2.2f,
                inc.cz + (Random.nextFloat() - 0.5f) * 3.4f,
                (Random.nextFloat() - 0.5f) * 0.6f, 2.2f + Random.nextFloat() * 1.4f, (Random.nextFloat() - 0.5f) * 0.6f,
                0.7f, 0.7f, if (Random.nextBoolean()) 0xFFFF6A1A.toInt() else 0xFFFFC82A.toInt()
            )
            addSmoke(
                inc.cx + (Random.nextFloat() - 0.5f) * 2.5f,
                3.8f,
                inc.cz + (Random.nextFloat() - 0.5f) * 2.5f,
                (Random.nextFloat() - 0.5f) * 0.8f, 2.1f, (Random.nextFloat() - 0.5f) * 0.8f,
                2.2f, 1.0f, 0xFF33363B.toInt()
            )
        } else if (inc.kind == "crash" && inc.emitAcc > 0.16f) {
            inc.emitAcc = 0f
            addSmoke(inc.cx - 0.8f, 1.1f, inc.cz - 0.4f, 0.1f, 1.1f, 0.1f, 1.4f, 0.55f, 0xFF55595E.toInt())
        }

        if (inc.phase == "approach") {
            var arrivedCount = 0
            for (u in inc.units) {
                if (u.emHold || abs(u.tx - inc.x) + abs(u.ty - inc.y) <= 1) {
                    u.emHold = true
                    u.path = null
                    arrivedCount++
                }
            }
            val elapsed = sim.clockSec - inc.startClock
            if ((arrivedCount > 0 && arrivedCount >= inc.units.size) || elapsed > 12f) {
                inc.phase = "work"
                spawnStaff(inc)
            }
        } else if (inc.phase == "work") {
            inc.timer -= dt
            for (st in inc.staff) {
                st.ph += dt * 5f
                if (st.role == "fire" && Random.nextFloat() < 0.35f) {
                    val dirX = inc.cx - st.x
                    val dirZ = inc.cz - st.z
                    val len = max(0.1f, hypot(dirX, dirZ))
                    addWater(
                        st.x, 1.2f, st.z,
                        dirX / len * 6.5f + (Random.nextFloat() - 0.5f) * 0.9f,
                        2.5f,
                        dirZ / len * 6.5f + (Random.nextFloat() - 0.5f) * 0.9f,
                        0.65f, 0.28f, 0xFF6CD4FF.toInt()
                    )
                }
            }
            if (inc.kind == "fire") {
                inc.fireInt = max(0.05f, inc.fireInt - dt * 0.045f)
            }
            if (inc.timer <= 0f) {
                inc.phase = "clear"
                inc.dead = true
                sim.addLogMsg("✅ ${inc.title()} resuelto por los servicios.")
            }
        }
    }

    private fun spawnStaff(inc: Incident) {
        if (inc.staff.isNotEmpty()) return
        when (inc.kind) {
            "crash" -> {
                inc.staff.add(EmergencyStaff("police", inc.cx - 2.2f, inc.cz - 2.0f, 0.5f, inc.cx, inc.cz, pose = "notes"))
                inc.staff.add(EmergencyStaff("medic", inc.cx + 2.2f, inc.cz - 1.5f, -0.7f, inc.cx, inc.cz, pose = "treat"))
            }
            "fire" -> {
                inc.staff.add(EmergencyStaff("fire", sim.gx2x(inc.x) - 1.4f, sim.gx2x(inc.y) - 1.0f, atan2(inc.cx - sim.gx2x(inc.x), inc.cz - sim.gx2x(inc.y)), inc.cx, inc.cz, pose = "hose"))
                inc.staff.add(EmergencyStaff("fire", sim.gx2x(inc.x) + 1.4f, sim.gx2x(inc.y) + 1.0f, atan2(inc.cx - sim.gx2x(inc.x), inc.cz - sim.gx2x(inc.y)), inc.cx, inc.cz, pose = "hose"))
                inc.staff.add(EmergencyStaff("police", sim.gx2x(inc.x) + 2.2f, sim.gx2x(inc.y) - 1.5f, 0.3f, inc.cx, inc.cz, pose = "point"))
            }
            else -> {
                inc.staff.add(EmergencyStaff("medic", inc.cx - 1.2f, inc.cz + 1.0f, 0.8f, inc.cx, inc.cz, pose = "treat"))
                inc.staff.add(EmergencyStaff("medic", inc.cx + 1.3f, inc.cz - 0.9f, -1.2f, inc.cx, inc.cz, pose = "shock"))
            }
        }
    }

    fun stepParticles(dt: Float) {
        fun updateList(list: MutableList<SimpleParticle>, gravity: Float) {
            for (i in list.indices.reversed()) {
                val p = list[i]
                p.life -= dt
                if (p.life <= 0f) {
                    list.removeAt(i)
                    continue
                }
                p.vy += gravity * dt
                p.x += p.vx * dt
                p.y += p.vy * dt
                p.z += p.vz * dt
            }
        }
        updateList(smokeParticles, 0.25f)
        updateList(fireParticles, 0.45f)
        updateList(waterParticles, -9.8f)
        updateList(sparkParticles, -4.0f)

        for (i in bolts.indices.reversed()) {
            bolts[i].life -= dt
            if (bolts[i].life <= 0f) bolts.removeAt(i)
        }
        for (i in divRain.indices.reversed()) {
            val dr = divRain[i]
            dr.life -= dt
            dr.acc += dt
            if (dr.acc > 0.05f) {
                dr.acc = 0f
                addWater(dr.x + (Random.nextFloat() - 0.5f) * 6f, 18f, dr.z + (Random.nextFloat() - 0.5f) * 6f, 0f, -18f, 0f, 0.9f, 0.25f, 0xFF88D8FF.toInt())
            }
            if (dr.life <= 0f) divRain.removeAt(i)
        }

        // Update rain & snow pools around camera
        val fx = if (sim.walkMode) sim.walkX else sim.camX
        val fz = if (sim.walkMode) sim.walkZ else sim.camZ
        val rInt = (sim.wx.rain * (1f - sim.wx.snow) * 420).roundToInt().coerceIn(0, 420)
        while (rainDrops.size < rInt) {
            rainDrops.add(RainDrop(fx + (Random.nextFloat() - 0.5f) * 140f, Random.nextFloat() * 48f, fz + (Random.nextFloat() - 0.5f) * 140f, 1.4f + Random.nextFloat() * 0.9f))
        }
        while (rainDrops.size > rInt) rainDrops.removeAt(rainDrops.lastIndex)
        val wxV = cos(sim.wx.windDir) * sim.wx.windNow() * 9f
        val wzV = sin(sim.wx.windDir) * sim.wx.windNow() * 9f
        for (r in rainDrops) {
            r.x += wxV * dt
            r.z += wzV * dt
            r.y -= 42f * dt
            if (r.y < 0f) {
                r.y = 42f + Random.nextFloat() * 8f
                r.x = fx + (Random.nextFloat() - 0.5f) * 140f
                r.z = fz + (Random.nextFloat() - 0.5f) * 140f
            }
        }

        val sInt = (sim.wx.snow * 320).roundToInt().coerceIn(0, 320)
        while (snowFlakes.size < sInt) {
            snowFlakes.add(SnowFlake(fx + (Random.nextFloat() - 0.5f) * 130f, Random.nextFloat() * 44f, fz + (Random.nextFloat() - 0.5f) * 130f, 0.18f + Random.nextFloat() * 0.14f, Random.nextFloat() * 6f))
        }
        while (snowFlakes.size > sInt) snowFlakes.removeAt(snowFlakes.lastIndex)
        for (s in snowFlakes) {
            s.x += (wxV * 0.35f + sin(sim.clockSec * 1.4f + s.seed) * 1.1f) * dt
            s.z += (wzV * 0.35f + cos(sim.clockSec * 1.1f + s.seed) * 1.1f) * dt
            s.y -= 5.2f * dt
            if (s.y < 0f) {
                s.y = 38f + Random.nextFloat() * 8f
                s.x = fx + (Random.nextFloat() - 0.5f) * 130f
                s.z = fz + (Random.nextFloat() - 0.5f) * 130f
            }
        }
    }

    fun addSmoke(x: Float, y: Float, z: Float, vx: Float, vy: Float, vz: Float, life: Float, size: Float, col: Int) {
        if (smokeParticles.size >= 250) smokeParticles.removeAt(0)
        smokeParticles.add(SimpleParticle(x, y, z, vx, vy, vz, life, life, size, col))
    }

    fun addFire(x: Float, y: Float, z: Float, vx: Float, vy: Float, vz: Float, life: Float, size: Float, col: Int) {
        if (fireParticles.size >= 190) fireParticles.removeAt(0)
        fireParticles.add(SimpleParticle(x, y, z, vx, vy, vz, life, life, size, col))
    }

    fun addWater(x: Float, y: Float, z: Float, vx: Float, vy: Float, vz: Float, life: Float, size: Float, col: Int) {
        if (waterParticles.size >= 300) waterParticles.removeAt(0)
        waterParticles.add(SimpleParticle(x, y, z, vx, vy, vz, life, life, size, col))
    }

    fun addSpark(x: Float, y: Float, z: Float, vx: Float, vy: Float, vz: Float, life: Float, size: Float, col: Int) {
        if (sparkParticles.size >= 75) sparkParticles.removeAt(0)
        sparkParticles.add(SimpleParticle(x, y, z, vx, vy, vz, life, life, size, col))
    }

    fun zeusAct(wx: Float, wz: Float): PlaceOutcome {
        var nearInc: Incident? = null
        var bdInc = 7f * 7f
        for (inc in incidents) {
            val d = (inc.cx - wx) * (inc.cx - wx) + (inc.cz - wz) * (inc.cz - wz)
            if (d < bdInc) {
                bdInc = d
                nearInc = inc
            }
        }
        if (nearInc != null) {
            val need = when (nearInc.kind) {
                "fire" -> 25f
                "crash" -> 15f
                else -> 10f
            }
            if (sim.mana < need) {
                return PlaceOutcome.Rejected("Falta maná de Zeus (${need.toInt()} ⚡).")
            }
            sim.mana -= need
            bolts.add(BoltFx(nearInc.cx, nearInc.cz, 0.45f))
            if (nearInc.kind == "fire") divRain.add(DivRainFx(nearInc.cx, nearInc.cz, 2.4f))
            for (i in 0 until 18) {
                addSpark(
                    nearInc.cx, 1.5f, nearInc.cz,
                    (Random.nextFloat() - 0.5f) * 9f, 3f + Random.nextFloat() * 5f, (Random.nextFloat() - 0.5f) * 9f,
                    0.8f, 0.3f, 0xFFFFE566.toInt()
                )
            }
            for (k in nearInc.blkKeys) blk.remove(k)
            for (u in nearInc.units) {
                u.em = null; u.emHold = false; u.siren = false
            }
            incidents.remove(nearInc)
            rebuildBarriers()
            sim.coins += 10.0
            sim.civ += 1.5
            sim.wx.flash = 0.65f
            sim.triggerSound("zap")
            sim.addLogMsg("⚡ Zeus resolvió: ${nearInc.title().lowercase()} (+10 monedas).")
            return PlaceOutcome.Success(false, BuildTool.ZEUS)
        }

        var nearV: VillagerAgent? = null
        var bdV = 5f * 5f
        for (v in sim.villagers) {
            if (!v.visible || v.dead) continue
            val d = (v.wx - wx) * (v.wx - wx) + (v.wz - wz) * (v.wz - wz)
            if (d < bdV) {
                bdV = d
                nearV = v
            }
        }
        if (nearV != null) {
            if (sim.mana < 10f) {
                return PlaceOutcome.Rejected("Falta maná de Zeus (10 ⚡).")
            }
            sim.mana -= 10f
            nearV.blessed = true
            nearV.line = BLESS_DIALOGUES.random()
            sim.bless++
            sim.coins += 10.0
            bolts.add(BoltFx(nearV.wx, nearV.wz, 0.4f))
            for (i in 0 until 14) {
                addSpark(
                    nearV.wx, 1.6f, nearV.wz,
                    (Random.nextFloat() - 0.5f) * 6f, 2f + Random.nextFloat() * 4f, (Random.nextFloat() - 0.5f) * 6f,
                    0.75f, 0.26f, 0xFFFFD84A.toInt()
                )
            }
            sim.wx.flash = 0.45f
            sim.triggerSound("zap")
            sim.addLogMsg("⚡ Zeus bendijo a ${nearV.look.name} (+10 monedas).")
            return PlaceOutcome.Success(false, BuildTool.ZEUS)
        }

        if (sim.mana < 3f) {
            return PlaceOutcome.Rejected("Sin maná suficiente para lanzar un rayo (3 ⚡).")
        }
        sim.mana -= 3f
        bolts.add(BoltFx(wx, wz, 0.35f))
        for (i in 0 until 10) {
            addSpark(wx, 0.8f, wz, (Random.nextFloat() - 0.5f) * 6f, 2f + Random.nextFloat() * 3f, (Random.nextFloat() - 0.5f) * 6f, 0.6f, 0.24f, 0xFF9CE8FF.toInt())
        }
        sim.wx.flash = 0.35f
        sim.triggerSound("zap")
        return PlaceOutcome.Success(false, BuildTool.ZEUS)
    }
}
