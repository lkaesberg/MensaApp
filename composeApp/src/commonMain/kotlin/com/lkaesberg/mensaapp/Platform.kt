package com.lkaesberg.mensaapp

interface Platform {
    val name: String
    val isWeb: Boolean get() = false
}

expect fun getPlatform(): Platform