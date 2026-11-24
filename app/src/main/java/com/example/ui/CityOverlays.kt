package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.simulation.ERAS
import com.example.simulation.GRID_SIZE
import com.example.simulation.HudState
import com.example.simulation.MinimapCell
import com.example.ui.theme.DangerRed
import com.example.ui.theme.GoldCoin
import com.example.ui.theme.SuccessGreen
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
fun WalkJoystick(
    sprinting: Boolean,
    onSprintToggle: () -> Unit,
    onMove: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var knobX by remember { mutableFloatStateOf(0f) }
    var knobY by remember { mutableFloatStateOf(0f) }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.28f))
                .border(2.dp, Color.White.copy(alpha = 0.55f), CircleShape)
                .pointerInput(Unit) {
                    val maxR = size.width * 0.36f
                    detectDragGestures(
                        onDragEnd = { knobX = 0f; knobY = 0f; onMove(0f, 0f) },
                        onDragCancel = { knobX = 0f; knobY = 0f; onMove(0f, 0f) },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            var nx = knobX + dragAmount.x
                            var ny = knobY + dragAmount.y
                            val dist = hypot(nx, ny)
                            if (dist > maxR) {
                                nx = nx / dist * maxR
                                ny = ny / dist * maxR
                            }
                            knobX = nx; knobY = ny
                            onMove(nx / maxR, ny / maxR)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(knobX.roundToInt(), knobY.roundToInt()) }
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.88f))
            )
        }

        FilledTonalIconToggleButton(
            checked = sprinting,
            onCheckedChange = { onSprintToggle() },
            modifier = Modifier.size(50.dp).testTag("sprint_button")
        ) {
            Icon(imageVector = Icons.Rounded.DirectionsRun, contentDescription = "Correr")
        }
    }
}

