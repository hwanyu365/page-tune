import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ReleaseVersionTest {

    @Test
    fun `TC#1 태그에서 versionName 과 versionCode 를 만든다`() {
        assertEquals(ReleaseVersion("1.2.3", 10203), ReleaseVersion.fromTag("v1.2.3"))
        assertEquals(ReleaseVersion("12.0.99", 120099), ReleaseVersion.fromTag("v12.0.99"))
    }

    @Test
    fun `TC#2 형식이 다르거나 자릿수를 넘는 태그는 거부한다`() {
        listOf("1.2.3", "v1.2", "v1.2.3-rc1", "v1.100.0", "v1.0.100", "v0.0.0").forEach { tag ->
            assertThrows(tag, IllegalArgumentException::class.java) { ReleaseVersion.fromTag(tag) }
        }
    }

    @Test
    fun `TC#3 태그가 없으면 개발 버전으로 빌드한다`() {
        assertEquals(ReleaseVersion("0.0.0-dev", 1), ReleaseVersion.fromTag(null))
        assertEquals(ReleaseVersion("0.0.0-dev", 1), ReleaseVersion.fromTag(""))
    }
}
