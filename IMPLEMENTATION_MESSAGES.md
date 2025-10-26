# Messages Tool - Implementation Progress

## Status: Service Layer Complete (Phases 1-5 completed, Phase 6-7 pending)

---

## ✅ Phase 1: Centralized Scheduling Infrastructure (COMPLETED)

**Created:**
- `core/tools/ToolScheduler.kt` - Interface for tools with scheduling needs
- `core/tools/ToolTypeContract.kt` - Added `getScheduler()` method (default null)
- `core/scheduling/CoreScheduler.kt` - Centralized scheduler (AI + Tools)
- `core/scheduling/CoreSchedulerWorker.kt` - WorkManager background worker (15 min)

**Modified:**
- `core/ai/orchestration/AIOrchestrator.kt` - Removed internal heartbeat (delegated to CoreScheduler)
- `MainActivity.kt` - Initialize CoreScheduler, schedule CoreSchedulerWorker

**Result:** Scheduling centralized and extensible via discovery pattern. Tools add schedulers without core modifications.

---

## ✅ Phase 2: Notification Service (COMPLETED)

**Created:**
- `core/notifications/NotificationChannels.kt` - Registry for 3 priority channels (high/default/low)
- `core/notifications/NotificationService.kt` - Core service for Android notifications
- Strings in `shared.xml` - Channel names/descriptions, error messages

**Modified:**
- `core/coordinator/ServiceRegistry.kt` - Added NotificationService (resource: "notifications")
- `MainActivity.kt` - Initialize notification channels at startup

**Result:** Notification infrastructure ready, reusable by all tools (Messages, future Alerts).

---

## ✅ Phase 3: Data Layer & Architecture (COMPLETED)

**Architecture Decision Change:**
- **Initial plan:** Custom MessageData entity + MessageDao + migration
- **Final implementation:** Uses unified ToolDataEntity (no custom entity/DAO needed)
- **Rationale:** Messages fit perfectly in tool_data table (JSON value field for executions array)

**Created:**
- `core/utils/ScheduleConfigSchema.kt` - Complete JSON Schema for ScheduleConfig validation (all 6 patterns)
- `core/validation/SchemaUtils.embedScheduleConfig()` - Utility to embed ScheduleConfig in schemas via placeholder
- `core/validation/SchemaUtils.stripSystemManagedFields()` - Strips fields marked systemManaged from AI commands
- `core/validation/Schema.kt` - Added UTILITY category for reusable schemas
- `tools/messages/MessageToolType.kt` - Complete ToolTypeContract implementation with schemas
  - `messages_config` schema: default_priority, external_notifications (extends BaseConfigSchema)
  - `messages_data` schema: Custom schema with title, content, schedule (embedded), priority, triggers (stub), executions (systemManaged)
- Strings in `shared.xml` - Schedule validation descriptions

**Modified:**
- `core/ai/processing/AICommandProcessor.kt` - Added systemManaged field stripping in enrichWithSchemaId()
  - Retrieves schema via ToolTypeManager
  - Calls SchemaUtils.stripSystemManagedFields() on each entry
  - Prevents AI from modifying executions array

**Result:**
- Messages data layer complete using existing infrastructure
- systemManaged keyword prevents AI manipulation of scheduler-owned fields
- ScheduleConfig fully reusable via embedding
- No database migration needed (table exists)

---

## ✅ Phase 4: Business Logic (COMPLETED)

**Created:**
- `tools/messages/MessageService.kt` - Custom service extending ExecutableService
  - **CRUD operations:** Delegates to ToolDataService directly (not via Coordinator)
  - **Custom operations:** get_history (with filters), mark_read, mark_archived, stats
  - **Internal method:** appendExecution() called only by MessageScheduler
  - **Verbalization:** Implements verbalize() with delegation to ToolDataService for CRUD
- `tools/messages/scheduler/MessageScheduler.kt` - Implements ToolScheduler
  - Scans all Messages tool instances
  - For each message with schedule: checks nextExecutionTime
  - Creates execution entry with snapshots (title_snapshot, content_snapshot)
  - Sends notification via NotificationService (if external_notifications enabled)
  - Updates execution status (sent/failed)
  - **MVP simplification:** Sets nextExecutionTime to MAX_VALUE (one-time execution)
  - **TODO:** Implement proper next execution calculation with ScheduleCalculator

