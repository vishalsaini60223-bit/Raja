package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.AdminVaultEntity
import com.example.data.model.LiveTickerEntity
import com.example.data.model.NotificationAlertEntity
import com.example.data.model.PayoutMethodEntity
import com.example.data.model.ReferralEntity
import com.example.data.model.SupportMessageEntity
import com.example.data.model.TransactionEntity
import com.example.data.model.UserAccountEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Database(
    entities = [
        UserAccountEntity::class,
        TransactionEntity::class,
        PayoutMethodEntity::class,
        NotificationAlertEntity::class,
        SupportMessageEntity::class,
        ReferralEntity::class,
        LiveTickerEntity::class,
        AdminVaultEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class JioPanalDatabase : RoomDatabase() {
    abstract fun dao(): JioPanalDao

    companion object {
        @Volatile
        private var INSTANCE: JioPanalDatabase? = null

        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE users ADD COLUMN passwordHash TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE users ADD COLUMN lastLoginAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE users ADD COLUMN loginCount INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE users ADD COLUMN isLoggedIn INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): JioPanalDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(appContext: Context): JioPanalDatabase {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            return Room.databaseBuilder(
                appContext,
                JioPanalDatabase::class.java,
                "jio_panal_vault.db"
            )
                .addMigrations(MIGRATION_1_2)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        scope.launch {
                            seedInitialState(getInstance(appContext).dao())
                        }
                    }
                })
                .build()
        }

        suspend fun seedInitialState(dao: JioPanalDao) {
            if (dao.getAdminVault() == null) {
                dao.upsertAdminVault(AdminVaultEntity())
            }
            val now = System.currentTimeMillis()
            val initialTickers = listOf(
                LiveTickerEntity(
                    message = "Account Activated (vishal***@gmail.com) • Deposit ₹5,000",
                    highlightAmount = "+₹750 (15% Comm)",
                    isCredit = true,
                    createdAt = now - 300_000L
                ),
                LiveTickerEntity(
                    message = "UPI Deposit Verified via vishalkumar90@upi",
                    highlightAmount = "₹5,000 Deposited",
                    isCredit = false,
                    createdAt = now - 180_000L
                ),
                LiveTickerEntity(
                    message = "Partner Commission Credited (15% Tier)",
                    highlightAmount = "+₹1,500 Credited",
                    isCredit = true,
                    createdAt = now - 90_000L
                ),
                LiveTickerEntity(
                    message = "Admin Commission Pool Live & Active",
                    highlightAmount = "₹2,00,000 Ready",
                    isCredit = true,
                    createdAt = now - 30_000L
                )
            )
            initialTickers.forEach { dao.insertTicker(it) }
        }
    }
}
