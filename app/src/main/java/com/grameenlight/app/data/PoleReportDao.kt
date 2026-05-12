package com.grameenlight.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PoleReportDao {
    @Query("SELECT * FROM pole_reports ORDER BY reportedAtMillis DESC")
    fun observeReports(): Flow<List<PoleReportEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(report: PoleReportEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(reports: List<PoleReportEntity>)

    @Query("DELETE FROM pole_reports")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(reports: List<PoleReportEntity>) {
        deleteAll()
        upsertAll(reports)
    }

    @Query("UPDATE pole_reports SET synced = :synced WHERE complaintId = :complaintId")
    suspend fun updateSynced(complaintId: String, synced: Boolean)
}
