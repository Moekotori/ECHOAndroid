package app.echo.android.usbaudio

/** Only repository-supported behavior belongs here. No inferred rules from another player's APK. */
data class UsbDeviceQuirkRule(
    val id: String,
    val vendorId: Int,
    val productId: Int? = null,
    val reselectStreamingAfterRateChange: Boolean = false,
    val reason: String,
    val verification: String,
)

data class UsbDeviceQuirkProfile(val matchedRules: List<UsbDeviceQuirkRule> = emptyList()) {
    val reselectStreamingAfterRateChange: Boolean
        get() = matchedRules.any { it.reselectStreamingAfterRateChange }
}

object UsbDeviceQuirks {
    private val rules = listOf(
        UsbDeviceQuirkRule(
            id = "teac-reselect-streaming-after-rate-change",
            vendorId = 0x0644,
            reselectStreamingAfterRateChange = true,
            reason = "Reapply the streaming alternate setting after programming the sample rate.",
            verification = "Preserves the existing vendor-wide ECHO workaround; not validated on every TEAC DAC.",
        ),
    )

    fun resolve(vendorId: Int, productId: Int): UsbDeviceQuirkProfile = UsbDeviceQuirkProfile(
        rules.filter { it.vendorId == vendorId && (it.productId == null || it.productId == productId) },
    )
}
