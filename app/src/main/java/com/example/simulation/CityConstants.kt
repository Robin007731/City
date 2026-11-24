package com.example.simulation

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.EraAldea
import com.example.ui.theme.EraCiudad
import com.example.ui.theme.EraFuturista
import com.example.ui.theme.EraPueblo
import com.example.ui.theme.EraVilla
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin

const val GRID_SIZE = 70
const val TILE_SIZE = 8f
const val EVO = 0.15
const val BRIDGE_COST = 14
const val YARD_DAYS = 12f

const val LAKE_X = -335f
const val LAKE_Z = -345f
const val LAKE_R = 88f

fun riverX(z: Float): Float = -52f + 14f * sin(z / 55f) + 6f * sin(z / 23f + 1f)

fun nearWater(x: Float, z: Float, pad: Float = 0f): Boolean {
    return hypot(x - LAKE_X, z - LAKE_Z) < LAKE_R + pad || abs(x - riverX(z)) < 14f + pad
}

data class EraDef(
    val index: Int,
    val name: String,
    val atCiv: Int,
    val accent: Color,
    val skyHex: Int,
    val groundHex: Int,
    val accentHex: Int
)

val ERAS = listOf(
    EraDef(0, "Aldea", 0, EraAldea, 0xFFBFE3F5.toInt(), 0xFF86B05F.toInt(), 0xFF8FB04A.toInt()),
    EraDef(1, "Pueblo", 80, EraPueblo, 0xFFCFE2E6.toInt(), 0xFF7FAE60.toInt(), 0xFFD98A3D.toInt()),
    EraDef(2, "Villa", 300, EraVilla, 0xFFD9D6CC.toInt(), 0xFF98A77D.toInt(), 0xFFC4604F.toInt()),
    EraDef(3, "Ciudad", 800, EraCiudad, 0xFFA9C6E0.toInt(), 0xFF8B9392.toInt(), 0xFF5B9BD5.toInt()),
    EraDef(4, "Ciudad futurista", 1800, EraFuturista, 0xFF2A1250.toInt(), 0xFF0D1422.toInt(), 0xFF22E3F2.toInt())
)

enum class BuildTool(
    val key: String,
    val label: String,
    val cost: Int,
    val minEra: Int,
    val isBuildingSite: Boolean,
    val requiresRoad: Boolean
) {
    ZEUS("zeus", "Zeus", 0, 0, false, false),
    ROAD("road", "Calle", 2, 0, false, false),
    HOUSE("house", "Casa", 10, 0, false, false),
    ERASE("erase", "Demoler", 0, 0, false, false),
    CAMERA("cam", "Cámara", 0, 0, false, false),
    AVENIDA("avenida", "Avenida", 6, 1, false, false),
    AUTOPISTA("autopista", "Autopista", 16, 3, false, false),
    TREN("tren", "Vía", 8, 2, false, false),
    ESTACION("estacion", "Estación", 90, 2, false, false),
    TERMINAL("terminal", "Bus", 60, 0, true, true),
    EMPRESA("empresa", "Empresa", 80, 0, true, true),
    HOSPITAL("hospital", "Hospital", 150, 1, true, true),
    COMISARIA("comisaria", "Policía", 120, 1, true, true),
    BOMBEROS("bomberos", "Bomberos", 120, 1, true, true),
    AYUNTAMIENTO("ayuntamiento", "Ayunt.", 300, 1, true, true),
    IGLESIA("iglesia", "Iglesia", 100, 0, true, true),
    ESTADIO("estadio", "Estadio", 400, 2, true, true),
    BIBLIOTECA("biblioteca", "Biblio.", 90, 1, true, true),
    COMERCIO("comercio", "Comercio", 40, 0, true, true),
    HUERTO("huerto", "Huerto", 20, 0, true, true),
    TORRE("torre", "Torre", 120, 0, true, true),
    GIMNASIO("gimnasio", "Gimnasio", 70, 1, true, true),
    ESCUELA("escuela", "Escuela", 100, 1, true, true);

    companion object {
        fun fromKey(k: String): BuildTool? = entries.firstOrNull { it.key == k }
    }
}

