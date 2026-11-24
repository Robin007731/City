package com.example.simulation

import com.example.data.CitySaveDoc
import com.example.data.CitySnapshot
import com.example.data.SavedCell
import java.util.ArrayDeque
import kotlin.math.*
import kotlin.random.Random

data class GridCell(
    val t: String,       // "road", "house", "b", "site", "rail", "part"
    val k: String = "",  // building/road kind: "ave", "hwy", or building key
    val s: Double = 0.5, // random seed
    var p: Double = 1.0, // construction progress 0..1
    val dur: Double = 16.0,
    val auto: Boolean = false,
    var nm: String = "", // company name
    var br: Boolean = false, // bridge over river
    var rx: String = "", // rail crossing orientation on road: "h", "v", ""
    var st: Boolean = false, // station on rail
    val big: Int = 0,    // 2 for 2x2 anchor
    val ox: Int = 0,     // 2x2 top-left grid X
    val oy: Int = 0,     // 2x2 top-left grid Y
    val ax: Int = 0,     // anchor X if t=="part"
    val ay: Int = 0      // anchor Y if t=="part"
) {
    @Volatile var hasWorker: Boolean = false
}

data class VillagerLook(
    val female: Boolean,
    val skinIdx: Int,
    val hairIdx: Int,
    val heightScale: Float,
    val longHair: Boolean,
    val beard: Boolean,
    val c1: Float,
    val c2: Float,
    val c3: Float,
    val hatSeed: Float,
    val name: String
)

data class VillagerAgent(
    var fx: Int,
    var fy: Int,
    var tx: Int,
    var ty: Int,
    var lx: Int = -1,
    var ly: Int = -1,
    var p: Float = 1f,
    var wait: Float = 0f,
    var path: MutableList<IntArray>? = null,
    var jobX: Int = -1,
    var jobY: Int = -1,
    var jobCell: GridCell? = null,
    var jobRoutine: Boolean = false,
    var working: Boolean = false,
    var dead: Boolean = false,
    var homeX: Int,
    var homeY: Int,
    var homeCell: GridCell?,
    val hourOffset: Float,
    val isStudent: Boolean,
    val isNightOwl: Boolean,
    val hobby: String,
    var act: String = "wander",
    var want: String = "",
    var nextPlanClock: Float = 0f,
    var ph: Float = 0f,
    var mv: Float = 0f,
    val lane: Int = 1,
    var ox: Float = 0f,
    var oz: Float = 0f,
    var dx: Float = 0f,
    var dz: Float = 1f,
    var yaw: Float = 0f,
    val look: VillagerLook,
    var line: String? = null,
    var lineWork: String = "",
    var wx: Float = 0f,
    var wy: Float = 0.05f,
    var wz: Float = 0f,
    var visible: Boolean = true,
    var blessed: Boolean = false,
    val destCache: MutableMap<String, Pair<Int, Int>> = HashMap()
)

data class VehicleAgent(
    val type: String, // auto, moto, camion, bus, ambulancia, policia, bombero
    val color: Int,
    var fx: Int,
    var fy: Int,
    var tx: Int,
    var ty: Int,
    var lx: Int = -1,
    var ly: Int = -1,
    var p: Float = 1f,
    var wait: Float = 0f,
    var path: MutableList<IntArray>? = null,
    var destX: Int = -1,
    var destY: Int = -1,
    val speed: Float,
    var ox: Float = 0f,
    var oz: Float = 0f,
    var dx: Float = 0f,
    var dz: Float = 1f,
    var yaw: Float = 0f,
    var stuck: Float = 0f,
    var dead: Boolean = false,
    var wx: Float = 0f,
    var wy: Float = 0.05f,
    var wz: Float = 0f,
    var amp: Float = 0f,
    var visible: Boolean = true,
    var siren: Boolean = false,
    var em: Incident? = null,
    var emHold: Boolean = false,
    var brake: Float = 0f
)

data class CityStats(
    val houses: Int,
    val connectedHouses: Int,
    val buildings: Int,
    val shops: Int,
    val terminals: Int,
    val empresas: Int,
    val roads: Int,
    val autoCount: Int,
    val population: Int,
    val era: Int,
    val wellbeing: Double,
    val counts: Map<String, Int>
)

data class IncidentSummary(
    val id: Int,
    val emoji: String,
    val title: String,
    val status: String,
    val cx: Float,
    val cz: Float
)

data class SpeechBubbleData(
    val speakerName: String,
    val activityLabel: String?,
    val text: String,
    val worldX: Float,
    val worldY: Float,
    val worldZ: Float
)

data class InspectedTile(
    val gx: Int,
    val gy: Int,
    val title: String,
    val subtitle: String,
    val canDemolish: Boolean,
    val refundCoins: Int
)

sealed interface PlaceOutcome {
    data class Success(val isBuildSite: Boolean, val tool: BuildTool) : PlaceOutcome
    data class Demolished(val refund: Int) : PlaceOutcome
    data class Rejected(val reason: String) : PlaceOutcome
    data object NoOp : PlaceOutcome
}

data class ServiceStatus(
    val key: String,
    val icon: String,
    val label: String,
    val have: Int,
    val need: Int
)

data class ActivityStatus(
    val emoji: String,
    val count: Int,
    val label: String
)

data class MinimapCell(val x: Int, val y: Int, val kind: Int) // 1=road, 2=house, 3=building, 4=site, 5=rail, 6=water

data class HudState(
    val cityName: String = "Mi Aldea",
    val coins: Int = 500,
    val population: Int = 0,
    val houses: Int = 0,
    val buildings: Int = 0,
    val roads: Int = 0,
    val eraIndex: Int = 0,
    val eraName: String = "Aldea",
    val civPoints: Int = 10,
    val nextEraName: String? = "Pueblo",
    val nextEraAt: Int? = 80,
    val eraProgress: Float = 0f,
    val territorySize: Int = 24,
    val expTier: Int = 0,
    val nextExpandCost: Int? = 80,
    val wellbeing: Int = 50,
    val formattedTime: String = "09:00",
    val services: List<ServiceStatus> = emptyList(),
    val activities: List<ActivityStatus> = emptyList(),
    val logs: List<String> = listOf("Tres familias fundaron la aldea."),
    val hintText: String? = null,
    val mana: Int = 100,
    val tax: Int = 10,
    val spend: Int = 100,
    val bless: Int = 0,
    val broke: Boolean = false,
    val netPerSec: Double = 0.0,
    val inPerSec: Double = 0.0,
    val outPerSec: Double = 0.0,
    val wxIcon: String = "☀️",
    val tempC: Int = 22,
    val wxDesc: String = "Despejado",
    val seasonName: String = "Primavera",
    val windKmh: Int = 8,
    val wxAuto: Boolean = true,
    val wxRaining: Boolean = false,
    val incAuto: Boolean = true,
    val incidents: List<IncidentSummary> = emptyList()
)

class CitySimulation {
    val lock = Any()

    val grid = Array(GRID_SIZE) { arrayOfNulls<GridCell>(GRID_SIZE) }
    val water = Array(GRID_SIZE) { y ->
        BooleanArray(GRID_SIZE) { x ->
            abs(gx2x(x) - riverX(gx2x(y))) < 10f
        }
    }

    var cityName: String = "Mi Aldea"
    var coins: Double = 500.0
    var civ: Double = 10.0
    var gameTime: Double = 0.0
    var era: Int = 0
    var exp: Int = 0
    var tax: Int = 10
    var spend: Int = 100
    var bless: Int = 0
    var broke: Boolean = false
    var mana: Float = 100f
    var lastIn: Double = 0.0
    var lastOut: Double = 0.0
    val logs = mutableListOf("Tres familias fundaron la aldea.")

    val villagers = mutableListOf<VillagerAgent>()
    val vehicles = mutableListOf<VehicleAgent>()

    val wx = WeatherEngine()
    val events = WorldEventsController(this)
    private val soundQueue = mutableListOf<String>()

    var camX = 0f
    var camZ = 0f
    var camDist = 92f
    var camYaw = 0.7f
    var camPitch = 0.85f

    var walkMode = false
    var walkX = 0f
    var walkY = 0f
    var walkVy = 0f
    var walkJy = 0f
    var walkZ = 0f
    var walkHeading = 0f
    var walkCamYaw = 0.7f
    var walkCamPitch = 0.28f
    var walkCamDist = 8.5f
    var walkMv = 0f
    var walkPh = 0f
    var joyX = 0f
    var joyY = 0f
    var sprinting = false

    @Volatile var gridRevision: Long = 1L
    @Volatile var eraChangedEvent: EraDef? = null
    @Volatile var lastBannerMessage: String? = null

    var clockSec = 0f
        private set
    private var econAcc = 0f
    private var spawnAcc = 0f
    private var vehAcc = 0f
    private var siteAcc = 0f
    private var planTick = 0
    private var cachedStats: CityStats? = null
    private var statAcc = 0f
    private var lastBellHour = -1

    init {
        initStarterVillage()
    }

    fun triggerSound(name: String) {
        if (soundQueue.size < 12) soundQueue.add(name)
    }

    fun drainSounds(): List<String> {
        synchronized(lock) {
            if (soundQueue.isEmpty()) return emptyList()
            val out = soundQueue.toList()
            soundQueue.clear()
            return out
        }
    }

