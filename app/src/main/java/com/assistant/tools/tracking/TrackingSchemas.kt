package com.assistant.tools.tracking

import android.content.Context
import com.assistant.core.tools.BaseSchemas
import com.assistant.core.strings.Strings

/**
 * External JSON schemas for Tracking tool type
 * Keeps schemas separate from business logic for better maintainability
 */
object TrackingSchemas {
    
    /**
     * Configuration schema template with placeholders for localized descriptions
     * @deprecated Use getConfigSchema(context) instead for localized descriptions
     */
    @Deprecated("Use getConfigSchema(context) for localized descriptions")
    val CONFIG_SCHEMA get() = CONFIG_SCHEMA_TEMPLATE
    
    /**
     * Configuration schema template with placeholders
     */
    private val CONFIG_SCHEMA_TEMPLATE = """
        {
            "properties": {
                "type": {
                    "type": "string",
                    "enum": ["numeric", "text", "scale", "boolean", "timer", "choice", "counter"],
                    "description": "{{TYPE_DESC}}"
                }
            },
            "required": ["type"],
            "allOf": [
                {
                    "if": {
                        "properties": { "type": { "const": "numeric" } }
                    },
                    "then": {
                        "properties": {
                            "items": {
                                "type": "array",
                                "description": "{{NUMERIC_ITEMS_DESC}}",
                                "items": {
                                    "type": "object",
                                    "properties": {
                                        "name": { "type": "string", "minLength": 1, "maxLength": ${BaseSchemas.FieldLimits.SHORT_LENGTH} },
                                        "default_quantity": { "type": "number" },
                                        "unit": { "type": "string", "maxLength": ${BaseSchemas.FieldLimits.SHORT_LENGTH} }
                                    },
                                    "required": ["name"]
                                }
                            }
                        }
                    }
                },
                {
                    "if": {
                        "properties": { "type": { "const": "timer" } }
                    },
                    "then": {
                        "properties": {
                            "items": {
                                "type": "array",
                                "description": "{{TIMER_ITEMS_DESC}}",
                                "items": {
                                    "type": "object",
                                    "properties": {
                                        "name": { "type": "string", "minLength": 1, "maxLength": ${BaseSchemas.FieldLimits.SHORT_LENGTH} }
                                    },
                                    "required": ["name"]
                                }
                            }
                        }
                    }
                },
                {
                    "if": {
                        "properties": { "type": { "const": "choice" } }
                    },
                    "then": {
                        "properties": {
                            "options": {
                                "type": "array",
                                "description": "{{CHOICE_OPTIONS_DESC}}",
                                "items": {
                                    "type": "string",
                                    "minLength": 1,
                                    "maxLength": ${BaseSchemas.FieldLimits.SHORT_LENGTH}
                                }
                            }
                        },
                        "required": ["options"]
                    }
                },
                {
                    "if": {
                        "properties": { "type": { "const": "scale" } }
                    },
                    "then": {
                        "properties": {
                            "items": {
                                "type": "array",
                                "description": "{{SCALE_ITEMS_DESC}}",
                                "items": {
                                    "type": "object",
                                    "properties": {
                                        "name": { "type": "string", "minLength": 1, "maxLength": ${BaseSchemas.FieldLimits.SHORT_LENGTH} }
                                    },
                                    "required": ["name"]
                                }
                            },
                            "min": { "type": "integer", "default": 1, "description": "{{SCALE_MIN_DESC}}" },
                            "max": { "type": "integer", "default": 10, "description": "{{SCALE_MAX_DESC}}" },
                            "min_label": { "type": "string", "maxLength": ${BaseSchemas.FieldLimits.SHORT_LENGTH}, "description": "{{SCALE_MIN_LABEL_DESC}}" },
                            "max_label": { "type": "string", "maxLength": ${BaseSchemas.FieldLimits.SHORT_LENGTH}, "description": "{{SCALE_MAX_LABEL_DESC}}" }
                        }
                    }
                },
                {
                    "if": {
                        "properties": { "type": { "const": "counter" } }
                    },
                    "then": {
                        "properties": {
                            "items": {
                                "type": "array",
                                "description": "{{COUNTER_ITEMS_DESC}}",
                                "items": {
                                    "type": "object",
                                    "properties": {
                                        "name": { "type": "string", "minLength": 1, "maxLength": ${BaseSchemas.FieldLimits.SHORT_LENGTH} }
                                    },
                                    "required": ["name"]
                                }
                            },
                            "allow_decrement": {
                                "type": "boolean",
                                "default": true,
                                "description": "{{COUNTER_ALLOW_DECREMENT_DESC}}"
                            }
                        }
                    }
                },
                {
                    "if": {
                        "properties": { "type": { "const": "boolean" } }
                    },
                    "then": {
                        "properties": {
                            "items": {
                                "type": "array",
                                "description": "{{BOOLEAN_ITEMS_DESC}}",
                                "items": {
                                    "type": "object",
                                    "properties": {
                                        "name": { "type": "string", "minLength": 1, "maxLength": ${BaseSchemas.FieldLimits.SHORT_LENGTH} },
                                        "true_label": { "type": "string", "maxLength": ${BaseSchemas.FieldLimits.SHORT_LENGTH}},
                                        "false_label": { "type": "string", "maxLength": ${BaseSchemas.FieldLimits.SHORT_LENGTH}}
                                    },
                                    "required": ["name", "true_label", "false_label"]
                                }
                            }
                        }
                    }
                },
                {
                    "if": {
                        "properties": { "type": { "const": "text" } }
                    },
                    "then": {
                        "properties": {
                            "items": {
                                "type": "array",
                                "description": "{{TEXT_ITEMS_DESC}}",
                                "items": {
                                    "type": "object",
                                    "properties": {
                                        "name": { "type": "string", "minLength": 1, "maxLength": ${BaseSchemas.FieldLimits.SHORT_LENGTH} }
                                    },
                                    "required": ["name"]
                                }
                            }
                        }
                    }
                }
            ]
        }
    """.trimIndent()
    
