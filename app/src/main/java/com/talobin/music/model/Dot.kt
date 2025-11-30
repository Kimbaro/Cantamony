package com.talobin.music.parser.model

import com.tickaroo.tikxml.annotation.TextContent
import com.tickaroo.tikxml.annotation.Xml

@Xml(name = "dot")
data class Dot(
    @TextContent
    val content: String = ""
)