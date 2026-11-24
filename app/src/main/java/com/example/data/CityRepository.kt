package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class CityRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Debes iniciar sesión con Google antes de acceder a la nube.")
    }

    fun observeCity(userId: String, cityId: String = DEFAULT_CITY_ID): Flow<CitySaveDoc?> {
        return db.collection("users")
            .document(userId)
            .collection("cities")
            .document(cityId)
            .snapshots()
            .map { snapshot ->
                if (!snapshot.exists()) {
                    null
                } else {
                    snapshot.toObject(
                        CitySaveDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
            }
    }

    suspend fun getCityOnce(userId: String, cityId: String = DEFAULT_CITY_ID): Result<CitySaveDoc?> {
        val path = "users/$userId/cities/$cityId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "La sesión cambió; lectura cancelada." }
            val snap = db.collection("users")
                .document(userId)
                .collection("cities")
                .document(cityId)
                .get()
                .await()
            val doc = if (snap.exists()) {
                snap.toObject(CitySaveDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } else null
            Result.success(doc)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun saveCity(
        userId: String,
        snapshot: CitySnapshot,
        isInitialCreate: Boolean,
        cityId: String = DEFAULT_CITY_ID
    ): Result<Unit> {
        val path = "users/$userId/cities/$cityId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "La sesión cambió; guardado cancelado." }
            val safeName = snapshot.cityName.trim().take(40).ifBlank { "Mi Aldea" }
            val safeCoins = snapshot.coins.coerceIn(0.0, 1_000_000_000.0)
            val safeCiv = snapshot.civ.coerceIn(0.0, 1_000_000_000.0)
            val safeTime = snapshot.gameTime.coerceIn(0.0, 10_000_000_000.0)
            val safeEra = snapshot.era.coerceIn(0, 4).toLong()
            val safeExp = snapshot.exp.coerceIn(0, 6).toLong()
            val safeTax = snapshot.tax.coerceIn(0, 25).toLong()
            val safeSpend = snapshot.spend.coerceIn(50, 150).toLong()
            val safeBless = snapshot.bless.coerceIn(0, 10000).toLong()
            val safeLogs = snapshot.logEntries.take(6).map { it.take(160) }
            val safeCells = snapshot.cells.take(4900).map { it.toFirestoreMap() }

            val docRef = db.collection("users")
                .document(userId)
                .collection("cities")
                .document(cityId)

            if (isInitialCreate) {
                val payload = mapOf(
                    "userId" to uid,
                    "cityName" to safeName,
                    "coins" to safeCoins,
                    "civ" to safeCiv,
                    "gameTime" to safeTime,
                    "era" to safeEra,
                    "exp" to safeExp,
                    "tax" to safeTax,
                    "spend" to safeSpend,
                    "bless" to safeBless,
                    "logEntries" to safeLogs,
                    "cells" to safeCells,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                val payload = mapOf(
                    "cityName" to safeName,
                    "coins" to safeCoins,
                    "civ" to safeCiv,
                    "gameTime" to safeTime,
                    "era" to safeEra,
                    "exp" to safeExp,
                    "tax" to safeTax,
                    "spend" to safeSpend,
                    "bless" to safeBless,
                    "logEntries" to safeLogs,
                    "cells" to safeCells,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (isInitialCreate) OperationType.CREATE else OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    companion object {
        const val DEFAULT_CITY_ID = "main_city"
    }
}
