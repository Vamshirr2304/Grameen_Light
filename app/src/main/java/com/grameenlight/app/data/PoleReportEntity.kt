package com.grameenlight.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pole_reports")
data class PoleReportEntity(
    @PrimaryKey val complaintId: String,
    val poleId: String,
    val poleName: String,
    val street: String,
    val status: String,
    val repairState: String,
    val reportedAtMillis: Long,
    val synced: Boolean,
    val updatedAtMillis: Long? = null,
    val fixedAtMillis: Long? = null
) {
    val statusEnum: PoleStatus
        get() = PoleStatus.fromStored(status)

    val repairStateEnum: RepairState
        get() = RepairState.fromStored(repairState)
}