val BIG_TOOLS = setOf("estadio", "hospital", "ayuntamiento", "escuela", "empresa")

val UPKEEP_COSTS = mapOf(
    "hospital" to 0.6,
    "comisaria" to 0.5,
    "bomberos" to 0.5,
    "escuela" to 0.3,
    "biblioteca" to 0.2,
    "iglesia" to 0.1,
    "estadio" to 0.8,
    "ayuntamiento" to 0.5,
    "huerto" to 0.05
)

val EXPAND_COSTS = listOf(80, 200, 450, 900, 1800, 3200)

val DIRS = arrayOf(
    intArrayOf(1, 0),
    intArrayOf(-1, 0),
    intArrayOf(0, 1),
    intArrayOf(0, -1)
)

val SKIN_COLORS = intArrayOf(
    0xFFF2CFAE.toInt(),
    0xFFE0AD82.toInt(),
    0xFFC68A5E.toInt(),
    0xFF9A6A45.toInt(),
    0xFF6E4A30.toInt()
)

val HAIR_COLORS = intArrayOf(
    0xFF2A1C14.toInt(),
    0xFF4A3020.toInt(),
    0xFF7A5230.toInt(),
    0xFFC9A45A.toInt(),
    0xFFA8A8A0.toInt(),
    0xFF8A3A22.toInt()
)

val MALE_NAMES = listOf(
    "Tomás", "Mateo", "Bruno", "Iván", "Joaquín", "Lucas",
    "Nicolás", "Martín", "Diego", "Santiago", "Emilio", "Facundo"
)

val FEMALE_NAMES = listOf(
    "Lucía", "Marta", "Sofía", "Camila", "Valentina", "Elena",
    "Paula", "Julieta", "Rosa", "Inés", "Abril", "Florencia"
)

val ERA_DIALOGUES = listOf(
    listOf(
        "¡Buen día, viajero! Hoy toca arar.",
        "Dicen que pronto va a haber un mercado.",
        "Cuidado con las gallinas, que andan sueltas.",
        "Si tenés sed, el pozo queda cerca."
    ),
    listOf(
        "El mercado se llena cada día más.",
        "¿Viste las casas nuevas? Crecemos rápido.",
        "Vengo de la herrería, qué calor hace.",
        "Si buscás trabajo, hay obras por todos lados."
    ),
    listOf(
        "Esta villa ya tiene hasta campanario.",
        "Mi abuelo nunca imaginó tanta gente.",
        "Hoy hay feria en la plaza.",
        "Cada semana llegan más familias."
    ),
    listOf(
        "Llego tarde a la oficina.",
        "Qué lindo quedó el parque.",
        "Hace unos años esto era una aldea, ¿te imaginás?",
        "Hay mucha gente en el centro hoy."
    ),
    listOf(
        "Las torres brillan más cada noche.",
        "Mi abuela contaba que acá había chozas.",
        "Bienvenido a la ciudad del futuro.",
        "Los tomates del domo son increíbles."
    )
)

val WORK_DIALOGUES = listOf(
    "Aguantá que termino esta obra.",
    "Un poco más y queda lista.",
    "¡Con cuidado, que algo nuevo se levanta!"
)

val BLESS_DIALOGUES = listOf(
    "¡Qué energía! Me siento imparable.",
    "¡Siento que el cielo me sonríe hoy!",
    "¡Me tocó un rayo de buena suerte!",
    "Hoy nada me para, ¡a trabajar con todo!"
)

val LOOK_DIALOGUES = listOf(
    "¡Uy, mirá lo que pasó ahí!",
    "Ojalá lleguen rápido los servicios.",
    "Por suerte ya están actuando.",
    "¡Qué susto! Menos mal que me quedé en la vereda."
)

