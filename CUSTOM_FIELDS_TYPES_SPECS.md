# Custom Fields - Field Types Specifications V1 Complete

## Architecture Overview

**Principle**: Field system is generic and reusable. Custom fields use the same infrastructure as native tooltype fields.

**Naming convention**:
- Core components: Generic naming (FieldType, FieldValueValidator, FieldSchemaGenerator, etc.)
- JSON namespace: `custom_fields` (distinction between dynamic fields and native tooltype fields)
- UI strings: `custom_fields_*` (specific to dynamically added fields UI)

**Two validation levels**:
1. **Config validation** (`FieldConfigValidator`): Validates field definitions
2. **Value validation** (`FieldValueValidator`): Validates field values with business constraints

---

## Field Types Specifications

### 1. TEXT_SHORT

**Description**: Short text for identifiers, names, short labels.

**Usage**: Names, short titles, visible identifiers.

**Config**: `null` (no configuration)

**Constraints**:
- Max length: 60 chars (FieldLimits.SHORT_LENGTH) - fixed, not configurable

**JSON Schema generated**:
```json
{
  "type": "string",
  "maxLength": 60,
  "description": "..."
}
```

**UI Rendering**:
- FieldType: `TEXT`
- Single line input
- Character counter

**Value format**: `"string"`

**Default value**: Supported via `default_value` in config

**Validation**:
- Config: None (null config)
- Value: maxLength enforced by JSON Schema

---

### 2. TEXT_LONG

**Description**: Long text for substantial content.

**Usage**: Detailed descriptions, comments, long notes.

**Config**: `null` (no configuration)

**Constraints**:
- Max length: 1500 chars (FieldLimits.LONG_LENGTH) - fixed, not configurable

**JSON Schema generated**:
```json
{
  "type": "string",
  "maxLength": 1500,
  "description": "..."
}
```

**UI Rendering**:
- FieldType: `TEXT_LONG`
- Multi-line input
- Character counter

**Value format**: `"string"`

**Default value**: Supported via `default_value` in config

**Validation**:
- Config: None (null config)
- Value: maxLength enforced by JSON Schema

---

### 3. TEXT_UNLIMITED

**Description**: Unlimited text (already implemented).

**Usage**: Transcriptions, documentation, exports.

**Config**: `null` (no configuration)

**JSON Schema generated**:
```json
{
  "type": "string",
  "description": "..."
}
```

**UI Rendering**:
- FieldType: `TEXT_UNLIMITED`
- Multi-line input unlimited

**Value format**: `"string"`

**Default value**: Supported via `default_value` in config

**Validation**:
- Config: None (null config)
- Value: None (no maxLength)

---

### 4. NUMERIC

**Description**: Numeric value with optional unit and constraints.

**Usage**: Measurements, quantities, scores.

**Config**:
```json
{
  "unit": "kg",           // Optional - displayed unit
  "min": 0,               // Optional - minimum
  "max": 1000,            // Optional - maximum
  "decimals": 2,          // Optional - decimal places (default 0)
  "step": 0.1,            // Optional - increment (default calculated)
  "default_value": 0      // Optional
}
```

**JSON Schema generated**:
```json
{
  "type": "number",
  "minimum": 0,           // If min defined
  "maximum": 1000,        // If max defined
  "multipleOf": 0.01,     // Calculated from decimals (10^-decimals)
  "description": "..."
}
```

**UI Rendering**:
- FieldType: `NUMERIC`
- Numeric keyboard
- Unit suffix if defined
- +/- buttons for step increment
- Step used for UI suggestion only, not strict validation

**Value format**: `42.5` (number)

**Default value**: Supported via `default_value` in config

**Validation**:
- Config:
  - If both min and max defined: `min <= max`
  - decimals >= 0
  - step > 0 (if defined)
- Value: min/max/multipleOf enforced by JSON Schema

**Defaults**:
- decimals: 0
- step: calculated (1 if decimals=0, else 10^-decimals)

**Edge cases**:
- min > max: error in FieldConfigValidator
- decimals < 0: error in FieldConfigValidator
- step <= 0: error in FieldConfigValidator

