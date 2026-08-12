package com.hwanyu365.pagetune.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Plugin::class, PluginAction::class, WebPage::class],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun pluginDao(): PluginDao
    abstract fun webPageDao(): WebPageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** "color" 액션의 CSS 선언부를 담을 컬럼. 기존 액션은 null 이 되어 색상 규칙에서 자동으로 빠진다. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE plugin_actions ADD COLUMN styleCss TEXT")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "web_plugin_database"
                )
                // 저장된 Tune 과 웹페이지는 날려선 안 된다. 버전을 올릴 땐 마이그레이션을 반드시 함께 추가한다.
                // fallbackToDestructiveMigration() 을 쓰면 마이그레이션 누락이 조용한 데이터 삭제로 이어진다.
                .addMigrations(MIGRATION_2_3)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
