package app.treelune.core.validation

import android.content.Context
import com.networknt.schema.JsonSchema
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import com.networknt.schema.ValidationMessage
import org.json.JSONObject
import app.treelune.core.utils.LogManager

/**
 * Unified JSON Schema validator with recursive validation support
 * Clean API for all validation scenarios
 */
object SchemaValidator {
    private const val TAG = "SchemaValidator"
    
    // Cache for compiled schemas - enabled for production performance
    private val schemaCache: MutableMap<String, JsonSchema> = mutableMapOf()
    private val cacheEnabled = true
    
    private val schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7)

    /**
     * The library's messages in the phone's language, read as the ISO-8859-1 its files are
     * written in: Android reads a properties bundle as UTF-8, which turned every accent of the
     * French messages into a replacement character ("dépasser" shown as "d?passer").
     */
    private val schemaConfig = com.networknt.schema.SchemaValidatorsConfig().apply {
        resourceBundle = messageBundle(java.util.Locale.getDefault())
    }

    /** The library's messages for [locale], their accents read right. */
    internal fun messageBundle(locale: java.util.Locale): java.util.ResourceBundle =
        java.util.ResourceBundle.getBundle("jsv-messages", locale, Latin1Properties)

    /** Reads a properties bundle in ISO-8859-1, the encoding of the library's message files. */
    private object Latin1Properties : java.util.ResourceBundle.Control() {
        override fun getFormats(baseName: String): List<String> = FORMAT_PROPERTIES

        override fun newBundle(baseName: String, locale: java.util.Locale, format: String, loader: ClassLoader, reload: Boolean): java.util.ResourceBundle? {
            val resource = toResourceName(toBundleName(baseName, locale), "properties")
            val stream = loader.getResourceAsStream(resource) ?: return null
            return stream.reader(Charsets.ISO_8859_1).use { java.util.PropertyResourceBundle(it) }
        }
    }
    
    /**
     * Main validation function using direct schema objects
     * @param schema Complete Schema object with content and metadata
     * @param data Data to validate as key-value map
     * @param context Android context for string resource access
     * @param partialValidation If true, ignores 'required' fields that are missing (for partial updates)
     * @return ValidationResult with success/error status and user-friendly messages
     */
    fun validate(
        schema: Schema,
        data: Map<String, Any?>,
        context: Context,
        partialValidation: Boolean = false
    ): ValidationResult {
        // Only a refusal is logged: every write is validated, and a trace per validation (the
        // schema and the data written out) made an import of 64 000 lines run out of memory
        return try {
            val cleanData = filterEmptyValues(convertJsonObjectsToMaps(data))

            // For partial validation, modify schema to make 'required' fields optional
            // This allows updates to only specify the fields they want to change
            val schemaContent = if (partialValidation) {
                removeRequiredConstraint(schema.content)
            } else {
                schema.content
            }

            val jsonSchema = getOrCompileSchema(schemaContent)
            val objectMapper = com.fasterxml.jackson.databind.ObjectMapper()
            val dataNode = objectMapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(cleanData)

            val errors = jsonSchema.validate(dataNode)

            val result = if (errors.isEmpty()) {
                ValidationResult.success()
            } else {
                // A variant's refusal names nothing to fix: it is replaced by the errors of the
                // branch the value's selector points to
                val (others, explained) = VariantErrors.sort(errors, JSONObject(schemaContent), dataNode)
                val errorMessage = (listOfNotNull(
                    ValidationErrorProcessor.filterErrors(others.toSet(), schema.content, context).takeIf { it.isNotEmpty() }
                ) + explained).joinToString("\n")
                if (errorMessage.isEmpty()) {
                    ValidationResult.success()
                } else {
                    LogManager.schema("Validation failed for ${schema.id}: $errorMessage", "ERROR")
                    ValidationResult.error(errorMessage)
                }
            }

            result

        } catch (e: Exception) {
            LogManager.schema("Exception during validation: ${e.message}", "ERROR", e)
            ValidationResult.error("Erreur de validation: ${e.message}")
        }
    }
    
    
    /**
     * Filters out empty values from data before validation
     * Recursively handles nested structures to prevent type mismatch errors
     *
     * IMPORTANT: This filters out null values (removes them from the Map)
     * This is appropriate for CREATE operations where null = "don't specify this field"
     * For UPDATE operations with partial validation, nulls are already handled differently
     */
    internal fun filterEmptyValues(data: Map<String, Any?>): Map<String, Any> {
        return data.mapNotNull { (key, value) ->
            val filteredValue = filterEmptyValue(value)
            if (filteredValue != null) {
                key to filteredValue
            } else {
                null
            }
        }.toMap()
    }
    
    /**
     * Filters a single value recursively
     *
     * CRITICAL: Empty strings are NOT filtered (they are valid values to intentionally clear a field)
     * Only null values are filtered for partial updates
     * A list is filtered only if all its contents were filtered out: one sent empty is a value
     * (a home screen without zone groups), which a required list must find
     */
    private fun filterEmptyValue(value: Any?): Any? {
        return when (value) {
            null -> null
            // DO NOT filter empty strings - they represent intentional clearing of a field
            // This is different from null/absent field (partial update = keep existing value)
            is String -> value
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                val originalMap = value as Map<String, Any?>
                val filteredMap = filterEmptyValues(originalMap)
                // Keep the map even if empty - it may be required by schema
                // The schema validation will catch if it shouldn't be empty
                filteredMap
            }
            is List<*> -> {
                val filteredList = value.mapNotNull { filterEmptyValue(it) }
                if (filteredList.isEmpty() && value.isNotEmpty()) null else filteredList
            }
            else -> value
        }
    }
    
    /**
     * Converts Android JSONObjects to Maps for Jackson compatibility
     * Recursively handles nested structures
     *
     * IMPORTANT: Preserves null values - schemas may explicitly allow null for certain fields
     */
    private fun convertJsonObjectsToMaps(data: Map<String, Any?>): Map<String, Any?> {
        return data.mapValues { (_, value) ->
            convertAnyJsonObjectToMap(value)  // Returns null for null values, no fallback to ""
        }
    }
    
    /**
     * Recursively converts any JSONObject, JSONArray, or nested structures
     *
     * IMPORTANT: Preserves null values - schemas may explicitly allow null for certain fields
     */
    private fun convertAnyJsonObjectToMap(value: Any?): Any? {
        return when (value) {
            // Explicit null handling - preserve null values
            null -> null

            // Convert JSONObject.NULL to Kotlin null
            org.json.JSONObject.NULL -> null

            is org.json.JSONObject -> {
                val map = mutableMapOf<String, Any?>()
                value.keys().forEach { key ->
                    val rawValue = value.get(key)
                    // Recursively convert, preserving null values
                    val convertedValue = if (rawValue == org.json.JSONObject.NULL) {
                        null
                    } else {
                        convertAnyJsonObjectToMap(rawValue)
                    }
                    map[key] = convertedValue  // Add even if null - schema may allow it
                }
                map
            }
            is org.json.JSONArray -> {
                val list = mutableListOf<Any?>()
                for (i in 0 until value.length()) {
                    val rawValue = value.get(i)
                    val convertedValue = if (rawValue == org.json.JSONObject.NULL) {
                        null
                    } else {
                        convertAnyJsonObjectToMap(rawValue)
                    }
                    if (convertedValue != null) {
                        list.add(convertedValue)
                    }
                }
                list
            }
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                val originalMap = value as Map<String, Any?>
                // Preserve null values in nested Maps - no fallback to ""
                originalMap.mapValues { (_, v) -> convertAnyJsonObjectToMap(v) }
            }
            is List<*> -> {
                value.map { convertAnyJsonObjectToMap(it) }
            }
            else -> value
        }
    }
    
    /**
     * Remove 'required' constraints from schema for partial validation
     * Recursively processes nested objects to remove all 'required' arrays
     *
     * This allows partial updates where only modified fields are provided
     * while still validating the types/formats of fields that ARE provided
     */
    private fun removeRequiredConstraint(schemaJson: String): String {
        return try {
            val schemaObject = JSONObject(schemaJson)
            removeRequiredFromObject(schemaObject)
            schemaObject.toString()
        } catch (e: Exception) {
            LogManager.schema("Failed to remove required constraint: ${e.message}", "WARN", e)
            schemaJson // Return original on error
        }
    }

    /**
     * Recursively remove 'required' arrays from a JSONObject and its nested objects
     */
    private fun removeRequiredFromObject(obj: JSONObject) {
        // Remove 'required' array at this level
        if (obj.has("required")) {
            obj.remove("required")
        }

        // Recursively process nested objects
        obj.keys().forEach { key ->
            val value = obj.opt(key)
            when (value) {
                is JSONObject -> removeRequiredFromObject(value)
                is org.json.JSONArray -> {
                    for (i in 0 until value.length()) {
                        val item = value.opt(i)
                        if (item is JSONObject) {
                            removeRequiredFromObject(item)
                        }
                    }
                }
            }
        }
    }

    /**
     * Gets or compiles schema with optional caching
     */
    private fun getOrCompileSchema(schemaJson: String): JsonSchema {
        val cacheKey = schemaJson.hashCode().toString()
        
        return if (cacheEnabled) {
            schemaCache.getOrPut(cacheKey) {
                LogManager.schema("Compiling and caching schema")
                compileSchema(schemaJson)
            }
        } else {
            LogManager.schema("Compiling schema (no cache)")
            compileSchema(schemaJson)
        }
    }
    
    /**
     * Compiles JSON schema string into JsonSchema object
     */
    private fun compileSchema(schemaJson: String): JsonSchema {
        val objectMapper = com.fasterxml.jackson.databind.ObjectMapper()
        val schemaNode = objectMapper.readTree(schemaJson)
        return schemaFactory.getSchema(schemaNode, schemaConfig)
    }
    
    /**
     * Clears schema cache - useful for tests or development
     */
    fun clearCache() {
        schemaCache.clear()
        LogManager.schema("Schema cache cleared")
    }

}