---

### 5. SCALE

**Description**: Graduated scale between min and max with labels.

**Usage**: Mood (1-10), satisfaction, intensity.

**Config**:
```json
{
  "min": 1,               // REQUIRED
  "max": 10,              // REQUIRED
  "min_label": "label_scale_bad",  // Optional, i18n key
  "max_label": "label_scale_excellent",  // Optional, i18n key
  "step": 1,              // Optional (default 1)
  "default_value": 5      // Optional
}
```

**JSON Schema generated**:
```json
{
  "type": "number",
  "minimum": 1,
  "maximum": 10,
  "multipleOf": 1,
  "description": "..."
}
```

**UI Rendering**:
- Slider with min/max labels (translated from i18n keys)
- Current value displayed
- Increments by step
- **Note**: Current Slider component doesn't support step, needs implementation

**Value format**: `7` (number)

**Default value**: Supported via `default_value` in config

**Validation**:
- Config:
  - min and max REQUIRED
  - min < max (strict)
  - step > 0
  - step <= (max - min) (warning but accepted)
- Value: min/max/multipleOf enforced by JSON Schema

**Defaults**:
- step: 1

**Edge cases**:
- min >= max: error in FieldConfigValidator
- step <= 0: error in FieldConfigValidator
- min_label/max_label: if not defined, no labels displayed

---

### 6. CHOICE

**Description**: Single or multiple choice from predefined options.

**Usage**: Categories, tags, selections.

**Config**:
```json
{
  "options": ["Option A", "Option B", "Option C"],  // REQUIRED, min 2 options
  "multiple": false,      // Optional (default false)
  "allow_custom": false,  // Optional (default false) - NOT IMPLEMENTED V1, for future
  "default_value": "Option A"  // Optional (single) or ["Option A"] (multiple)
}
```

**JSON Schema generated (single)**:
```json
{
  "type": "string",
  "enum": ["Option A", "Option B", "Option C"],
  "description": "..."
}
```

**JSON Schema generated (multiple)**:
```json
{
  "type": "array",
  "items": {
    "type": "string",
    "enum": ["Option A", "Option B", "Option C"]
  },
  "uniqueItems": true,
  "description": "..."
}
```

**UI Rendering**:
- Single: Dropdown
- Multiple: Multi-select

**Value format**:
- Single: `"Option A"` (string)
- Multiple: `["Option A", "Option C"]` (array)

**Default value**:
- Single: string value
- Multiple: array of strings

**Validation**:
- Config:
  - options array REQUIRED
  - options.length >= 2
  - No duplicate options
  - multiple is boolean
  - If multiple=true and default_value provided, must be array
  - If multiple=false and default_value provided, must be string
- Value:
  - Single: must be in enum (JSON Schema)
  - Multiple: array, each item in enum, no duplicates (JSON Schema uniqueItems)

**Defaults**:
- multiple: false
- allow_custom: false (not implemented V1)

**Edge cases**:
- Empty options or < 2: error in FieldConfigValidator
- Duplicate options: error in FieldConfigValidator
- Multiple with empty selection: null (not empty array)

---

### 7. BOOLEAN

**Description**: Boolean true/false value.

**Usage**: Flags, binary options, confirmations.

**Config**:
```json
{
  "true_label": "label_yes",    // Optional, i18n key (default "label_yes")
  "false_label": "label_no",    // Optional, i18n key (default "label_no")
  "default_value": false        // Optional (default false)
}
```

**JSON Schema generated**:
```json
{
  "type": "boolean",
  "description": "..."
}
```

**UI Rendering**:
- Toggle switch
- Labels next to toggle (translated from i18n keys)
- 2 states only (true/false, no null)

**Value format**: `true` or `false` (boolean)

**Default value**: Supported via `default_value` in config (default false)

**Validation**:
- Config:
  - true_label and false_label must be non-empty strings if provided
- Value:
  - Type boolean required (JSON Schema)
  - null NOT allowed (field is optional at custom_fields level, but if present must be boolean)

