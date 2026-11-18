package ir.aegis.core.ai

import java.security.SecureRandom
import kotlin.math.log2

enum class PasswordGrade {
    CRITICAL_WEAK,
    WEAK,
    FAIR,
    STRONG,
    FORTRESS,
}

data class PasswordAuditReport(
    val entropyBits: Int,
    val scorePercent: Int,
    val grade: PasswordGrade,
    val warningsFa: List<String>,
    val warningsEn: List<String>,
)

object PasswordSecurityEngine {

    private val commonPasswords = setOf(
        "123456", "12345678", "123456789", "password", "qwerty", "111111", "abc123",
        "admin", "letmein", "000000", "pass123", "123123", "987654321",
    )

    private val random = SecureRandom()

    fun auditPassword(password: String): PasswordAuditReport {
        if (password.isEmpty()) {
            return PasswordAuditReport(
                entropyBits = 0,
                scorePercent = 0,
                grade = PasswordGrade.CRITICAL_WEAK,
                warningsFa = listOf("رمز عبور خالی است."),
                warningsEn = listOf("Password is empty."),
            )
        }

        val warningsFa = mutableListOf<String>()
        val warningsEn = mutableListOf<String>()
        var penalty = 0

        if (password.lowercase() in commonPasswords) {
            warningsFa += "این رمز در فهرست رمزهای لو رفته و پرکاربرد قرار دارد."
            warningsEn += "Password appears in top breached password lists."
            penalty += 60
        }
        if (password.length < 10) {
            warningsFa += "طول رمز کمتر از ۱۰ کاراکتر است (توصیه استاندارد: حداقل ۱۲ کاراکتر)."
            warningsEn += "Length below 10 characters (recommended >= 12)."
            penalty += 22
        }
        val hasLower = password.any { it.isLowerCase() }
        val hasUpper = password.any { it.isUpperCase() }
        val hasDigit = password.any { it.isDigit() }
        val hasSymbol = password.any { !it.isLetterOrDigit() }

        var pool = 0
        if (hasLower) pool += 26
        if (hasUpper) pool += 26
        if (hasDigit) pool += 10
        if (hasSymbol) pool += 32
        if (pool == 0) pool = 16

        if ((if (hasLower) 1 else 0) + (if (hasUpper) 1 else 0) + (if (hasDigit) 1 else 0) + (if (hasSymbol) 1 else 0) < 3) {
            warningsFa += "تنوع کاراکترها کم است (از حروف بزرگ، کوچک، عدد و نماد ترکیب کنید)."
            warningsEn += "Low character diversity (mix uppercase, lowercase, digits, and symbols)."
            penalty += 15
        }

        val uniqueRatio = password.toSet().size.toDouble() / password.length
        if (uniqueRatio < 0.55) {
            warningsFa += "تکرار بیش از حد کاراکترها آنتروپی واقعی را کاهش می‌دهد."
            warningsEn += "Excessive repeated characters reduce effective entropy."
            penalty += 16
        }

        val rawEntropy = (password.length * log2(pool.toDouble())).toInt()
        val effectiveEntropy = (rawEntropy - penalty).coerceIn(0, 140)
        val score = ((effectiveEntropy / 95.0) * 100.0).toInt().coerceIn(0, 100)

        val grade = when {
            score >= 85 -> PasswordGrade.FORTRESS
            score >= 65 -> PasswordGrade.STRONG
            score >= 42 -> PasswordGrade.FAIR
            score >= 20 -> PasswordGrade.WEAK
            else -> PasswordGrade.CRITICAL_WEAK
        }

        return PasswordAuditReport(
            entropyBits = effectiveEntropy,
            scorePercent = score,
            grade = grade,
            warningsFa = warningsFa,
            warningsEn = warningsEn,
        )
    }

    fun generateStrongPassword(length: Int = 18): String {
        val upper = "ABCDEFGHJKLMNPQRSTUVWXYZ"
        val lower = "abcdefghijkmnopqrstuvwxyz"
        val digits = "23456789"
        val symbols = "!@#\$%^&*-_=+"
        val all = upper + lower + digits + symbols
        val targetLen = length.coerceIn(12, 32)
        val chars = mutableListOf(
            upper[random.nextInt(upper.length)],
            lower[random.nextInt(lower.length)],
            digits[random.nextInt(digits.length)],
            symbols[random.nextInt(symbols.length)],
        )
        while (chars.size < targetLen) {
            chars += all[random.nextInt(all.length)]
        }
        for (i in chars.lastIndex downTo 1) {
            val j = random.nextInt(i + 1)
            val tmp = chars[i]
            chars[i] = chars[j]
            chars[j] = tmp
        }
        return chars.joinToString("")
    }
}