    /**
     * Data schema with localized descriptions
     * @deprecated Use getDataSchema(context) instead for localized descriptions
     */
    @Deprecated("Use getDataSchema(context) for localized descriptions")
    val DATA_SCHEMA get() = DATA_SCHEMA_TEMPLATE
    
    /**
     * Data schema template with placeholders
     */
    private val DATA_SCHEMA_TEMPLATE = """
        {
            "properties": {
                "name": { "type": "string", "minLength": 1, "maxLength": ${BaseSchemas.FieldLimits.SHORT_LENGTH} },
                "timestamp": { "type": "number" },
                "data": {
                    "type": "object",
                    "description": "{{DATA_DESC}}",
                    "properties": {
                        "type": { "type": "string" },
                        "raw": { "type": "string", "maxLength": ${BaseSchemas.FieldLimits.MEDIUM_LENGTH} }
                    },
                    "required": ["type"],
                    "additionalProperties": false
                }
            },
            "required": ["name", "timestamp", "data"],
            "allOf": [
                {
                    "if": {
                        "properties": { 
                            "data": { 
                                "properties": { "type": { "const": "numeric" } } 
                            } 
                        }
                    },
                    "then": {
                        "properties": {
                            "data": {
                                "properties": {
                                    "type": { "const": "numeric" },
                                    "quantity": { "type": "number" },
                                    "unit": { "type": "string", "maxLength": ${BaseSchemas.FieldLimits.SHORT_LENGTH} },
                                    "raw": { "type": "string", "maxLength": ${BaseSchemas.FieldLimits.MEDIUM_LENGTH} }
                                },
                                "required": ["type", "quantity"],
                                "additionalProperties": false
                            }
                        }
                    }
                },
                {
                    "if": {
                        "properties": { 
                            "data": { 
                                "properties": { "type": { "const": "scale" } } 
                            } 
                        }
                    },
                    "then": {
                        "properties": {
                            "data": {
                                "properties": {
                                    "type": { "const": "scale" },
                                    "rating": { "type": "integer" },
                                    "min_value": { "type": "integer" },
                                    "max_value": { "type": "integer" },
                                    "min_label": { "type": "string", "maxLength": ${BaseSchemas.FieldLimits.SHORT_LENGTH} },
                                    "max_label": { "type": "string", "maxLength": ${BaseSchemas.FieldLimits.SHORT_LENGTH} },
                                    "raw": { "type": "string", "maxLength": ${BaseSchemas.FieldLimits.MEDIUM_LENGTH} }
                                },
                                "required": ["type", "rating", "min_value", "max_value"],
                                "additionalProperties": false
                            }
                        }
                    }
                },
                {
                    "if": {
                        "properties": { 
                            "data": { 
                                "properties": { "type": { "const": "boolean" } } 
                            } 
                        }
                    },
                    "then": {
                        "properties": {
                            "data": {
                                "properties": {
                                    "type": { "const": "boolean" },
                                    "state": { "type": "boolean" },
                                    "true_label": { "type": "string", "maxLength": ${BaseSchemas.FieldLimits.SHORT_LENGTH} },
                                    "false_label": { "type": "string", "maxLength": ${BaseSchemas.FieldLimits.SHORT_LENGTH} },
                                    "raw": { "type": "string", "maxLength": ${BaseSchemas.FieldLimits.MEDIUM_LENGTH} }
                                },
                                "required": ["type", "state"],
                                "additionalProperties": false
                            }
                        }
                    }
                },
                {
                    "if": {
                        "properties": { 
                            "data": { 
                                "properties": { "type": { "const": "choice" } } 
                            } 
                        }
                    },
                    "then": {
                        "properties": {
                            "data": {
                                "properties": {
                                    "type": { "const": "choice" },
                                    "selected_option": { "type": "string", "maxLength": ${BaseSchemas.FieldLimits.SHORT_LENGTH} },
                                    "available_options": {
                                        "type": "array",
                                        "items": { "type": "string", "maxLength": ${BaseSchemas.FieldLimits.SHORT_LENGTH} }
                                    },
                                    "raw": { "type": "string", "maxLength": ${BaseSchemas.FieldLimits.MEDIUM_LENGTH} }
                                },
                                "required": ["type", "selected_option", "available_options"],
                                "additionalProperties": false
                            }
                        }
                    }
                },
                {
                    "if": {
                        "properties": { 
                            "data": { 
                                "properties": { "type": { "const": "counter" } } 
                            } 
                        }
                    },
                    "then": {
                        "properties": {
                            "data": {
                                "properties": {
                                    "type": { "const": "counter" },
                                    "increment": { "type": "integer" },
                                    "raw": { "type": "string", "maxLength": ${BaseSchemas.FieldLimits.MEDIUM_LENGTH} }
                                },
                                "required": ["type", "increment"],
                                "additionalProperties": false
                            }
                        }
                    }
                },
                {
                    "if": {
                        "properties": { 
                            "data": { 
                                "properties": { "type": { "const": "timer" } } 
                            } 
                        }
                    },
                    "then": {
                        "properties": {
                            "data": {
                                "properties": {
                                    "type": { "const": "timer" },
                                    "duration_seconds": { "type": "integer", "minimum": 0 },
                                    "raw": { "type": "string", "maxLength": ${BaseSchemas.FieldLimits.MEDIUM_LENGTH} }
                                },
                                "required": ["type", "duration_seconds"],
                                "additionalProperties": false
                            }
                        }
                    }
                }
            ]
        }
    """.trimIndent()
    