    fun addLogMsg(msg: String) {
        logs.add(0, msg)
        while (logs.size > 6) logs.removeAt(logs.lastIndex)
    }

    fun initStarterVillage() {
        synchronized(lock) {
            for (y in 0 until GRID_SIZE) {
                for (x in 0 until GRID_SIZE) {
                    grid[y][x] = null
                }
            }
            val o = (GRID_SIZE - 64) / 2 // 3
            for (x in (29 + o)..(34 + o)) {
                grid[32 + o][x] = GridCell(t = "road")
            }
            listOf(30 + o to 31 + o, 33 + o to 31 + o, 31 + o to 33 + o).forEach { (x, y) ->
                grid[y][x] = GridCell(t = "house", s = Random.nextDouble())
            }
            cityName = "Mi Aldea"
            coins = 500.0
            civ = 10.0
            gameTime = 0.0
            era = 0
            exp = 0
            tax = 10
            spend = 100
            bless = 0
            broke = false
            mana = 100f
            lastIn = 0.0
            lastOut = 0.0
            logs.clear()
            logs.add("Tres familias fundaron la aldea.")
            villagers.clear()
            vehicles.clear()
            events.clearAll()
            wx.reset()
            cachedStats = null
            camX = 0f; camZ = 0f; camDist = 92f; camYaw = 0.7f; camPitch = 0.85f
            walkMode = false; walkY = 0f; walkVy = 0f; walkJy = 0f
            lastBellHour = -1
            gridRevision++
        }
    }

    fun loadFromSave(doc: CitySaveDoc) {
        synchronized(lock) {
            for (y in 0 until GRID_SIZE) {
                for (x in 0 until GRID_SIZE) {
                    grid[y][x] = null
                }
            }
            doc.cells.forEach { raw ->
                val sc = SavedCell.fromMap(raw) ?: return@forEach
                val inWater = water[sc.y][sc.x]
                if (inWater && sc.t != "road") return@forEach
                grid[sc.y][sc.x] = GridCell(
                    t = sc.t,
                    k = sc.k,
                    s = sc.s,
                    p = sc.p,
                    dur = sc.dur.coerceAtLeast(1.0),
                    auto = sc.auto,
                    nm = sc.nm,
                    br = if (inWater && sc.t == "road") true else sc.br,
                    rx = sc.rx,
                    st = sc.st,
                    big = sc.big,
                    ox = sc.ox,
                    oy = sc.oy,
                    ax = sc.ax,
                    ay = sc.ay
                )
            }
            // Sanitize orphan "part" cells
            for (y in 0 until GRID_SIZE) {
                for (x in 0 until GRID_SIZE) {
                    val c = grid[y][x] ?: continue
                    if (c.t == "part") {
                        val a = if (inBounds(c.ax, c.ay)) grid[c.ay][c.ax] else null
                        if (a == null || (a.t != "b" && a.t != "site")) {
                            grid[y][x] = null
                        }
                    }
                }
            }
            cityName = doc.cityName.ifBlank { "Mi Aldea" }
            coins = doc.coins.coerceAtLeast(0.0)
            civ = doc.civ.coerceAtLeast(0.0)
            gameTime = doc.gameTime.coerceAtLeast(0.0)
            era = eraOfCiv(civ)
            exp = doc.exp.toInt().coerceIn(0, EXPAND_COSTS.size)
            tax = doc.tax.toInt().coerceIn(0, 25)
            spend = doc.spend.toInt().coerceIn(50, 150)
            bless = doc.bless.toInt().coerceIn(0, 10000)
            broke = false
            mana = 100f
            logs.clear()
            if (doc.logEntries.isNotEmpty()) {
                logs.addAll(doc.logEntries.take(6))
            } else {
                logs.add("Ciudad cargada desde la nube.")
            }
            villagers.clear()
            vehicles.clear()
            events.clearAll()
            events.refreshStationsCache()
            cachedStats = null
            gridRevision++
        }
    }

    fun exportSnapshot(): CitySnapshot {
        synchronized(lock) {
            val cells = ArrayList<SavedCell>(512)
            for (y in 0 until GRID_SIZE) {
                for (x in 0 until GRID_SIZE) {
                    val c = grid[y][x] ?: continue
                    cells.add(
                        SavedCell(
                            x = x, y = y, t = c.t, k = c.k, s = c.s, p = c.p,
                            dur = c.dur, auto = c.auto, nm = c.nm,
                            br = c.br, rx = c.rx, st = c.st,
                            big = c.big, ox = c.ox, oy = c.oy, ax = c.ax, ay = c.ay
                        )
                    )
                }
            }
            return CitySnapshot(
                cityName = cityName, coins = coins, civ = civ, gameTime = gameTime,
                era = era, exp = exp, tax = tax, spend = spend, bless = bless,
                logEntries = logs.toList(), cells = cells
            )
        }
    }

    fun territorySize(): Int = min(GRID_SIZE, 24 + 8 * exp)
    fun boundsLo(): Int = (GRID_SIZE - territorySize()) / 2
    fun boundsHi(): Int = boundsLo() + territorySize() - 1
    fun inUsable(x: Int, y: Int): Boolean {
        val lo = boundsLo()
        val hi = boundsHi()
        return x in lo..hi && y in lo..hi
    }

    fun inBounds(x: Int, y: Int): Boolean = x in 0 until GRID_SIZE && y in 0 until GRID_SIZE
    fun isWater(x: Int, y: Int): Boolean = inBounds(x, y) && water[y][x]
    fun isRoad(x: Int, y: Int): Boolean = inBounds(x, y) && grid[y][x]?.t == "road"
    fun adjRoad(x: Int, y: Int): Boolean {
        for (d in DIRS) {
            val nx = x + d[0]; val ny = y + d[1]
            if (inBounds(nx, ny)) {
                val c = grid[ny][nx]
                if (c != null && c.t == "road" && c.k != "hwy") return true
            }
        }
        return false
    }

    fun isRail(x: Int, y: Int): Boolean {
        if (!inBounds(x, y)) return false
        val c = grid[y][x] ?: return false
        return c.t == "rail" || (c.t == "road" && c.rx.isNotEmpty())
    }

    private fun axOK(x: Int, y: Int, dx: Int, dy: Int): Boolean {
        val c = if (inBounds(x, y)) grid[y][x] else null
        val rx = c?.rx ?: return true
        if (rx == "h") return dy == 0
        if (rx == "v") return dx == 0
        return true
    }

    fun rLink(x: Int, y: Int, nx: Int, ny: Int): Boolean {
        return isRail(nx, ny) && axOK(x, y, nx - x, ny - y) && axOK(nx, ny, x - nx, y - ny)
    }

    fun rMask(x: Int, y: Int): Int {
        var m = 0
        if (rLink(x, y, x + 1, y)) m = m or 1
        if (rLink(x, y, x - 1, y)) m = m or 2
        if (rLink(x, y, x, y + 1)) m = m or 4
        if (rLink(x, y, x, y - 1)) m = m or 8
        return m
    }

    fun railAx(x: Int, y: Int): String? {
        val h = isRail(x + 1, y) || isRail(x - 1, y)
        val v = isRail(x, y + 1) || isRail(x, y - 1)
        if (h && v) return null
        return if (v && !h) "v" else "h"
    }

    fun gx2x(g: Float): Float = (g - GRID_SIZE / 2f + 0.5f) * TILE_SIZE
    fun gx2x(g: Int): Float = (g - GRID_SIZE / 2f + 0.5f) * TILE_SIZE
    fun x2g(x: Float): Int = floor((x / TILE_SIZE + GRID_SIZE / 2f).toDouble()).toInt()

    fun hAt(gx: Int, gy: Int): Float {
        if (!inBounds(gx, gy)) return 0f
        val c = grid[gy][gx]
        return if (c != null && c.t == "road" && c.br) 0.33f else 0f
    }

    fun lerpY(fx: Int, fy: Int, tx: Int, ty: Int, p: Float): Float {
        val u = p * p * (3f - 2f * p)
        return hAt(fx, fy) + (hAt(tx, ty) - hAt(fx, fy)) * u
    }

    fun hourNow(): Float {
        val h = (12f + (clockSec - 20f) / 220f * 24f) % 24f
        return if (h < 0f) h + 24f else h
    }

    fun dayness(): Float {
        val ang = 1.0f + clockSec * 2f * PI.toFloat() / 220f
        var d = 0.5f + 0.5f * sin(ang)
        if (era == 4) d = 0.1f + 0.2f * d
        return d
    }

    fun sunAngle(): Float = 1.0f + clockSec * 2f * PI.toFloat() / 220f

    fun isIntersection(x: Int, y: Int): Boolean {
        if (!isRoad(x, y)) return false
        var n = 0
        for (d in DIRS) if (isRoad(x + d[0], y + d[1])) n++
        return n >= 3
    }

    fun trafficGreen(axisZ: Boolean): Boolean {
        val t = gameTime % 14.0
        return if (axisZ) t < 6.0 else (t >= 7.0 && t < 13.0)
    }

