package com.assistant.core.transcription.database

import androidx.room.*

/**
 * DAO for transcription provider configurations
 */
@Dao
interface TranscriptionDao {

    // === Provider Configurations ===

    @Query("SELECT * FROM transcription_provider_configs ORDER BY providerId ASC")
    suspend fun getAllProviderConfigs(): List<TranscriptionProviderConfigEntity>

    @Query("SELECT * FROM transcription_provider_configs WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveProviderConfig(): TranscriptionProviderConfigEntity?

    @Query("SELECT * FROM transcription_provider_configs WHERE providerId = :providerId")
    suspend fun getProviderConfig(providerId: String): TranscriptionProviderConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProviderConfig(config: TranscriptionProviderConfigEntity)

    @Update
    suspend fun updateProviderConfig(config: TranscriptionProviderConfigEntity)

    @Query("UPDATE transcription_provider_configs SET isActive = 0")
    suspend fun deactivateAllProviders()

    @Query("UPDATE transcription_provider_configs SET isActive = 1 WHERE providerId = :providerId")
    suspend fun activateProvider(providerId: String)

    @Delete
    suspend fun deleteProviderConfig(config: TranscriptionProviderConfigEntity)

    @Query("DELETE FROM transcription_provider_configs WHERE providerId = :providerId")
    suspend fun deleteProviderConfigById(providerId: String)
}
