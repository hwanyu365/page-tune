package com.hwanyu365.pagetune

import java.io.DataInputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreListingTest {

    private val listings = File("src/main/play/listings")
    private val locales = listOf("en-US", "ko-KR")
    private val limits = mapOf("title.txt" to 30, "short-description.txt" to 80, "full-description.txt" to 4000)

    @Test
    fun `TC#401 listing 파일은 모든 언어에 있고 글자 수 제한 이하다`() {
        for (locale in locales) for ((name, max) in limits) {
            val f = File(listings, "$locale/$name")
            assertTrue("$f 없음", f.isFile)
            val len = f.readText().trim().length
            assertTrue("$f ${len}자 (최대 $max)", len in 1..max)
        }
    }

    @Test
    fun `TC#402 listing 은 광고 차단을 문구로 쓰지 않는다`() {
        val banned = listOf("ad block", "adblock", "광고 차단", "광고차단")
        for (locale in locales) for (name in limits.keys) {
            val text = File(listings, "$locale/$name").readText().lowercase()
            for (word in banned) assertFalse("$locale/$name 에 '$word'", word in text)
        }
    }

    @Test
    fun `TC#403 개인정보 처리방침은 한국어·영어 본문과 이슈 URL 을 갖고 이메일이 없다`() {
        val text = File("../docs/privacy.md").readText()
        assertTrue("# PageTune 개인정보 처리방침" in text)
        assertTrue("# PageTune Privacy Policy" in text)
        assertTrue("https://github.com/hwanyu365/page-tune/issues" in text)
        val dates = Regex("""(?:시행일|Effective date): (\d{4}-\d{2}-\d{2})""").findAll(text).map { it.groupValues[1] }.toList()
        assertEquals(2, dates.size)
        assertEquals(1, dates.toSet().size)
        assertFalse(Regex("""[\w.+-]+@[\w-]+\.[\w.]+""").containsMatchIn(text))
    }

    private fun section(text: String, heading: String) = text.substringAfter("## $heading\n").substringBefore("\n## ")

    @Test
    fun `TC#138 개인정보 처리방침의 기기 저장 절에 마지막으로 본 버전 설정값이 있다`() {
        val text = File("../docs/privacy.md").readText()
        assertTrue("마지막으로 본 앱 버전" in section(text, "기기에 저장하는 정보"))
        assertTrue("last app version you saw" in section(text, "Information stored on your device"))
    }

    @Test
    fun `TC#413 매니페스트의 권한·백업 설정이 방침 서술과 맞는다`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val permissions = Regex("""<uses-permission android:name="([^"]+)"""").findAll(manifest).map { it.groupValues[1] }.toList()
        assertEquals(listOf("android.permission.INTERNET"), permissions)

        assertTrue("allowBackup=\"true\"" in manifest)
        assertFalse("백업 제외 규칙이 생기면 방침 백업 항목을 고친다", "dataExtractionRules" in manifest || "fullBackupContent" in manifest)

        val policy = File("../docs/privacy.md").readText()
        assertTrue("Google 계정" in policy && "Google account" in policy)
        assertFalse("기기 밖으로 전송되지 않아요" in policy)
        assertFalse("not sent off your device" in policy)
    }

    @Test
    fun `TC#149 매니페스트는 다른 앱을 미리 조회하는 선언이 없다`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertFalse("<queries" in manifest)
        assertFalse("QUERY_ALL_PACKAGES" in manifest)
    }

    @Test
    fun `TC#512 임의의 http 사이트를 열 수 있게 평문 통신을 허용한다`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val application = Regex("""<application\s[^>]*>""").find(manifest)
        assertTrue("application 선언 없음", application != null)
        assertTrue(application!!.value, "android:usesCleartextTraffic=\"true\"" in application.value)
    }

    @Test
    fun `TC#513 자동 업로드는 internal 트랙에만 하고 공개 비율·승격을 지정하지 않는다`() {
        val play = File("build.gradle").readText().replace("\r\n", "\n").substringAfter("\nplay {").substringBefore("\n}")
        assertTrue(play, Regex("""\btrack\s*=\s*'internal'""").containsMatchIn(play))
        for (key in listOf("userFraction", "releaseStatus", "promoteTrack", "fromTrack", "updatePriority")) assertFalse("play 에 $key", key in play)
        val release = File("../.github/workflows/release.yml").readText()
        assertFalse("release.yml 이 promote 를 부름", Regex("""(?i)promote""").containsMatchIn(release))
    }

    @Test
    fun `TC#515 에뮬레이터 테스트는 릴리스 검증 워크플로에서만 돌고 업로드는 그 뒤에 한다`() {
        val verify = File("../.github/workflows/release-verify.yml").readText()
        for (key in listOf("workflow_dispatch:", "workflow_call:", "connectedDebugAndroidTest")) assertTrue("release-verify.yml 에 $key 없음", key in verify)
        assertTrue("API 36 에뮬레이터 아님", Regex("""api-level:\s*36\b""").containsMatchIn(verify))
        assertTrue("Pixel 8 Pro 프로필 아님", Regex("""profile:\s*pixel_8_pro\s""").containsMatchIn(verify))
        val release = File("../.github/workflows/release.yml").readText().replace("\r\n", "\n")
        val caller = Regex("""\n  ([\w-]+):\n    uses: \./\.github/workflows/release-verify\.yml\n""").find(release)
        assertTrue("release.yml 이 release-verify.yml 을 호출하지 않음", caller != null)
        val publish = release.substringAfter("\n  release:\n").split(Regex("""\n  \S""")).first()
        assertTrue("업로드 job 이 ${caller!!.groupValues[1]} 에 의존하지 않음", Regex("""needs:\s*\[?\s*${caller.groupValues[1]}\b""").containsMatchIn(publish))
        val ci = File("../.github/workflows/ci.yml").readText()
        assertFalse("ci.yml 이 에뮬레이터 테스트를 돌림", "connectedDebugAndroidTest" in ci || "android-emulator-runner" in ci)
        assertTrue("ci.yml 이 에뮬레이터 테스트를 컴파일하지 않음", "assembleDebugAndroidTest" in ci)
    }

    @Test
    fun `TC#517 에뮬레이터 테스트 라이브러리는 androidTestImplementation 으로만 둔다`() {
        val dependencies = File("build.gradle").readText().replace("\r\n", "\n").substringAfter("\ndependencies {").substringBefore("\n}")
        val lines = dependencies.lines().map { it.trim() }.filter { "androidx.test" in it }
        assertTrue("androidx.test 의존성 없음", lines.isNotEmpty())
        for (line in lines) assertTrue(line, line.startsWith("androidTestImplementation "))
    }

    @Test
    fun `TC#150 처리방침 공유 절은 링크·다운로드 주소를 다른 앱에 넘기는 것을 밝힌다`() {
        val policy = File("../docs/privacy.md").readText().replace("\r\n", "\n")
        fun section(title: String) = policy.substringAfter("## $title\n").substringBefore("\n## ")
        val ko = section("공유")
        assertTrue(ko, "다른 앱" in ko && "다운로드" in ko && "메일" in ko)
        val en = section("Sharing")
        assertTrue(en, "other app" in en && "download" in en && "email" in en)
    }

    @Test
    fun `TC#306 권한과 테스트 밖 의존성은 허용 목록 그대로다`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val permissions = Regex("""<uses-permission\b[^>]*android:name="([^"]+)"""").findAll(manifest).map { it.groupValues[1] }.toList()
        assertEquals(listOf("android.permission.INTERNET"), permissions)

        assertEquals(DEPENDENCY_RULE, allowedDependencies, declaredDependencies())
    }

    @Test
    fun `TC#211 compose 없이 허용 목록의 의존성만 쓴다`() {
        assertFalse(Regex("compose", RegexOption.IGNORE_CASE).containsMatchIn(File("build.gradle").readText()))
        assertEquals(DEPENDENCY_RULE, allowedDependencies, declaredDependencies())
    }

    private val allowedDependencies = setOf(
        "implementation fileTree(dir: 'libs', include: ['*.jar'])",
        "implementation androidx.appcompat:appcompat",
        "implementation androidx.core:core-ktx",
        "implementation com.google.android.material:material",
        "implementation androidx.recyclerview:recyclerview",
        "implementation androidx.cardview:cardview",
        "implementation androidx.viewpager2:viewpager2",
        "implementation androidx.room:room-runtime",
        "implementation androidx.room:room-ktx",
        "kapt androidx.room:room-compiler",
        "kapt org.jetbrains.kotlin:kotlin-metadata-jvm",
    )

    private fun declaredDependencies(): Set<String> {
        val block = File("build.gradle").readText().substringAfter("\ndependencies {\n").substringBefore("\n}")
        val coordinate = Regex("""^(\w+)\s*\(?\s*['"]([^:'"]+:[^:'"]+)(:[^'"]*)?['"]\s*\)?$""")
        return block.lines().map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("//") && !it.startsWith("def ") }
            .filterNot { it.startsWith("testImplementation ") || it.startsWith("androidTestImplementation ") }
            .map { line -> coordinate.find(line)?.let { "${it.groupValues[1]} ${it.groupValues[2]}" } ?: line }
            .toSet()
    }

    @Test
    fun `TC#307 설정 화면은 다른 앱이 열 수 없다`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val activity = Regex("""<activity\b[^>]*android:name="\.SettingsActivity"[^>]*>""").find(manifest)
        assertTrue("SettingsActivity 선언 없음", activity != null)
        assertTrue(activity!!.value, "android:exported=\"false\"" in activity.value)
    }

    @Test
    fun `TC#414 listing 은 백업 예외를 밝힌다`() {
        for ((locale, phrase) in listOf("ko-KR" to "Google 계정", "en-US" to "Google account")) {
            val text = File(listings, "$locale/full-description.txt").readText()
            assertTrue("$locale 전체 설명에 '$phrase' 없음", phrase in text)
        }
    }

    @Test
    fun `TC#415 listing 은 특정 사이트·커뮤니티를 가리키지 않는다`() {
        val issues = "https://github.com/hwanyu365/page-tune/issues"
        val names = listOf("ruliweb", "dcinside", "clien", "ppomppu", "fmkorea", "theqoo", "inven", "bobaedream", "mlbpark", "todayhumor", "instiz", "naver", "daum")
        val urlPatterns = listOf("www.", ".com", ".co.kr", ".net", ".kr", "http")
        for (locale in locales) for (name in limits.keys) {
            val text = File(listings, "$locale/$name").readText().replace(issues, "").lowercase()
            for (w in names + urlPatterns) assertFalse("$locale/$name 에 '$w'", w in text)
        }
    }

    private data class Png(val width: Int, val height: Int, val bitDepth: Int, val colorType: Int)

    private fun png(f: File): Png = DataInputStream(f.inputStream()).use { input ->
        val signature = ByteArray(8).also { input.readFully(it) }
        assertTrue("$f PNG 아님", signature.contentEquals(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)))
        input.readInt()
        assertEquals("$f 첫 청크", "IHDR", String(ByteArray(4).also { input.readFully(it) }, Charsets.US_ASCII))
        Png(input.readInt(), input.readInt(), input.readUnsignedByte(), input.readUnsignedByte())
    }

    private fun graphics(locale: String, type: String): List<File> =
        File(listings, "$locale/graphics/$type").listFiles().orEmpty().filter { it.isFile }.sortedBy { it.name }

    @Test
    fun `TC#416 스토어 아이콘은 언어마다 512x512 RGBA PNG 한 장이다`() {
        for (locale in locales) {
            val files = graphics(locale, "icon")
            assertEquals("$locale icon", 1, files.size)
            assertEquals(Png(512, 512, 8, 6), png(files.single()))
        }
    }

    @Test
    fun `TC#417 대표 이미지는 언어마다 1024x500 알파 없는 PNG 한 장이다`() {
        for (locale in locales) {
            val files = graphics(locale, "feature-graphic")
            assertEquals("$locale feature-graphic", 1, files.size)
            assertEquals(Png(1024, 500, 8, 2), png(files.single()))
        }
    }

    @Test
    fun `TC#418 폰 스크린샷은 언어마다 4~8장이고 Play 크기·비율 제한 안이다`() {
        for (locale in locales) {
            val files = graphics(locale, "phone-screenshots")
            assertTrue("$locale 스크린샷 ${files.size}장", files.size in 4..8)
            for (f in files) {
                val p = png(f)
                assertEquals("$f 색 형식", 2, p.colorType)
                assertEquals("$f 비트 깊이", 8, p.bitDepth)
                val (short, long) = listOf(p.width, p.height).sorted()
                assertTrue("$f ${p.width}x${p.height}", short >= 320 && long <= 3840 && long <= short * 2)
            }
        }
    }

    private companion object {
        const val DEPENDENCY_RULE = "의존성을 더하거나 빼면 그 이유를 PR 에 적고 이 목록을 고친다"
    }
}