**Defaults**:
- true_label: "label_yes"
- false_label: "label_no"
- default_value: false

**Edge cases**:
- Distinction: null = field not present, false = explicitly false
- User can omit field entirely (optional) but if present must be true or false

---

### 8. RANGE

**Description**: Range between two numeric values.

**Usage**: Time ranges converted to minutes, age ranges, intervals.

**Config**:
```json
{
  "min": 0,          // Optional - absolute minimum
  "max": 1000,       // Optional - absolute maximum
  "unit": "kg",      // Optional - displayed unit
  "decimals": 0,     // Optional - precision (default 0)
  "default_value": { // Optional
    "start": 10,
    "end": 20
  }
}
```

**JSON Schema generated**:
```json
{
  "type": "object",
  "properties": {
    "start": {
      "type": "number",
      "minimum": 0,      // If min defined
      "maximum": 1000    // If max defined
    },
    "end": {
      "type": "number",
      "minimum": 0,
      "maximum": 1000
    }
  },
  "required": ["start", "end"],
  "description": "..."
}
```

**Business constraint**: `start <= end` (validated by FieldValueValidator)

**UI Rendering**:
- Two numeric fields side by side (start, end)
- Unit suffix if defined
- Both fields required together

**Value format**:
```json
{
  "start": 10.5,
  "end": 25.0
}
```

**Default value**: Supported via `default_value` in config (must be complete object with start and end)

**Validation**:
- Config:
  - If both min and max defined: `min <= max`
  - decimals >= 0
  - If default_value provided: must be object with start and end, start <= end
- Value:
  - start and end REQUIRED if value non-null (JSON Schema)
  - start <= end (FieldValueValidator custom constraint)
  - min/max applied to both start and end (JSON Schema)

**Defaults**:
- decimals: 0

**Edge cases**:
- start > end: error in FieldValueValidator
- min > max in config: error in FieldConfigValidator
- start or end alone: invalid, complete object required
- null value: entire field absent (valid if field optional)

---

### 9. DATE

**Description**: Date (day/month/year).

**Usage**: Event dates, birthdays, deadlines.

**Config**:
```json
{
  "min": "2020-01-01",  // Optional - minimum date (ISO 8601)
  "max": "2030-12-31",  // Optional - maximum date (ISO 8601)
  "default_value": "2025-01-01"  // Optional (ISO 8601)
}
```

**JSON Schema generated**:
```json
{
  "type": "string",
  "format": "date",      // ISO 8601: YYYY-MM-DD
  "description": "..."
}
```

**Note**: JSON Schema `format: "date"` provides validation, but min/max require custom validation or pattern

**UI Rendering**:
- `UI.DatePicker` (existing component)
- Formatted display according to locale
- ISO 8601 storage

**Value format**: `"2025-11-11"` (ISO 8601 string YYYY-MM-DD)

**Default value**: Supported via `default_value` in config (ISO 8601 string)

**Validation**:
- Config:
  - min and max must be valid ISO 8601 dates if provided
  - If both defined: min <= max
  - default_value must be valid ISO 8601 date and within min/max if defined
- Value:
  - ISO 8601 format YYYY-MM-DD required
  - min/max enforced (custom validation in FieldValueValidator if JSON Schema insufficient)

**Defaults**: None

**Edge cases**:
- min > max: error in FieldConfigValidator
- Invalid format: error in validation
- Invalid dates (Feb 31): rejected by parser
- Timezone: not stored (date only)

---

### 10. TIME

**Description**: Time (hh:mm).

**Usage**: Wake times, schedules, time of day.

**Config**:
```json
{
  "format": "24h",        // Optional: "24h" or "12h" (default "24h")
  "default_value": "14:30"  // Optional (HH:MM string, always 24h format)
}
```

**JSON Schema generated**:
```json
{
  "type": "string",
  "pattern": "^([01]?[0-9]|2[0-3]):[0-5][0-9]$",  // HH:MM
  "description": "..."
}
```

**UI Rendering**:
- `UI.TimePicker` (existing component)
- Display format 24h or 12h according to config
- Storage always 24h

