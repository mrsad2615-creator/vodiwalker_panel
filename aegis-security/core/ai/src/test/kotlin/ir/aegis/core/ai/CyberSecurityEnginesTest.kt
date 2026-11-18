package ir.aegis.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CyberSecurityEnginesTest {

    @Test
    fun threatEngineDetectsTrojanOverlayAndCleanPlayApp() {
        val trojan = InstalledAppInput(
            packageName = "com.sana.eblagh.viewer",
            appLabel = "Sana Eblagh",
            installerPackage = null,
            isSystemApp = false,
            isDebuggable = true,
            isTestOnly = false,
            usesCleartextTraffic = true,
            signingCertSha256 = "e89b158e4bcf988ebd09eb83f5378e87c4bd3e7b",
            signerSubject = "CN=Android Debug",
            requestedPermissions = setOf(
                "android.permission.BIND_ACCESSIBILITY_SERVICE",
                "android.permission.SYSTEM_ALERT_WINDOW",
                "android.permission.READ_SMS",
                "android.permission.RECEIVE_SMS",
                "android.permission.REQUEST_INSTALL_PACKAGES",
                "android.permission.INTERNET",
            ),
            exportedComponentsCount = 2,
            targetSdk = 33,
        )
        val trojanRes = ThreatSignatureEngine.analyzeInstalledApp(trojan)
        assertTrue(trojanRes.riskLevel == ThreatRiskLevel.CRITICAL)
        assertTrue(trojanRes.riskScore >= 85)
        assertTrue(trojanRes.isSideloaded)
        assertTrue(trojanRes.findings.any { it.code == "ACCESSIBILITY_OVERLAY_COMBO" })
        assertTrue(trojanRes.findings.any { it.code == "SMS_OTP_EXFIL" })

        val cleanApp = InstalledAppInput(
            packageName = "org.example.calculator",
            appLabel = "Calculator",
            installerPackage = "com.android.vending",
            isSystemApp = false,
            isDebuggable = false,
            isTestOnly = false,
            usesCleartextTraffic = false,
            signingCertSha256 = "1234567890abcdef1234567890abcdef",
            signerSubject = "CN=Example Org",
            requestedPermissions = emptySet(),
            exportedComponentsCount = 0,
            targetSdk = 35,
        )
        val cleanRes = ThreatSignatureEngine.analyzeInstalledApp(cleanApp)
        assertEquals(ThreatRiskLevel.CLEAN, cleanRes.riskLevel)
        assertEquals(0, cleanRes.riskScore)
    }

    @Test
    fun payloadScannerDetectsDoubleExtensionAndEicarHash() {
        val fakePdf = ThreatSignatureEngine.scanLocalPayloadFile("eblagh_court_notice.pdf.apk", 420_000L, "abcd")
        assertEquals(ThreatRiskLevel.CRITICAL, fakePdf.riskLevel)

        val eicar = ThreatSignatureEngine.scanLocalPayloadFile(
            "test.txt",
            68L,
            "275a021bbfb6489e54d471899f7db9d1663fc695ec2fe2a2c4538aabf651fd0f",
        )
        assertEquals(ThreatRiskLevel.CRITICAL, eicar.riskLevel)
    }

    @Test
    fun phishingAnalyzerFlagsFakeSanaAndBankUrlsWhileAllowingLegitDomains() {
        val scam = PhishingAnalyzer.analyzeTextOrUrl("ابلاغیه فوری ثنا: http://sana-adliran-portal.xyz/eblagh.apk")
        assertEquals(PhishingVerdict.MALICIOUS_PHISHING, scam.verdict)
        assertTrue(scam.riskScore >= 60)

        val legit = PhishingAnalyzer.analyzeTextOrUrl("https://shaparak.ir")
        assertEquals(PhishingVerdict.SAFE, legit.verdict)
    }

    @Test
    fun passwordEngineMeasuresEntropyAndGeneratesFortressPasswords() {
        val weak = PasswordSecurityEngine.auditPassword("123456")
        assertEquals(PasswordGrade.CRITICAL_WEAK, weak.grade)

        val generated = PasswordSecurityEngine.generateStrongPassword(18)
        assertEquals(18, generated.length)
        val strong = PasswordSecurityEngine.auditPassword(generated)
        assertTrue(strong.grade == PasswordGrade.FORTRESS || strong.grade == PasswordGrade.STRONG)
    }
}
