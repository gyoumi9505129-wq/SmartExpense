package com.smartexpense.data.local.converter

import androidx.room.TypeConverter
import com.smartexpense.data.local.entity.club.ClubTransactionType
import com.smartexpense.data.local.entity.club.DuesPaymentMethod
import com.smartexpense.data.local.entity.club.DuesTerm
import com.smartexpense.data.local.entity.club.MemberRole
import com.smartexpense.data.local.entity.club.MemberStatus

class ClubConverters {
    @TypeConverter
    fun fromMemberStatus(value: MemberStatus): String = value.name

    @TypeConverter
    fun toMemberStatus(value: String): MemberStatus = when (value.trim()) {
        MemberStatus.ACTIVE.name, "활동중", "활동" -> MemberStatus.ACTIVE
        MemberStatus.DORMANT.name, "휴면" -> MemberStatus.DORMANT
        MemberStatus.WITHDRAWN.name, "탈퇴" -> MemberStatus.WITHDRAWN
        else -> runCatching { MemberStatus.valueOf(value) }.getOrDefault(MemberStatus.ACTIVE)
    }

    @TypeConverter
    fun fromMemberRole(value: MemberRole): String = value.name

    @TypeConverter
    fun toMemberRole(value: String): MemberRole = MemberRole.valueOf(value)

    @TypeConverter
    fun fromDuesTerm(value: DuesTerm): String = value.name

    @TypeConverter
    fun toDuesTerm(value: String): DuesTerm = DuesTerm.valueOf(value)

    @TypeConverter
    fun fromDuesPaymentMethod(value: DuesPaymentMethod): String = value.name

    @TypeConverter
    fun toDuesPaymentMethod(value: String): DuesPaymentMethod = DuesPaymentMethod.valueOf(value)

    @TypeConverter
    fun fromClubTransactionType(value: ClubTransactionType): String = value.name

    @TypeConverter
    fun toClubTransactionType(value: String): ClubTransactionType =
        ClubTransactionType.valueOf(value)
}
