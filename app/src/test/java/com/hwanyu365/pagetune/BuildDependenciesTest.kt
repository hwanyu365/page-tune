package com.hwanyu365.pagetune

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildDependenciesTest {

    private val allowed = mapOf(
        "implementation" to setOf(
            "fileTree(libs)",
            "androidx.appcompat:appcompat",
            "androidx.core:core-ktx",
            "com.google.android.material:material",
            "androidx.recyclerview:recyclerview",
            "androidx.cardview:cardview",
            "androidx.viewpager2:viewpager2",
            "androidx.room:room-runtime",
            "androidx.room:room-ktx",
        ),
        "kapt" to setOf("androidx.room:room-compiler", "org.jetbrains.kotlin:kotlin-metadata-jvm"),
        "testImplementation" to setOf("junit:junit", "org.htmlunit:htmlunit", "org.json:json"),
        "androidTestImplementation" to setOf(
            "androidx.test:runner",
            "androidx.test.ext:junit",
            "androidx.test.espresso:espresso-core",
            "androidx.test.espresso:espresso-intents",
            "androidx.room:room-testing",
        ),
    )

    @Test
    fun `TC#127 app 의존성은 허용 목록의 설정과 좌표만 쓴다`() {
        assertEquals(emptyList<String>(), violations(File("build.gradle").readText().replace("\r\n", "\n")))
    }

    @Test
    fun `TC#127 목록 밖 설정·좌표와 해석할 수 없는 줄은 위반이다`() {
        val gradle = """
            dependencies {
                implementation 'androidx.core:core-ktx:1.0'
                implementation 'com.squareup.okhttp3:okhttp:4.12.0'
                api 'androidx.core:core-ktx:1.0'
                testImplementation 'androidx.room:room-runtime:2.8.5'
                implementation platform('com.example:bom:1')
                implementation libs.something
                def extra = dependencies.add('implementation', 'com.example:lib:1')
                def version = "1.0"
            }
        """.trimIndent()
        assertEquals(6, violations(gradle).size)
    }

    @Test
    fun `TC#127 dependencies 블록이 없으면 위반이다`() {
        assertTrue(violations("android {}").isNotEmpty())
    }

    private fun violations(gradle: String): List<String> {
        val block = Regex("""(?m)^dependencies \{\n(.*?)^}""", RegexOption.DOT_MATCHES_ALL).findAll(gradle).toList()
        if (block.size != 1) return listOf("dependencies 블록 ${block.size}개")
        return block.single().groupValues[1].lines().map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("//") && !VERSION_VARIABLE.matches(it) }
            .mapNotNull { line ->
                val config = line.substringBefore(' ')
                val coordinate = coordinateOf(line.substringAfter(' ').trim())
                line.takeUnless { coordinate != null && coordinate in allowed[config].orEmpty() }
            }
    }

    private companion object {
        // 버전 문자열만 담는 변수 → 의존성을 추가할 수 있는 다른 `def` 줄은 해석할 수 없는 줄로 센다.
        val VERSION_VARIABLE = Regex("""^def \w+ = "[\w.-]+"$""")
    }

    private fun coordinateOf(notation: String): String? {
        if (notation == "fileTree(dir: 'libs', include: ['*.jar'])") return "fileTree(libs)"
        val quoted = Regex("""^(['"])([^'"]+)\1$""").find(notation)?.groupValues?.get(2) ?: return null
        val parts = quoted.split(':')
        return if (parts.size == 3) "${parts[0]}:${parts[1]}" else null
    }
}
