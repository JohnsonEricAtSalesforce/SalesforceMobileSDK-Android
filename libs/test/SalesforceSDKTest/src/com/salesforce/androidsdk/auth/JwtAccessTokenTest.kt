package com.salesforce.androidsdk.auth

import org.junit.Assert
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Locale
import java.util.stream.Collectors
import java.util.stream.Stream

class JwtAccessTokenTest {

    companion object {
        private const val HEADER = """{"tnk":"some-tnk","ver":"1.0","kid":"some-kid","tty":"sfdc-core-token","typ":"JWT","alg":"RS256"}"""
        private const val PAYLOAD = """{"scp":"refresh_token web api","aud":["https://mobilesdkatsdb6.test1.my.pc-rnd.salesforce.com"],"sub":"uid:some-uid","nbf":1730386620,"mty":"oauth","sfi":"some-sfi","roles":[],"iss":"https://mobilesdkatsdb6.test1.my.pc-rnd.salesforce.com","hsc":false,"exp":1730386695,"iat":1730386635,"client_id":"some-client-id"}"""
        private const val SIGNATURE = "FAKE_SIGNATURE"
        private val TEST_RAW_JWT: String = Stream.of(HEADER, PAYLOAD, SIGNATURE)
            .map { s -> Base64.getEncoder().encodeToString(s.toByteArray()) }
            .collect(Collectors.joining("."))
    }

    @Test
    fun testDecodeValidJwtAndParseHeader() {
        val decodedJwt = JwtAccessToken(TEST_RAW_JWT)
        Assert.assertNotNull(decodedJwt)
        val jwtHeader = decodedJwt.header
        Assert.assertNotNull(jwtHeader)

        Assert.assertEquals("RS256", jwtHeader.algorithn)
        Assert.assertEquals("JWT", jwtHeader.type)
        Assert.assertEquals("some-kid", jwtHeader.keyId)
        Assert.assertEquals("sfdc-core-token", jwtHeader.tokenType)
        Assert.assertEquals("some-tnk", jwtHeader.tenantKey)
        Assert.assertEquals("1.0", decodedJwt.header.version)
    }

    @Test
    fun testDecodeValidJwtAndParsePayload() {
        val decodedJwt = JwtAccessToken(TEST_RAW_JWT)
        Assert.assertNotNull(decodedJwt)
        val jwtPayload = decodedJwt.payload
        Assert.assertNotNull(jwtPayload)

        Assert.assertEquals(listOf("https://mobilesdkatsdb6.test1.my.pc-rnd.salesforce.com"), jwtPayload.audience)
        Assert.assertEquals(1730386695, jwtPayload.expirationTime!!.toInt())
        Assert.assertEquals("https://mobilesdkatsdb6.test1.my.pc-rnd.salesforce.com", jwtPayload.issuer)
        Assert.assertEquals(1730386620, jwtPayload.notBeforeTime!!.toInt())
        Assert.assertEquals("uid:some-uid", jwtPayload.subject)
        Assert.assertEquals("refresh_token web api", jwtPayload.scopes)
        Assert.assertEquals("some-client-id", jwtPayload.clientId)
    }

    @Test
    fun testExpirationDate() {
        val decodedJwt = JwtAccessToken(TEST_RAW_JWT)
        Assert.assertNotNull(decodedJwt)
        val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        Assert.assertEquals("2024-10-31 07:58:15", dateFormatter.format(decodedJwt.expirationDate()))
    }

    @Test
    fun testInvalidJwt() {
        val invalidRawJwt = "invalid-jwt-string"

        try {
            JwtAccessToken(invalidRawJwt)
            Assert.fail("Expected illegal argument exception")
        } catch (e: IllegalArgumentException) {
            Assert.assertEquals("Wrong exception thrown", "Invalid JWT format", e.message)
        }
    }
}