    fun trafficSignalStates(): Pair<Int, Int> {
        val t = gameTime % 14.0
        val ns = when {
            t < 6.0 -> 0xFF3ADF5A.toInt()
            t < 7.0 -> 0xFFF0C030.toInt()
            else -> 0xFFE03030.toInt()
        }
        val ew = when {
            t < 7.0 -> 0xFFE03030.toInt()
            t < 13.0 -> 0xFF3ADF5A.toInt()
            else -> 0xFFF0C030.toInt()
        }
        return ns to ew
    }

    private fun computeRawWellbeing(counts: Map<String, Int>, rawPop: Int, e: Int): Double {
        if (rawPop < 1) return 50.0
        var got = 0.0
        var all = 0
        for (s in SERVICE_REQUIREMENTS) {
            if (e < s.minEra || (s.minEra >= 1 && rawPop < 12) || (s.key == "ayuntamiento" && rawPop < 30)) continue
            all += s.weight
            val need = if (s.perPop > 0) ceil(rawPop.toDouble() / s.perPop).toInt() else 1
            val have = counts[s.key] ?: 0
            got += min(1.0, have.toDouble() / need) * s.weight
        }
        return if (all > 0) got / all * 100.0 else 50.0
    }

    private fun adjustWellbeing(rawW: Double): Double {
        val sv = (if (broke) 0.6 else 1.0) * (spend / 100.0)
        val taxPenalty = if (tax > 10) (tax - 10) * 1.6 else -(10 - tax) * 0.6
        var w = (rawW * min(1.2, 0.55 + 0.45 * sv) - taxPenalty).coerceIn(0.0, 100.0)
        w = (w + min(12.0, bless * 1.5)).coerceIn(0.0, 100.0)
        return w
    }

    fun stats(): CityStats {
        var houses = 0
        var conn = 0
        var b = 0
        var shops = 0
        var terms = 0
        var emps = 0
        var roads = 0
        var auto = 0
        val cnt = HashMap<String, Int>()
        for (y in 0 until GRID_SIZE) {
            for (x in 0 until GRID_SIZE) {
                val c = grid[y][x] ?: continue
                when (c.t) {
                    "road" -> roads++
                    "house" -> {
                        houses++
                        if (adjRoad(x, y)) conn++
                        if (c.auto) auto++
                    }
                    "b" -> {
                        b++
                        if (c.k == "comercio") shops++
                        if (c.k == "terminal") terms++
                        if (c.k == "empresa") emps++
                        cnt[c.k] = (cnt[c.k] ?: 0) + 1
                        if (c.auto) auto++
                    }
                    "site" -> {
                        auto++
                        cnt[c.k] = (cnt[c.k] ?: 0) + 1
                    }
                }
            }
        }
        val e = eraOfCiv(civ)
        val rawPop = conn * (2 + e)
        val w = adjustWellbeing(computeRawWellbeing(cnt, rawPop, e))
        val pop = (rawPop * (0.8 + 0.4 * w / 100.0)).roundToInt()
        return CityStats(houses, conn, b, shops, terms, emps, roads, auto, pop, e, w, cnt)
    }

    fun expandTerritory(): Boolean {
        synchronized(lock) {
            if (exp >= EXPAND_COSTS.size) return false
            val cost = EXPAND_COSTS[exp]
            if (coins < cost) return false
            coins -= cost
            exp++
            val sz = territorySize()
            addLogMsg("Ampliaste el territorio a $sz x $sz.")
            lastBannerMessage = "Territorio ampliado: $sz x $sz"
            gridRevision++
            return true
        }
    }

    fun setTaxRate(v: Int) {
        synchronized(lock) { tax = v.coerceIn(0, 25); cachedStats = null }
    }

    fun setSpendRate(v: Int) {
        synchronized(lock) { spend = v.coerceIn(50, 150); cachedStats = null }
    }

    fun addDebugCash() {
        synchronized(lock) { coins += 500.0; addLogMsg("💰 Fondos añadidos (+500 monedas).") }
    }

    fun zeusTap(wx: Float, wz: Float): PlaceOutcome {
        synchronized(lock) { return events.zeusAct(wx, wz) }
    }

    fun bigFootprintValid(gx: Int, gy: Int): Pair<Int, Int>? {
        if (!inBounds(gx, gy) || !inBounds(gx + 1, gy + 1)) return null
        var anchor: Pair<Int, Int>? = null
        for (dy in 0..1) {
            for (dx in 0..1) {
                val x = gx + dx; val y = gy + dy
                if (!inUsable(x, y) || water[y][x] || grid[y][x] != null) return null
                if (anchor == null && adjRoad(x, y)) anchor = x to y
            }
        }
        return anchor
    }

    fun place(gx: Int, gy: Int, tool: BuildTool): PlaceOutcome {
        synchronized(lock) {
            if (!inBounds(gx, gy)) return PlaceOutcome.NoOp
            if (tool == BuildTool.ZEUS) {
                return events.zeusAct(gx2x(gx), gx2x(gy))
            }
            val existing = grid[gy][gx]
            if (tool == BuildTool.ERASE) {
                if (existing == null) return PlaceOutcome.NoOp
                if (existing.t == "part" || existing.big > 0) {
                    val ax = if (existing.t == "part") existing.ax else gx
                    val ay = if (existing.t == "part") existing.ay else gy
                    val an = if (inBounds(ax, ay)) grid[ay][ax] else null
                    val refund = (BuildTool.fromKey(an?.k ?: "")?.cost ?: 20) / 2
                    val ox = an?.ox ?: gx
                    val oy = an?.oy ?: gy
                    for (dy in 0..1) {
                        for (dx in 0..1) {
                            if (inBounds(ox + dx, oy + dy)) grid[oy + dy][ox + dx] = null
                        }
                    }
                    coins += refund
                    cachedStats = null
                    gridRevision++
                    return PlaceOutcome.Demolished(refund)
                }
                val refund = when (existing.t) {
                    "road" -> if (existing.br) BRIDGE_COST / 2 else 1
                    "house" -> 5
                    "rail" -> if (existing.st) BuildTool.ESTACION.cost / 2 else BuildTool.TREN.cost / 2
                    "b", "site" -> (BuildTool.fromKey(existing.k)?.cost ?: 20) / 2
                    else -> 0
                }
                coins += refund
                grid[gy][gx] = null
                if (existing.t == "rail") events.refreshStationsCache()
                cachedStats = null
                gridRevision++
                return PlaceOutcome.Demolished(refund)
            }

            if (tool == BuildTool.CAMERA) return PlaceOutcome.NoOp
            if (!inUsable(gx, gy)) {
                return PlaceOutcome.Rejected("Esa zona es bosque. Ampliá el territorio para construir ahí.")
            }
            val currentEra = eraOfCiv(civ)
            if (currentEra < tool.minEra) {
                return PlaceOutcome.Rejected("Necesitás llegar a ${ERAS[tool.minEra].name} para construir eso.")
            }

            // Road types (Calle, Avenida, Autopista)
            val isRoadTool = tool == BuildTool.ROAD || tool == BuildTool.AVENIDA || tool == BuildTool.AUTOPISTA
            val rk = when (tool) {
                BuildTool.AVENIDA -> "ave"
                BuildTool.AUTOPISTA -> "hwy"
                else -> ""
            }
            if (water[gy][gx]) {
                if (tool == BuildTool.ROAD && existing == null) {
                    if (coins < BRIDGE_COST) {
                        return PlaceOutcome.Rejected("Faltan monedas ($BRIDGE_COST 🪙) para tender un puente.")
                    }
                    coins -= BRIDGE_COST
                    grid[gy][gx] = GridCell(t = "road", br = true)
                    cachedStats = null
                    gridRevision++
                    return PlaceOutcome.Success(false, tool)
                }
                return PlaceOutcome.Rejected("Ahí hay agua: solo se puede tender un puente (Calle).")
            }

            if (isRoadTool) {
                if (existing != null) {
                    if (existing.t == "rail" && !existing.st) {
                        val ax = railAx(gx, gy) ?: return PlaceOutcome.Rejected("No se puede cruzar sobre una curva ferroviaria.")
                        if (coins < tool.cost) return PlaceOutcome.Rejected("Faltan monedas (${tool.cost} 🪙).")
                        coins -= tool.cost
                        grid[gy][gx] = GridCell(t = "road", k = rk, rx = ax)
                        events.refreshStationsCache()
                        cachedStats = null
                        gridRevision++
                        return PlaceOutcome.Success(false, tool)
                    }
                    if ((tool == BuildTool.AVENIDA || tool == BuildTool.AUTOPISTA) &&
                        existing.t == "road" && !existing.br && existing.k != rk && existing.k != "hwy"
                    ) {
                        if (coins < tool.cost) return PlaceOutcome.Rejected("Faltan monedas (${tool.cost} 🪙).")
                        coins -= tool.cost
                        grid[gy][gx] = existing.copy(k = rk)
                        cachedStats = null
                        gridRevision++
                        return PlaceOutcome.Success(false, tool)
                    }
                    return PlaceOutcome.NoOp
                }
                if (coins < tool.cost) return PlaceOutcome.Rejected("Faltan monedas (${tool.cost} 🪙).")
                coins -= tool.cost
                grid[gy][gx] = GridCell(t = "road", k = rk)
                cachedStats = null
                gridRevision++
                return PlaceOutcome.Success(false, tool)
            }

            if (tool == BuildTool.TREN) {
                if (existing != null) {
                    if (existing.t == "road" && existing.k == "" && !existing.br && existing.rx.isEmpty()) {
                        val rh = isRoad(gx + 1, gy) || isRoad(gx - 1, gy)
                        val rv = isRoad(gx, gy + 1) || isRoad(gx, gy - 1)
                        if (rh && rv) return PlaceOutcome.Rejected("No se puede cruzar por una intersección de calles.")
                        if (coins < tool.cost) return PlaceOutcome.Rejected("Faltan monedas (${tool.cost} 🪙).")
                        coins -= tool.cost
                        existing.rx = if (rh) "v" else "h"
                        events.refreshStationsCache()
                        gridRevision++
                        return PlaceOutcome.Success(false, tool)
                    }
                    return PlaceOutcome.NoOp
                }
                if (coins < tool.cost) return PlaceOutcome.Rejected("Faltan monedas (${tool.cost} 🪙).")
                coins -= tool.cost
                grid[gy][gx] = GridCell(t = "rail", s = Random.nextDouble())
                events.refreshStationsCache()
                gridRevision++
                return PlaceOutcome.Success(false, tool)
            }

            if (tool == BuildTool.ESTACION) {
                if (existing != null) {
                    if (existing.t == "rail" && !existing.st) {
                        if (coins < tool.cost) return PlaceOutcome.Rejected("Faltan monedas (${tool.cost} 🪙).")
                        coins -= tool.cost
                        grid[gy][gx] = existing.copy(st = true, s = Random.nextDouble())
                        events.refreshStationsCache()
                        gridRevision++
                        return PlaceOutcome.Success(false, tool)
                    }
                    return PlaceOutcome.NoOp
                }
                if (coins < tool.cost) return PlaceOutcome.Rejected("Faltan monedas (${tool.cost} 🪙).")
                coins -= tool.cost
                grid[gy][gx] = GridCell(t = "rail", st = true, s = Random.nextDouble())
                events.refreshStationsCache()
                gridRevision++
                return PlaceOutcome.Success(false, tool)
            }

            // 2x2 Big Buildings
            if (tool.key in BIG_TOOLS) {
                val st = cachedStats ?: stats().also { cachedStats = it }
                if (tool == BuildTool.AYUNTAMIENTO && (st.counts["ayuntamiento"] ?: 0) >= 1) {
                    return PlaceOutcome.Rejected("Ya hay un ayuntamiento en la ciudad.")
                }
                val anchor = bigFootprintValid(gx, gy)
                    ?: return PlaceOutcome.Rejected("Necesita espacio libre de 2x2 sin agua y pegado a una calle.")
                if (coins < tool.cost) {
                    return PlaceOutcome.Rejected("Faltan monedas (${tool.cost} 🪙).")
                }
                coins -= tool.cost
                val ax = anchor.first
                val ay = anchor.second
                val siteCell = GridCell(
                    t = "site", k = tool.key, p = 0.0,
                    dur = (14.0 + Random.nextDouble() * 8.0) * 1.6,
                    s = Random.nextDouble(), big = 2, ox = gx, oy = gy
                )
                for (dy in 0..1) {
                    for (dx in 0..1) {
                        val px = gx + dx; val py = gy + dy
                        grid[py][px] = if (px == ax && py == ay) siteCell else GridCell(t = "part", ax = ax, ay = ay)
                    }
                }
                assignWorker(ax, ay, siteCell)
                assignWorker(ax, ay, siteCell)
                cachedStats = null
                gridRevision++
                return PlaceOutcome.Success(true, tool)
            }

            if (existing != null) return PlaceOutcome.NoOp
            if (coins < tool.cost) {
                return PlaceOutcome.Rejected("Faltan monedas (${tool.cost} 🪙). Los aldeanos recaudan con el tiempo.")
            }
            if (tool.requiresRoad && !adjRoad(gx, gy)) {
                return PlaceOutcome.Rejected("Ponelo pegado a una calle.")
            }
            coins -= tool.cost
            if (tool.isBuildingSite) {
                val cell = GridCell(
                    t = "site", k = tool.key, p = 0.0,
                    dur = 14.0 + Random.nextDouble() * 8.0, s = Random.nextDouble()
                )
                grid[gy][gx] = cell
                assignWorker(gx, gy, cell)
                assignWorker(gx, gy, cell)
                cachedStats = null
                gridRevision++
                return PlaceOutcome.Success(true, tool)
            }
            grid[gy][gx] = GridCell(t = "house", s = Random.nextDouble())
            cachedStats = null
            gridRevision++
            return PlaceOutcome.Success(false, tool)
        }
    }

