package com.example.ui

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.UiState
import com.example.gl.CityGLSurfaceView
import com.example.simulation.BuildTool
import com.example.simulation.ERAS
import com.example.ui.theme.AldeaMetropolisTheme
import com.example.ui.theme.DangerRed
import com.example.ui.theme.GoldCoin
import com.example.ui.theme.SuccessGreen
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CityScreen(
    viewModel: CityViewModel,
    onSignedOut: () -> Unit
) {
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val hud by viewModel.hudState.collectAsStateWithLifecycle()
    val activeTool by viewModel.activeTool.collectAsStateWithLifecycle()
    val walkMode by viewModel.walkMode.collectAsStateWithLifecycle()
    val speedMultiplier by viewModel.speedMultiplier.collectAsStateWithLifecycle()
    val soundMuted by viewModel.soundMuted.collectAsStateWithLifecycle()
    val photoMode by viewModel.photoMode.collectAsStateWithLifecycle()
    val sprinting by viewModel.sprinting.collectAsStateWithLifecycle()
    val bannerText by viewModel.bannerText.collectAsStateWithLifecycle()
    val inspectedTile by viewModel.inspectedTile.collectAsStateWithLifecycle()
    val projectedBubble by viewModel.projectedBubble.collectAsStateWithLifecycle()
    val projectedMarkers by viewModel.projectedMarkers.collectAsStateWithLifecycle()
    val syncMessage by viewModel.syncMessage.collectAsStateWithLifecycle()

    val eraDef = ERAS[hud.eraIndex.coerceIn(0, ERAS.lastIndex)]
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var showDashboardSheet by remember { mutableStateOf(false) }
    var showMinimap by remember { mutableStateOf(false) }
    var glViewRef by remember { mutableStateOf<CityGLSurfaceView?>(null) }

    BackHandler(enabled = showDashboardSheet || showMinimap || photoMode || walkMode || inspectedTile != null) {
        when {
            showMinimap -> showMinimap = false
            showDashboardSheet -> showDashboardSheet = false
            photoMode -> viewModel.togglePhotoMode()
            inspectedTile != null -> viewModel.dismissInspector()
            walkMode -> viewModel.toggleWalkMode()
        }
    }

    DisposableEffect(lifecycleOwner, glViewRef) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> glViewRef?.onResume()
                Lifecycle.Event.ON_PAUSE -> {
                    glViewRef?.onPause()
                    viewModel.saveCityNow()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(glViewRef, activeTool) { glViewRef?.activeTool = activeTool }
    LaunchedEffect(glViewRef, speedMultiplier) { glViewRef?.renderer?.speedMultiplier = speedMultiplier }

    AldeaMetropolisTheme(eraAccent = eraDef.accent) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWideScreen = maxWidth >= 840.dp

            Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    // 3D OpenGL Viewport
                    AndroidView(
                        factory = { ctx ->
                            CityGLSurfaceView(
                                context = ctx,
                                sim = viewModel.sim,
                                onFrameTick = { dt ->
                                    val r = glViewRef?.renderer
                                    if (r != null) viewModel.onGlFrameTick(dt, r)
                                },
                                onTileAction = { gx, gy, isTap ->
                                    val changed = viewModel.handleTileAction(gx, gy, isTap)
                                    if (changed) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                },
                                onZeusTap = { wx, wz ->
                                    viewModel.handleZeusTap(wx, wz)
                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                }
                            ).also {
                                it.activeTool = activeTool
                                it.renderer.speedMultiplier = speedMultiplier
                                glViewRef = it
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Floating 3D Emergency Markers
                    if (!photoMode && projectedMarkers.isNotEmpty()) {
                        EmergencyMarkersOverlay(
                            markers = projectedMarkers,
                            onMarkerClick = { viewModel.focusIncident(it) }
                        )
                    }

                    // Floating villager speech bubble in Walk Mode
                    if (walkMode && !photoMode && projectedBubble != null) {
                        SpeechBubbleOverlay(bubble = projectedBubble!!)
                    }

                    // Top HUD bar (hidden in Photo Mode)
                    if (!photoMode) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                ) {
                                    Text(
                                        text = hud.cityName,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 180.dp).padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }

                                Surface(
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
                                    shape = CircleShape,
                                    border = BorderStroke(1.5.dp, eraDef.accent)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(eraDef.accent))
                                        Text(text = hud.eraName, style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }

                            // Scrollable Resource Chips Row
                            val netSign = if (hud.netPerSec >= 0) "+" else ""
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                item { HudChip("🪙 ${hud.coins}") }
                                item {
                                    HudChip(
                                        text = "📊 $netSign${String.format(Locale.US, "%.1f", hud.netPerSec)}/s",
                                        textColor = if (hud.netPerSec >= 0) SuccessGreen else DangerRed
                                    )
                                }
                                item { HudChip("👥 ${hud.population}") }
                                item { HudChip("🕐 ${hud.formattedTime}") }
                                item { HudChip("${hud.wxIcon} ${hud.tempC}°C") }
                                item { HudChip("😊 ${hud.wellbeing}%") }
                                item { HudChip("⚡ ${hud.mana}") }
                            }
                        }

                        // Right Action FAB Column
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .statusBarsPadding()
                                .padding(top = 98.dp, end = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            if (!isWideScreen) {
                                HudFab(
                                    icon = Icons.Rounded.Menu,
                                    contentDescription = "Panel de ciudad",
                                    testTag = "menu_panel_button",
                                    onClick = { showDashboardSheet = true }
                                )
                            }
                            if (hud.incidents.isNotEmpty()) {
                                HudFab(
                                    icon = Icons.Rounded.Warning,
                                    contentDescription = "Ir a emergencia activa",
                                    highlighted = true,
                                    highlightColor = DangerRed,
                                    testTag = "sos_button",
                                    onClick = { viewModel.focusNearestIncident() }
                                )
                            }
                            HudFab(
                                icon = if (walkMode) Icons.Rounded.Construction else Icons.Rounded.DirectionsWalk,
                                contentDescription = if (walkMode) "Volver a construir" else "Recorrer a pie",
                                highlighted = walkMode,
                                testTag = "walk_mode_button",
                                onClick = { viewModel.toggleWalkMode() }
                            )
                            HudFab(
                                icon = Icons.Rounded.Map,
                                contentDescription = "Abrir minimapa",
                                testTag = "minimap_button",
                                onClick = { showMinimap = true }
                            )
                            HudFab(
                                icon = Icons.Rounded.PhotoCamera,
                                contentDescription = "Modo foto",
                                testTag = "photo_mode_button",
                                onClick = { viewModel.togglePhotoMode() }
                            )
                            HudFab(
                                icon = Icons.Rounded.Add,
                                contentDescription = "Acercar cámara",
                                onClick = { viewModel.zoomCamera(0.80f) }
                            )
                            HudFab(
                                icon = Icons.Rounded.Remove,
                                contentDescription = "Alejar cámara",
                                onClick = { viewModel.zoomCamera(1.25f) }
                            )
                        }
                    } else {
                        FilledTonalButton(
                            onClick = { viewModel.togglePhotoMode() },
                            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(14.dp)
                        ) {
                            Icon(Icons.Rounded.Visibility, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Mostrar HUD")
                        }
                    }

                    // Era / Expansion Banner
                    androidx.compose.animation.AnimatedVisibility(
                        visible = bannerText != null && !photoMode,
                        enter = slideInVertically { -it } + fadeIn(),
                        exit = slideOutVertically { -it } + fadeOut(),
                        modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 96.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(2.dp, eraDef.accent),
                            shadowElevation = 8.dp
                        ) {
                            Text(
                                text = bannerText.orEmpty(),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                            )
                        }
                    }

                    // Bottom section: Inspector, Hint, Joystick+Jump or Build Tool Dock
                    if (!photoMode) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val inspected = inspectedTile
                            if (inspected != null && !walkMode) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, eraDef.accent),
                                    shadowElevation = 6.dp,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(inspected.title, style = MaterialTheme.typography.titleSmall)
                                            Text(inspected.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        if (inspected.canDemolish) {
                                            FilledTonalButton(
                                                onClick = { viewModel.demolishInspected() },
                                                colors = ButtonDefaults.filledTonalButtonColors(
                                                    containerColor = DangerRed.copy(alpha = 0.16f),
                                                    contentColor = DangerRed
                                                )
                                            ) { Text("Demoler (+${inspected.refundCoins})") }
                                        }
                                        IconButton(onClick = { viewModel.dismissInspector() }) {
                                            Icon(Icons.Rounded.Close, contentDescription = "Cerrar inspector")
                                        }
                                    }
                                }
                            }

                            val hint = hud.hintText
                            if (hint != null && inspected == null) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(text = hint, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                                }
                            }

                            if (walkMode) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    WalkJoystick(
                                        sprinting = sprinting,
                                        onSprintToggle = { viewModel.toggleSprint() },
                                        onMove = { jx, jy -> viewModel.setJoystick(jx, jy) }
                                    )
                                    Column(
                                        horizontalAlignment = Alignment.End,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        FilledTonalIconButton(
                                            onClick = { viewModel.jumpWalk() },
                                            modifier = Modifier.size(54.dp).testTag("jump_button")
                                        ) {
                                            Icon(Icons.Rounded.ArrowUpward, contentDescription = "Saltar")
                                        }
                                        Surface(
                                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                                            shape = RoundedCornerShape(14.dp),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                        ) {
                                            Text(
                                                text = "Deslizá para mirar\nAcercate a un vecino",
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                            )
                                        }
                                    }
                                }
                            } else {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.93f),
                                    shape = RoundedCornerShape(20.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    shadowElevation = 6.dp,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        items(BuildTool.entries) { tool ->
                                            ToolButton(
                                                tool = tool,
                                                selected = activeTool == tool,
                                                unlocked = hud.eraIndex >= tool.minEra,
                                                affordable = hud.coins >= tool.cost,
                                                onClick = { viewModel.selectTool(tool) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (loadState is UiState.Loading) {
                        Box(
                            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CircularProgressIndicator()
                                    Text("Cargando tu ciudad...", style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                    }
                }

                if (isWideScreen && !photoMode) {
                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        tonalElevation = 2.dp,
                        modifier = Modifier.width(340.dp).fillMaxHeight().statusBarsPadding().navigationBarsPadding()
                    ) {
                        CityDashboardContent(
                            hud = hud,
                            speedMultiplier = speedMultiplier,
                            soundMuted = soundMuted,
                            syncMessage = syncMessage,
                            onRenameCity = viewModel::renameCity,
                            onExpandTerritory = viewModel::expandTerritory,
                            onToggleWalk = viewModel::toggleWalkMode,
                            onSetSpeed = viewModel::setSpeed,
                            onToggleSound = viewModel::toggleSoundMute,
                            onResetCamera = viewModel::resetCamera,
                            onSetTax = viewModel::setTax,
                            onSetSpend = viewModel::setSpend,
                            onTriggerIncident = viewModel::triggerIncident,
                            onFocusIncident = viewModel::focusIncident,
                            onToggleIncAuto = viewModel::toggleIncAuto,
                            onToggleRain = viewModel::toggleWeatherRain,
                            onToggleWeatherAuto = viewModel::toggleWeatherAuto,
                            onAddDebugCash = viewModel::addDebugCash,
                            onSyncNow = { viewModel.saveCityNow() },
                            onResetCity = viewModel::resetCity,
                            onSignOut = { viewModel.signOut(credentialManager, onSignedOut) }
                        )
                    }
                }
            }

            if (showDashboardSheet && !isWideScreen) {
                ModalBottomSheet(
                    onDismissRequest = { showDashboardSheet = false },
                    containerColor = MaterialTheme.colorScheme.background
                ) {
                    CityDashboardContent(
                        hud = hud,
                        speedMultiplier = speedMultiplier,
                        soundMuted = soundMuted,
                        syncMessage = syncMessage,
                        onRenameCity = viewModel::renameCity,
                        onExpandTerritory = viewModel::expandTerritory,
                        onToggleWalk = { showDashboardSheet = false; viewModel.toggleWalkMode() },
                        onSetSpeed = viewModel::setSpeed,
                        onToggleSound = viewModel::toggleSoundMute,
                        onResetCamera = { showDashboardSheet = false; viewModel.resetCamera() },
                        onSetTax = viewModel::setTax,
                        onSetSpend = viewModel::setSpend,
                        onTriggerIncident = { showDashboardSheet = false; viewModel.triggerIncident(it) },
                        onFocusIncident = { showDashboardSheet = false; viewModel.focusIncident(it) },
                        onToggleIncAuto = viewModel::toggleIncAuto,
                        onToggleRain = viewModel::toggleWeatherRain,
                        onToggleWeatherAuto = viewModel::toggleWeatherAuto,
                        onAddDebugCash = viewModel::addDebugCash,
                        onSyncNow = { viewModel.saveCityNow() },
                        onResetCity = viewModel::resetCity,
                        onSignOut = { showDashboardSheet = false; viewModel.signOut(credentialManager, onSignedOut) }
                    )
                }
            }

            if (showMinimap) {
                val camGx = viewModel.sim.x2g(if (walkMode) viewModel.sim.walkX else viewModel.sim.camX)
                val camGy = viewModel.sim.x2g(if (walkMode) viewModel.sim.walkZ else viewModel.sim.camZ)
                MinimapDialog(
                    cells = viewModel.sim.minimapCells(),
                    territorySize = hud.territorySize,
                    camGx = camGx,
                    camGy = camGy,
                    eraIndex = hud.eraIndex,
                    onJumpToTile = { gx, gy -> viewModel.jumpCameraTo(gx, gy); showMinimap = false },
                    onDismiss = { showMinimap = false }
                )
            }
        }
    }
}

