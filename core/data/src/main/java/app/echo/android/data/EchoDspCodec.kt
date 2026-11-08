package app.echo.android.data

import app.echo.android.model.playback.EchoCompressorMode
import app.echo.android.model.playback.EchoDspSettings
import org.json.JSONObject

internal object EchoDspCodec {
    fun encode(value: EchoDspSettings): JSONObject = value.normalized.let { s -> JSONObject()
        .put("limiterEnabled", s.limiterEnabled).put("limiterCeilingDb", s.limiterCeilingDb)
        .put("crossfeedEnabled", s.crossfeedEnabled).put("crossfeedAmount", s.crossfeedAmount)
        .put("compressor", s.compressor.id).put("hrtfEnabled", s.hrtfEnabled)
        .put("hrtfProfile", s.hrtfProfile).put("hrtfAmount", s.hrtfAmount)
        .put("equalLoudnessEnabled", s.equalLoudnessEnabled).put("equalLoudnessReferenceDb", s.equalLoudnessReferenceDb)
        .put("equalLoudnessBassDb", s.equalLoudnessBassDb).put("equalLoudnessTrebleDb", s.equalLoudnessTrebleDb)
        .put("soundEffectEnabled", s.soundEffect.enabled).put("soundEffectPreset", s.soundEffect.preset.id) }

    fun decode(j: JSONObject): EchoDspSettings = EchoDspSettings(
        limiterEnabled = j.optBoolean("limiterEnabled"), limiterCeilingDb = j.optDouble("limiterCeilingDb", -1.0).toFloat(),
        crossfeedEnabled = j.optBoolean("crossfeedEnabled"), crossfeedAmount = j.optDouble("crossfeedAmount", 0.3).toFloat(),
        compressor = EchoCompressorMode.fromId(j.optString("compressor")), hrtfEnabled = j.optBoolean("hrtfEnabled"),
        hrtfProfile = j.optInt("hrtfProfile", 1), hrtfAmount = j.optDouble("hrtfAmount", 1.0).toFloat(),
        equalLoudnessEnabled = j.optBoolean("equalLoudnessEnabled"), equalLoudnessReferenceDb = j.optDouble("equalLoudnessReferenceDb", -10.0).toFloat(),
        equalLoudnessBassDb = j.optDouble("equalLoudnessBassDb", 6.0).toFloat(), equalLoudnessTrebleDb = j.optDouble("equalLoudnessTrebleDb", 3.0).toFloat(),
        soundEffect = app.echo.android.model.playback.EchoSoundEffectSettings.fromStored(j.optBoolean("soundEffectEnabled"), j.optString("soundEffectPreset")),
    ).normalized
}
