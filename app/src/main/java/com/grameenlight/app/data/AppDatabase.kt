package com.grameenlight.app.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [PoleReportEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun poleReportDao(): PoleReportDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pole_reports ADD COLUMN updatedAtMillis INTEGER")
                db.execSQL("ALTER TABLE pole_reports ADD COLUMN fixedAtMillis INTEGER")
            }
        }
    }
}
