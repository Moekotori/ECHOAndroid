package app.echo.android.design

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.asComposeRenderEffect

/** Size-dependent effects are built in drawWithCache, never once per animation frame. */
@RequiresApi(31)
internal fun glassBackdropEffect(
    width: Float,
    height: Float,
    inset: Float,
    corner: Float,
    blur: Float,
    refraction: Float,
): androidx.compose.ui.graphics.RenderEffect {
    val softened = RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP)
    return if (Build.VERSION.SDK_INT >= 33) {
        refractedGlassEffect(width, height, inset, corner, refraction, softened).asComposeRenderEffect()
    } else softened.asComposeRenderEffect()
}

@RequiresApi(33)
private fun refractedGlassEffect(
    width: Float,
    height: Float,
    inset: Float,
    corner: Float,
    refraction: Float,
    softened: RenderEffect,
): RenderEffect {
    val shader = RuntimeShader(GlassRefractionShader)
    shader.setFloatUniform("extent", width, height)
    shader.setFloatUniform("inset", inset)
    shader.setFloatUniform("corner", corner)
    shader.setFloatUniform("bend", refraction)
    return RenderEffect.createChainEffect(
        RenderEffect.createRuntimeShaderEffect(shader, "backdrop"),
        softened,
    )
}

// Rounded-rectangle distance and normal concentrate lens distortion at the rim.
// The padded input supplies real neighbouring pixels for both blur and refraction.
private const val GlassRefractionShader = """
uniform shader backdrop;
uniform float2 extent;
uniform float inset;
uniform float corner;
uniform float bend;
half4 main(float2 xy) {
    float2 p = xy - float2(inset) - extent * 0.5;
    float2 q = abs(p) - (extent * 0.5 - float2(corner));
    float2 outside = max(q, float2(0.0));
    float distance = length(outside) + min(max(q.x, q.y), 0.0) - corner;
    float2 normal = length(outside) > 0.001
        ? normalize(outside) * sign(p)
        : (q.x > q.y ? float2(sign(p.x), 0.0) : float2(0.0, sign(p.y)));
    float edge = 1.0 - smoothstep(0.0, max(corner * 0.8, 1.0), -distance);
    return backdrop.eval(xy - normal * bend * edge * edge);
}
"""