val EMERGENCY_SAY = mapOf(
    "police" to listOf(
        "Circulen, zona asegurada.",
        "Estamos tomando los datos.",
        "Mantenemos el tránsito ordenado."
    ),
    "fire" to listOf(
        "¡Agua va! Alejense del humo.",
        "Controlando el foco, ¡atrás!",
        "Enfriando la estructura."
    ),
    "medic" to listOf(
        "Signos estables, ya lo atendemos.",
        "¡Paso a la camilla!",
        "Todo va a estar bien, respirá tranquilo."
    ),
    "civil" to listOf(
        "¡Qué golpe! Me tiemblan las piernas.",
        "Venía distraído, perdón...",
        "Gracias por venir tan rápido."
    )
)

val EMERGENCY_TITLES = mapOf(
    "policia" to "Agente",
    "bombero" to "Bombero",
    "medic" to "Paramédico",
    "civil" to "Afectado"
)

val ACTIVITY_DIALOGUES = mapOf(
    "work" to listOf("Hoy hay mucho laburo.", "Ya casi es hora del almuerzo.", "Atiendo a todo el que pase."),
    "gym" to listOf("¡Una serie más y listo!", "Hay que cuidarse el cuerpo.", "Hoy toca piernas."),
    "study" to listOf("Mañana tengo examen.", "Estoy aprendiendo a leer mapas.", "Shh, estoy concentrado."),
    "park" to listOf("¿Un matecito? Está recién cebado.", "Qué lindo día para estar al aire libre.", "El mate es mejor compartido."),
    "home" to listOf("Estoy descansando un rato.", "Ya vuelvo, me preparo algo."),
    "show" to listOf("¡Qué partidazo!", "Vamos, que ganamos hoy.", "Traje el termo para el mate."),
    "look" to LOOK_DIALOGUES
)

val ACTIVITY_LABELS = mapOf(
    "build" to "construyendo",
    "work" to "trabajando",
    "gym" to "haciendo ejercicio",
    "study" to "estudiando",
    "park" to "tomando mates",
    "home" to "en casa",
    "sleep" to "durmiendo",
    "wander" to "paseando",
    "show" to "mirando el partido",
    "look" to "mirando la emergencia",
    "veh" to "vehículos"
)

val ACTIVITY_EMOJIS = mapOf(
    "build" to "🔨",
    "work" to "💼",
    "gym" to "🏋️",
    "study" to "📚",
    "park" to "🧉",
    "home" to "🏠",
    "sleep" to "😴",
    "wander" to "🚶",
    "show" to "⚽",
    "look" to "📱",
    "veh" to "🚗"
)

val SEASON_NAMES = listOf("Primavera", "Verano", "Otoño", "Invierno")

data class WeatherRegime(
    val key: String,
    val label: String,
    val cloud: Float,
    val rain: Float,
    val fog: Float,
    val wind: Float,
    val storm: Boolean = false
)

val WEATHER_REGIMES = mapOf(
    "clr" to WeatherRegime("clr", "Despejado", 0.05f, 0f, 0f, 0.25f),
    "pc" to WeatherRegime("pc", "Parcialmente nublado", 0.35f, 0f, 0f, 0.35f),
    "ov" to WeatherRegime("ov", "Nublado", 0.72f, 0f, 0.08f, 0.45f),
    "drz" to WeatherRegime("drz", "Llovizna", 0.80f, 0.25f, 0.22f, 0.50f),
    "rn" to WeatherRegime("rn", "Lluvia", 0.88f, 0.68f, 0.30f, 0.72f),
    "stm" to WeatherRegime("stm", "Tormenta", 0.98f, 1.0f, 0.42f, 1.0f, storm = true),
    "fg" to WeatherRegime("fg", "Niebla", 0.50f, 0f, 0.90f, 0.15f),
    "wd" to WeatherRegime("wd", "Ventoso", 0.38f, 0f, 0f, 0.95f)
)

