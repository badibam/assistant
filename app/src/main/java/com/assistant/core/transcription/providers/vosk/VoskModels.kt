package com.assistant.core.transcription.providers.vosk

import com.assistant.core.transcription.models.TranscriptionModel

/**
 * Official Vosk models registry
 * Source: https://alphacephei.com/vosk/models
 *
 * Models are organized by language code (ISO 639-1)
 * Each model has size and quality indicators
 */
object VoskModels {

    /**
     * All available Vosk models
     * Grouped by language, sorted by quality (small → large)
     */
    val ALL_MODELS = listOf(
        // Arabic (ar)
        TranscriptionModel(
            id = "vosk-model-ar-mgb2-0.4",
            name = "Arabic",
            language = "ar",
            size = 318_000_000,
            quality = "medium"
        ),

        // Catalan (ca)
        TranscriptionModel(
            id = "vosk-model-small-ca-0.4",
            name = "Catalan (Small)",
            language = "ca",
            size = 42_000_000,
            quality = "small"
        ),

        // Chinese (cn)
        TranscriptionModel(
            id = "vosk-model-small-cn-0.22",
            name = "Chinese (Small)",
            language = "cn",
            size = 42_000_000,
            quality = "small"
        ),
        TranscriptionModel(
            id = "vosk-model-cn-0.22",
            name = "Chinese",
            language = "cn",
            size = 1_300_000_000,
            quality = "large"
        ),

        // Czech (cs)
        TranscriptionModel(
            id = "vosk-model-small-cs-0.4-rhasspy",
            name = "Czech (Small)",
            language = "cs",
            size = 44_000_000,
            quality = "small"
        ),
        TranscriptionModel(
            id = "vosk-model-cs-0.4-rhasspy",
            name = "Czech",
            language = "cs",
            size = 220_000_000,
            quality = "large"
        ),

        // German (de)
        TranscriptionModel(
            id = "vosk-model-small-de-0.15",
            name = "German (Small)",
            language = "de",
            size = 45_000_000,
            quality = "small"
        ),
        TranscriptionModel(
            id = "vosk-model-de-0.21",
            name = "German",
            language = "de",
            size = 1_900_000_000,
            quality = "large"
        ),

        // English (en)
        TranscriptionModel(
            id = "vosk-model-small-en-us-0.15",
            name = "English US (Small)",
            language = "en",
            size = 40_000_000,
            quality = "small"
        ),
        TranscriptionModel(
            id = "vosk-model-en-us-0.22",
            name = "English US",
            language = "en",
            size = 1_800_000_000,
            quality = "large"
        ),
        TranscriptionModel(
            id = "vosk-model-en-us-0.22-lgraph",
            name = "English US (Large Graph)",
            language = "en",
            size = 128_000_000,
            quality = "medium"
        ),
        TranscriptionModel(
            id = "vosk-model-en-in-0.5",
            name = "English Indian",
            language = "en",
            size = 1_000_000_000,
            quality = "large"
        ),

        // Spanish (es)
        TranscriptionModel(
            id = "vosk-model-small-es-0.42",
            name = "Spanish (Small)",
            language = "es",
            size = 39_000_000,
            quality = "small"
        ),
        TranscriptionModel(
            id = "vosk-model-es-0.42",
            name = "Spanish",
            language = "es",
            size = 1_400_000_000,
            quality = "large"
        ),

        // Farsi (fa)
        TranscriptionModel(
            id = "vosk-model-small-fa-0.4",
            name = "Farsi (Small)",
            language = "fa",
            size = 47_000_000,
            quality = "small"
        ),
        TranscriptionModel(
            id = "vosk-model-fa-0.5",
            name = "Farsi",
            language = "fa",
            size = 1_000_000_000,
            quality = "large"
        ),

        // French (fr)
        TranscriptionModel(
            id = "vosk-model-small-fr-0.22",
            name = "French (Small)",
            language = "fr",
            size = 41_000_000,
            quality = "small"
        ),
        TranscriptionModel(
            id = "vosk-model-fr-0.22",
            name = "French",
            language = "fr",
            size = 1_400_000_000,
            quality = "large"
        ),

        // Hindi (hi)
        TranscriptionModel(
            id = "vosk-model-small-hi-0.22",
            name = "Hindi (Small)",
            language = "hi",
            size = 42_000_000,
            quality = "small"
        ),
        TranscriptionModel(
            id = "vosk-model-hi-0.22",
            name = "Hindi",
            language = "hi",
            size = 1_500_000_000,
            quality = "large"
        ),

        // Italian (it)
        TranscriptionModel(
            id = "vosk-model-small-it-0.22",
            name = "Italian (Small)",
            language = "it",
            size = 48_000_000,
            quality = "small"
        ),
        TranscriptionModel(
            id = "vosk-model-it-0.22",
            name = "Italian",
            language = "it",
            size = 1_200_000_000,
            quality = "large"
        ),

        // Japanese (ja)
        TranscriptionModel(
            id = "vosk-model-small-ja-0.22",
            name = "Japanese (Small)",
            language = "ja",
            size = 48_000_000,
            quality = "small"
        ),
        TranscriptionModel(
            id = "vosk-model-ja-0.22",
            name = "Japanese",
            language = "ja",
            size = 1_100_000_000,
            quality = "large"
        ),

        // Korean (ko)
        TranscriptionModel(
            id = "vosk-model-small-ko-0.22",
            name = "Korean (Small)",
            language = "ko",
            size = 42_000_000,
            quality = "small"
        ),
        TranscriptionModel(
            id = "vosk-model-ko-0.22",
            name = "Korean",
            language = "ko",
            size = 1_300_000_000,
            quality = "large"
        ),

        // Dutch (nl)
        TranscriptionModel(
            id = "vosk-model-small-nl-0.22",
            name = "Dutch (Small)",
            language = "nl",
            size = 39_000_000,
            quality = "small"
        ),
        TranscriptionModel(
            id = "vosk-model-nl-spraakherkenning-0.6",
            name = "Dutch",
            language = "nl",
            size = 860_000_000,
            quality = "large"
        ),

        // Polish (pl)
        TranscriptionModel(
            id = "vosk-model-small-pl-0.22",
            name = "Polish (Small)",
            language = "pl",
            size = 50_000_000,
            quality = "small"
        ),

        // Portuguese (pt)
        TranscriptionModel(
            id = "vosk-model-small-pt-0.3",
            name = "Portuguese (Small)",
            language = "pt",
            size = 31_000_000,
            quality = "small"
        ),
        TranscriptionModel(
            id = "vosk-model-pt-fb-v0.1.1-20220516_2113",
            name = "Portuguese Brazil",
            language = "pt",
            size = 1_600_000_000,
            quality = "large"
        ),

        // Russian (ru)
        TranscriptionModel(
            id = "vosk-model-small-ru-0.22",
            name = "Russian (Small)",
            language = "ru",
            size = 45_000_000,
            quality = "small"
        ),
        TranscriptionModel(
            id = "vosk-model-ru-0.42",
            name = "Russian",
            language = "ru",
            size = 1_500_000_000,
            quality = "large"
        ),

        // Swedish (sv)
        TranscriptionModel(
            id = "vosk-model-small-sv-rhasspy-0.15",
            name = "Swedish (Small)",
            language = "sv",
            size = 40_000_000,
            quality = "small"
        ),

        // Turkish (tr)
        TranscriptionModel(
            id = "vosk-model-small-tr-0.3",
            name = "Turkish (Small)",
            language = "tr",
            size = 35_000_000,
            quality = "small"
        ),

        // Ukrainian (uk)
        TranscriptionModel(
            id = "vosk-model-small-uk-v3-small",
            name = "Ukrainian (Small)",
            language = "uk",
            size = 133_000_000,
            quality = "small"
        ),
        TranscriptionModel(
            id = "vosk-model-uk-v3",
            name = "Ukrainian",
            language = "uk",
            size = 343_000_000,
            quality = "large"
        ),

        // Vietnamese (vi)
        TranscriptionModel(
            id = "vosk-model-small-vi-0.4",
            name = "Vietnamese (Small)",
            language = "vi",
            size = 32_000_000,
            quality = "small"
        ),
        TranscriptionModel(
            id = "vosk-model-vi-0.4",
            name = "Vietnamese",
            language = "vi",
            size = 78_000_000,
            quality = "medium"
        )
    )

    /**
     * Get download URL for a model
     */
    fun getDownloadUrl(modelId: String): String {
        return "https://alphacephei.com/vosk/models/${modelId}.zip"
    }

    /**
     * Get models for a specific language
     */
    fun getModelsForLanguage(languageCode: String): List<TranscriptionModel> {
        return ALL_MODELS.filter { it.language == languageCode }
    }

    /**
     * Get all available language codes
     */
    fun getAllLanguages(): List<String> {
        return ALL_MODELS.map { it.language }.distinct().sorted()
    }
}
