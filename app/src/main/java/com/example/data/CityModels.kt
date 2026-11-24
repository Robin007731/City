package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@Keep
@IgnoreExtraProperties
data class CitySaveDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val cityName: String = "Mi Aldea",
    val coins: Double = 500.0,
    val civ: Double = 10.0,
    val gameTime: Double = 0.0,
    val era: Long = 0L,
    val exp: Long = 0L,
    val tax: Long = 10L,
    val spend: Long = 100L,
    val bless: Long = 0L,
    val logEntries: List<String> = emptyList(),
    val cells: List<Map<String, Any>> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class SavedCell(
    val x: Int,
    val y: Int,
    val t: String,
    val k: String = "",
    val s: Double = 0.5,
    val p: Double = 1.0,
    val dur: Double = 16.0,
    val auto: Boolean = false,
    val nm: String = "",
    val br: Boolean = false,
    val rx: String = "",
    val st: Boolean = false,
    val big: Int = 0,
    val ox: Int = 0,
    val oy: Int = 0,
    val ax: Int = 0,
    val ay: Int = 0
) {
    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "x" to x.toLong(),
        "y" to y.toLong(),
        "t" to t,
        "k" to k,
        "s" to s,
        "p" to p,
        "dur" to dur,
        "auto" to auto,
        "nm" to nm,
        "br" to br,
        "rx" to rx,
        "st" to st,
        "big" to big.toLong(),
        "ox" to ox.toLong(),
        "oy" to oy.toLong(),
        "ax" to ax.toLong(),
        "ay" to ay.toLong()
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): SavedCell? {
            val x = (map["x"] as? Number)?.toInt() ?: return null
            val y = (map["y"] as? Number)?.toInt() ?: return null
            val t = map["t"] as? String ?: return null
            if (x !in 0..69 || y !in 0..69) return null
            return SavedCell(
                x = x,
                y = y,
                t = t,
                k = map["k"] as? String ?: "",
                s = (map["s"] as? Number)?.toDouble() ?: 0.5,
                p = (map["p"] as? Number)?.toDouble() ?: 1.0,
                dur = (map["dur"] as? Number)?.toDouble() ?: 16.0,
                auto = map["auto"] as? Boolean ?: false,
                nm = map["nm"] as? String ?: "",
                br = map["br"] as? Boolean ?: false,
                rx = map["rx"] as? String ?: "",
                st = map["st"] as? Boolean ?: false,
                big = (map["big"] as? Number)?.toInt() ?: 0,
                ox = (map["ox"] as? Number)?.toInt() ?: 0,
                oy = (map["oy"] as? Number)?.toInt() ?: 0,
                ax = (map["ax"] as? Number)?.toInt() ?: 0,
                ay = (map["ay"] as? Number)?.toInt() ?: 0
            )
        }
    }
}

data class CitySnapshot(
    val cityName: String,
    val coins: Double,
    val civ: Double,
    val gameTime: Double,
    val era: Int,
    val exp: Int,
    val tax: Int,
    val spend: Int,
    val bless: Int,
    val logEntries: List<String>,
    val cells: List<SavedCell>
)