**Pattern followed:**
- MessageService → ToolDataService (direct call, not via Coordinator)
- Consistent with Journal/Tracking pattern for tools with custom operations

**Result:** Messages service layer functional with scheduler integration. Supports one-time scheduled messages.

---

## ✅ Phase 5: Tool Contract & Registration (COMPLETED)

**Created/Modified:**
- `tools/messages/MessageToolType.kt` - Complete ToolTypeContract implementation
  - Display name, description, suggested icons
  - Default config JSON
  - Schema provider (messages_config, messages_data)
  - Available operations list
  - Service: MessageService
  - DAO: DefaultExtendedToolDataDao (generic)
  - Database entities: ToolDataEntity (unified)
  - Scheduler: MessageScheduler
  - **UI screens:** Stubbed (TODOs in place)
- `core/tools/ToolTypeScanner.kt` - Registered "messages" → MessageToolType

**Result:** Messages tool discoverable by core. Service and scheduler automatically wired. Ready for UI layer.

---

## 🔄 Phase 6: UI (PENDING)

**TODO:**
- Create `tools/messages/ui/MessagesConfigScreen.kt`
  - ToolGeneralConfigSection (8 base fields + always_send)
  - FormSelection for default_priority (default/high/low)
  - Toggle for external_notifications
  - Save via ValidationHelper.validateAndSave()
- Create `tools/messages/ui/MessagesScreen.kt`
  - **Tab 1 "Messages reçus":** Execution history list with filters (checkboxes: Non lus/Lus/Archivés, OR logic, default Non lus)
  - **Tab 2 "Gestion messages":** Message templates CRUD with schedule configuration
  - Integration with ScheduleConfigEditor for schedule editing
  - Mark read/archived actions
- Create `tools/messages/ui/MessagesDisplayComponent.kt`
  - Minimal display for ToolCard (icon + title + unread badge)
- Add tool-specific strings in `tools/messages/strings.xml`
- Generate resources via gradle task

---

## 🔄 Phase 7: Validation & Testing (PENDING)

**TODO:**
- Compile tests (verify no errors)
- Create Messages instance via UI
- Create message template with schedule
- Verify scheduler tick creates execution
- Verify notification sent
- Test filters (read/archived)
- Test edge cases (schedule null, external_notifications false, priority variations)

---

## Architecture Decisions Summary

1. **Scheduling:** CoreScheduler discovers tool schedulers via ToolTypeContract.getScheduler()
2. **Notifications:** 3 app-wide channels (assistant_high/default/low), priority parameter routes to channel
3. **Data structure change:**
   - **Initial:** Config = templates, Data = instances
   - **Final:** Config = settings, Data = templates with executions array (systemManaged)
4. **Entity reuse:** Uses ToolDataEntity (no custom MessageData) → no migration needed
5. **Service pattern:** MessageService delegates CRUD to ToolDataService directly, implements custom operations
6. **AI protection:** systemManaged keyword + stripping in AICommandProcessor prevents AI from modifying executions
7. **Schedule reuse:** Messages embed ScheduleConfig schema via placeholder replacement (SchemaUtils)
8. **Execution snapshots:** Immutable title_snapshot, content_snapshot preserve what was sent (no priority_snapshot, superfluous)
9. **MVP scheduler:** One-time execution (nextExecutionTime → MAX_VALUE), TODO recurrence calculation

---

## Key Files Created

**Core infrastructure:**
- `core/scheduling/CoreScheduler.kt`
- `core/notifications/NotificationService.kt`
- `core/utils/ScheduleConfigSchema.kt`
- `core/validation/SchemaUtils.stripSystemManagedFields()`

**Messages tool:**
- `tools/messages/MessageToolType.kt` (270 lines - schemas, contract, discovery)
- `tools/messages/MessageService.kt` (400+ lines - operations, delegation, execution management)
- `tools/messages/scheduler/MessageScheduler.kt` (300+ lines - tick logic, notification sending)

**Modified core files:**
- `core/ai/processing/AICommandProcessor.kt` - systemManaged stripping
- `core/tools/ToolTypeScanner.kt` - Messages registration
- `core/validation/Schema.kt` - UTILITY category

---

**Next:** Phase 6 - UI implementation (ConfigScreen, Screen with 2 tabs, DisplayComponent, strings)