val SEASON_PROBS: Array<List<Pair<String, Double>>> = arrayOf(
    // Primavera
    listOf("clr" to 0.34, "pc" to 0.24, "ov" to 0.11, "drz" to 0.10, "rn" to 0.09, "stm" to 0.05, "fg" to 0.03, "wd" to 0.04),
    // Verano
    listOf("clr" to 0.44, "pc" to 0.22, "ov" to 0.07, "drz" to 0.04, "rn" to 0.08, "stm" to 0.10, "fg" to 0.02, "wd" to 0.03),
    // Otoño
    listOf("clr" to 0.24, "pc" to 0.22, "ov" to 0.17, "drz" to 0.12, "rn" to 0.09, "stm" to 0.03, "fg" to 0.07, "wd" to 0.06),
    // Invierno
    listOf("clr" to 0.22, "pc" to 0.20, "ov" to 0.22, "drz" to 0.12, "rn" to 0.09, "stm" to 0.02, "fg" to 0.09, "wd" to 0.04)
)

val COMPANY_NAMES = listOf(
    "Logística Rioplatense", "Mate Tech", "Textiles del Plata", "Constructora Aldea",
    "Alimentos Don Tomás", "Ferretería Central", "Transportes del Sur",
    "Molino Dorado", "Energía Viva", "Café Candil"
)

val VEHICLE_COLORS = intArrayOf(
    0xFFD9483A.toInt(),
    0xFF2A6FD6.toInt(),
    0xFFE8C26A.toInt(),
    0xFF3A8A40.toInt(),
    0xFFF0F0EA.toInt(),
    0xFF2B2F36.toInt(),
    0xFF8A3AD6.toInt(),
    0xFFE07A2A.toInt()
)

data class ServiceRequirement(
    val key: String,
    val label: String,
    val icon: String,
    val perPop: Int,
    val weight: Int,
    val minEra: Int
)

val SERVICE_REQUIREMENTS = listOf(
    ServiceRequirement("hospital", "Hospital", "🏥", 40, 18, 1),
    ServiceRequirement("comisaria", "Policía", "🚓", 45, 16, 1),
    ServiceRequirement("bomberos", "Bomberos", "🚒", 60, 12, 1),
    ServiceRequirement("escuela", "Escuelas", "🏫", 50, 14, 1),
    ServiceRequirement("huerto", "Parques", "🌳", 30, 12, 0),
    ServiceRequirement("biblioteca", "Biblioteca", "📚", 80, 6, 1),
    ServiceRequirement("iglesia", "Iglesia", "⛪", 70, 6, 0),
    ServiceRequirement("estadio", "Estadio", "🏟️", 120, 8, 2),
    ServiceRequirement("ayuntamiento", "Ayuntamiento", "🏛️", 0, 8, 1)
)

fun buildingDisplayName(kind: String, era: Int): String = when (kind) {
    "comercio" -> listOf("Puesto de mercado", "Tienda local", "Mercado cubierto", "Centro comercial", "Centro holográfico")[era]
    "huerto" -> listOf("Huerto", "Huerto frutal", "Plaza con fuente", "Parque urbano", "Domo hidropónico")[era]
    "gimnasio" -> listOf("Corral de entreno", "Salón de lucha", "Gimnasio", "Gimnasio", "Gimnasio gravedad cero")[era]
    "escuela" -> listOf("Escuelita", "Escuelita", "Escuela", "Escuela", "Academia holográfica")[era]
    "terminal" -> listOf("Parada de carretas", "Terminal de carretas", "Terminal de ómnibus", "Terminal de autobuses", "Estación magnética")[era]
    "empresa" -> listOf("Taller", "Casa de comercio", "Empresa", "Empresa", "Corporación")[era]
    "torre" -> listOf("Atalaya", "Torre de piedra", "Campanario", "Torre de oficinas", "Aguja de energía")[era]
    "hospital" -> "Hospital"
    "comisaria" -> "Comisaría"
    "bomberos" -> "Cuartel de bomberos"
    "ayuntamiento" -> "Ayuntamiento"
    "iglesia" -> "Iglesia"
    "estadio" -> "Estadio"
    "biblioteca" -> "Biblioteca"
    else -> "Edificio"
}

fun eraOfCiv(civ: Double): Int {
    var e = 0
    for (i in ERAS.indices) {
        if (civ >= ERAS[i].atCiv) e = i
    }
    return e
}
