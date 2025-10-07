package com.lkaesberg.mensaapp

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

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
            // We only need the database (PostgREST) module for now
            install(Postgrest)
        }.also { cached = it }
    }
} 