    fun inspectTile(gx: Int, gy: Int): InspectedTile? {
        synchronized(lock) {
            if (!inBounds(gx, gy)) return null
            val raw = grid[gy][gx] ?: return null
            val tx = if (raw.t == "part") raw.ax else gx
            val ty = if (raw.t == "part") raw.ay else gy
            val c = if (inBounds(tx, ty)) grid[ty][tx] else null
            c ?: return null
            val e = eraOfCiv(civ)
            return when (c.t) {
                "road" -> {
                    val title = when {
                        c.br -> "Puente sobre el río"
                        c.k == "ave" -> "Avenida arbolada"
                        c.k == "hwy" -> "Autopista rápida"
                        c.rx.isNotEmpty() -> "Paso a nivel ferroviario"
                        else -> "Calle (${ERAS[e].name})"
                    }
                    val sub = when {
                        c.br -> "Conecta ambas orillas del río."
                        c.k == "hwy" -> "Alta velocidad para tráfico pasante (sin edificios pegados)."
                        c.rx.isNotEmpty() -> "Cruce de calle y vía férrea con barrera automática."
                        else -> "Conecta casas, comercios y vehículos."
                    }
                    InspectedTile(tx, ty, title, sub, true, if (c.br) BRIDGE_COST / 2 else 1)
                }
                "rail" -> InspectedTile(
                    tx, ty,
                    if (c.st) "Estación ferroviaria" else "Vía férrea",
                    if (c.st) "Conecta pasajeros entre estaciones (+monedas por viaje)." else "Conectá al menos 2 estaciones para activar trenes.",
                    true,
                    if (c.st) BuildTool.ESTACION.cost / 2 else BuildTool.TREN.cost / 2
                )
                "house" -> InspectedTile(
                    tx, ty,
                    if (e >= 3) "Torre Residencial" else "Casa",
                    if (adjRoad(tx, ty)) "Conectada a la red vial · Habitable" else "Sin calle adyacente · Conectala para atraer vecinos",
                    true, 5
                )
                "site" -> {
                    val pct = (c.p * 100).roundToInt().coerceIn(0, 100)
                    InspectedTile(
                        tx, ty,
                        "Obra: ${buildingDisplayName(c.k, e)}",
                        "Progreso: $pct% ${if (c.hasWorker) "· Obreros trabajando" else "· Esperando obreros"}",
                        true,
                        (BuildTool.fromKey(c.k)?.cost ?: 20) / 2
                    )
                }
                "b" -> {
                    val name = if (c.k == "empresa" && c.nm.isNotBlank()) "Empresa: ${c.nm}" else buildingDisplayName(c.k, e)
                    val sub = when (c.k) {
                        "comercio" -> "Genera monedas y actividad comercial"
                        "empresa" -> "Genera empleos y +2 monedas/seg"
                        "terminal" -> "Habilita autobuses y conecta rutas"
                        "hospital" -> "Mejora salud y envía ambulancias"
                        "comisaria" -> "Aumenta seguridad y patrullas"
                        "bomberos" -> "Protege contra incendios"
                        "huerto" -> "Espacio verde para paseos y mates"
                        "estadio" -> "Eventos deportivos para la ciudad"
                        else -> "Servicio cívico activo"
                    }
                    InspectedTile(tx, ty, name, sub, true, (BuildTool.fromKey(c.k)?.cost ?: 20) / 2)
                }
                else -> null
            }
        }
    }

    fun toggleWalkMode(): Boolean {
        synchronized(lock) {
            if (!walkMode) {
                val r = nearestRoad(camX, camZ) ?: (camX to camZ)
                walkX = r.first; walkZ = r.second - 2.5f
                walkY = 0f; walkVy = 0f; walkJy = 0f
                walkCamYaw = camYaw
                walkHeading = walkCamYaw + PI.toFloat()
                walkMode = true
            } else {
                camX = walkX; camZ = walkZ
                walkMode = false
            }
            return walkMode
        }
    }

    fun jumpWalk() {
        synchronized(lock) {
            if (walkMode && walkJy <= 0.05f) {
                walkVy = 11.5f
            }
        }
    }

    private fun nearestRoad(wx: Float, wz: Float): Pair<Float, Float>? {
        var best: Pair<Float, Float>? = null
        var bd = Float.MAX_VALUE
        for (y in 0 until GRID_SIZE) {
            for (x in 0 until GRID_SIZE) {
                if (!isRoad(x, y)) continue
                val rx = gx2x(x); val rz = gx2x(y)
                val d = (rx - wx) * (rx - wx) + (rz - wz) * (rz - wz)
                if (d < bd) { bd = d; best = rx to rz }
            }
        }
        return best
    }

