package com.example.mensaapp

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform