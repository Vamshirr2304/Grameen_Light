package com.grameenlight.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.ListenerRegistration
import com.grameenlight.app.data.FirebaseLightRepository
import com.grameenlight.app.data.FirebasePoleState
import com.grameenlight.app.data.Pole
import com.grameenlight.app.data.PoleEnergyUpdate
import com.grameenlight.app.data.PoleReportDao
import com.grameenlight.app.data.PoleReportEntity
import com.grameenlight.app.data.PoleStatus
import com.grameenlight.app.data.RepairState
import com.grameenlight.app.data.seedVillagePoles
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GrameenLightUiState(
    val poles: List<Pole> = emptyList(),
    val reports: List<PoleReportEntity> = emptyList(),
    val selectedPoleId: String? = null,
    val isDarkAudit: Boolean = true,
    val energySavedKwh: Double = 0.0,
    val monthlyBaselineKwh: Double = 0.0,
    val activeDaytimeBurningCount: Int = 0,
    val activeDaytimeWasteKwh: Double = 0.0,
    val repairedDelayWasteKwh: Double = 0.0,
    val lastComplaintId: String? = null,
    val message: String? = null
) {
    val selectedPole: Pole?
        get() = poles.firstOrNull { it.id == selectedPoleId }
}

class GrameenLightViewModel(
    private val reportDao: PoleReportDao,
    private val firebaseRepository: FirebaseLightRepository
) : ViewModel() {
    private val basePoles = seedVillagePoles()
    private var latestReports: List<PoleReportEntity> = emptyList()
    private var remoteStates: Map<String, FirebasePoleState> = emptyMap()
    private var localPoleOverrides: Map<String, LocalPoleOverride> = emptyMap()
    private var poleStatusListener: ListenerRegistration? = null
    private var reportListener: ListenerRegistration? = null

    private val _uiState = MutableStateFlow(
        GrameenLightUiState(
            poles = basePoles,
            isDarkAudit = isNightAuditNow()
        )
    )
    val uiState: StateFlow<GrameenLightUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            reportDao.observeReports().collect { reports ->
                latestReports = reports
                refreshUi()
            }
        }

        runCatching {
            poleStatusListener = firebaseRepository.listenPoleStatuses(
                onChange = { states ->
                    remoteStates = states
                    refreshUi()
                },
                onError = {
                    showMessage("Firebase listener paused. Local reports still work.")
                }
            )
        }.onFailure {
            showMessage("Firebase is not ready yet. Reports will stay saved locally.")
        }

        runCatching {
            reportListener = firebaseRepository.listenReports(
                onChange = { reports ->
                    viewModelScope.launch {
                        val mergedReports = mergeRemoteReportsWithLocalFixTimes(reports)
                        reportDao.replaceAll(mergedReports)
                        closeBurningTimersForFixedReports(mergedReports)
                    }
                },
                onError = {
                    showMessage("Firebase tracker paused. Local reports still work.")
                }
            )
        }.onFailure {
            showMessage("Firebase tracker is not ready yet.")
        }

        viewModelScope.launch {
            while (true) {
                updateAuditThemeFromDeviceTime()
                refreshUi()
                delay(60_000L)
            }
        }
    }

    fun selectPole(poleId: String) {
        _uiState.update { state ->
            state.copy(selectedPoleId = poleId)
        }
    }

    fun dismissPoleSheet() {
        _uiState.update { state ->
            state.copy(selectedPoleId = null)
        }
    }

    fun clearMessage() {
        _uiState.update { state ->
            state.copy(message = null)
        }
    }

    fun reportPole(poleId: String, status: PoleStatus) {
        val pole = _uiState.value.poles.firstOrNull { it.id == poleId } ?: return
        val now = System.currentTimeMillis()
        val activeComplaint = activeComplaintForPole(poleId)
        val activeComplaintId = activeComplaint?.complaintId
            ?: pole.lastComplaintId.takeIf { pole.repairState != RepairState.FIXED }
        val activeRepairState = activeComplaint?.repairStateEnum
            ?: pole.repairState.takeIf { activeComplaintId != null }
            ?: RepairState.REPORTED
        val energyUpdate = buildEnergyUpdate(
            previousPole = pole,
            nextStatus = status,
            transitionAtMillis = now
        )

        if (status == PoleStatus.WORKING) {
            applyVisualStatus(
                poleId = poleId,
                status = PoleStatus.WORKING,
                repairState = activeRepairState.takeIf { activeComplaintId != null } ?: RepairState.FIXED,
                lastComplaintId = activeComplaintId,
                energyUpdate = energyUpdate,
                updatedAtMillis = now
            )
            pushPoleStatusOnly(
                poleId = poleId,
                status = PoleStatus.WORKING,
                repairState = if (activeComplaintId == null) RepairState.FIXED else null,
                lastComplaintId = activeComplaintId,
                energyUpdate = energyUpdate,
                updatedAtMillis = now
            )
            showMessage(
                activeComplaintId?.let {
                    "Marked green. Complaint $it stays active until backend fixes it."
                } ?: "Pole marked working"
            )
            return
        }

        if (activeComplaintId != null) {
            applyVisualStatus(
                poleId = poleId,
                status = status,
                repairState = activeRepairState,
                lastComplaintId = activeComplaintId,
                energyUpdate = energyUpdate,
                updatedAtMillis = now
            )
            pushPoleStatusOnly(
                poleId = poleId,
                status = status,
                lastComplaintId = activeComplaintId,
                energyUpdate = energyUpdate,
                updatedAtMillis = now
            )
            showMessage("Complaint already active: $activeComplaintId")
            return
        }

        val complaintId = generateComplaintId(poleId, now)
        val repairState = RepairState.REPORTED
        val report = PoleReportEntity(
            complaintId = complaintId,
            poleId = pole.id,
            poleName = pole.name,
            street = pole.street,
            status = status.name,
            repairState = repairState.name,
            reportedAtMillis = now,
            synced = false,
            updatedAtMillis = now,
            fixedAtMillis = null
        )

        viewModelScope.launch {
            reportDao.upsert(report)
            applyVisualStatus(
                poleId = poleId,
                status = status,
                repairState = repairState,
                lastComplaintId = complaintId,
                energyUpdate = energyUpdate,
                updatedAtMillis = now
            )
            _uiState.update { state ->
                state.copy(
                    selectedPoleId = null,
                    lastComplaintId = complaintId,
                    message = "Complaint $complaintId created"
                )
            }
        }

        runCatching {
            firebaseRepository.submitReport(
                report = report,
                energyUpdate = energyUpdate,
                onSuccess = {
                    viewModelScope.launch {
                        reportDao.updateSynced(complaintId, true)
                    }
                },
                onFailure = {
                    showMessage("Saved offline. It will remain in Room DB.")
                }
            )
        }.onFailure {
            showMessage("Saved offline. Firebase sync can be retried later.")
        }
    }

    private fun refreshUi() {
        val latestByPole = latestReports
            .groupBy { it.poleId }
            .mapValues { (_, reports) -> reports.maxBy { it.reportedAtMillis } }

        val poles = basePoles.map { base ->
            val remote = remoteStates[base.id]
            val local = latestByPole[base.id]
            val override = localPoleOverrides[base.id]
            val localUpdatedAt = local?.updatedAtMillis ?: local?.reportedAtMillis ?: 0L
            val remoteUpdatedAt = remote?.updatedAtMillis ?: 0L
            val overrideUpdatedAt = override?.updatedAtMillis ?: 0L

            when {
                remote?.repairState == RepairState.FIXED && remoteUpdatedAt >= localUpdatedAt -> {
                    localPoleOverrides = localPoleOverrides - base.id
                    base.copy(
                        status = PoleStatus.WORKING,
                        repairState = RepairState.FIXED,
                        lastComplaintId = remote.lastComplaintId,
                        burningStartedAtMillis = null,
                        daytimeWasteMillis = remote.daytimeWasteMillis,
                        energyMonthKey = remote.energyMonthKey
                    )
                }

                local?.repairStateEnum == RepairState.FIXED && localUpdatedAt >= remoteUpdatedAt -> {
                    localPoleOverrides = localPoleOverrides - base.id
                    base.copy(
                        status = PoleStatus.WORKING,
                        repairState = RepairState.FIXED,
                        lastComplaintId = local.complaintId,
                        burningStartedAtMillis = null,
                        daytimeWasteMillis = fixedPoleWasteMillis(local, remote),
                        energyMonthKey = remote?.energyMonthKey
                            ?: currentEnergyMonthKey(local.fixedAtMillis ?: local.updatedAtMillis ?: local.reportedAtMillis)
                    )
                }

                override != null && overrideUpdatedAt >= localUpdatedAt && overrideUpdatedAt >= remoteUpdatedAt -> base.copy(
                    status = override.status,
                    repairState = override.repairState,
                    lastComplaintId = override.lastComplaintId,
                    burningStartedAtMillis = override.burningStartedAtMillis,
                    daytimeWasteMillis = override.daytimeWasteMillis,
                    energyMonthKey = override.energyMonthKey
                )

                local != null && localUpdatedAt >= remoteUpdatedAt -> base.copy(
                    status = if (local.repairStateEnum == RepairState.FIXED) {
                        PoleStatus.WORKING
                    } else {
                        local.statusEnum
                    },
                    repairState = local.repairStateEnum,
                    lastComplaintId = local.complaintId,
                    burningStartedAtMillis = if (
                        local.statusEnum == PoleStatus.BURNING_IN_DAY &&
                        local.repairStateEnum != RepairState.FIXED
                    ) {
                        local.reportedAtMillis
                    } else {
                        null
                    },
                    daytimeWasteMillis = if (local.repairStateEnum == RepairState.FIXED) {
                        fixedPoleWasteMillis(local, remote)
                    } else {
                        0L
                    },
                    energyMonthKey = remote?.energyMonthKey ?: currentEnergyMonthKey(local.reportedAtMillis)
                )

                remote != null -> base.copy(
                    status = if (remote.repairState == RepairState.FIXED) {
                        PoleStatus.WORKING
                    } else {
                        remote.status
                    },
                    repairState = remote.repairState,
                    lastComplaintId = remote.lastComplaintId,
                    burningStartedAtMillis = if (
                        remote.status == PoleStatus.BURNING_IN_DAY &&
                        remote.repairState != RepairState.FIXED
                    ) {
                        remote.burningStartedAtMillis
                    } else {
                        null
                    },
                    daytimeWasteMillis = remote.daytimeWasteMillis,
                    energyMonthKey = remote.energyMonthKey
                )

                local != null -> base.copy(
                    status = if (local.repairStateEnum == RepairState.FIXED) {
                        PoleStatus.WORKING
                    } else {
                        local.statusEnum
                    },
                    repairState = local.repairStateEnum,
                    lastComplaintId = local.complaintId,
                    burningStartedAtMillis = if (
                        local.statusEnum == PoleStatus.BURNING_IN_DAY &&
                        local.repairStateEnum != RepairState.FIXED
                    ) {
                        local.reportedAtMillis
                    } else {
                        null
                    },
                    daytimeWasteMillis = if (local.repairStateEnum == RepairState.FIXED) {
                        fixedPoleWasteMillis(local, remote)
                    } else {
                        0L
                    },
                    energyMonthKey = remote?.energyMonthKey ?: currentEnergyMonthKey(local.reportedAtMillis)
                )

                else -> base
            }
        }

        _uiState.update { state ->
            val energyStats = calculateEnergyStats(poles, latestReports)
            state.copy(
                poles = poles,
                reports = latestReports,
                energySavedKwh = energyStats.savedKwh,
                monthlyBaselineKwh = energyStats.monthlyBaselineKwh,
                activeDaytimeBurningCount = energyStats.activeDaytimeBurningCount,
                activeDaytimeWasteKwh = energyStats.activeDaytimeWasteKwh,
                repairedDelayWasteKwh = energyStats.repairedDelayWasteKwh
            )
        }
    }

    private fun activeComplaintForPole(poleId: String): PoleReportEntity? =
        latestReports
            .filter { report ->
                report.poleId == poleId &&
                    report.statusEnum != PoleStatus.WORKING &&
                    report.repairStateEnum != RepairState.FIXED
            }
            .maxByOrNull { it.reportedAtMillis }

    private fun applyVisualStatus(
        poleId: String,
        status: PoleStatus,
        repairState: RepairState,
        lastComplaintId: String?,
        energyUpdate: PoleEnergyUpdate,
        updatedAtMillis: Long
    ) {
        localPoleOverrides = localPoleOverrides + (
            poleId to LocalPoleOverride(
                status = status,
                repairState = repairState,
                lastComplaintId = lastComplaintId,
                burningStartedAtMillis = energyUpdate.burningStartedAtMillis,
                daytimeWasteMillis = energyUpdate.daytimeWasteMillis,
                energyMonthKey = energyUpdate.energyMonthKey,
                updatedAtMillis = updatedAtMillis
            )
        )
        _uiState.update { state ->
            val updatedPoles = state.poles.map { pole ->
                if (pole.id == poleId) {
                    pole.copy(
                        status = status,
                        repairState = repairState,
                        lastComplaintId = lastComplaintId,
                        burningStartedAtMillis = energyUpdate.burningStartedAtMillis,
                        daytimeWasteMillis = energyUpdate.daytimeWasteMillis,
                        energyMonthKey = energyUpdate.energyMonthKey
                    )
                } else {
                    pole
                }
            }
            val energyStats = calculateEnergyStats(updatedPoles, latestReports)
            state.copy(
                selectedPoleId = null,
                poles = updatedPoles,
                energySavedKwh = energyStats.savedKwh,
                monthlyBaselineKwh = energyStats.monthlyBaselineKwh,
                activeDaytimeBurningCount = energyStats.activeDaytimeBurningCount,
                activeDaytimeWasteKwh = energyStats.activeDaytimeWasteKwh,
                repairedDelayWasteKwh = energyStats.repairedDelayWasteKwh
            )
        }
    }

    private fun pushPoleStatusOnly(
        poleId: String,
        status: PoleStatus,
        repairState: RepairState? = null,
        lastComplaintId: String? = null,
        energyUpdate: PoleEnergyUpdate,
        updatedAtMillis: Long
    ) {
        runCatching {
            firebaseRepository.updatePoleStatusOnly(
                poleId = poleId,
                status = status,
                repairState = repairState,
                lastComplaintId = lastComplaintId,
                energyUpdate = energyUpdate,
                updatedAtMillis = updatedAtMillis,
                onSuccess = {},
                onFailure = {
                    showMessage("Color changed locally. Firebase status update failed.")
                }
            )
        }.onFailure {
            showMessage("Color changed locally. Firebase status update failed.")
        }
    }

    private fun mergeRemoteReportsWithLocalFixTimes(
        remoteReports: List<PoleReportEntity>
    ): List<PoleReportEntity> {
        val localByComplaintId = latestReports.associateBy { it.complaintId }
        val now = System.currentTimeMillis()

        return remoteReports.map { remote ->
            val local = localByComplaintId[remote.complaintId]
            if (remote.repairStateEnum == RepairState.FIXED) {
                val fixedAtMillis = remote.fixedAtMillis
                    ?: local?.fixedAtMillis
                    ?: remote.updatedAtMillis
                    ?: now
                remote.copy(
                    fixedAtMillis = fixedAtMillis,
                    updatedAtMillis = remote.updatedAtMillis ?: fixedAtMillis
                )
            } else {
                remote
            }
        }
    }

    private fun closeBurningTimersForFixedReports(reports: List<PoleReportEntity>) {
        reports
            .filter { it.repairStateEnum == RepairState.FIXED }
            .forEach { report ->
                val pole = _uiState.value.poles.firstOrNull { it.id == report.poleId } ?: return@forEach
                if (
                    pole.status != PoleStatus.BURNING_IN_DAY ||
                    pole.burningStartedAtMillis == null ||
                    pole.lastComplaintId != report.complaintId
                ) {
                    return@forEach
                }

                val fixedAtMillis = report.fixedAtMillis
                    ?: report.updatedAtMillis
                    ?: System.currentTimeMillis()
                val energyUpdate = buildEnergyUpdate(
                    previousPole = pole,
                    nextStatus = PoleStatus.WORKING,
                    transitionAtMillis = fixedAtMillis
                )
                applyVisualStatus(
                    poleId = pole.id,
                    status = PoleStatus.WORKING,
                    repairState = RepairState.FIXED,
                    lastComplaintId = report.complaintId,
                    energyUpdate = energyUpdate,
                    updatedAtMillis = fixedAtMillis
                )
                pushPoleStatusOnly(
                    poleId = pole.id,
                    status = PoleStatus.WORKING,
                    repairState = RepairState.FIXED,
                    lastComplaintId = report.complaintId,
                    energyUpdate = energyUpdate,
                    updatedAtMillis = fixedAtMillis
                )
            }
    }

    private fun buildEnergyUpdate(
        previousPole: Pole,
        nextStatus: PoleStatus,
        transitionAtMillis: Long
    ): PoleEnergyUpdate {
        val monthKey = currentEnergyMonthKey(transitionAtMillis)
        val storedWasteMillis = normalizedDaytimeWasteMillis(
            wasteMillis = previousPole.daytimeWasteMillis,
            storedMonthKey = previousPole.energyMonthKey,
            currentMonthKey = monthKey
        )
        val activeStartMillis = if (
            previousPole.status == PoleStatus.BURNING_IN_DAY &&
            previousPole.repairState != RepairState.FIXED
        ) {
            activeBurningStartForCurrentMonth(previousPole, transitionAtMillis)
        } else {
            null
        }
        val closedWasteMillis = if (activeStartMillis != null && nextStatus != PoleStatus.BURNING_IN_DAY) {
            storedWasteMillis + (transitionAtMillis - activeStartMillis).coerceAtLeast(0L)
        } else {
            storedWasteMillis
        }
        val nextBurningStartedAtMillis = if (nextStatus == PoleStatus.BURNING_IN_DAY) {
            activeStartMillis ?: transitionAtMillis
        } else {
            null
        }

        return PoleEnergyUpdate(
            burningStartedAtMillis = nextBurningStartedAtMillis,
            clearBurningStartedAtMillis = nextStatus != PoleStatus.BURNING_IN_DAY,
            daytimeWasteMillis = if (nextStatus == PoleStatus.BURNING_IN_DAY) {
                storedWasteMillis
            } else {
                closedWasteMillis
            },
            energyMonthKey = monthKey
        )
    }

    private fun fixedReportWasteMillis(report: PoleReportEntity): Long {
        if (report.statusEnum != PoleStatus.BURNING_IN_DAY) return 0L
        val fixedAtMillis = report.fixedAtMillis
            ?: report.updatedAtMillis
            ?: report.reportedAtMillis
        if (currentEnergyMonthKey(report.reportedAtMillis) != currentEnergyMonthKey(fixedAtMillis)) {
            return 0L
        }
        return (fixedAtMillis - report.reportedAtMillis).coerceAtLeast(0L)
    }

    private fun fixedPoleWasteMillis(
        report: PoleReportEntity,
        remote: FirebasePoleState?
    ): Long {
        val fixedAtMillis = report.fixedAtMillis
            ?: report.updatedAtMillis
            ?: report.reportedAtMillis
        val monthKey = currentEnergyMonthKey(fixedAtMillis)
        val remoteWasteMillis = remote?.let {
            normalizedDaytimeWasteMillis(
                wasteMillis = it.daytimeWasteMillis,
                storedMonthKey = it.energyMonthKey,
                currentMonthKey = monthKey
            )
        }

        if (
            remote?.status == PoleStatus.BURNING_IN_DAY &&
            remote.repairState != RepairState.FIXED &&
            remote.lastComplaintId == report.complaintId &&
            remote.burningStartedAtMillis != null
        ) {
            val startMonthKey = remote.energyMonthKey
                ?: currentEnergyMonthKey(remote.burningStartedAtMillis)
            val startedAtMillis = if (startMonthKey == monthKey) {
                remote.burningStartedAtMillis
            } else {
                startOfMonthMillis(fixedAtMillis)
            }
            return (remoteWasteMillis ?: 0L) + (fixedAtMillis - startedAtMillis).coerceAtLeast(0L)
        }

        return remoteWasteMillis ?: fixedReportWasteMillis(report)
    }

    private fun normalizedDaytimeWasteMillis(
        wasteMillis: Long,
        storedMonthKey: String?,
        currentMonthKey: String
    ): Long =
        if (storedMonthKey == currentMonthKey) {
            wasteMillis.coerceAtLeast(0L)
        } else {
            0L
        }

    private fun activeBurningStartForCurrentMonth(pole: Pole, nowMillis: Long): Long? {
        val startedAtMillis = pole.burningStartedAtMillis ?: return null
        val monthKey = currentEnergyMonthKey(nowMillis)
        val startMonthKey = pole.energyMonthKey ?: currentEnergyMonthKey(startedAtMillis)
        return if (startMonthKey == monthKey) {
            startedAtMillis
        } else {
            startOfMonthMillis(nowMillis)
        }
    }

    private fun currentEnergyMonthKey(millis: Long): String {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = millis
        }
        return String.format(
            Locale.US,
            "%04d-%02d",
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1
        )
    }

    private fun startOfMonthMillis(millis: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun calculateEnergyStats(
        poles: List<Pole>,
        reports: List<PoleReportEntity>
    ): EnergyStats {
        val nowMillis = System.currentTimeMillis()
        val monthKey = currentEnergyMonthKey(nowMillis)

        val activeDaytimeBurningCount = poles.count { pole ->
            pole.status == PoleStatus.BURNING_IN_DAY &&
                pole.repairState != RepairState.FIXED &&
                pole.burningStartedAtMillis != null
        }

        val monthlyBaselineKwh = basePoles.size * POLE_WATTAGE_KW * NIGHT_HOURS_PER_DAY * DAYS_IN_MONTH_MODEL
        val storedWasteMillis = poles.sumOf { pole ->
            normalizedDaytimeWasteMillis(pole.daytimeWasteMillis, pole.energyMonthKey, monthKey)
        }
        val liveWasteMillis = poles.sumOf { pole ->
            if (pole.status == PoleStatus.BURNING_IN_DAY && pole.repairState != RepairState.FIXED) {
                val startedAtMillis = activeBurningStartForCurrentMonth(pole, nowMillis)
                ((startedAtMillis?.let { nowMillis - it } ?: 0L)).coerceAtLeast(0L)
            } else {
                0L
            }
        }
        val totalWasteKwh = (storedWasteMillis + liveWasteMillis) / MILLIS_PER_HOUR * POLE_WATTAGE_KW
        val storedWasteKwh = storedWasteMillis / MILLIS_PER_HOUR * POLE_WATTAGE_KW
        val netSavedKwh = (monthlyBaselineKwh - totalWasteKwh).coerceAtLeast(0.0)

        return EnergyStats(
            savedKwh = netSavedKwh.roundToTwoDecimals(),
            monthlyBaselineKwh = monthlyBaselineKwh.roundToOneDecimal(),
            activeDaytimeBurningCount = activeDaytimeBurningCount,
            activeDaytimeWasteKwh = totalWasteKwh.roundToTwoDecimals(),
            repairedDelayWasteKwh = storedWasteKwh.roundToTwoDecimals()
        )
    }

    private fun isInCurrentMonth(millis: Long, now: Calendar): Boolean {
        val reportTime = Calendar.getInstance().apply {
            timeInMillis = millis
        }
        return reportTime.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
            reportTime.get(Calendar.MONTH) == now.get(Calendar.MONTH)
    }

    private fun generateComplaintId(poleId: String, millis: Long): String {
        val timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(millis))
        val suffix = UUID.randomUUID()
            .toString()
            .take(8)
            .uppercase()
        return "GL-$timestamp-$poleId-$suffix"
    }

    private fun updateAuditThemeFromDeviceTime() {
        _uiState.update { state ->
            state.copy(isDarkAudit = isNightAuditNow())
        }
    }

    private fun isNightAuditNow(): Boolean {
        val hour = LocalTime.now().hour
        return hour < 6 || hour >= 18
    }

    private fun Double.roundToOneDecimal(): Double = (this * 10.0).roundToInt() / 10.0
    private fun Double.roundToTwoDecimals(): Double = (this * 100.0).roundToInt() / 100.0

    private fun showMessage(text: String) {
        _uiState.update { state ->
            state.copy(message = text)
        }
    }

    override fun onCleared() {
        poleStatusListener?.remove()
        reportListener?.remove()
        super.onCleared()
    }
}

class GrameenLightViewModelFactory(
    private val reportDao: PoleReportDao,
    private val firebaseRepository: FirebaseLightRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GrameenLightViewModel::class.java)) {
            return GrameenLightViewModel(reportDao, firebaseRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

private data class EnergyStats(
    val savedKwh: Double,
    val monthlyBaselineKwh: Double,
    val activeDaytimeBurningCount: Int,
    val activeDaytimeWasteKwh: Double,
    val repairedDelayWasteKwh: Double
)

private data class LocalPoleOverride(
    val status: PoleStatus,
    val repairState: RepairState,
    val lastComplaintId: String?,
    val burningStartedAtMillis: Long?,
    val daytimeWasteMillis: Long,
    val energyMonthKey: String,
    val updatedAtMillis: Long
)

private const val POLE_WATTAGE_KW = 0.04
private const val NIGHT_HOURS_PER_DAY = 12
private const val DAYLIGHT_WASTE_HOURS_PER_DAY = 12
private const val DAYS_IN_MONTH_MODEL = 30
private const val MILLIS_PER_HOUR = 3_600_000.0