@Composable
fun SpeechBubbleOverlay(bubble: ProjectedBubble) {
    Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            shape = RoundedCornerShape(14.dp),
            shadowElevation = 6.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .offset {
                    IntOffset(
                        (bubble.screenX - 240f).roundToInt().coerceAtLeast(16),
                        (bubble.screenY - 140f).roundToInt().coerceAtLeast(120)
                    )
                }
                .widthIn(max = 220.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    text = if (bubble.activityLabel != null) "${bubble.speakerName} · ${bubble.activityLabel}" else bubble.speakerName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(2.dp))
                Text(text = bubble.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
fun EmergencyMarkersOverlay(
    markers: List<ProjectedMarker>,
    onMarkerClick: (Int) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        for (m in markers) {
            Surface(
                color = Color(0xFFD9302A).copy(alpha = 0.92f),
                shape = CircleShape,
                shadowElevation = 6.dp,
                border = BorderStroke(1.5.dp, Color.White),
                modifier = Modifier
                    .offset { IntOffset((m.screenX - 42f).roundToInt(), (m.screenY - 42f).roundToInt()) }
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable { onMarkerClick(m.id) }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(m.emoji, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
fun MinimapDialog(
    cells: List<MinimapCell>,
    territorySize: Int,
    camGx: Int,
    camGy: Int,
    eraIndex: Int,
    onJumpToTile: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val accent = ERAS[eraIndex.coerceIn(0, ERAS.lastIndex)].accent
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Mapa de la ciudad", style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Cerrar mapa")
                    }
                }
                Text(
                    "Tocá cualquier punto para mover la cámara allí.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF1D2E20))
                        .pointerInput(Unit) {
                            detectTapGestures { pos ->
                                val gx = ((pos.x / size.width) * GRID_SIZE).toInt().coerceIn(0, GRID_SIZE - 1)
                                val gy = ((pos.y / size.height) * GRID_SIZE).toInt().coerceIn(0, GRID_SIZE - 1)
                                onJumpToTile(gx, gy)
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cellPx = size.width / GRID_SIZE
                        val lo = (GRID_SIZE - territorySize) / 2
                        drawRect(
                            color = Color(0xFF314D34),
                            topLeft = Offset(lo * cellPx, lo * cellPx),
                            size = Size(territorySize * cellPx, territorySize * cellPx)
                        )
                        for (c in cells) {
                            val col = when (c.kind) {
                                1 -> Color(0xFF9AA3A8) // road
                                2 -> Color(0xFFE8A53A) // house
                                3 -> accent            // building
                                5 -> Color(0xFFD0C4A0) // rail
                                6 -> Color(0xFF3E83B5) // water
                                else -> GoldCoin       // site
                            }
                            drawRect(
                                color = col,
                                topLeft = Offset(c.x * cellPx, c.y * cellPx),
                                size = Size(cellPx.coerceAtLeast(2f), cellPx.coerceAtLeast(2f))
                            )
                        }
                        drawCircle(
                            color = Color.White,
                            radius = (cellPx * 1.8f).coerceAtLeast(5f),
                            center = Offset((camGx + 0.5f) * cellPx, (camGy + 0.5f) * cellPx)
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    LegendDot("Río", Color(0xFF3E83B5))
                    LegendDot("Calle", Color(0xFF9AA3A8))
                    LegendDot("Vía", Color(0xFFD0C4A0))
                    LegendDot("Casa", Color(0xFFE8A53A))
                    LegendDot("Edif.", accent)
                }
            }
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun CityDashboardContent(
    hud: HudState,
    speedMultiplier: Int,
    soundMuted: Boolean,
    syncMessage: String,
    onRenameCity: (String) -> Unit,
    onExpandTerritory: () -> Unit,
    onToggleWalk: () -> Unit,
    onSetSpeed: (Int) -> Unit,
    onToggleSound: () -> Unit,
    onResetCamera: () -> Unit,
    onSetTax: (Int) -> Unit,
    onSetSpend: (Int) -> Unit,
    onTriggerIncident: (String) -> Unit,
    onFocusIncident: (Int) -> Unit,
    onToggleIncAuto: () -> Unit,
    onToggleRain: () -> Unit,
    onToggleWeatherAuto: () -> Unit,
    onAddDebugCash: () -> Unit,
    onSyncNow: () -> Unit,
    onResetCity: () -> Unit,
    onSignOut: () -> Unit
) {
    var editingName by remember(hud.cityName) { mutableStateOf(hud.cityName) }
    var resetArmed by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // City Name & Core Stats
        DashboardCard(title = "Tu ciudad") {
            OutlinedTextField(
                value = editingName,
                onValueChange = { editingName = it.take(40) },
                label = { Text("Nombre de la ciudad") },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { onRenameCity(editingName) }, modifier = Modifier.testTag("save_city_name_button")) {
                        Icon(Icons.Rounded.Check, contentDescription = "Guardar nombre")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatPill("Monedas", "${hud.coins}", Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                StatPill("Habitantes", "${hud.population}", Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatPill("Casas", "${hud.houses}", Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                StatPill("Edificios", "${hud.buildings}", Modifier.weight(1f))
            }
        }

        // Presupuesto (Taxes & Service Spending)
        DashboardCard(title = "Presupuesto y fiscalidad") {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Impuestos", style = MaterialTheme.typography.bodyMedium)
                Text("${hud.tax}%", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = hud.tax.toFloat(),
                onValueChange = { onSetTax(it.roundToInt()) },
                valueRange = 0f..25f,
                modifier = Modifier.testTag("tax_slider")
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Gasto en servicios", style = MaterialTheme.typography.bodyMedium)
                Text("${hud.spend}%", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = hud.spend.toFloat(),
                onValueChange = { onSetSpend(it.roundToInt()) },
                valueRange = 50f..150f,
                modifier = Modifier.testTag("spend_slider")
            )
            val netSign = if (hud.netPerSec >= 0) "+" else ""
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Ingresos: +${String.format(Locale.US, "%.1f", hud.inPerSec)}/s", style = MaterialTheme.typography.bodySmall, color = SuccessGreen)
                Text("Gasto: -${String.format(Locale.US, "%.1f", hud.outPerSec)}/s", style = MaterialTheme.typography.bodySmall, color = DangerRed)
            }
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "Balance: $netSign${String.format(Locale.US, "%.1f", hud.netPerSec)}/s",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (hud.netPerSec >= 0) SuccessGreen else DangerRed
                )
                Text("⚡ Maná Zeus: ${hud.mana} · ✨ ${hud.bless}", style = MaterialTheme.typography.bodySmall)
            }
            if (hud.broke) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "⚠ Déficit de tesorería: los servicios operan al 60%.",
                    style = MaterialTheme.typography.labelSmall,
                    color = DangerRed
                )
            }
        }

        // Emergencies & Incidents
        DashboardCard(title = "Emergencias (${hud.incidents.size})") {
            if (hud.incidents.isEmpty()) {
                Text("Todo tranquilo por ahora.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    hud.incidents.forEach { inc ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${inc.emoji} ${inc.title}", style = MaterialTheme.typography.labelLarge)
                                Text(inc.status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            FilledTonalButton(onClick = { onFocusIncident(inc.id) }) {
                                Text("Ver")
                            }
                        }
                    }
                }
            }
        }

        // Civilization Era Progress
        DashboardCard(title = "Civilización · ${hud.eraName}") {
            LinearProgressIndicator(
                progress = { hud.eraProgress },
                modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape)
            )
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${hud.civPoints} pts", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = if (hud.nextEraName != null) "Siguiente: ${hud.nextEraName} (${hud.nextEraAt})" else "Era máxima",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Territory Expansion
        DashboardCard(title = "Territorio") {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${hud.territorySize} x ${hud.territorySize}", style = MaterialTheme.typography.titleMedium)
                Text("máximo 70 x 70", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onExpandTerritory,
                enabled = hud.nextExpandCost != null,
                modifier = Modifier.fillMaxWidth().testTag("expand_territory_button")
            ) {
                Text(
                    if (hud.nextExpandCost != null) "Ampliar territorio (${hud.nextExpandCost} monedas)"
                    else "Territorio al máximo"
                )
            }
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(onClick = onToggleWalk, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.DirectionsWalk, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Recorrer calles a pie")
            }
        }

        // Services & Wellbeing
        DashboardCard(title = "Servicios y bienestar (${hud.wellbeing}%)") {
            LinearProgressIndicator(
                progress = { (hud.wellbeing / 100f).coerceIn(0f, 1f) },
                color = if (hud.wellbeing >= 70) SuccessGreen else MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape)
            )
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                hud.services.chunked(2).forEach { pair ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { svc ->
                            val ok = svc.have >= svc.need
                            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${svc.icon} ${svc.label} ${svc.have}/${svc.need}${if (!ok) " ⚠" else ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (ok) MaterialTheme.colorScheme.onSurface else DangerRed,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                )
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        // Activities now
        if (hud.activities.isNotEmpty()) {
            DashboardCard(title = "Ahora en la ciudad") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    hud.activities.chunked(2).forEach { pair ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { act ->
                                Text(text = "${act.emoji} ${act.count} ${act.label}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // Event log
        DashboardCard(title = "Crónica de los aldeanos") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                hud.logs.forEachIndexed { idx, entry ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (idx == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                        )
                        Text(
                            text = entry,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (idx == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Weather, Triggers, Speed & Cloud Sync
        DashboardCard(title = "Clima, pruebas y nube · $syncMessage") {
            Text(
                "${hud.wxIcon} ${hud.wxDesc} · ${hud.tempC}°C · ${hud.seasonName} · Viento ${hud.windKmh} km/h",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = hud.wxRaining,
                    onClick = onToggleRain,
                    label = { Text(if (hud.wxRaining) "☀️ Despejar" else "🌧 Lluvia", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = hud.wxAuto,
                    onClick = onToggleWeatherAuto,
                    label = { Text(if (hud.wxAuto) "⛅ Clima auto" else "⛅ Clima fijo", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { onTriggerIncident("crash") }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Text("💥 Choque", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(onClick = { onTriggerIncident("fire") }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Text("🔥 Fuego", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(onClick = { onTriggerIncident("medical") }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Text("🚑 Médica", style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = hud.incAuto,
                    onClick = onToggleIncAuto,
                    label = { Text("🚨 Incidentes auto", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(onClick = onAddDebugCash, modifier = Modifier.weight(1f)) {
                    Text("💰 +500", style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "Pausa", 1 to "Normal x1", 3 to "Rápido x3").forEach { (spd, lbl) ->
                    FilterChip(
                        selected = speedMultiplier == spd,
                        onClick = { onSetSpeed(spd) },
                        label = { Text(lbl, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onToggleSound, modifier = Modifier.weight(1f)) {
                    Icon(if (soundMuted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (soundMuted) "Silencio" else "Sonido")
                }
                OutlinedButton(onClick = onResetCamera, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.CenterFocusStrong, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Centrar")
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onSyncNow, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Guardar")
                }
                OutlinedButton(
                    onClick = {
                        if (!resetArmed) resetArmed = true
                        else { resetArmed = false; onResetCity() }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (resetArmed) DangerRed else MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (resetArmed) "¿Confirmar?" else "Reiniciar")
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = onSignOut,
                colors = ButtonDefaults.textButtonColors(contentColor = DangerRed),
                modifier = Modifier.fillMaxWidth().testTag("sign_out_button")
            ) {
                Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Cerrar sesión")
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DashboardCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun StatPill(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp), modifier = modifier) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