**Value format**: `"14:30"` (HH:MM string, always 24h in storage)

**Default value**: Supported via `default_value` in config (HH:MM string)

**Validation**:
- Config:
  - format must be "24h" or "12h" if provided
  - default_value must match pattern HH:MM if provided
- Value:
  - Format HH:MM required (pattern validation)
  - HH between 00-23
  - MM between 00-59

**Defaults**:
- format: "24h"

**Edge cases**:
- Storage always 24h, format is UI only
- No seconds in V1

---

### 11. DATETIME

**Description**: Combined date and time.

**Usage**: Precise timestamps, appointments, dated events.

**Config**:
```json
{
  "min": "2020-01-01T00:00:00",  // Optional - minimum datetime (ISO 8601)
  "max": "2030-12-31T23:59:59",  // Optional - maximum datetime
  "time_format": "24h",            // Optional: time display format
  "default_value": "2025-11-11T14:30:00"  // Optional (ISO 8601)
}
```

**JSON Schema generated**:
```json
{
  "type": "string",
  "format": "date-time",  // ISO 8601: YYYY-MM-DDTHH:MM:SS
  "description": "..."
}
```

**UI Rendering**:
- `UI.DatePicker` + `UI.TimePicker` side by side
- Combined into single ISO 8601 string

**Value format**: `"2025-11-11T14:30:00"` (ISO 8601 string without timezone)

**Default value**: Supported via `default_value` in config (ISO 8601 string)

**Validation**:
- Config:
  - min and max must be valid ISO 8601 datetime if provided
  - If both defined: min <= max
  - time_format must be "24h" or "12h" if provided
  - default_value must be valid ISO 8601 datetime and within min/max if defined
- Value:
  - ISO 8601 format YYYY-MM-DDTHH:MM:SS required
  - min/max enforced (custom validation if needed)

**Defaults**:
- time_format: "24h"

**Edge cases**:
- Timezone: stored without timezone (local implied)
- Seconds: always :00 in V1 (no seconds picker)
- min > max: error in FieldConfigValidator

---

## Value Formatting (Display)

**Purpose**: Format field values as human-readable strings for display in read-only views, lists, cards, history, and exports.

**Implementation**: Extension function on `FieldDefinition`:
```kotlin
fun FieldDefinition.formatValue(value: Any?, context: Context): String
```

**General rule**: All null/empty values return `s.shared("label_no_value")`

---

### 1. TEXT_SHORT, TEXT_LONG, TEXT_UNLIMITED

**Format**: Display the text as-is

**Null/Empty**: `s.shared("label_no_value")`

**Examples**:
- `"My short text"` → `"My short text"`
- `null` → `"Aucune valeur"`
- `""` → `"Aucune valeur"`

**Note**: Truncation for previews is decided by caller, not by formatValue

---

### 2. NUMERIC

**Format**: Number formatted with decimals + unit suffix if defined

**Decimals**: No trailing zeros (e.g., `10.0` → `"10"`, `10.50` → `"10.5"`, `10.12` → `"10.12"`)

**Unit**: Append unit with space if defined in config

**Null**: `s.shared("label_no_value")`

**Examples**:
- `42.5` with unit="kg" → `"42.5 kg"`
- `150` with unit="kg" → `"150 kg"`
- `42` with no unit → `"42"`
- `10.00` with decimals=2 → `"10"` (no trailing zeros)
- `null` → `"Aucune valeur"`

**Implementation**:
```kotlin
val formatted = if (config.decimals > 0) {
    value.stripTrailingZeros()
} else {
    value.toInt().toString()
}
val unit = config.unit?.let { " $it" } ?: ""
return "$formatted$unit"
```

---

### 3. SCALE

**Format**: `"value (min à max - "min_label" à "max_label")"`

**Components**:
- Value: Current rating
- Range: min à max
- Labels: Translated from i18n keys if defined

**Null**: `s.shared("label_no_value")`

**Examples**:
- `7` with min=1, max=10, min_label="label_scale_bad", max_label="label_scale_excellent"
  → `"7 (1 à 10 - "Mauvais" à "Excellent")"`
