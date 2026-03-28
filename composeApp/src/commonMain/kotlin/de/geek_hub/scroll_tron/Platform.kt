package de.geek_hub.scroll_tron

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform