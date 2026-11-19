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
import javax.net.ssl.SSLContext
import kotlin.concurrent.thread

val database: AppDatabase get() = AppDatabase.getDatabase(targetContext)

fun clearData() = database.clearAllTables()

fun addTune(name: String, actions: List<PluginAction> = emptyList(), testUrl: String? = null): Long = runBlocking {
    val id = database.pluginDao().insertPlugin(Plugin(name = name, testUrl = testUrl))
    database.pluginDao().insertActions(actions.map { it.copy(pluginId = id) })
    id
}

fun addWebPage(title: String, url: String, tuneId: Long? = null): Long = runBlocking {
    database.webPageDao().insertWebPage(WebPage(title = title, url = url, pluginId = tuneId))
}

fun hideRule(id: String) = PluginAction(pluginId = 0, actionType = "hidden", targetSelector = """{"selector":"#$id","tagName":"div","id":"$id","attributes":{}}""")

fun colorRule(id: String, css: String) = PluginAction(pluginId = 0, actionType = "color", targetSelector = """{"selector":"#$id","tagName":"div","id":"$id","attributes":{}}""", styleCss = css)

fun rulesOf(tuneId: Long) = runBlocking { database.pluginDao().getPluginWithActions(tuneId)?.actions.orEmpty() }
    .sortedBy { it.id }.map { Triple(it.actionType, it.targetSelector, it.styleCss) }

fun clickRule(trigger: String, selector: String) = PluginAction(pluginId = 0, actionType = "click", targetSelector = selector, triggerType = trigger)

data class Reply(val status: Int = 200, val body: String = "", val headers: Map<String, String> = mapOf("Content-Type" to "text/html; charset=utf-8"))

/** 앱이 여는 웹페이지를 기기 안에서 내준다 → 네트워크 없이도 열람 화면이 같은 페이지를 읽는다. */
class LocalPage(
    private val html: String,
    port: Int = 0,
    private val routes: Map<String, Reply> = emptyMap(),
    tls: SSLContext? = null,
    host: String = "127.0.0.1",
) : Closeable {
    private val server = (tls?.serverSocketFactory?.createServerSocket() ?: ServerSocket())
        .apply { reuseAddress = true; bind(java.net.InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 16) }
    val url = "${if (tls == null) "http" else "https"}://$host:${server.localPort}/"

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
            val path = reader.readLine()?.split(" ")?.getOrNull(1) ?: return@use
            while (!reader.readLine().isNullOrEmpty()) Unit
            val reply = routes[path] ?: Reply(body = html)
            val body = reply.body.toByteArray()
            val head = buildString {
                append("HTTP/1.1 ${reply.status} X\r\n")
                reply.headers.forEach { (name, value) -> append("$name: $value\r\n") }
                append("Content-Length: ${body.size}\r\nConnection: close\r\n\r\n")
            }
            it.getOutputStream().apply { write(head.toByteArray()); write(body); flush() }
        }
    }

    override fun close() = server.close()
}
