package com.lkaesberg.mensaapp

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform