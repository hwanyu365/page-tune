package com.hwanyu365.pagetune

import com.hwanyu365.pagetune.db.AppDatabase
import com.hwanyu365.pagetune.db.Plugin
import com.hwanyu365.pagetune.db.PluginAction
import com.hwanyu365.pagetune.db.WebPage
import kotlinx.coroutines.runBlocking
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

val database: AppDatabase get() = AppDatabase.getDatabase(targetContext)

fun clearData() = database.clearAllTables()

fun addTune(name: String, actions: List<PluginAction> = emptyList()): Long = runBlocking {
    val id = database.pluginDao().insertPlugin(Plugin(name = name))
    database.pluginDao().insertActions(actions.map { it.copy(pluginId = id) })
    id
}

fun addWebPage(title: String, url: String, tuneId: Long? = null): Long = runBlocking {
    database.webPageDao().insertWebPage(WebPage(title = title, url = url, pluginId = tuneId))
}

fun clickRule(trigger: String, selector: String) = PluginAction(pluginId = 0, actionType = "click", targetSelector = selector, triggerType = trigger)

/** 앱이 여는 웹페이지를 기기 안에서 내준다 → 네트워크 없이도 열람 화면이 같은 페이지를 읽는다. */
class LocalPage(private val html: String) : Closeable {
    private val server = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
    val url = "http://127.0.0.1:${server.localPort}/"

    init {
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: continue
                // WebView 는 요청을 보내지 않는 연결을 미리 열기도 한다 → 연결마다 따로 처리하고 오래 기다리지 않는다.
                thread(isDaemon = true) { respond(socket) }
            }
        }
    }

    private fun respond(socket: Socket) = runCatching {
        socket.use {
            it.soTimeout = 5_000
            val reader = it.getInputStream().bufferedReader()
            while (!reader.readLine().isNullOrEmpty()) Unit
            val body = html.toByteArray()
            val head = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
            it.getOutputStream().apply { write(head.toByteArray()); write(body); flush() }
        }
    }

    override fun close() = server.close()
}
