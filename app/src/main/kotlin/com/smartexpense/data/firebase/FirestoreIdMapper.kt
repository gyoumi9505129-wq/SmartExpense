package com.smartexpense.data.firebase

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore 문서 ID(String) ↔ 기존 Room Long ID 호환 매핑.
 * UI/ViewModel이 Long을 쓰는 동안 실시간 스트림에서 양방향 캐시를 유지합니다.
 */
@Singleton
class FirestoreIdMapper @Inject constructor() {
    private val memberDocToLong = ConcurrentHashMap<String, Long>()
    private val memberLongToDoc = ConcurrentHashMap<Long, String>()
    private val txDocToLong = ConcurrentHashMap<String, Long>()
    private val txLongToDoc = ConcurrentHashMap<Long, String>()

    private val duesDocToLong = ConcurrentHashMap<String, Long>()
    private val duesLongToDoc = ConcurrentHashMap<Long, String>()
    private val duesDetailDocToLong = ConcurrentHashMap<String, Long>()
    private val duesDetailLongToDoc = ConcurrentHashMap<Long, String>()

    fun memberLongId(docId: String): Long = remember(docId, memberDocToLong, memberLongToDoc)

    fun transactionLongId(docId: String): Long = remember(docId, txDocToLong, txLongToDoc)

    fun duesLongId(docId: String): Long = remember(docId, duesDocToLong, duesLongToDoc)

    fun duesDetailLongId(docId: String): Long = remember(docId, duesDetailDocToLong, duesDetailLongToDoc)

    fun memberDocId(longId: Long): String? = memberLongToDoc[longId]

    fun transactionDocId(longId: Long): String? = txLongToDoc[longId]

    fun duesDocId(longId: Long): String? = duesLongToDoc[longId]

    fun duesDetailDocId(longId: Long): String? = duesDetailLongToDoc[longId]

    fun rememberMember(docId: String, longId: Long) {
        memberDocToLong[docId] = longId
        memberLongToDoc[longId] = docId
    }

    fun rememberTransaction(docId: String, longId: Long) {
        txDocToLong[docId] = longId
        txLongToDoc[longId] = docId
    }

    fun rememberDues(docId: String, longId: Long) {
        duesDocToLong[docId] = longId
        duesLongToDoc[longId] = docId
    }

    fun rememberDuesDetail(docId: String, longId: Long) {
        duesDetailDocToLong[docId] = longId
        duesDetailLongToDoc[longId] = docId
    }

    private fun remember(
        docId: String,
        docToLong: ConcurrentHashMap<String, Long>,
        longToDoc: ConcurrentHashMap<Long, String>
    ): Long {
        docToLong[docId]?.let { return it }
        var candidate = docId.hashCode().toLong().and(0x7fffffffffffffffL)
        if (candidate == 0L) candidate = 1L
        while (longToDoc.putIfAbsent(candidate, docId) != null && longToDoc[candidate] != docId) {
            candidate = (candidate + 1L).and(0x7fffffffffffffffL).coerceAtLeast(1L)
        }
        docToLong[docId] = candidate
        return candidate
    }
}