    fun bfsPath(
        sx: Int,
        sy: Int,
        emergency: Boolean = false,
        ped: Boolean = false,
        goal: (Int, Int) -> Boolean
    ): MutableList<IntArray>? {
        val prev = HashMap<Int, Int>(64)
        val q = ArrayDeque<Int>(64)
        val startKey = sy * GRID_SIZE + sx
        prev[startKey] = -1
        q.addLast(startKey)
        while (q.isNotEmpty()) {
            val cur = q.removeFirst()
            val x = cur % GRID_SIZE
            val y = cur / GRID_SIZE
            if (goal(x, y)) {
                val path = ArrayList<IntArray>()
                var k = cur
                while (prev[k] != null && prev[k] != -1) {
                    path.add(0, intArrayOf(k % GRID_SIZE, k / GRID_SIZE))
                    k = prev[k]!!
                }
                return path
            }
            for (d in DIRS) {
                val nx = x + d[0]; val ny = y + d[1]
                val nk = ny * GRID_SIZE + nx
                if (!isRoad(nx, ny) || prev.containsKey(nk)) continue
                if (!emergency && events.blk.containsKey(nk)) continue
                if (ped && grid[ny][nx]?.k == "hwy") continue
                prev[nk] = cur
                q.addLast(nk)
            }
        }
        return null
    }

    private fun assignWorker(x: Int, y: Int, cell: GridCell) {
        var best: VillagerAgent? = null
        var bd = Int.MAX_VALUE
        for (v in villagers) {
            if ((v.jobCell != null && !v.jobRoutine) || v.dead) continue
            val d = abs(v.tx - x) + abs(v.ty - y)
            if (d < bd) { bd = d; best = v }
        }
        best ?: return
        val p = bfsPath(best.tx, best.ty, emergency = false, ped = true) { gx, gy ->
            abs(gx - x) + abs(gy - y) == 1
        }
        if (p != null) {
            best.path = p; best.jobX = x; best.jobY = y; best.jobCell = cell
            best.jobRoutine = false; best.act = "build"; best.working = false; best.line = null
        }
    }

    private fun spawnVillager() {
        val list = ArrayList<IntArray>()
        for (y in 0 until GRID_SIZE) {
            for (x in 0 until GRID_SIZE) {
                val c = grid[y][x]
                if (c != null && c.t == "house") {
                    for (d in DIRS) {
                        val nx = x + d[0]; val ny = y + d[1]
                        if (isRoad(nx, ny) && grid[ny][nx]?.k != "hwy") list.add(intArrayOf(nx, ny, x, y))
                    }
                }
            }
        }
        if (list.isEmpty()) return
        val p = list[Random.nextInt(list.size)]
        val female = Random.nextFloat() < 0.5f
        val look = VillagerLook(
            female = female,
            skinIdx = Random.nextInt(SKIN_COLORS.size),
            hairIdx = Random.nextInt(HAIR_COLORS.size),
            heightScale = 0.92f + Random.nextFloat() * 0.16f,
            longHair = female && Random.nextFloat() < 0.7f,
            beard = !female && Random.nextFloat() < 0.2f,
            c1 = Random.nextFloat(), c2 = Random.nextFloat(), c3 = Random.nextFloat(),
            hatSeed = Random.nextFloat(),
            name = (if (female) FEMALE_NAMES else MALE_NAMES).random()
        )
        villagers.add(
            VillagerAgent(
                fx = p[0], fy = p[1], tx = p[0], ty = p[1],
                homeX = p[2], homeY = p[3], homeCell = grid[p[3]][p[2]],
                hourOffset = (Random.nextFloat() - 0.5f) * 1.4f,
                isStudent = Random.nextFloat() < 0.4f,
                isNightOwl = Random.nextFloat() < 0.12f,
                hobby = listOf("gym", "gym", "park", "park", "park", "show").random(),
                ph = Random.nextFloat() * 6f,
                lane = if (Random.nextFloat() < 0.5f) -1 else 1,
                yaw = Random.nextFloat() * 6f,
                look = look
            )
        )
    }

    private fun xwait(v: VillagerAgent, next: IntArray): Boolean {
        if (!isIntersection(next[0], next[1])) return false
        return !trafficGreen(next[0] == v.fx)
    }

    private fun stepVillager(v: VillagerAgent, dt: Float) {
        if (!isRoad(v.fx, v.fy) || !isRoad(v.tx, v.ty)) { v.dead = true; return }
        if (v.jobCell != null && grid[v.jobY][v.jobX] !== v.jobCell) {
            v.jobCell = null; v.path = null; v.working = false
        }
        // Watch active incident nearby
        var nearInc: Incident? = null
        var bd = 14f * 14f
        for (inc in events.incidents) {
            val d = (inc.cx - v.wx) * (inc.cx - v.wx) + (inc.cz - v.wz) * (inc.cz - v.wz)
            if (d < bd) { bd = d; nearInc = inc }
        }
        if (nearInc != null && v.jobCell == null) {
            v.act = "look"
            v.working = false
            v.yaw = angLerp(v.yaw, atan2(nearInc.cx - v.wx, nearInc.cz - v.wz), dt * 6f)
            return
        }
        if (v.act == "look") v.act = "wander"

        if (v.wait > 0f) { v.wait -= dt; return }
        if (v.p >= 1f) {
            v.fx = v.tx; v.fy = v.ty
            val jc = v.jobCell
            if (jc != null) {
                val path = v.path
                if (path != null && path.isNotEmpty()) {
                    if (xwait(v, path[0])) { v.wait = 0.4f; return }
                    val n = path.removeAt(0)
                    v.lx = v.fx; v.ly = v.fy; v.tx = n[0]; v.ty = n[1]; v.p = 0f
                } else {
                    v.working = true; v.wait = 0.2f
                }
            } else {
                v.working = false
                val opts = ArrayList<IntArray>(4)
                for (d in DIRS) {
                    val nx = v.fx + d[0]; val ny = v.fy + d[1]
                    if (isRoad(nx, ny) && grid[ny][nx]?.k != "hwy" && !(nx == v.lx && ny == v.ly)) {
                        opts.add(intArrayOf(nx, ny))
                    }
                }
                if (opts.isEmpty() && isRoad(v.lx, v.ly)) opts.add(intArrayOf(v.lx, v.ly))
                if (opts.isNotEmpty()) {
                    val o = opts[Random.nextInt(opts.size)]
                    if (xwait(v, o)) { v.wait = 0.4f; return }
                    v.lx = v.fx; v.ly = v.fy; v.tx = o[0]; v.ty = o[1]; v.p = 0f
                    if (Random.nextFloat() < 0.12f) v.wait = 1f + Random.nextFloat() * 3f
                } else {
                    v.wait = 0.5f
                }
            }
            return
        }
        v.p = min(1f, v.p + dt * 0.45f)
    }

    private fun planVillager(v: VillagerAgent) {
        if (v.jobCell != null && !v.jobRoutine) return
        if (v.act == "build") v.act = "wander"
        val h = (hourNow() + v.hourOffset + 24f) % 24f
        val desired = when {
            h >= 22f || h < 6f -> if (v.isNightOwl) "park" else "sleep"
            h < 8f -> "park"
            h < 13f -> "work"
            h < 14f -> "home"
            h < 17f -> if (v.isStudent) "study" else "work"
            h < 20f -> v.hobby
            else -> if (v.hobby == "park") "park" else "home"
        }
        if (v.want == desired && (v.jobCell != null || clockSec < v.nextPlanClock)) return

        val kinds = mapOf(
            "work" to listOf("comercio", "torre", "empresa", "hospital", "comisaria", "bomberos", "ayuntamiento", "biblioteca"),
            "gym" to listOf("gimnasio"),
            "study" to listOf("escuela", "biblioteca"),
            "park" to listOf("huerto", "iglesia"),
            "show" to listOf("estadio")
        )
        val fallback = mapOf("gym" to "park", "study" to "work", "work" to "wander", "park" to "wander", "show" to "park")
        var t = desired
        var dest: Triple<Int, Int, GridCell>? = null
        var guard = 0
        while (guard++ < 3) {
            if (t == "wander") break
            dest = findDest(v, t, kinds)
            if (dest != null) break
            t = fallback[t] ?: "wander"
        }
        v.want = desired; v.act = t; v.line = null; v.working = false; v.jobCell = null; v.path = null
        if (dest == null) { v.act = "wander"; v.nextPlanClock = clockSec + 15f; return }
        val p = bfsPath(v.tx, v.ty, emergency = false, ped = true) { gx, gy ->
            abs(gx - dest.first) + abs(gy - dest.second) == 1
        }
        if (p == null) { v.act = "wander"; v.nextPlanClock = clockSec + 15f; return }
        v.path = p; v.jobX = dest.first; v.jobY = dest.second; v.jobCell = dest.third; v.jobRoutine = true
    }

