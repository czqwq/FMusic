package com.Lilith.FMusic.dango.shader.impls

import com.Lilith.FMusic.dango.shader.GlShader
class QuadShader : GlShader(
    "quad.vert",
    "quad.frag",
) {
    override fun uniformsFields(): Array<String> {
        return arrayOf(
            "u_Texture",
        )
    }

    fun setup(){
        setUniform1i("u_Texture", 0)
    }
}