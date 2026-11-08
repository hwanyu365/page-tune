package com.hwanyu365.pagetune.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainAvatarTest {

    @Test
    fun `TC#30 www 를 뗀 호스트와 첫 글자 대문자`() {
        val a = DomainAvatar.of("https://www.example.org/a", "t")

        assertEquals("example.org", a.host)
        assertEquals("E", a.letter)
        assertEquals(Math.floorMod("example.org".hashCode(), 8), a.colorIndex)
    }

    @Test
    fun `TC#30 m 과 www 는 같은 글자·색`() {
        val m = DomainAvatar.of("https://m.example.org/x", "a")
        val www = DomainAvatar.of("http://www.example.org", "b")

        assertEquals(www.letter, m.letter)
        assertEquals(www.colorIndex, m.colorIndex)
        assertEquals("example.org", m.host)
    }

    @Test
    fun `TC#30 scheme 이 없으면 https 로 보고 호스트를 구한다`() {
        val a = DomainAvatar.of("example.net/x", "t")

        assertEquals("example.net", a.host)
        assertEquals("E", a.letter)
    }

    @Test
    fun `TC#30 대문자·포트·사용자 정보는 호스트 표기에 없다`() {
        assertEquals("example.org", DomainAvatar.of("https://user@WWW.Example.ORG:8080/p?q#f", "t").host)
    }

    @Test
    fun `TC#30 IDN 은 유니코드 첫 글자`() {
        val a = DomainAvatar.of("https://xn--3e0b707e.kr/", "t")

        assertEquals("한국.kr", a.host)
        assertEquals("한", a.letter)
    }

    @Test
    fun `TC#30 IP 주소는 첫 숫자`() {
        assertEquals("1", DomainAvatar.of("http://192.168.0.1/", "t").letter)
    }

    @Test
    fun `TC#30 호스트가 없으면 대체 이름의 첫 글자와 그 이름의 색`() {
        val a = DomainAvatar.of(null, "뉴스")

        assertEquals("", a.host)
        assertEquals("뉴", a.letter)
        assertEquals(Math.floorMod("뉴스".hashCode(), 8), a.colorIndex)
        assertEquals("뉴", DomainAvatar.of("", "뉴스").letter)
    }

    @Test
    fun `TC#30 이모지로 시작하면 첫 문자·숫자`() {
        assertEquals("P", DomainAvatar.of("", "🎉 party").letter)
    }

    @Test
    fun `TC#30 호스트·이름 모두 문자가 없으면 글자 없음`() {
        assertNull(DomainAvatar.of("", "").letter)
        assertNull(DomainAvatar.of(null, "🎉 !").letter)
    }

    @Test
    fun `TC#30 hashCode 가 음수여도 색 번호는 0~7`() {
        assertTrue("polygenelubricants".hashCode() < 0)
        val a = DomainAvatar.of("https://polygenelubricants", "t")

        assertTrue(a.colorIndex in 0..7)
        assertEquals(Math.floorMod("polygenelubricants".hashCode(), 8), a.colorIndex)
    }
}