    private fun findDest(v: VillagerAgent, act: String, kinds: Map<String, List<String>>): Triple<Int, Int, GridCell>? {
        if (act == "sleep" || act == "home") {
            val hc = v.homeCell
            if (hc != null && grid[v.homeY][v.homeX] === hc) return Triple(v.homeX, v.homeY, hc)
            for (y in 0 until GRID_SIZE) {
                for (x in 0 until GRID_SIZE) {
                    val c = grid[y][x]
                    if (c != null && c.t == "house" && adjRoad(x, y) && Random.nextFloat() < 0.25f) {
                        v.homeX = x; v.homeY = y; v.homeCell = c
                        return Triple(x, y, c)
                    }
                }
            }
            return null
        }
        val cached = v.destCache[act]
        val ks = kinds[act] ?: return null
        if (cached != null) {
            val cc = grid[cached.second][cached.first]
            if (cc != null && cc.t == "b" && cc.k in ks) return Triple(cached.first, cached.second, cc)
        }
        var best: Triple<Int, Int, GridCell>? = null
        var bd = Float.MAX_VALUE
        for (y in 0 until GRID_SIZE) {
            for (x in 0 until GRID_SIZE) {
                val c = grid[y][x]
                if (c != null && c.t == "b" && c.k in ks) {
                    val d = abs(x - v.tx) + abs(y - v.ty) + Random.nextFloat() * 10f
                    if (d < bd) { bd = d; best = Triple(x, y, c) }
                }
            }
        }
        if (best != null) v.destCache[act] = best.first to best.second
        return best
    }

    private fun updateVillagerVisual(v: VillagerAgent, dt: Float, fx: Float, fz: Float) {
        val f = v.p
        val gxf = v.fx + (v.tx - v.fx) * f
        val gyf = v.fy + (v.ty - v.fy) * f
        val ddx = (v.tx - v.fx).toFloat()
        val ddz = (v.ty - v.fy).toFloat()
        if (ddx != 0f || ddz != 0f) { v.dx = ddx; v.dz = ddz }
        var moving = (v.wait <= 0f) && (v.p < 1f && (ddx != 0f || ddz != 0f))
        if (v.working || v.act == "look") moving = false
        val tox = -v.dz * v.lane * 3.2f
        val toz = v.dx * v.lane * 3.2f
        val k = min(1f, dt * 5f)
        v.ox += (tox - v.ox) * k
        v.oz += (toz - v.oz) * k
        val x = gx2x(gxf) + v.ox
        val z = gx2x(gyf) + v.oz
        var targetYaw = atan2(v.dx, v.dz)
        if (v.working && v.jobCell != null) {
            targetYaw = atan2(gx2x(v.jobX) - x, gx2x(v.jobY) - z)
        }
        if (v.act != "look") v.yaw = angLerp(v.yaw, targetYaw, dt * 8f)
        v.mv += ((if (moving) 1f else 0f) - v.mv) * min(1f, dt * 8f)
        v.ph += dt * 8f * v.mv
        val dx = x - fx
        val dz = z - fz
        v.visible = (dx * dx + dz * dz < 185f * 185f) && !(v.working && v.act == "sleep")
        v.wx = x
        v.wy = 0.05f + lerpY(v.fx, v.fy, v.tx, v.ty, v.p)
        v.wz = z
    }

    fun spawnEmergencyVehicle(type: String, nearX: Int, nearY: Int): VehicleAgent? {
        var bestX = -1; var bestY = -1; var bd = Int.MAX_VALUE
        for (y in 0 until GRID_SIZE) {
            for (x in 0 until GRID_SIZE) {
                if (!isRoad(x, y) || events.blk.containsKey(y * GRID_SIZE + x)) continue
                val d = abs(x - nearX) + abs(y - nearY)
                if (d in 4..28 && d < bd) { bd = d; bestX = x; bestY = y }
            }
        }
        if (bestX < 0) return spawnVehicle(type)
        val color = when (type) {
            "ambulancia" -> 0xFFF4F4F0.toInt()
            "policia" -> 0xFF2A3A6A.toInt()
            else -> 0xFFD9302A.toInt()
        }
        val v = VehicleAgent(
            type = type, color = color,
            fx = bestX, fy = bestY, tx = bestX, ty = bestY,
            speed = 2.0f, wx = gx2x(bestX), wz = gx2x(bestY)
        )
        vehicles.add(v)
        return v
    }

    private fun spawnVehicle(type: String): VehicleAgent? {
        val rs = ArrayList<IntArray>()
        for (y in 0 until GRID_SIZE) {
            for (x in 0 until GRID_SIZE) {
                if (isRoad(x, y)) rs.add(intArrayOf(x, y))
            }
        }
        if (rs.isEmpty()) return null
        val p = rs[Random.nextInt(rs.size)]
        val baseSpd = when (type) {
            "auto" -> 1.3f
            "moto" -> 1.6f
            "camion" -> 0.95f
            "bus" -> 1.05f
            "ambulancia" -> 1.85f
            "policia" -> 1.95f
            else -> 1.6f
        }
        val color = when (type) {
            "bus" -> intArrayOf(0xFF2A6FD6.toInt(), 0xFFD9483A.toInt(), 0xFFE8C26A.toInt()).random()
            "ambulancia" -> 0xFFF4F4F0.toInt()
            "policia" -> 0xFF2A3A6A.toInt()
            "bombero" -> 0xFFD9302A.toInt()
            else -> VEHICLE_COLORS.random()
        }
        val v = VehicleAgent(
            type = type, color = color, fx = p[0], fy = p[1], tx = p[0], ty = p[1],
            speed = baseSpd * (0.9f + Random.nextFloat() * 0.2f),
            wx = gx2x(p[0]), wz = gx2x(p[1])
        )
        vehicles.add(v)
        return v
    }

    private fun manageVehicles(st: CityStats) {
        val h = hourNow()
        val night = h >= 22f || h < 6f
        val rush = (h in 7f..10f) || (h in 17f..20f)
        val f = if (night) 0.25f else if (rush) 1f else 0.6f
        val base = if (st.roads >= 6) min(st.population / 4f, st.roads * 0.35f) else 0f
        val au = if (base > 0f) min(32, max(if (st.population >= 3) 1 else 0, (base * f).roundToInt())) else 0
        val want = mapOf(
            "bus" to if (st.population >= 6) min(8, if (night) st.terminals else st.terminals * 2) else 0,
            "camion" to if (night) 0 else min(10, min(st.empresas * 2, (st.roads * 0.1f).toInt())),
            "auto" to au,
            "moto" to min(10, au / 3),
            "ambulancia" to min(3, st.counts["hospital"] ?: 0),
            "policia" to min(4, (st.counts["comisaria"] ?: 0) * 2),
            "bombero" to min(3, st.counts["bomberos"] ?: 0)
        )
        for ((type, target) in want) {
            val mine = vehicles.filter { it.type == type }
            if (mine.size < target) {
                spawnVehicle(type)
            } else if (mine.size > target) {
                val hid = mine.firstOrNull { !it.visible && it.em == null }
                    ?: if (mine.size > target + 2) mine.firstOrNull { it.em == null } else null
                if (hid != null) vehicles.remove(hid)
            }
        }
    }

    private fun newVehicleTrip(c: VehicleAgent): Boolean {
        val ks = when (c.type) {
            "auto", "moto" -> listOf("comercio", "torre", "empresa", "gimnasio", "escuela", "huerto")
            "camion" -> listOf("empresa", "comercio")
            "bus" -> listOf("terminal")
            "ambulancia" -> listOf("hospital", "comercio", "empresa", "escuela")
            "policia" -> listOf("comisaria", "comercio", "terminal", "torre", "estadio")
            else -> listOf("bomberos", "empresa", "torre", "escuela")
        }
        var bestX = -1; var bestY = -1; var n = 0
        for (y in 0 until GRID_SIZE) {
            for (x in 0 until GRID_SIZE) {
                val cl = grid[y][x]
                if (cl != null && cl.t == "b" && cl.k in ks) {
                    if (Random.nextFloat() * (++n) < 1f) { bestX = x; bestY = y }
                }
            }
        }
        if (bestX < 0) return false
        val p = bfsPath(c.tx, c.ty, emergency = c.em != null, ped = false) { gx, gy ->
            abs(gx - bestX) + abs(gy - bestY) == 1
        } ?: return false
        c.path = p; c.destX = bestX; c.destY = bestY
        return true
    }

    private fun isBlockedAhead(c: VehicleAgent): Boolean {
        if (c.emHold) return true
        val nx = c.tx; val ny = c.ty
        val key = ny * GRID_SIZE + nx
        if (c.em == null && (events.blk.containsKey(key) || events.trainBlocks.contains(key))) {
            return true
        }
        // Keep safe separation from vehicle directly in front in same lane
        for (other in vehicles) {
            if (other === c || other.dead) continue
            val d2 = (other.wx - c.wx) * (other.wx - c.wx) + (other.wz - c.wz) * (other.wz - c.wz)
            if (d2 < 3.6f * 3.6f) {
                val dot = (other.wx - c.wx) * c.dx + (other.wz - c.wz) * c.dz
                if (dot > 0.3f && other.dx * c.dx + other.dz * c.dz > 0.5f) {
                    return true
                }
            }
        }
        return false
    }

