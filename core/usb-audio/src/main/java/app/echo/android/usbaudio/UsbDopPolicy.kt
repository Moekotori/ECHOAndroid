package app.echo.android.usbaudio

/** USB PCM descriptors cannot establish DoP support. Require an explicit device profile. */
object UsbDopPolicy {
    fun permits(limits: Map<String, Int>, vendorId: Int, productId: Int, carrierRateHz: Int): Boolean {
        val limit = limits["$vendorId:$productId"] ?: return false
        val dsdRate = carrierRateHz.toLong() * 16
        return carrierRateHz in listOf(176_400, 352_800, 705_600, 1_411_200) &&
            limit in listOf(2_822_400, 5_644_800, 11_289_600, 22_579_200) && dsdRate <= limit
    }
}
