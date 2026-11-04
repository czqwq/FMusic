package com.Lilith.FMusic.dango.shader.impls.glow

import com.Lilith.FMusic.dango.shader.GlShader
class GlowUpsampleShader : GlShader(
    "outline/base.vert",
    "glow/upsample.frag",
) {
    override fun uniformsFields(): Array<String> {
        return arrayOf("u_Low", "u_High", "u_TexelSize", "u_Weight")
    }

    fun setup(lowWidth: Int, lowHeight: Int, weight: Float) {
        setUniform1i("u_Low", 0)
        setUniform1i("u_High", 1)
        setUniform2f("u_TexelSize", 1f / lowWidth, 1f / lowHeight)
        setUniform1f("u_Weight", weight)
    }
}
