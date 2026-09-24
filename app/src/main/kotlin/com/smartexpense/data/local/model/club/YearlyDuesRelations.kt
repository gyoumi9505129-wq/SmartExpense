package com.smartexpense.data.local.model.club

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Relation
import com.smartexpense.data.local.entity.club.DuesDetailEntity
import com.smartexpense.data.local.entity.club.YearlyDuesEntity

data class YearlyDuesWithDetails(
    @Embedded
    val yearlyDues: YearlyDuesEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "yearly_dues_id"
    )
    val details: List<DuesDetailEntity>
)

data class YearlyDuesWithMemberAndDetails(
    @Embedded
    val yearlyDues: YearlyDuesEntity,
    @ColumnInfo(name = "member_name")
    val memberName: String,
    @Relation(
        parentColumn = "id",
        entityColumn = "yearly_dues_id"
    )
    val details: List<DuesDetailEntity>
)
