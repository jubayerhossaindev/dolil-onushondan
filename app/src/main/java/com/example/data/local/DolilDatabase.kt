package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        DeedEntity::class,
        AuditLogEntity::class,
        PendingSyncEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(RoomConverters::class)
abstract class DolilDatabase : RoomDatabase() {
    abstract fun dolilDao(): DolilDao

    companion object {
        @Volatile
        private var INSTANCE: DolilDatabase? = null

        @Volatile
        private var GUEST_INSTANCE: DolilDatabase? = null

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE deeds ADD COLUMN ownerUid TEXT NOT NULL DEFAULT '$LEGACY_LOCAL_OWNER_UID'"
                )
                db.execSQL("ALTER TABLE deeds ADD COLUMN deviceId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE deeds ADD COLUMN revisionId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE deeds ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE deeds ADD COLUMN deletedByDeviceId TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_deeds_ownerUid ON deeds(ownerUid)")

                db.execSQL(
                    "ALTER TABLE audit_logs ADD COLUMN ownerUid TEXT NOT NULL DEFAULT '$LEGACY_LOCAL_OWNER_UID'"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_audit_logs_ownerUid ON audit_logs(ownerUid)")

                db.execSQL(
                    "ALTER TABLE pending_sync ADD COLUMN ownerUid TEXT NOT NULL DEFAULT '$LEGACY_LOCAL_OWNER_UID'"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pending_sync_ownerUid ON pending_sync(ownerUid)")
            }
        }

        fun getInstance(context: Context): DolilDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DolilDatabase::class.java,
                    "dolil_onusondhan.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }

        fun getGuestSandboxInstance(context: Context): DolilDatabase {
            return GUEST_INSTANCE ?: synchronized(this) {
                GUEST_INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DolilDatabase::class.java,
                    "dolil_guest_sandbox.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { GUEST_INSTANCE = it }
            }
        }
    }
}
