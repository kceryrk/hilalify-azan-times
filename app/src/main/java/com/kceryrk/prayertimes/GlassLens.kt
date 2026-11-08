package com.kceryrk.prayertimes

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.asComposeRenderEffect

/**
 * Edge-only refraction for a rounded-rect glass surface. The content (the live page backdrop) is
 * passed through unchanged in the interior and is bent only inside a band of [bandPx] along the
 * edge, so icons and the background stay sharp except at the bevel.
 */
@RequiresApi(33)
class GlassLens {
    private val shader = RuntimeShader(SRC)

    /**
     * Refracts [content] only within [bandPx] of the rounded rect (centre [cx],[cy], half-size [hw],[hh]).
     * If [blurPx] > 0, the content is blurred before refraction. Uniforms are captured when the
     * RenderEffect is created, so a fresh effect is built per call.
     */
    fun effectFor(
        cx: Float, cy: Float, hw: Float, hh: Float,
        radiusPx: Float,
        bandPx: Float,
        strengthPx: Float,
        blurPx: Float = 0f
    ): androidx.compose.ui.graphics.RenderEffect {
        shader.setFloatUniform("uRect", cx, cy, hw, hh)
        shader.setFloatUniform("uRadius", radiusPx)
        shader.setFloatUniform("uBand", bandPx)
        shader.setFloatUniform("uStrength", strengthPx)
        val lensEffect = RenderEffect.createRuntimeShaderEffect(shader, "content")
        val effect = if (blurPx > 0f) {
            // The outer effect receives the blurred result of the inner one.
            RenderEffect.createChainEffect(
                lensEffect,
                RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
            )
        } else lensEffect
        return effect.asComposeRenderEffect()
    }

    private companion object {
        const val SRC = """
            uniform shader content;
            uniform float4 uRect;
            uniform float uRadius;
            uniform float uBand;
            uniform float uStrength;

            half4 main(float2 p) {
                float2 c = uRect.xy;
                float2 h = uRect.zw;
                float2 q = abs(p - c) - (h - float2(uRadius));
                float sd = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - uRadius;
                // Only the inside of the rounded rect is refracted; the outside edge stays untouched.
                float a = -sd;
                if (a < 0.0 || a >= uBand || uStrength <= 0.0) {
                    return content.eval(p);
                }

                float t = 1.0 - a / uBand;
                float2 qp = max(q, 0.0);
                float2 n;
                if (length(qp) > 1e-3) {
                    n = normalize(qp) * sign(p - c);
                } else {
                    n = q.x > q.y ? float2(sign(p.x - c.x), 0.0) : float2(0.0, sign(p.y - c.y));
                }

                return content.eval(p - n * uStrength * t * t);
            }
        """
    }
}
