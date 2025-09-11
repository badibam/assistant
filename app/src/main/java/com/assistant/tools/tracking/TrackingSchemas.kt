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
                    "description": "{{CONFIG_TYPE_DESC}}"
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
                                "description": "{{CONFIG_NUMERIC_ITEMS_DESC}}",
                                "items": {
                                    "type": "object",
                                    "properties": {
                                        "name": { "type": "string", "minLength": 1, "maxLength": ${BaseSchemas.FieldLimits.SHORT_LENGTH}, "description": "{{CONFIG_NUMERIC_ITEM_NAME_DESC}}" },
                                        "default_quantity": { "type": "number", "description": "{{CONFIG_NUMERIC_ITEM_DEFAULT_QUANTITY_DESC}}" },
                                        "unit": { "type": "string", "maxLength": ${BaseSchemas.FieldLimits.SHORT_LENGTH}, "description": "{{CONFIG_NUMERIC_ITEM_UNIT_DESC}}" }
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
                                "description": "{{CONFIG_TIMER_ITEMS_DESC}}",
                                "items": {
                                    "type": "object",
                                    "properties": {
                                        "name": { "type": "string", "minLength": 1, "maxLength": ${BaseSchemas.FieldLimits.SHORT_LENGTH}, "description": "{{CONFIG_TIMER_ITEM_NAME_DESC}}" }
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
                                "description": "{{CONFIG_CHOICE_OPTIONS_DESC}}",
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
                                "description": "{{CONFIG_SCALE_ITEMS_DESC}}",
                                "items": {
                                    "type": "object",
                                    "properties": {
                                        "name": { "type": "string", "minLength": 1, "maxLength": ${BaseSchemas.FieldLimits.SHORT_LENGTH}, "description": "{{CONFIG_SCALE_ITEM_NAME_DESC}}" }
                                    },
                                    "required": ["name"]
                                }
                            },
                            "min": { "type": "integer", "default": 1, "description": "{{CONFIG_SCALE_MIN_DESC}}" },
                            "max": { "type": "integer", "default": 10, "description": "{{CONFIG_SCALE_MAX_DESC}}" },
                            "min_label": { "type": "string", "maxLength": ${BaseSchemas.FieldLimits.SHORT_LENGTH}, "description": "{{CONFIG_SCALE_MIN_LABEL_DESC}}" },
                            "max_label": { "type": "string", "maxLength": ${BaseSchemas.FieldLimits.SHORT_LENGTH}, "description": "{{CONFIG_SCALE_MAX_LABEL_DESC}}" }
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
                                "description": "{{CONFIG_COUNTER_ITEMS_DESC}}",
                                "items": {
                                    "type": "object",
                                    "properties": {
                                        "name": { "type": "string", "minLength": 1, "maxLength": ${BaseSchemas.FieldLimits.SHORT_LENGTH}, "description": "{{CONFIG_COUNTER_ITEM_NAME_DESC}}" }
                                    },
                                    "required": ["name"]
                                }
                            },
                            "allow_decrement": {
                                "type": "boolean",
                                "default": true,
                                "description": "{{CONFIG_COUNTER_ALLOW_DECREMENT_DESC}}"
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
                                "description": "{{CONFIG_BOOLEAN_ITEMS_DESC}}",
                                "items": {
                                    "type": "object",
                                    "properties": {
                                        "name": { "type": "string", "minLength": 1, "maxLength": ${BaseSchemas.FieldLimits.SHORT_LENGTH}, "description": "{{CONFIG_BOOLEAN_ITEM_NAME_DESC}}" },
                                        "true_label": { "type": "string", "maxLength": ${BaseSchemas.FieldLimits.SHORT_LENGTH}, "description": "{{CONFIG_BOOLEAN_ITEM_TRUE_LABEL_DESC}}"},
                                        "false_label": { "type": "string", "maxLength": ${BaseSchemas.FieldLimits.SHORT_LENGTH}, "description": "{{CONFIG_BOOLEAN_ITEM_FALSE_LABEL_DESC}}"}
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
                                "description": "{{CONFIG_TEXT_ITEMS_DESC}}",
                                "items": {
                                    "type": "object",
                                    "properties": {
                                        "name": { "type": "string", "minLength": 1, "maxLength": ${BaseSchemas.FieldLimits.SHORT_LENGTH}, "description": "{{CONFIG_TEXT_ITEM_NAME_DESC}}" }
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
                "name": { "type": "string", "minLength": 1, "maxLength": ${BaseSchemas.FieldLimits.SHORT_LENGTH}, "description": "{{DATA_NAME_DESC}}" },
                "timestamp": { "type": "number", "description": "{{DATA_TIMESTAMP_DESC}}" },
                "data": {
                    "type": "object",
                    "description": "{{DATA_DATA_DESC}}",
                    "properties": {
                        "type": { "type": "string", "description": "{{DATA_TYPE_DESC}}" },
                        "raw": { "type": "string", "maxLength": ${BaseSchemas.FieldLimits.MEDIUM_LENGTH}, "description": "{{DATA_RAW_DESC}}" }
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
                                    "quantity": { "type": "number", "description": "{{DATA_NUMERIC_QUANTITY_DESC}}" },
                                    "unit": { "type": "string", "maxLength": ${BaseSchemas.FieldLimits.SHORT_LENGTH}, "description": "{{DATA_NUMERIC_UNIT_DESC}}" },
                                    "raw": { "type": "string", "maxLength": ${BaseSchemas.FieldLimits.MEDIUM_LENGTH}, "description": "{{DATA_RAW_DESC}}" }
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
                                    "rating": { "type": "integer", "description": "{{DATA_SCALE_RATING_DESC}}" },
                                    "min_value": { "type": "integer", "description": "{{DATA_SCALE_MIN_VALUE_DESC}}" },
                                    "max_value": { "type": "integer", "description": "{{DATA_SCALE_MAX_VALUE_DESC}}" },
                                    "min_label": { "type": "string", "maxLength": ${BaseSchemas.FieldLimits.SHORT_LENGTH}, "description": "{{DATA_SCALE_MIN_LABEL_DESC}}" },
                                    "max_label": { "type": "string", "maxLength": ${BaseSchemas.FieldLimits.SHORT_LENGTH}, "description": "{{DATA_SCALE_MAX_LABEL_DESC}}" },
                                    "raw": { "type": "string", "maxLength": ${BaseSchemas.FieldLimits.MEDIUM_LENGTH}, "description": "{{DATA_RAW_DESC}}" }
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
                                    "state": { "type": "boolean", "description": "{{DATA_BOOLEAN_STATE_DESC}}" },
                                    "true_label": { "type": "string", "maxLength": ${BaseSchemas.FieldLimits.SHORT_LENGTH}, "description": "{{DATA_BOOLEAN_TRUE_LABEL_DESC}}" },
                                    "false_label": { "type": "string", "maxLength": ${BaseSchemas.FieldLimits.SHORT_LENGTH}, "description": "{{DATA_BOOLEAN_FALSE_LABEL_DESC}}" },
                                    "raw": { "type": "string", "maxLength": ${BaseSchemas.FieldLimits.MEDIUM_LENGTH}, "description": "{{DATA_RAW_DESC}}" }
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
                                    "selected_option": { "type": "string", "maxLength": ${BaseSchemas.FieldLimits.SHORT_LENGTH}, "description": "{{DATA_CHOICE_SELECTED_OPTION_DESC}}" },
                                    "available_options": {
                                        "type": "array",
                                        "description": "{{DATA_CHOICE_AVAILABLE_OPTIONS_DESC}}",
                                        "items": { "type": "string", "maxLength": ${BaseSchemas.FieldLimits.SHORT_LENGTH} }
                                    },
                                    "raw": { "type": "string", "maxLength": ${BaseSchemas.FieldLimits.MEDIUM_LENGTH}, "description": "{{DATA_RAW_DESC}}" }
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
                                    "increment": { "type": "integer", "description": "{{DATA_COUNTER_INCREMENT_DESC}}" },
                                    "raw": { "type": "string", "maxLength": ${BaseSchemas.FieldLimits.MEDIUM_LENGTH}, "description": "{{DATA_RAW_DESC}}" }
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
                                    "duration_seconds": { "type": "integer", "minimum": 0, "description": "{{DATA_TIMER_DURATION_SECONDS_DESC}}" },
                                    "raw": { "type": "string", "maxLength": ${BaseSchemas.FieldLimits.MEDIUM_LENGTH}, "description": "{{DATA_RAW_DESC}}" }
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
            .replace("{{CONFIG_TYPE_DESC}}", s.tool("schema_config_type"))
            .replace("{{CONFIG_NUMERIC_ITEMS_DESC}}", s.tool("schema_config_numeric_items"))
            .replace("{{CONFIG_NUMERIC_ITEM_NAME_DESC}}", s.tool("schema_config_numeric_item_name"))
            .replace("{{CONFIG_NUMERIC_ITEM_DEFAULT_QUANTITY_DESC}}", s.tool("schema_config_numeric_item_default_quantity"))
            .replace("{{CONFIG_NUMERIC_ITEM_UNIT_DESC}}", s.tool("schema_config_numeric_item_unit"))
            .replace("{{CONFIG_TIMER_ITEMS_DESC}}", s.tool("schema_config_timer_items"))
            .replace("{{CONFIG_TIMER_ITEM_NAME_DESC}}", s.tool("schema_config_timer_item_name"))
            .replace("{{CONFIG_CHOICE_OPTIONS_DESC}}", s.tool("schema_config_choice_options"))
            .replace("{{CONFIG_SCALE_ITEMS_DESC}}", s.tool("schema_config_scale_items"))
            .replace("{{CONFIG_SCALE_ITEM_NAME_DESC}}", s.tool("schema_config_scale_item_name"))
            .replace("{{CONFIG_SCALE_MIN_DESC}}", s.tool("schema_config_scale_min"))
            .replace("{{CONFIG_SCALE_MAX_DESC}}", s.tool("schema_config_scale_max"))
            .replace("{{CONFIG_SCALE_MIN_LABEL_DESC}}", s.tool("schema_config_scale_min_label"))
            .replace("{{CONFIG_SCALE_MAX_LABEL_DESC}}", s.tool("schema_config_scale_max_label"))
            .replace("{{CONFIG_COUNTER_ITEMS_DESC}}", s.tool("schema_config_counter_items"))
            .replace("{{CONFIG_COUNTER_ITEM_NAME_DESC}}", s.tool("schema_config_counter_item_name"))
            .replace("{{CONFIG_COUNTER_ALLOW_DECREMENT_DESC}}", s.tool("schema_config_counter_allow_decrement"))
            .replace("{{CONFIG_BOOLEAN_ITEMS_DESC}}", s.tool("schema_config_boolean_items"))
            .replace("{{CONFIG_BOOLEAN_ITEM_NAME_DESC}}", s.tool("schema_config_boolean_item_name"))
            .replace("{{CONFIG_BOOLEAN_ITEM_TRUE_LABEL_DESC}}", s.tool("schema_config_boolean_item_true_label"))
            .replace("{{CONFIG_BOOLEAN_ITEM_FALSE_LABEL_DESC}}", s.tool("schema_config_boolean_item_false_label"))
            .replace("{{CONFIG_TEXT_ITEMS_DESC}}", s.tool("schema_config_text_items"))
            .replace("{{CONFIG_TEXT_ITEM_NAME_DESC}}", s.tool("schema_config_text_item_name"))
    }
    
    /**
     * Get data schema with localized descriptions
     */
    fun getDataSchema(context: Context): String {
        val s = Strings.`for`(tool = "tracking", context = context)
        return DATA_SCHEMA_TEMPLATE
            .replace("{{DATA_NAME_DESC}}", s.tool("schema_data_name"))
            .replace("{{DATA_TIMESTAMP_DESC}}", s.tool("schema_data_timestamp"))
            .replace("{{DATA_DATA_DESC}}", s.tool("schema_data_data"))
            .replace("{{DATA_TYPE_DESC}}", s.tool("schema_data_type"))
            .replace("{{DATA_RAW_DESC}}", s.tool("schema_data_raw"))
            .replace("{{DATA_NUMERIC_QUANTITY_DESC}}", s.tool("schema_data_numeric_quantity"))
            .replace("{{DATA_NUMERIC_UNIT_DESC}}", s.tool("schema_data_numeric_unit"))
            .replace("{{DATA_SCALE_RATING_DESC}}", s.tool("schema_data_scale_rating"))
            .replace("{{DATA_SCALE_MIN_VALUE_DESC}}", s.tool("schema_data_scale_min_value"))
            .replace("{{DATA_SCALE_MAX_VALUE_DESC}}", s.tool("schema_data_scale_max_value"))
            .replace("{{DATA_SCALE_MIN_LABEL_DESC}}", s.tool("schema_data_scale_min_label"))
            .replace("{{DATA_SCALE_MAX_LABEL_DESC}}", s.tool("schema_data_scale_max_label"))
            .replace("{{DATA_BOOLEAN_STATE_DESC}}", s.tool("schema_data_boolean_state"))
            .replace("{{DATA_BOOLEAN_TRUE_LABEL_DESC}}", s.tool("schema_data_boolean_true_label"))
            .replace("{{DATA_BOOLEAN_FALSE_LABEL_DESC}}", s.tool("schema_data_boolean_false_label"))
            .replace("{{DATA_CHOICE_SELECTED_OPTION_DESC}}", s.tool("schema_data_choice_selected_option"))
            .replace("{{DATA_CHOICE_AVAILABLE_OPTIONS_DESC}}", s.tool("schema_data_choice_available_options"))
            .replace("{{DATA_COUNTER_INCREMENT_DESC}}", s.tool("schema_data_counter_increment"))
            .replace("{{DATA_TIMER_DURATION_SECONDS_DESC}}", s.tool("schema_data_timer_duration_seconds"))
    }
}