    private fun stepVehicle(c: VehicleAgent, dt: Float, st: CityStats) {
        if (!isRoad(c.fx, c.fy) || !isRoad(c.tx, c.ty)) { c.dead = true; return }
        if (c.emHold) { c.brake = 1f; return }
        if (c.wait > 0f) { c.wait -= dt; return }
        if (c.p >= 1f) {
            c.fx = c.tx; c.fy = c.ty
            val path = c.path
            if (path != null && path.isNotEmpty()) {
                val n = path[0]
                if (c.em == null && (events.blk.containsKey(n[1] * GRID_SIZE + n[0]) || events.trainBlocks.contains(n[1] * GRID_SIZE + n[0]))) {
                    c.wait = 0.5f; c.brake = 1f; return
                }
                path.removeAt(0)
                c.lx = c.fx; c.ly = c.fy; c.tx = n[0]; c.ty = n[1]; c.p = 0f
            } else if (c.destX >= 0) {
                c.destX = -1
                c.wait = 3f + Random.nextFloat() * 4f
                if (st.population > 0) {
                    coins += when (c.type) {
                        "auto" -> 0.5
                        "moto" -> 0.3
                        "camion" -> 3.0
                        "bus" -> 2.0
                        else -> 0.0
                    }
                }
            } else if (!newVehicleTrip(c)) {
                val op = ArrayList<IntArray>(4)
                for (d in DIRS) {
                    val nx = c.fx + d[0]; val ny = c.fy + d[1]
                    if (isRoad(nx, ny) && !(nx == c.lx && ny == c.ly)) op.add(intArrayOf(nx, ny))
                }
                if (op.isEmpty() && isRoad(c.lx, c.ly)) op.add(intArrayOf(c.lx, c.ly))
                if (op.isNotEmpty()) {
                    val o = op[Random.nextInt(op.size)]
                    c.lx = c.fx; c.ly = c.fy; c.tx = o[0]; c.ty = o[1]; c.p = 0f
                } else {
                    c.wait = 0.5f
                }
            }
            return
        }
        val stopP = when (c.type) {
            "auto" -> 0.3f
            "moto" -> 0.35f
            "camion" -> 0.2f
            "bus" -> 0.12f
            else -> 0.25f
        }
        if (c.em == null && c.p >= stopP && c.p < stopP + 0.1f && isIntersection(c.tx, c.ty) && !trafficGreen(c.tx == c.fx)) {
            c.brake = 1f
            return
        }
        if (isBlockedAhead(c)) {
            c.brake = 1f
            c.stuck += dt
            if (c.stuck > 2.5f && c.visible && Random.nextFloat() < 0.02f) {
                triggerSound("honk")
            }
            return
        }
        c.stuck = 0f
        c.brake = max(0f, c.brake - dt * 3f)
        val cellSpd = when (grid[c.fy][c.fx]?.k) {
            "hwy" -> 1.65f
            "ave" -> 1.2f
            else -> 1f
        }
        val spdMul = cellSpd * (if (c.em != null) 1.45f else 1f) * (1f - wx.rain * 0.12f)
        c.p = min(1f, c.p + dt * c.speed * spdMul)
        c.amp += dt * c.speed * 7f
    }

    private fun updateVehicleVisual(c: VehicleAgent, dt: Float, fx: Float, fz: Float) {
        val f = c.p
        val gxf = c.fx + (c.tx - c.fx) * f
        val gyf = c.fy + (c.ty - c.fy) * f
        val ddx = (c.tx - c.fx).toFloat()
        val ddz = (c.ty - c.fy).toFloat()
        if (ddx != 0f || ddz != 0f) { c.dx = ddx; c.dz = ddz }
        val laneOff = when (grid[c.fy][c.fx]?.k) {
            "ave" -> 1.85f
            "hwy" -> 1.75f
            else -> 1.45f
        }
        val k = min(1f, dt * 5f)
        c.ox += (-c.dz * laneOff - c.ox) * k
        c.oz += (c.dx * laneOff - c.oz) * k
        val x = gx2x(gxf) + c.ox
        val z = gx2x(gyf) + c.oz
        c.yaw = angLerp(c.yaw, atan2(c.dx, c.dz), dt * 8f)
        c.wx = x
        c.wy = 0.05f + lerpY(c.fx, c.fy, c.tx, c.ty, c.p)
        c.wz = z
        c.visible = (x - fx) * (x - fx) + (z - fz) * (z - fz) < 195f * 195f
    }

    private fun finishSites(dt: Float) {
        val done = ArrayList<Triple<Int, Int, GridCell>>()
        for (y in 0 until GRID_SIZE) {
            for (x in 0 until GRID_SIZE) {
                val c = grid[y][x]
                if (c != null && c.t == "site") c.hasWorker = false
            }
        }
        for (v in villagers) {
            val jc = v.jobCell
            if (v.working && jc != null && jc.t == "site") jc.hasWorker = true
        }
        for (y in 0 until GRID_SIZE) {
            for (x in 0 until GRID_SIZE) {
                val c = grid[y][x] ?: continue
                if (c.t != "site") continue
                val rate = if (c.hasWorker) 1.0 else 0.45
                c.p += dt * rate / c.dur
                if (c.p >= 1.0) done.add(Triple(x, y, c))
            }
        }
        if (done.isNotEmpty()) {
            val e = eraOfCiv(civ)
            for ((x, y, c) in done) {
                if (c.k == "house") {
                    grid[y][x] = GridCell(t = "house", s = c.s, auto = c.auto)
                    addLogMsg("Los aldeanos levantaron una casa.")
                } else {
                    val compName = if (c.k == "empresa") COMPANY_NAMES.random() else ""
                    grid[y][x] = GridCell(
                        t = "b", k = c.k, s = c.s, auto = c.auto, nm = compName,
                        big = c.big, ox = c.ox, oy = c.oy
                    )
                    if (c.k == "empresa") {
                        addLogMsg("Se fundó la empresa $compName.")
                    } else {
                        addLogMsg("Se inauguró: ${buildingDisplayName(c.k, e).lowercase()}.")
                    }
                }
            }
            cachedStats = null
            gridRevision++
        }
    }

    private fun isBlockedForWalker(x: Float, z: Float): Boolean {
        val gx = x2g(x); val gy = x2g(z)
        if (isWater(gx, gy) && grid[gy][gx]?.br != true) return true
        for (dy in -1..1) {
            for (dx in -1..1) {
                val cx = gx + dx; val cy = gy + dy
                if (!inBounds(cx, cy)) continue
                val c = grid[cy][cx] ?: continue
                if (c.t != "road" && c.t != "rail" && !(c.t == "b" && c.k == "huerto")) {
                    if (abs(x - gx2x(cx)) < 3.5f && abs(z - gx2x(cy)) < 3.5f) return true
                }
            }
        }
        return false
    }

    private fun updateWalker(dt: Float) {
        if (!walkMode) return
        if (walkJy > 0f || walkVy != 0f) {
            walkVy -= 28f * dt
            walkJy += walkVy * dt
            if (walkJy <= 0f) { walkJy = 0f; walkVy = 0f }
        }
        var ix = joyX
        var iy = -joyY
        var len = hypot(ix, iy)
        if (len > 1f) { ix /= len; iy /= len; len = 1f }
        val s = sin(walkCamYaw); val c = cos(walkCamYaw)
        val mx = -s * iy + c * ix
        val mz = -c * iy - s * ix
        val run = sprinting || len > 0.94f
        val spd = (if (run) 9.5f else 4.5f) * len
        val lim = GRID_SIZE * TILE_SIZE / 2f - 3f
        if (len > 0.05f) {
            val nx = (walkX + mx / len * spd * dt).coerceIn(-lim, lim)
            val nz = (walkZ + mz / len * spd * dt).coerceIn(-lim, lim)
            if (!isBlockedForWalker(nx, walkZ)) walkX = nx
            if (!isBlockedForWalker(walkX, nz)) walkZ = nz
            walkHeading = angLerp(walkHeading, atan2(mx, mz), dt * 10f)
        }
        val gx = x2g(walkX); val gy = x2g(walkZ)
        walkY += (hAt(gx, gy) - walkY) * min(1f, dt * 10f)
        walkMv += ((if (len > 0.05f) 1f else 0f) - walkMv) * min(1f, dt * 10f)
        walkPh += dt * (if (run) 13f else 8f) * walkMv
    }

