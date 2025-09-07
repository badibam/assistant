package com.assistant.tools.tracking

import com.assistant.core.tools.BaseSchemas

/**
 * External JSON schemas for Tracking tool type
 * Keeps schemas separate from business logic for better maintainability
 */
object TrackingSchemas {
    
    /**
     * Configuration schema specific to tracking tools
     * Combined with base config schema via BaseSchemas.createExtendedSchema()
     */
    val CONFIG_SCHEMA = """
        {
            "properties": {
                "type": {
                    "type": "string",
                    "enum": ["numeric", "text", "scale", "boolean", "timer", "choice", "counter"],
                    "description": "Data type for all items in this tracking instance"
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
                                "description": "Predefined numeric items with quantity and unit",
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
                                "description": "Predefined timer activities",
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
                            "items": {
                                "type": "array",
                                "description": "Available choice options",
                                "items": {
                                    "type": "object",
                                    "properties": {
                                        "name": { "type": "string", "minLength": 1, "maxLength": ${BaseSchemas.FieldLimits.SHORT_LENGTH} }
                                    },
                                    "required": ["name"]
                                }
                            }
                        },
                        "required": ["items"]
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
                                "description": "Predefined scale items (names only, min/max shared globally)",
                                "items": {
                                    "type": "object",
                                    "properties": {
                                        "name": { "type": "string", "minLength": 1, "maxLength": ${BaseSchemas.FieldLimits.SHORT_LENGTH} }
                                    },
                                    "required": ["name"]
                                }
                            },
                            "min": { "type": "integer", "default": 1, "description": "Minimum value for all scale items" },
                            "max": { "type": "integer", "default": 10, "description": "Maximum value for all scale items" },
                            "min_label": { "type": "string", "maxLength": ${BaseSchemas.FieldLimits.SHORT_LENGTH}, "description": "Label for minimum value" },
                            "max_label": { "type": "string", "maxLength": ${BaseSchemas.FieldLimits.SHORT_LENGTH}, "description": "Label for maximum value" }
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
                                "description": "Predefined counter items",
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
                                "description": "Allow negative increments"
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
                                "description": "Predefined boolean items with custom labels",
                                "items": {
                                    "type": "object",
                                    "properties": {
                                        "name": { "type": "string", "minLength": 1, "maxLength": ${BaseSchemas.FieldLimits.SHORT_LENGTH} },
                                        "true_label": { "type": "string", "maxLength": ${BaseSchemas.FieldLimits.SHORT_LENGTH}, "default": "Oui" },
                                        "false_label": { "type": "string", "maxLength": ${BaseSchemas.FieldLimits.SHORT_LENGTH}, "default": "Non" }
                                    },
                                    "required": ["name"]
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
                                "description": "Predefined text items",
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
     * Data schema specific to tracking data entries
     * Combined with base data schema via BaseSchemas.createExtendedSchema()
     */
    val DATA_SCHEMA = """
        {
            "properties": {
                "name": { "type": "string", "minLength": 1, "maxLength": ${BaseSchemas.FieldLimits.SHORT_LENGTH} },
                "timestamp": { "type": "number" },
                "data": {
                    "type": "object",
                    "description": "Tracking data specific to the tracking type",
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
                                    "rating": { "type": "integer", "minimum": 1, "maximum": 100 },
                                    "min_value": { "type": "integer", "minimum": 1 },
                                    "max_value": { "type": "integer", "maximum": 100 },
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
                                    "activity": { "type": "string", "maxLength": ${BaseSchemas.FieldLimits.SHORT_LENGTH} },
                                    "duration_minutes": { "type": "integer", "minimum": 0 },
                                    "raw": { "type": "string", "maxLength": ${BaseSchemas.FieldLimits.MEDIUM_LENGTH} }
                                },
                                "required": ["type", "activity", "duration_minutes"],
                                "additionalProperties": false
                            }
                        }
                    }
                }
            ]
        }
    """.trimIndent()
}