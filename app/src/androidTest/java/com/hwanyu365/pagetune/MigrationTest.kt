package com.hwanyu365.pagetune

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hwanyu365.pagetune.db.AppDatabase
import com.hwanyu365.pagetune.db.PluginAction
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val name = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Before
    @After
    fun deleteDatabase() {
        targetContext.deleteDatabase(name)
    }

    @Test
    @JvmName("tc508a")
    fun `TC#508 v3 DB 의 Tune·규칙·웹페이지가 현재 버전에서 유지된다`() = upgradeKeepsData(3)

    @Test
    @JvmName("tc508b")
    fun `TC#508 v4 DB 의 Tune·규칙·웹페이지가 현재 버전에서 유지된다`() = upgradeKeepsData(4)

    @Test
    @JvmName("tc23")
    fun `TC#23 v4 에서 당겨 이동을 켠 Tune 에만 당기기 규칙이 생기고 지운 Tune 의 id 는 다시 쓰지 않는다`() = pullMigration()

    private fun pullMigration() {
        val snapshots = InstrumentationRegistry.getInstrumentation().context.assets.list(AppDatabase::class.java.name).orEmpty().toSet()
        assertEquals(setOf("3.json", "4.json", "5.json"), snapshots.filter { it.endsWith(".json") }.toSet())
        val prev = """{"selector":"#prev","tagName":"a","id":"prev","attributes":{}}"""
        val next = """{"selector":"#next","tagName":"a","id":"next","attributes":{}}"""
        helper.createDatabase(name, 4).use { db ->
            for ((id, on) in listOf(1 to 1, 2 to 0, 9 to 0)) db.execSQL("INSERT INTO plugins (id, name, testUrl, pullNavigation) VALUES ($id, 'T$id', NULL, $on)")
            db.execSQL("DELETE FROM plugins WHERE id = 9")
            for (tune in listOf(1, 2)) {
                val base = tune * 10
                db.execSQL("INSERT INTO plugin_actions (id, pluginId, actionType, targetSelector, triggerType, gestureAreaPercent, styleCss) VALUES (${base + 1}, $tune, 'click', ?, 'gnb_prev', 30, NULL)", arrayOf(prev))
                db.execSQL("INSERT INTO plugin_actions (id, pluginId, actionType, targetSelector, triggerType, gestureAreaPercent, styleCss) VALUES (${base + 2}, $tune, 'click', ?, 'gnb_next', 30, NULL)", arrayOf(next))
                db.execSQL("INSERT INTO plugin_actions (id, pluginId, actionType, targetSelector, triggerType, gestureAreaPercent, styleCss) VALUES (${base + 3}, $tune, 'hidden', ?, NULL, 30, NULL)", arrayOf(hide))
            }
            db.execSQL("INSERT INTO webpages (id, title, url, pluginId) VALUES (5, 'Orphan', 'https://o.example/', 9)")
        }

        val db = Room.databaseBuilder(targetContext, AppDatabase::class.java, name).addMigrations(*AppDatabase.MIGRATIONS).build()
        try {
            runBlocking {
                suspend fun rules(tune: Long) = db.pluginDao().getPluginWithActions(tune)!!.actions.sortedBy { it.id }.map { Triple(it.actionType, it.triggerType, it.targetSelector) }
                val original = listOf(Triple("click", "gnb_prev", prev), Triple("click", "gnb_next", next), Triple("hidden", null, hide))
                assertEquals(original + listOf(Triple("click", "pull_top", prev), Triple("click", "pull_bottom", next)), rules(1))
                assertEquals(original, rules(2))
                assertEquals(listOf(1L, 2L), db.pluginDao().getAllPlugins().map { it.id }.sorted())
                assertEquals(9L, db.webPageDao().getWebPageById(5)!!.pluginId)
                val fresh = db.pluginDao().insertPlugin(com.hwanyu365.pagetune.db.Plugin(name = "New"))
                org.junit.Assert.assertTrue("새 Tune id $fresh 가 지운 Tune id 9 이하", fresh > 9)
            }
        } finally {
            db.close()
        }
    }

    private val hide = """{"selector":"#ad","tagName":"div","id":"ad","attributes":{}}"""
    private val box = """{"selector":"#box","tagName":"div","id":"box","attributes":{}}"""
    private val color = "background-color:#000000;color:#ffffff"

    private fun upgradeKeepsData(version: Int) {
        helper.createDatabase(name, version).use { db ->
            val pull = if (version >= 4) ", pullNavigation" to ", 0" else "" to ""
            db.execSQL("INSERT INTO plugins (id, name, testUrl${pull.first}) VALUES (7, 'News', 'https://example.com/'${pull.second})")
            db.execSQL("INSERT INTO plugin_actions (id, pluginId, actionType, targetSelector, triggerType, gestureAreaPercent, styleCss) VALUES (11, 7, 'hidden', ?, NULL, 30, NULL)", arrayOf(hide))
            db.execSQL("INSERT INTO plugin_actions (id, pluginId, actionType, targetSelector, triggerType, gestureAreaPercent, styleCss) VALUES (12, 7, 'color', ?, NULL, 30, ?)", arrayOf(box, color))
            db.execSQL("INSERT INTO webpages (id, title, url, pluginId) VALUES (3, 'Home', 'https://example.com/', 7)")
        }

        // 앱과 같은 마이그레이션 목록으로 연다 → Room 이 올린 뒤 스키마가 현재 엔티티와 다르면 열 때 실패한다.
        val db = Room.databaseBuilder(targetContext, AppDatabase::class.java, name).addMigrations(*AppDatabase.MIGRATIONS).build()
        try {
            runBlocking {
                val tune = db.pluginDao().getPluginWithActions(7)!!
                assertEquals("News", tune.plugin.name)
                assertEquals("https://example.com/", tune.plugin.testUrl)
                assertEquals(
                    listOf(
                        PluginAction(11, 7, "hidden", hide, null, 30, null),
                        PluginAction(12, 7, "color", box, null, 30, color),
                    ),
                    tune.actions.sortedBy { it.id },
                )
                val page = db.webPageDao().getWebPageById(3)!!
                assertEquals(listOf("Home", "https://example.com/", 7L), listOf(page.title, page.url, page.pluginId))
            }
        } finally {
            db.close()
        }
    }
}