    /**
     * Get config schema with localized descriptions
     */
    fun getConfigSchema(context: Context): String {
        val s = Strings.`for`(tool = "tracking", context = context)
        return CONFIG_SCHEMA_TEMPLATE
            .replace("{{TYPE_DESC}}", s.tool("schema_config_desc_type"))
            .replace("{{NUMERIC_ITEMS_DESC}}", s.tool("schema_config_desc_numeric_items"))
            .replace("{{TIMER_ITEMS_DESC}}", s.tool("schema_config_desc_timer_items"))
            .replace("{{CHOICE_OPTIONS_DESC}}", s.tool("schema_config_desc_choice_options"))
            .replace("{{SCALE_ITEMS_DESC}}", s.tool("schema_config_desc_scale_items"))
            .replace("{{SCALE_MIN_DESC}}", s.tool("schema_config_desc_scale_min"))
            .replace("{{SCALE_MAX_DESC}}", s.tool("schema_config_desc_scale_max"))
            .replace("{{SCALE_MIN_LABEL_DESC}}", s.tool("schema_config_desc_scale_min_label"))
            .replace("{{SCALE_MAX_LABEL_DESC}}", s.tool("schema_config_desc_scale_max_label"))
            .replace("{{COUNTER_ITEMS_DESC}}", s.tool("schema_config_desc_counter_items"))
            .replace("{{COUNTER_ALLOW_DECREMENT_DESC}}", s.tool("schema_config_desc_counter_allow_decrement"))
            .replace("{{BOOLEAN_ITEMS_DESC}}", s.tool("schema_config_desc_boolean_items"))
            .replace("{{TEXT_ITEMS_DESC}}", s.tool("schema_config_desc_text_items"))
    }
    
    /**
     * Get data schema with localized descriptions
     */
    fun getDataSchema(context: Context): String {
        val s = Strings.`for`(tool = "tracking", context = context)
        return DATA_SCHEMA_TEMPLATE
            .replace("{{DATA_DESC}}", s.tool("schema_data_desc_data"))
    }
}