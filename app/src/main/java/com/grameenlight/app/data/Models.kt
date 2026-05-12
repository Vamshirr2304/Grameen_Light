package com.grameenlight.app.data

enum class PoleStatus(val label: String, val shortLabel: String) {
    WORKING("Working", "OK"),
    FUSED("Fused", "OFF"),
    BURNING_IN_DAY("Burning in Day", "DAY");

    companion object {
        fun fromStored(value: String): PoleStatus =
            entries.firstOrNull { it.name == value } ?: WORKING
    }
}

enum class RepairState(val label: String) {
    REPORTED("Reported"),
    ASSIGNED("Assigned"),
    FIXED("Fixed");

    companion object {
        fun fromStored(value: String): RepairState =
            entries.firstOrNull { it.name == value } ?: REPORTED
    }
}

data class Pole(
    val id: String,
    val name: String,
    val street: String,
    val xPercent: Float,
    val yPercent: Float,
    val status: PoleStatus = PoleStatus.WORKING,
    val repairState: RepairState = RepairState.FIXED,
    val lastComplaintId: String? = null,
    val burningStartedAtMillis: Long? = null,
    val daytimeWasteMillis: Long = 0L,
    val energyMonthKey: String? = null
)

data class FirebasePoleState(
    val status: PoleStatus,
    val repairState: RepairState,
    val lastComplaintId: String?,
    val updatedAtMillis: Long?,
    val burningStartedAtMillis: Long?,
    val daytimeWasteMillis: Long,
    val energyMonthKey: String?
)

data class PoleEnergyUpdate(
    val burningStartedAtMillis: Long?,
    val clearBurningStartedAtMillis: Boolean,
    val daytimeWasteMillis: Long,
    val energyMonthKey: String
)
