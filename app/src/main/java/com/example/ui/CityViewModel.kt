package com.example.ui

import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CityRepository
import com.example.data.UiState
import com.example.gl.CityRenderer
import com.example.simulation.*
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

data class ProjectedBubble(
    val speakerName: String,
    val activityLabel: String?,
    val text: String,
    val screenX: Float,
    val screenY: Float
)

data class ProjectedMarker(
    val id: Int,
    val emoji: String,
    val screenX: Float,
    val screenY: Float
)

class CityViewModel(
    private val repository: CityRepository,
    private val currentUserId: String
) : ViewModel() {

    val sim = CitySimulation()
    val sound = SoundEffects()

    private val _loadState = MutableStateFlow<UiState<Unit>>(UiState.Loading)
    val loadState: StateFlow<UiState<Unit>> = _loadState.asStateFlow()

    private val _hudState = MutableStateFlow(sim.buildHudState(null))
    val hudState: StateFlow<HudState> = _hudState.asStateFlow()

    private val _activeTool = MutableStateFlow(BuildTool.ROAD)
    val activeTool: StateFlow<BuildTool> = _activeTool.asStateFlow()

    private val _walkMode = MutableStateFlow(false)
    val walkMode: StateFlow<Boolean> = _walkMode.asStateFlow()

    private val _speedMultiplier = MutableStateFlow(1)
    val speedMultiplier: StateFlow<Int> = _speedMultiplier.asStateFlow()

    private val _soundMuted = MutableStateFlow(false)
    val soundMuted: StateFlow<Boolean> = _soundMuted.asStateFlow()

    private val _photoMode = MutableStateFlow(false)
    val photoMode: StateFlow<Boolean> = _photoMode.asStateFlow()

    private val _sprinting = MutableStateFlow(false)
    val sprinting: StateFlow<Boolean> = _sprinting.asStateFlow()

    private val _bannerText = MutableStateFlow<String?>(null)
    val bannerText: StateFlow<String?> = _bannerText.asStateFlow()

    private val _inspectedTile = MutableStateFlow<InspectedTile?>(null)
    val inspectedTile: StateFlow<InspectedTile?> = _inspectedTile.asStateFlow()

    private val _projectedBubble = MutableStateFlow<ProjectedBubble?>(null)
    val projectedBubble: StateFlow<ProjectedBubble?> = _projectedBubble.asStateFlow()

    private val _projectedMarkers = MutableStateFlow<List<ProjectedMarker>>(emptyList())
    val projectedMarkers: StateFlow<List<ProjectedMarker>> = _projectedMarkers.asStateFlow()

    private val _syncMessage = MutableStateFlow("Sincronizado")
    val syncMessage: StateFlow<String> = _syncMessage.asStateFlow()

    private var hasCloudDocument = false
    private var initialHydrated = false
    private var hintOverride: String? = null
    private var hintJob: Job? = null
    private var bannerJob: Job? = null
    private var uiRefreshAcc = 0f
    private var autoSaveAcc = 0f

    init {
        hydrateInitialCity()
        startAutoSaveLoop()
    }

    private fun hydrateInitialCity() {
        viewModelScope.launch {
            _loadState.value = UiState.Loading
            val res = repository.getCityOnce(currentUserId)
            res.fold(
                onSuccess = { doc ->
                    if (doc != null) {
                        hasCloudDocument = true
                        sim.loadFromSave(doc)
                    } else {
                        hasCloudDocument = false
                        sim.initStarterVillage()
                        saveCityNow(forceCreateCheck = true)
                    }
                    initialHydrated = true
                    _hudState.value = sim.buildHudState(hintOverride)
                    _loadState.value = UiState.Success(Unit)
                },
                onFailure = { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    Log.w(TAG, "Failed to load city from cloud", error)
                    initialHydrated = true
                    _hudState.value = sim.buildHudState("Modo sin conexión: ${(firestoreError ?: error).localizedMessage}")
                    _loadState.value = UiState.Success(Unit)
                }
            )
        }
    }

    private fun startAutoSaveLoop() {
        viewModelScope.launch {
            while (true) {
                delay(10_000L)
                if (initialHydrated) {
                    saveCityNow()
                }
            }
        }
    }

    fun onGlFrameTick(rawDt: Float, renderer: CityRenderer) {
        val spd = _speedMultiplier.value
        sim.step(rawDt, spd)

        for (s in sim.drainSounds()) {
            when (s) {
                "thunder" -> sound.playThunder()
                "crash" -> sound.playCrash()
                "honk" -> sound.playHonk()
                "zap" -> sound.playZap()
                "train" -> sound.playTrainWhistle()
                "bell" -> sound.playBell()
            }
        }

        val eraEvt = sim.eraChangedEvent
        if (eraEvt != null) {
            sim.eraChangedEvent = null
            sound.playEraFanfare()
            showBanner("¡Tu ciudad ahora es: ${eraEvt.name}!")
            saveCityNow()
        }
        val bMsg = sim.lastBannerMessage
        if (bMsg != null) {
            sim.lastBannerMessage = null
            showBanner(bMsg)
        }

        uiRefreshAcc += rawDt
        if (uiRefreshAcc >= 0.18f) {
            uiRefreshAcc = 0f
            val hud = sim.buildHudState(hintOverride)
            _hudState.value = hud

            if (sim.walkMode) {
                val bubble = sim.nearestSpeechBubble()
                if (bubble != null) {
                    val pt = renderer.worldToScreen(bubble.worldX, bubble.worldY, bubble.worldZ)
                    _projectedBubble.value = if (pt.visible) {
                        ProjectedBubble(bubble.speakerName, bubble.activityLabel, bubble.text, pt.x, pt.y)
                    } else null
                } else {
                    _projectedBubble.value = null
                }
            } else {
                _projectedBubble.value = null
            }

            if (hud.incidents.isNotEmpty() && !_photoMode.value) {
                val projected = hud.incidents.mapNotNull { inc ->
                    val pt = renderer.worldToScreen(inc.cx, 8.5f, inc.cz)
                    if (pt.visible && pt.x in -40f..(renderer.viewportWidth + 40f) && pt.y in -40f..(renderer.viewportHeight + 40f)) {
                        ProjectedMarker(inc.id, inc.emoji, pt.x, pt.y)
                    } else null
                }
                _projectedMarkers.value = projected
            } else if (_projectedMarkers.value.isNotEmpty()) {
                _projectedMarkers.value = emptyList()
            }
        }

        autoSaveAcc += rawDt
        if (autoSaveAcc >= 25f) {
            autoSaveAcc = 0f
            saveCityNow()
        }
    }

    fun selectTool(tool: BuildTool) {
        if (_walkMode.value) {
            toggleWalkMode()
        }
        _activeTool.value = tool
        _inspectedTile.value = null
    }

    fun handleZeusTap(wx: Float, wz: Float) {
        when (val outcome = sim.zeusTap(wx, wz)) {
            is PlaceOutcome.Success -> {
                sound.playZap()
                _hudState.value = sim.buildHudState(hintOverride)
            }
            is PlaceOutcome.Rejected -> {
                sound.playError()
                flashHint(outcome.reason)
            }
            else -> Unit
        }
    }

    fun handleTileAction(gx: Int, gy: Int, isTap: Boolean): Boolean {
        val tool = _activeTool.value
        if (tool == BuildTool.CAMERA) {
            if (isTap) {
                _inspectedTile.value = sim.inspectTile(gx, gy)
            }
            return false
        }
        val outcome = sim.place(gx, gy, tool)
        return when (outcome) {
            is PlaceOutcome.Success -> {
                when (outcome.tool) {
                    BuildTool.ZEUS -> sound.playZap()
                    BuildTool.ROAD, BuildTool.AVENIDA, BuildTool.AUTOPISTA -> sound.playRoad()
                    else -> sound.playBuild()
                }
                _hudState.value = sim.buildHudState(hintOverride)
                true
            }
            is PlaceOutcome.Demolished -> {
                sound.playDemolish()
                _inspectedTile.value = null
                _hudState.value = sim.buildHudState(hintOverride)
                true
            }
            is PlaceOutcome.Rejected -> {
                sound.playError()
                flashHint(outcome.reason)
                false
            }
            PlaceOutcome.NoOp -> false
        }
    }

    fun demolishInspected() {
        val tile = _inspectedTile.value ?: return
        val outcome = sim.place(tile.gx, tile.gy, BuildTool.ERASE)
        if (outcome is PlaceOutcome.Demolished) {
            sound.playDemolish()
            _inspectedTile.value = null
            _hudState.value = sim.buildHudState(hintOverride)
            saveCityNow()
        }
    }

    fun dismissInspector() {
        _inspectedTile.value = null
    }

    fun toggleWalkMode() {
        val nowWalk = sim.toggleWalkMode()
        _walkMode.value = nowWalk
        _inspectedTile.value = null
        if (!nowWalk) {
            _projectedBubble.value = null
            sim.joyX = 0f; sim.joyY = 0f
            _sprinting.value = false; sim.sprinting = false
        }
        _hudState.value = sim.buildHudState(hintOverride)
    }

    fun jumpWalk() {
        sim.jumpWalk()
    }

    fun setJoystick(jx: Float, jy: Float) {
        sim.joyX = jx; sim.joyY = jy
    }

    fun toggleSprint() {
        val next = !_sprinting.value
        _sprinting.value = next
        sim.sprinting = next
    }

    fun setSpeed(mult: Int) {
        _speedMultiplier.value = mult
    }

    fun toggleSoundMute() {
        val next = !_soundMuted.value
        _soundMuted.value = next
        sound.muted = next
    }

    fun togglePhotoMode() {
        _photoMode.value = !_photoMode.value
    }

    fun setTax(rate: Int) {
        sim.setTaxRate(rate)
        _hudState.value = sim.buildHudState(hintOverride)
    }

    fun setSpend(rate: Int) {
        sim.setSpendRate(rate)
        _hudState.value = sim.buildHudState(hintOverride)
    }

    fun triggerIncident(kind: String) {
        synchronized(sim.lock) {
            val inc = sim.events.triggerIncident(kind)
            if (inc == null) {
                flashHint("Necesitás más calles o resolver emergencias activas primero.")
            } else {
                _hudState.value = sim.buildHudState(hintOverride)
            }
        }
    }

    fun focusIncident(id: Int) {
        synchronized(sim.lock) {
            val inc = sim.events.incidents.firstOrNull { it.id == id } ?: return
            if (sim.walkMode) {
                sim.toggleWalkMode()
                _walkMode.value = false
            }
            sim.camX = inc.cx
            sim.camZ = inc.cz
            sim.camDist = min(sim.camDist, 64f)
        }
    }

    fun focusNearestIncident() {
        synchronized(sim.lock) {
            val first = sim.events.incidents.firstOrNull() ?: return
            focusIncident(first.id)
        }
    }

    fun toggleIncAuto() {
        synchronized(sim.lock) {
            sim.events.incAuto = !sim.events.incAuto
            _hudState.value = sim.buildHudState(hintOverride)
        }
    }

    fun toggleWeatherRain() {
        synchronized(sim.lock) {
            sim.wx.toggleRain(sim)
            _hudState.value = sim.buildHudState(hintOverride)
        }
    }

    fun toggleWeatherAuto() {
        synchronized(sim.lock) {
            sim.wx.toggleAuto()
            _hudState.value = sim.buildHudState(hintOverride)
        }
    }

    fun addDebugCash() {
        sim.addDebugCash()
        sound.playExpand()
        _hudState.value = sim.buildHudState(hintOverride)
    }

    fun zoomCamera(factor: Float) {
        synchronized(sim.lock) {
            if (sim.walkMode) {
                sim.walkCamDist = (sim.walkCamDist * factor).coerceIn(3f, 18f)
            } else {
                sim.camDist = (sim.camDist * factor).coerceIn(18f, 340f)
            }
        }
    }

    fun resetCamera() {
        synchronized(sim.lock) {
            sim.camX = 0f; sim.camZ = 0f; sim.camDist = 92f; sim.camYaw = 0.7f; sim.camPitch = 0.85f
        }
    }

    fun jumpCameraTo(gx: Int, gy: Int) {
        synchronized(sim.lock) {
            if (sim.walkMode) {
                sim.toggleWalkMode()
                _walkMode.value = false
            }
            sim.camX = sim.gx2x(gx)
            sim.camZ = sim.gx2x(gy)
        }
    }

    fun expandTerritory() {
        if (sim.expandTerritory()) {
            sound.playExpand()
            _hudState.value = sim.buildHudState(hintOverride)
            saveCityNow()
        } else {
            val next = _hudState.value.nextExpandCost
            if (next != null) {
                sound.playError()
                flashHint("Necesitás $next monedas para ampliar el territorio.")
            }
        }
    }

    fun renameCity(newName: String) {
        val clean = newName.trim().take(40).ifBlank { "Mi Aldea" }
        synchronized(sim.lock) {
            sim.cityName = clean
        }
        _hudState.value = sim.buildHudState(hintOverride)
        saveCityNow()
    }

    fun resetCity() {
        sim.initStarterVillage()
        _walkMode.value = false
        _inspectedTile.value = null
        _projectedBubble.value = null
        _projectedMarkers.value = emptyList()
        _hudState.value = sim.buildHudState(null)
        showBanner("Aldea reiniciada")
        saveCityNow()
    }

    fun saveCityNow(forceCreateCheck: Boolean = false) {
        if (!initialHydrated && !forceCreateCheck) return
        val snapshot = sim.exportSnapshot()
        viewModelScope.launch {
            _syncMessage.value = "Guardando..."
            val create = !hasCloudDocument
            val result = repository.saveCity(currentUserId, snapshot, isInitialCreate = create)
            result.fold(
                onSuccess = {
                    hasCloudDocument = true
                    _syncMessage.value = "Guardado en la nube"
                },
                onFailure = {
                    _syncMessage.value = "Error al sincronizar"
                }
            )
        }
    }

    fun signOut(credentialManager: CredentialManager, onSignedOut: () -> Unit) {
        viewModelScope.launch {
            withContext(NonCancellable) {
                try {
                    val snap = sim.exportSnapshot()
                    if (initialHydrated) {
                        repository.saveCity(currentUserId, snap, isInitialCreate = !hasCloudDocument)
                    }
                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                } catch (e: Exception) {
                    Log.w(TAG, "Cleanup warning on sign out: ${e.message}")
                } finally {
                    Firebase.auth.signOut()
                    onSignedOut()
                }
            }
        }
    }

    private fun flashHint(msg: String) {
        hintOverride = msg
        _hudState.value = sim.buildHudState(msg)
        hintJob?.cancel()
        hintJob = viewModelScope.launch {
            delay(3200L)
            hintOverride = null
            _hudState.value = sim.buildHudState(null)
        }
    }

    private fun showBanner(msg: String) {
        _bannerText.value = msg
        bannerJob?.cancel()
        bannerJob = viewModelScope.launch {
            delay(3800L)
            _bannerText.value = null
        }
    }

    override fun onCleared() {
        super.onCleared()
        sound.release()
    }

    private companion object {
        const val TAG = "CityVM"
    }
}
