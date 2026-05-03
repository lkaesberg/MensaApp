package com.lkaesberg.mensaapp

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.serialization.json.Json

/**
 * Singleton for accessing the shared [SupabaseClient] instance. The client is lazy
 * initialised on first access.
 */
object SupabaseProvider {

    private var cached: SupabaseClient? = null

    /**
     * Returns a singleton instance of [SupabaseClient]. Make sure you have filled the
     * credentials in [SupabaseConfig] before calling this in production.
     */
    fun client(): SupabaseClient {
        return cached ?: createSupabaseClient(
            supabaseUrl = SupabaseConfig.SUPABASE_URL,
            supabaseKey = SupabaseConfig.SUPABASE_ANON_KEY
        ) {
            // Tolerate columns the client doesn't model yet — required because the
            // backend schema can add columns ahead of corresponding data class updates.
            defaultSerializer = KotlinXSerializer(Json { ignoreUnknownKeys = true })
            install(Postgrest)
        }.also { cached = it }
    }
} 