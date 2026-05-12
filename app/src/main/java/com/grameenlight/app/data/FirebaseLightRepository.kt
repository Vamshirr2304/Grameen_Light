package com.grameenlight.app.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions

class FirebaseLightRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    fun submitReport(
        report: PoleReportEntity,
        energyUpdate: PoleEnergyUpdate,
        onSuccess: () -> Unit,
        onFailure: (Throwable) -> Unit
    ) {
        val payload = mapOf<String, Any?>(
            "complaintId" to report.complaintId,
            "poleId" to report.poleId,
            "poleName" to report.poleName,
            "street" to report.street,
            "status" to report.status,
            "repairState" to report.repairState,
            "reportedAtMillis" to report.reportedAtMillis,
            "updatedAtMillis" to report.reportedAtMillis,
            "createdAtServer" to FieldValue.serverTimestamp(),
            "updatedAtServer" to FieldValue.serverTimestamp(),
            "source" to "android-citizen-app"
        )

        val statusPayload = mapOf<String, Any?>(
            "poleId" to report.poleId,
            "status" to report.status,
            "repairState" to report.repairState,
            "lastComplaintId" to report.complaintId,
            "updatedAtMillis" to report.reportedAtMillis,
            "daytimeWasteMillis" to energyUpdate.daytimeWasteMillis,
            "energyMonthKey" to energyUpdate.energyMonthKey,
            "updatedAtServer" to FieldValue.serverTimestamp()
        ).withEnergyStart(energyUpdate)

        firestore.runBatch { batch ->
            batch.set(
                firestore.collection("poleReports").document(report.complaintId),
                payload,
                SetOptions.merge()
            )
            batch.set(
                firestore.collection("poleStatuses").document(report.poleId),
                statusPayload,
                SetOptions.merge()
            )
        }.addOnSuccessListener {
            onSuccess()
        }.addOnFailureListener { error ->
            onFailure(error)
        }
    }

    fun updatePoleStatusOnly(
        poleId: String,
        status: PoleStatus,
        repairState: RepairState? = null,
        lastComplaintId: String? = null,
        energyUpdate: PoleEnergyUpdate,
        updatedAtMillis: Long,
        onSuccess: () -> Unit,
        onFailure: (Throwable) -> Unit
    ) {
        val payload = buildMap<String, Any?> {
            put("poleId", poleId)
            put("status", status.name)
            put("updatedAtMillis", updatedAtMillis)
            put("updatedAtServer", FieldValue.serverTimestamp())
            put("source", "android-citizen-observation")
            put("daytimeWasteMillis", energyUpdate.daytimeWasteMillis)
            put("energyMonthKey", energyUpdate.energyMonthKey)
            if (energyUpdate.burningStartedAtMillis != null) {
                put("burningStartedAtMillis", energyUpdate.burningStartedAtMillis)
            } else if (energyUpdate.clearBurningStartedAtMillis) {
                put("burningStartedAtMillis", FieldValue.delete())
            }
            repairState?.let { put("repairState", it.name) }
            if (lastComplaintId != null) {
                put("lastComplaintId", lastComplaintId)
            } else if (repairState == RepairState.FIXED) {
                put("lastComplaintId", FieldValue.delete())
            }
        }

        firestore.collection("poleStatuses")
            .document(poleId)
            .set(payload, SetOptions.merge())
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { error -> onFailure(error) }
    }

    fun listenPoleStatuses(
        onChange: (Map<String, FirebasePoleState>) -> Unit,
        onError: (Throwable) -> Unit
    ): ListenerRegistration =
        firestore.collection("poleStatuses")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }

                val states = snapshot?.documents.orEmpty().associate { document ->
                    val poleId = document.getString("poleId") ?: document.id
                    poleId to FirebasePoleState(
                        status = PoleStatus.fromStored(document.getString("status").orEmpty()),
                        repairState = RepairState.fromStored(document.getString("repairState").orEmpty()),
                        lastComplaintId = document.getString("lastComplaintId"),
                        updatedAtMillis = document.getLong("updatedAtMillis"),
                        burningStartedAtMillis = document.getLong("burningStartedAtMillis"),
                        daytimeWasteMillis = document.getLong("daytimeWasteMillis") ?: 0L,
                        energyMonthKey = document.getString("energyMonthKey")
                    )
                }
                onChange(states)
            }

    fun listenReports(
        onChange: (List<PoleReportEntity>) -> Unit,
        onError: (Throwable) -> Unit
    ): ListenerRegistration =
        firestore.collection("poleReports")
            .orderBy("reportedAtMillis", Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }

                val reports = snapshot?.documents.orEmpty().mapNotNull { document ->
                    val complaintId = document.getString("complaintId") ?: document.id
                    val poleId = document.getString("poleId") ?: return@mapNotNull null
                    val repairState = RepairState.fromStored(document.getString("repairState").orEmpty())
                    val reportedAtMillis = document.getLong("reportedAtMillis") ?: 0L
                    val updatedAtMillis = document.getLong("updatedAtMillis")
                    val fixedAtMillis = document.getLong("fixedAtMillis")
                        ?: document.getLong("resolvedAtMillis")
                        ?: if (repairState == RepairState.FIXED) updatedAtMillis else null

                    PoleReportEntity(
                        complaintId = complaintId,
                        poleId = poleId,
                        poleName = document.getString("poleName") ?: poleId,
                        street = document.getString("street") ?: "Village Street",
                        status = document.getString("status") ?: PoleStatus.WORKING.name,
                        repairState = repairState.name,
                        reportedAtMillis = reportedAtMillis,
                        synced = true,
                        updatedAtMillis = updatedAtMillis,
                        fixedAtMillis = fixedAtMillis
                    )
                }
                onChange(reports)
            }

    private fun Map<String, Any?>.withEnergyStart(
        energyUpdate: PoleEnergyUpdate
    ): Map<String, Any?> = buildMap {
        putAll(this@withEnergyStart)
        if (energyUpdate.burningStartedAtMillis != null) {
            put("burningStartedAtMillis", energyUpdate.burningStartedAtMillis)
        } else if (energyUpdate.clearBurningStartedAtMillis) {
            put("burningStartedAtMillis", FieldValue.delete())
        }
    }
}
