package com.example

import com.example.data.CitySaveDoc
import com.example.simulation.BRIDGE_COST
import com.example.simulation.BuildTool
import com.example.simulation.CitySimulation
import com.example.simulation.GRID_SIZE
import com.example.simulation.PlaceOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CitySimulationTest {

    @Test
    fun `starter village initializes 70x70 grid roads houses coins and era`() {
        val sim = CitySimulation()
        sim.initStarterVillage()

        val st = sim.stats()
        assertEquals(70, GRID_SIZE)
        assertEquals(0, st.era)
        assertEquals(6, st.roads)
        assertEquals(3, st.houses)
        assertEquals(500.0, sim.coins, 0.001)
        assertEquals(24, sim.territorySize())
    }

    @Test
    fun `placing commerce next to road creates construction site and deducts coins`() {
        val sim = CitySimulation()
        sim.initStarterVillage()
        val initialCoins = sim.coins

        var placedOutcome: PlaceOutcome = PlaceOutcome.NoOp
        outer@ for (y in sim.boundsLo()..sim.boundsHi()) {
            for (x in sim.boundsLo()..sim.boundsHi()) {
                if (sim.grid[y][x] == null && !sim.isWater(x, y) && sim.adjRoad(x, y)) {
                    placedOutcome = sim.place(x, y, BuildTool.COMERCIO)
                    break@outer
                }
            }
        }

        assertTrue(placedOutcome is PlaceOutcome.Success)
        assertEquals(initialCoins - BuildTool.COMERCIO.cost, sim.coins, 0.001)
    }

    @Test
    fun `river rejects non-road buildings and allows bridge road`() {
        val sim = CitySimulation()
        sim.initStarterVillage()
        sim.civ = 2000.0
        repeat(6) {
            sim.coins += 5000.0
            sim.expandTerritory()
        }

        var wx = -1
        var wy = -1
        outer@ for (y in sim.boundsLo()..sim.boundsHi()) {
            for (x in sim.boundsLo()..sim.boundsHi()) {
                if (sim.isWater(x, y) && sim.grid[y][x] == null) {
                    wx = x; wy = y
                    break@outer
                }
            }
        }
        assertTrue(wx >= 0)

        val houseOut = sim.place(wx, wy, BuildTool.HOUSE)
        assertTrue(houseOut is PlaceOutcome.Rejected)

        val beforeCoins = sim.coins
        val bridgeOut = sim.place(wx, wy, BuildTool.ROAD)
        assertTrue(bridgeOut is PlaceOutcome.Success)
        assertTrue(sim.grid[wy][wx]?.br == true)
        assertEquals(beforeCoins - BRIDGE_COST, sim.coins, 0.001)
    }

    @Test
    fun `2x2 building placement creates anchor and part cells and demolish clears full footprint`() {
        val sim = CitySimulation()
        sim.initStarterVillage()
        sim.civ = 100.0 // Era 1 unlocks Hospital
        sim.coins = 1000.0

        var gx = -1
        var gy = -1
        outer@ for (y in sim.boundsLo()..(sim.boundsHi() - 1)) {
            for (x in sim.boundsLo()..(sim.boundsHi() - 1)) {
                if (sim.bigFootprintValid(x, y) != null) {
                    gx = x; gy = y
                    break@outer
                }
            }
        }
        assertTrue(gx >= 0)

        val out = sim.place(gx, gy, BuildTool.HOSPITAL)
        assertTrue(out is PlaceOutcome.Success)
        val anchorCount = (0..1).sumOf { dy -> (0..1).count { dx -> sim.grid[gy + dy][gx + dx]?.t == "site" } }
        val partCount = (0..1).sumOf { dy -> (0..1).count { dx -> sim.grid[gy + dy][gx + dx]?.t == "part" } }
        assertEquals(1, anchorCount)
        assertEquals(3, partCount)

        val del = sim.place(gx, gy, BuildTool.ERASE)
        assertTrue(del is PlaceOutcome.Demolished)
        for (dy in 0..1) {
            for (dx in 0..1) {
                assertNull(sim.grid[gy + dy][gx + dx])
            }
        }
    }

    @Test
    fun `rails and level crossing work and Zeus resolves incidents and blesses villagers`() {
        val sim = CitySimulation()
        sim.initStarterVillage()
        sim.civ = 400.0 // Era 2
        sim.coins = 1000.0
        val o = 3

        // Place rail crossing road at (32+o, 32+o) where road is horizontal
        val crossOut = sim.place(32 + o, 32 + o, BuildTool.TREN)
        assertTrue(crossOut is PlaceOutcome.Success)
        assertEquals("v", sim.grid[32 + o][32 + o]?.rx)

        // Trigger crash incident and resolve with Zeus
        val inc = sim.events.triggerIncident("crash")
        assertNotNull(inc)
        val zeusInc = sim.zeusTap(inc!!.cx, inc.cz)
        assertTrue(zeusInc is PlaceOutcome.Success)
        assertTrue(sim.events.incidents.isEmpty())
        assertEquals(85f, sim.mana, 0.5f)
    }

    @Test
    fun `tax spend and weather step update economy and weather state`() {
        val sim = CitySimulation()
        sim.initStarterVillage()
        sim.setTaxRate(18)
        sim.setSpendRate(125)
        sim.wx.setRegime("stm", sim)
        repeat(10) { sim.step(0.05f, 2) }

        assertEquals(18, sim.tax)
        assertEquals(125, sim.spend)
        assertTrue(sim.wx.rain > 0f)
    }

    @Test
    fun `export and loadFromSave roundtrips 70x70 grid tax spend bless and progression`() {
        val sim = CitySimulation()
        sim.initStarterVillage()
        sim.coins = 250.0
        sim.setTaxRate(15)
        sim.setSpendRate(110)
        sim.bless = 4
        sim.expandTerritory()
        val snapshot = sim.exportSnapshot()

        val restored = CitySimulation()
        restored.loadFromSave(
            CitySaveDoc(
                id = "main_city",
                userId = "test_user",
                cityName = "Puerto Nuevo",
                coins = snapshot.coins,
                civ = 120.0,
                gameTime = snapshot.gameTime,
                era = 1L,
                exp = snapshot.exp.toLong(),
                tax = snapshot.tax.toLong(),
                spend = snapshot.spend.toLong(),
                bless = snapshot.bless.toLong(),
                logEntries = snapshot.logEntries,
                cells = snapshot.cells.map { it.toFirestoreMap() }
            )
        )

        assertEquals(32, restored.territorySize())
        assertEquals(1, restored.stats().era)
        assertEquals(15, restored.tax)
        assertEquals(110, restored.spend)
        assertEquals(4, restored.bless)
        assertFalse(restored.inUsable(0, 0))
    }
}