- `5` with min=0, max=10, no labels
  → `"5 (0 à 10)"`
- `null` → `"Aucune valeur"`

**Implementation**:
```kotlin
val minLabel = config.min_label?.let { s.shared(it) } ?: ""
val maxLabel = config.max_label?.let { s.shared(it) } ?: ""
val labelsStr = if (minLabel.isNotEmpty() && maxLabel.isNotEmpty()) {
    " - \"$minLabel\" à \"$maxLabel\""
} else {
    ""
}
return "$value (${config.min} à ${config.max}$labelsStr)"
```

---

### 4. CHOICE (single)

**Format**: Display the selected option as-is

**Null**: `s.shared("label_no_value")`

**Examples**:
- `"Option A"` → `"Option A"`
- `null` → `"Aucune valeur"`

---

### 5. CHOICE (multiple)

**Format**: Join selected options with comma-space separator

**No limit**: Display all selected options

**Empty/Null**: `s.shared("label_no_value")`

**Examples**:
- `["Option A", "Option C"]` → `"Option A, Option C"`
- `["A", "B", "C", "D", "E"]` → `"A, B, C, D, E"` (no truncation)
- `[]` → `"Aucune valeur"`
- `null` → `"Aucune valeur"`

**Implementation**:
```kotlin
return (value as? List<*>)?.joinToString(", ") ?: s.shared("label_no_value")
```

---

### 6. BOOLEAN

**Format**: Display translated label according to value

**Labels**: Use config.true_label or config.false_label (i18n keys), defaults to "label_yes"/"label_no"

**Null**: `s.shared("label_no_value")` (should not happen with 2-state toggle, but fallback)

**Examples**:
- `true` with true_label="label_yes" → `"Oui"`
- `false` with false_label="label_no" → `"Non"`
- `true` with custom true_label="label_active" → `"Actif"`
- `null` → `"Aucune valeur"` (fallback)

**Implementation**:
```kotlin
return when (value) {
    true -> s.shared(config.true_label ?: "label_yes")
    false -> s.shared(config.false_label ?: "label_no")
    else -> s.shared("label_no_value")
}
```

---

### 7. RANGE

**Format**: `"start - end unit"` with decimals formatting (no trailing zeros)

**Unit**: Append unit with space if defined in config

**Null**: `s.shared("label_no_value")`

**Examples**:
- `{"start": 10.5, "end": 25.0}` with unit="kg" → `"10.5 - 25 kg"`
- `{"start": 10, "end": 20}` with unit="kg" → `"10 - 20 kg"`
- `{"start": 5.5, "end": 12.3}` with no unit → `"5.5 - 12.3"`
- `null` → `"Aucune valeur"`

**Implementation**:
```kotlin
val obj = value as? JSONObject ?: return s.shared("label_no_value")
val start = obj.getDouble("start").stripTrailingZeros()
val end = obj.getDouble("end").stripTrailingZeros()
val unit = config.unit?.let { " $it" } ?: ""
return "$start - $end$unit"
```

---

### 8. DATE

**Format**: Short format by default (locale-specific)

**Formats**:
- Short (default): `"11/11/2025"` (locale-dependent separator)
- Long: `"11 novembre 2025"` (for specific use cases, not default)

**Null**: `s.shared("label_no_value")`

**Examples**:
- `"2025-11-11"` → `"11/11/2025"` (short format, default)
- `null` → `"Aucune valeur"`

**Implementation**: Use DateUtils for locale-aware formatting

---

### 9. TIME

**Format**: According to config.format ("24h" or "12h")

**Null**: `s.shared("label_no_value")`

**Examples**:
- `"14:30"` with format="24h" → `"14:30"`
- `"14:30"` with format="12h" → `"2:30 PM"`
- `"09:00"` with format="12h" → `"9:00 AM"`
- `null` → `"Aucune valeur"`

**Implementation**: Convert from 24h storage to display format

---

### 10. DATETIME

**Format**: Short format by default (date + time combined)

**Formats**:
- Short (default): `"11/11/2025 14:30"`
- Long: `"11 novembre 2025 à 14:30"` (for specific use cases, not default)

