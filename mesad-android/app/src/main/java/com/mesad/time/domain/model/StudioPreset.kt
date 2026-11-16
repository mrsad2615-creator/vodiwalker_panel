package com.mesad.time.domain.model

/**
 * Clock Studio custom preset — material, light, color, depth, scale.
 */
data class StudioPreset(
    val id: String = "default",
    val name: String = "MESAD Signature",
    val engine: ClockEngine = ClockEngine.ORBIT,
    val primaryColor: Long = 0xFF00E5FF,      // Electric Cyan
    val accentColor: Long = 0xFFE6C79C,       // Warm Champagne
    val ringColor: Long = 0xFFC8D0DC,         // Titanium Silver
    val coreColor: Long = 0xFF0B2D48,         // Deep Midnight
    val materialFinish: MaterialFinish = MaterialFinish.BRUSHED_TITANIUM,
    val lightIntensity: Float = 1.0f,         // 0.0 - 2.0
    val lightTemperature: Float = 0.65f,      // 0.0 (cool) - 1.0 (warm)
    val depth: Float = 1.0f,                  // 0.5 - 2.0 (ring separation)
    val scale: Float = 1.0f,                  // 0.7 - 1.3 (clock size)
    val glowIntensity: Float = 0.5f,          // 0.0 - 1.0 (emission)
    val rotationSpeed: Float = 1.0f,          // 0.0 - 3.0 (orbital velocity)
    val isDefault: Boolean = true
)

enum class MaterialFinish {
    BRUSHED_TITANIUM,
    POLISHED_TITANIUM,
    MATTE_OBSIDIAN,
    CHAMPAGNE_GOLD,
    SAPPHIRE_GLASS
}
