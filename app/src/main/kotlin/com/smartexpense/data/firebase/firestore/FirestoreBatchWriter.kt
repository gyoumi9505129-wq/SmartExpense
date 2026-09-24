package com.smartexpense.data.firebase.firestore

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.WriteBatch
import com.smartexpense.data.firebase.FirebaseInitializer
import com.smartexpense.data.firebase.auth.FirebaseAuthRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

data class FirestoreBatchSet(
    val collectionPath: String,
    val documentId: String,
    val data: Map<String, Any?>,
    val merge: Boolean = true
)

data class FirestoreBatchDelete(
    val collectionPath: String,
    val documentId: String
)

/**
 * Firestore WriteBatch 커밋 (최대 450 ops / batch).
 */
@Singleton
class FirestoreBatchWriter @Inject constructor(
    private val firebaseInitializer: FirebaseInitializer,
    private val authRepository: FirebaseAuthRepository
) {
    private val db: FirebaseFirestore get() = firebaseInitializer.firestore()

    suspend fun commit(
        sets: List<FirestoreBatchSet> = emptyList(),
        deletes: List<FirestoreBatchDelete> = emptyList()
    ) {
        authRepository.requireUid()
        val ops = buildList {
            deletes.forEach { add(Op.Delete(it)) }
            sets.forEach { add(Op.Set(it)) }
        }
        if (ops.isEmpty()) return
        ops.chunked(MAX_OPS_PER_BATCH).forEach { chunk ->
            val batch: WriteBatch = db.batch()
            chunk.forEach { op ->
                when (op) {
                    is Op.Delete -> {
                        batch.delete(
                            db.collection(op.value.collectionPath).document(op.value.documentId)
                        )
                    }
                    is Op.Set -> {
                        val ref = db.collection(op.value.collectionPath)
                            .document(op.value.documentId)
                        // Firestore Map set은 null 값을 허용하지 않음 → 제외
                        val data = op.value.data.filterValues { it != null }.mapValues { it.value as Any }
                        if (op.value.merge) {
                            batch.set(ref, data, SetOptions.merge())
                        } else {
                            batch.set(ref, data)
                        }
                    }
                }
            }
            batch.commit().await()
        }
    }

    suspend fun deleteAllInCollection(collectionPath: String) {
        authRepository.requireUid()
        val snapshot = db.collection(collectionPath).get().await()
        if (snapshot.isEmpty) return
        commit(
            deletes = snapshot.documents.map { doc ->
                FirestoreBatchDelete(collectionPath = collectionPath, documentId = doc.id)
            }
        )
    }

    private sealed class Op {
        data class Set(val value: FirestoreBatchSet) : Op()
        data class Delete(val value: FirestoreBatchDelete) : Op()
    }

    companion object {
        private const val MAX_OPS_PER_BATCH = 450
    }
}