@Composable
private fun HudChip(text: String, textColor: Color = Color.Unspecified) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        shape = CircleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Text(
            text = text,
            color = textColor,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun HudFab(
    icon: ImageVector,
    contentDescription: String,
    highlighted: Boolean = false,
    highlightColor: Color = MaterialTheme.colorScheme.primary,
    testTag: String? = null,
    onClick: () -> Unit
) {
    Surface(
        color = if (highlighted) highlightColor else MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        contentColor = if (highlighted) Color.White else MaterialTheme.colorScheme.onSurface,
        shape = CircleShape,
        shadowElevation = 4.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .let { if (testTag != null) it.testTag(testTag) else it }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun ToolButton(
    tool: BuildTool,
    selected: Boolean,
    unlocked: Boolean,
    affordable: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface
    Surface(
        color = bg,
        contentColor = fg,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .width(68.dp)
            .height(64.dp)
            .alpha(if (unlocked) 1f else 0.48f)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("tool_${tool.key}")
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(imageVector = toolIcon(tool), contentDescription = tool.label, modifier = Modifier.size(22.dp))
            Text(text = tool.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = when {
                    !unlocked -> "Era ${tool.minEra + 1}"
                    tool == BuildTool.ZEUS -> "⚡ Poder"
                    tool == BuildTool.ERASE -> "50%"
                    tool == BuildTool.CAMERA -> "Libre"
                    else -> "${tool.cost} 🪙"
                },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = when {
                    !unlocked -> fg.copy(alpha = 0.7f)
                    tool.cost > 0 && !affordable -> DangerRed
                    selected -> GoldCoin
                    else -> fg.copy(alpha = 0.75f)
                }
            )
        }
    }
}

private fun toolIcon(tool: BuildTool): ImageVector = when (tool) {
    BuildTool.ZEUS -> Icons.Rounded.Bolt
    BuildTool.ROAD -> Icons.Rounded.AddRoad
    BuildTool.HOUSE -> Icons.Rounded.Home
    BuildTool.ERASE -> Icons.Rounded.DeleteOutline
    BuildTool.CAMERA -> Icons.Rounded.OpenWith
    BuildTool.AVENIDA -> Icons.Rounded.AltRoute
    BuildTool.AUTOPISTA -> Icons.Rounded.Route
    BuildTool.TREN -> Icons.Rounded.Train
    BuildTool.ESTACION -> Icons.Rounded.DirectionsRailway
    BuildTool.TERMINAL -> Icons.Rounded.DirectionsBus
    BuildTool.EMPRESA -> Icons.Rounded.Business
    BuildTool.HOSPITAL -> Icons.Rounded.LocalHospital
    BuildTool.COMISARIA -> Icons.Rounded.LocalPolice
    BuildTool.BOMBEROS -> Icons.Rounded.LocalFireDepartment
    BuildTool.AYUNTAMIENTO -> Icons.Rounded.AccountBalance
    BuildTool.IGLESIA -> Icons.Rounded.Church
    BuildTool.ESTADIO -> Icons.Rounded.Stadium
    BuildTool.BIBLIOTECA -> Icons.Rounded.LocalLibrary
    BuildTool.COMERCIO -> Icons.Rounded.Storefront
    BuildTool.HUERTO -> Icons.Rounded.Park
    BuildTool.TORRE -> Icons.Rounded.CellTower
    BuildTool.GIMNASIO -> Icons.Rounded.FitnessCenter
    BuildTool.ESCUELA -> Icons.Rounded.School
}