**Time format**: According to config.time_format

**Null**: `s.shared("label_no_value")`

**Examples**:
- `"2025-11-11T14:30:00"` with time_format="24h" → `"11/11/2025 14:30"` (short, default)
- `"2025-11-11T14:30:00"` with time_format="12h" → `"11/11/2025 2:30 PM"`
- `null` → `"Aucune valeur"`

**Implementation**: Split date and time, format separately, combine with space

---

## Validation Architecture

### FieldConfigValidator

**Purpose**: Validate field definition (config) when creating/editing custom field definitions.

**Input**: `FieldDefinition` + `existingFields`

**Called by**: `ToolInstanceService.update()` when processing custom_fields in config

**Validates**:
- Unique name (no collision with existing fields)
- Type-specific config constraints:
  - NUMERIC: min <= max (if both defined), decimals >= 0, step > 0
  - SCALE: min < max (required), step > 0
  - CHOICE: options.length >= 2, no duplicates, multiple is boolean
  - BOOLEAN: labels non-empty if provided
  - RANGE: min <= max (if both defined), decimals >= 0
  - DATE: min <= max (if both defined), valid ISO 8601 dates
  - TIME: format in ["24h", "12h"]
  - DATETIME: min <= max (if both defined), valid ISO 8601 datetimes, format in ["24h", "12h"]
- default_value consistency with type and constraints

**Returns**: `ValidationResult(isValid: Boolean, errorMessage: String?)`

---

### FieldValueValidator

**Purpose**: Validate field values (data) for business constraints not expressible in JSON Schema.

**Input**: `FieldDefinition` + `value` + `Context`

**Called by**: `ToolDataService.execute()` after SchemaValidator, before persistence

**Validates**:
- RANGE: `start <= end` (custom constraint)
- DATE/DATETIME: min/max if JSON Schema insufficient
- Other types: generally handled by JSON Schema, custom validation only if needed

**Returns**: `ValidationResult(isValid: Boolean, errorMessage: String?)`

---

## Implementation Components

### Modified Components

1. **FieldType.kt**: Add all new enum values
2. **FieldTypeSchemaProvider.kt**: Add schemas for all types
3. **FieldSchemaGenerator.kt**: Rename + implement all type schemas
4. **FieldInputRenderer.kt**: Rename components + implement all type renderers
5. **FieldConfigValidator.kt**: Implement config validation for all types
6. **FieldValueValidator.kt**: NEW - Implement value validation (RANGE.start <= end, etc.)
7. **UITypes.kt**: Verify all FieldType UI mappings exist

### New UI Components Needed

1. **Slider with step support**: Modify or create for SCALE type
2. **Dropdown/MultiSelect**: For CHOICE single/multiple
3. **NumericInput with +/- buttons**: For NUMERIC type
4. **Range input**: Two numeric fields side by side for RANGE

### Strings to Add

All field type display names and descriptions via i18n:
- `field_type_text_short_display_name`
- `field_type_text_long_display_name`
- `field_type_numeric_display_name`
- `field_type_scale_display_name`
- `field_type_choice_display_name`
- `field_type_boolean_display_name`
- `field_type_range_display_name`
- `field_type_date_display_name`
- `field_type_time_display_name`
- `field_type_datetime_display_name`
- Error messages for validation
- Default labels (label_yes, label_no, label_scale_bad, label_scale_excellent, etc.)

---

## Implementation Order

1. **FieldType enum** + strings for all types
2. **FieldConfigValidator** complete implementation
3. **FieldValueValidator** NEW implementation
4. **FieldSchemaGenerator** (rename + all types)
5. **FieldTypeSchemaProvider** update for all types
6. **UI Components** new/modified (Slider, Dropdown, NumericInput, etc.)
7. **FieldInputRenderer** (rename + all types)
8. **Integration testing** with Journal tooltype
9. **Documentation** update CUSTOM_FIELDS.md

---

**Document Version**: 2.0
**Date**: 2025-01-11
**Status**: Complete specifications ready for implementation