    fun step(rawDt: Float, speedMultiplier: Int) {
        synchronized(lock) {
            val dtWall = rawDt.coerceIn(0f, 0.08f)
            updateWalker(dtWall)
            val dt = dtWall * speedMultiplier
            clockSec += dt
            if (speedMultiplier <= 0) {
                val fx = if (walkMode) walkX else camX
                val fz = if (walkMode) walkZ else camZ
                for (v in villagers) updateVillagerVisual(v, 0f, fx, fz)
                for (c in vehicles) updateVehicleVisual(c, 0f, fx, fz)
                return
            }

            gameTime += dt
            mana = min(100f, mana + 3f * dt)
            statAcc += dt
            if (cachedStats == null || statAcc >= 0.25f) {
                cachedStats = stats()
                statAcc = 0f
            }
            val st = cachedStats!!

            wx.step(dt, hourNow(), st.era, this)

            val hInt = floor(hourNow()).toInt()
            if (hInt != lastBellHour) {
                lastBellHour = hInt
                if ((hInt == 8 || hInt == 12 || hInt == 19) && (st.counts["iglesia"] ?: 0) > 0) {
                    triggerSound("bell")
                }
            }

            econAcc += dt
            while (econAcc >= 1f) {
                econAcc -= 1f
                val sNow = stats().also { cachedStats = it }
                val gain = if (sNow.population > 0 || bless > 0) {
                    (sNow.population * 0.2 + sNow.shops * 1.0 + sNow.empresas * 2.0 + sNow.terminals * 1.0 + bless * 0.3) *
                        (0.7 + 0.6 * sNow.wellbeing / 100.0) * (tax / 10.0)
                } else 0.0
                var upkeep = 0.0
                for ((k, u) in UPKEEP_COSTS) {
                    upkeep += (sNow.counts[k] ?: 0) * u
                }
                val cost2 = upkeep * (spend / 100.0)
                lastIn += (gain - lastIn) * 0.2
                lastOut += (cost2 - lastOut) * 0.2
                coins = max(0.0, coins + gain - cost2)
                if (coins < 1.0 && cost2 > gain) broke = true else if (coins > 25.0) broke = false

                if (sNow.population > 0) {
                    civ += (sNow.population * 0.12 + sNow.buildings * 0.5) * (0.7 + 0.6 * sNow.wellbeing / 100.0) * EVO
                }
                val newEra = eraOfCiv(civ)
                if (newEra > era) {
                    era = newEra
                    val def = ERAS[newEra]
                    lastBannerMessage = "Tu ciudad ahora es: ${def.name}"
                    addLogMsg("Nueva era alcanzada: ${def.name}.")
                    eraChangedEvent = def
                    gridRevision++
                }
            }

            siteAcc += dt
            if (siteAcc >= 0.25f) {
                finishSites(siteAcc)
                siteAcc = 0f
            }

            spawnAcc += dt
            val targetPeds = if (st.roads < 4) 0 else min(st.population, min((st.roads * 1.2f).toInt(), 100))
            if (spawnAcc > 0.22f) {
                spawnAcc = 0f
                if (villagers.size < targetPeds) {
                    spawnVillager()
                } else if (villagers.size > targetPeds && villagers.isNotEmpty()) {
                    val hidIdx = villagers.indexOfFirst { !it.visible }
                    villagers.removeAt(if (hidIdx >= 0) hidIdx else villagers.lastIndex)
                }
            }

            planTick++
            for (i in villagers.indices.reversed()) {
                val v = villagers[i]
                stepVillager(v, dt)
                if ((i + planTick) % 8 == 0) planVillager(v)
                if (v.dead) villagers.removeAt(i)
            }

            vehAcc += dt
            if (vehAcc > 0.7f) {
                vehAcc = 0f
                manageVehicles(st)
            }
            for (j in vehicles.indices.reversed()) {
                val c = vehicles[j]
                stepVehicle(c, dt, st)
                if (c.dead) vehicles.removeAt(j)
            }

            events.stepTrains(dt, st.population)
            events.stepIncidents(dt, st)
            events.stepParticles(dt)

            val fx = if (walkMode) walkX else camX
            val fz = if (walkMode) walkZ else camZ
            for (v in villagers) updateVillagerVisual(v, dt, fx, fz)
            for (c in vehicles) updateVehicleVisual(c, dt, fx, fz)
        }
    }

    fun nearestSpeechBubble(): SpeechBubbleData? {
        synchronized(lock) {
            if (!walkMode) return null
            var best: VillagerAgent? = null
            var bd = 36f
            for (v in villagers) {
                if (v.working && v.act == "sleep") continue
                val d = (v.wx - walkX) * (v.wx - walkX) + (v.wz - walkZ) * (v.wz - walkZ)
                if (d < bd) { bd = d; best = v }
            }
            best ?: return null
            val ak = if (best.working || best.act == "look") best.act else ""
            if (best.line == null || best.lineWork != ak) {
                best.lineWork = ak
                best.line = when {
                    best.blessed -> BLESS_DIALOGUES.random()
                    ak == "build" -> WORK_DIALOGUES.random()
                    ACTIVITY_DIALOGUES.containsKey(ak) -> ACTIVITY_DIALOGUES[ak]!!.random()
                    else -> ERA_DIALOGUES[eraOfCiv(civ)].random()
                }
            }
            for (v in villagers) if (v !== best) v.line = null
            val actLabel = if (best.working || best.act == "look") ACTIVITY_LABELS[best.act] else null
            return SpeechBubbleData(
                speakerName = best.look.name,
                activityLabel = actLabel,
                text = best.line!!,
                worldX = best.wx,
                worldY = best.wy + 2.25f * best.look.heightScale,
                worldZ = best.wz
            )
        }
    }

    fun buildHudState(hintOverride: String?): HudState {
        synchronized(lock) {
            val st = cachedStats ?: stats().also { cachedStats = it }
            val e = st.era.coerceIn(0, ERAS.lastIndex)
            val curAt = ERAS[e].atCiv
            val nextEra = if (e < ERAS.lastIndex) ERAS[e + 1] else null
            val prog = if (nextEra != null) {
                ((civ - curAt) / (nextEra.atCiv - curAt)).toFloat().coerceIn(0f, 1f)
            } else 1f
            val h = hourNow()
            val hh = floor(h).toInt().toString().padStart(2, '0')
            val mm = floor((h % 1f) * 60f).toInt().toString().padStart(2, '0')

            val svcList = SERVICE_REQUIREMENTS.mapNotNull { sr ->
                val skip = e < sr.minEra || (sr.minEra >= 1 && st.population < 12) || (sr.key == "ayuntamiento" && st.population < 30)
                if (skip && sr.minEra >= 1) null
                else {
                    val need = if (sr.perPop > 0) max(1, ceil(st.population.toDouble() / sr.perPop).toInt()) else 1
                    ServiceStatus(sr.key, sr.icon, sr.label, st.counts[sr.key] ?: 0, need)
                }
            }

            val actCounts = LinkedHashMap<String, Int>()
            for (v in villagers) {
                val a = if (ACTIVITY_LABELS.containsKey(v.act)) v.act else "wander"
                actCounts[a] = (actCounts[a] ?: 0) + 1
            }
            if (vehicles.isNotEmpty()) actCounts["veh"] = vehicles.size
            val actList = actCounts.entries.map { (k, count) ->
                ActivityStatus(ACTIVITY_EMOJIS[k] ?: "🚶", count, ACTIVITY_LABELS[k] ?: k)
            }

            val hint = hintOverride ?: if (st.population == 0 && !walkMode) {
                "Poné una casa pegada a una calle para que lleguen los primeros aldeanos."
            } else null

            val regObj = WEATHER_REGIMES[wx.reg] ?: WEATHER_REGIMES.getValue("clr")
            val wxDesc = when {
                wx.snow > 0.25f -> "Nevando"
                wx.cover > 0.25f && wx.rain < 0.1f -> "Nevado"
                else -> regObj.label
            }
            val incSummaries = events.incidents.map {
                IncidentSummary(it.id, it.emoji(), it.title(), it.statusLabel(), it.cx, it.cz)
            }

            return HudState(
                cityName = cityName,
                coins = coins.toInt(),
                population = st.population,
                houses = st.houses,
                buildings = st.buildings,
                roads = st.roads,
                eraIndex = e,
                eraName = ERAS[e].name,
                civPoints = civ.toInt(),
                nextEraName = nextEra?.name,
                nextEraAt = nextEra?.atCiv,
                eraProgress = prog,
                territorySize = territorySize(),
                expTier = exp,
                nextExpandCost = if (exp < EXPAND_COSTS.size) EXPAND_COSTS[exp] else null,
                wellbeing = st.wellbeing.roundToInt(),
                formattedTime = "$hh:$mm",
                services = svcList,
                activities = actList,
                logs = logs.toList(),
                hintText = hint,
                mana = mana.toInt(),
                tax = tax,
                spend = spend,
                bless = bless,
                broke = broke,
                netPerSec = lastIn - lastOut,
                inPerSec = lastIn,
                outPerSec = lastOut,
                wxIcon = wx.wxIcon(h),
                tempC = wx.temp.roundToInt(),
                wxDesc = wxDesc,
                seasonName = wx.seasonName(),
                windKmh = (wx.windNow() * 35f).roundToInt(),
                wxAuto = wx.auto,
                wxRaining = wx.rain > 0.35f,
                incAuto = events.incAuto,
                incidents = incSummaries
            )
        }
    }

    fun minimapCells(): List<MinimapCell> {
        synchronized(lock) {
            val out = ArrayList<MinimapCell>(700)
            for (y in 0 until GRID_SIZE) {
                for (x in 0 until GRID_SIZE) {
                    val c = grid[y][x]
                    if (c == null) {
                        if (water[y][x]) out.add(MinimapCell(x, y, 6))
                        continue
                    }
                    val kind = when (c.t) {
                        "road" -> 1
                        "house" -> 2
                        "rail" -> 5
                        "b" -> 3
                        else -> 4
                    }
                    out.add(MinimapCell(x, y, kind))
                }
            }
            return out
        }
    }

    companion object {
        fun angLerp(a: Float, b: Float, k: Float): Float {
            var d = b - a
            while (d > PI.toFloat()) d -= (PI * 2.0).toFloat()
            while (d < -PI.toFloat()) d += (PI * 2.0).toFloat()
            return a + d * min(1f, k)
        }
    }
}
