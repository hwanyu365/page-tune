package com.hwanyu365.pagetune.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Plugin::class, PluginAction::class, WebPage::class],
    version = 5,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 3, to = 4)],
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

        /** 끝에서 당겨 이동 설정을 Tune 칸에서 클릭 연결 규칙으로 옮긴다. 켠 Tune 은 이전/다음 버튼 연결을 복사해 위/아래 당기기 규칙을 만든다. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    INSERT INTO plugin_actions (pluginId, actionType, targetSelector, triggerType, gestureAreaPercent, styleCss)
                    SELECT a.pluginId, a.actionType, a.targetSelector,
                           CASE a.triggerType WHEN 'gnb_prev' THEN 'pull_top' ELSE 'pull_bottom' END,
                           a.gestureAreaPercent, a.styleCss
                    FROM plugin_actions a JOIN plugins p ON p.id = a.pluginId
                    WHERE p.pullNavigation = 1 AND a.actionType = 'click' AND a.triggerType IN ('gnb_prev', 'gnb_next')
                    ORDER BY a.id
                    """.trimIndent()
                )
                // 테이블을 다시 만들면 AUTOINCREMENT 순번이 남은 최대 id 로 내려간다.
                // 지운 Tune 을 가리키는 webpages.pluginId 가 남아 있으므로 순번을 지키지 않으면 새 Tune 이 그 연결을 이어받는다.
                val seq = db.query("SELECT seq FROM sqlite_sequence WHERE name = 'plugins'").use { if (it.moveToFirst()) it.getLong(0) else 0L }
                db.execSQL("CREATE TABLE IF NOT EXISTS `_new_plugins` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `testUrl` TEXT)")
                db.execSQL("INSERT INTO `_new_plugins` (`id`,`name`,`testUrl`) SELECT `id`,`name`,`testUrl` FROM `plugins`")
                db.execSQL("DROP TABLE `plugins`")
                db.execSQL("ALTER TABLE `_new_plugins` RENAME TO `plugins`")
                db.execSQL("INSERT INTO sqlite_sequence (name, seq) SELECT 'plugins', 0 WHERE NOT EXISTS (SELECT 1 FROM sqlite_sequence WHERE name = 'plugins')")
                db.execSQL("UPDATE sqlite_sequence SET seq = MAX(seq, $seq) WHERE name = 'plugins'")
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
                .addMigrations(MIGRATION_2_3, MIGRATION_4_5)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
