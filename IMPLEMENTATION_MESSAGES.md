# Messages Tool - Implementation Progress

## Status: IN PROGRESS (Phases 1-2 completed, Phase 3 partially done)

---

## ✅ Phase 1: Centralized Scheduling Infrastructure

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

## ✅ Phase 2: Notification Service

**Created:**
- `core/notifications/NotificationChannels.kt` - Registry for 3 priority channels (high/default/low)
- `core/notifications/NotificationService.kt` - Core service for Android notifications
- Strings in `shared.xml` - Channel names/descriptions, error messages

**Modified:**
- `core/coordinator/ServiceRegistry.kt` - Added NotificationService (resource: "notifications")
- `MainActivity.kt` - Initialize notification channels at startup

**Result:** Notification infrastructure ready, reusable by all tools (Messages, future Alerts).

---

## 🔄 Phase 3: Data Layer (IN PROGRESS)

**Created:**
- `core/utils/ScheduleConfigSchema.kt` - Complete JSON Schema for ScheduleConfig validation (all 6 patterns inlined)
- `core/validation/SchemaUtils.embedScheduleConfig()` - Utility to embed ScheduleConfig in other schemas via placeholder
- Strings in `shared.xml` - Schedule validation descriptions (schedule_config_*, schedule_pattern, etc.)
- `tools/messages/` - Directory structure (data, ui, scheduler folders)

**TODO:**
- Create `tools/messages/MessagesSchemas.kt` (messages_config with embedded ScheduleConfig, messages_data)
- Create `tools/messages/data/MessageData.kt` entity
- Create `tools/messages/data/MessageDao.kt`
- Database migration (add message_data table)

**Notes:**
- ScheduleConfig schema uses inlined validation (no definitions) for easy reusability
- SchemaValidator already supports JSON Schema Draft 7 features (no modifications needed)
- Placeholder system chosen over $ref for simplicity and zero duplication

---

## 🔄 Phase 4: Business Logic (PENDING)

**TODO:**
- Create `MessageService.kt` (get, get_single, mark_read, archive, delete, stats)
- Create `MessageScheduler.kt` (implements ToolScheduler)

---

## 🔄 Phase 5: Tool Contract (PENDING)

**TODO:**
- Create `MessageToolType.kt` (implements ToolTypeContract)
- Register in `ToolTypeScanner`

---

## 🔄 Phase 6: UI (PENDING)

**TODO:**
- Create `MessagesConfigScreen.kt` (with ScheduleConfigEditor reuse)
- Create `MessagesScreen.kt` (list + filters + actions)
- Create `MessagesDisplayComponent.kt`
- Tool-specific strings

---

## 🔄 Phase 7: Validation (PENDING)

**TODO:**
- End-to-end tests (create instance → configure message → wait tick → verify notif + data)
- Edge cases (schedule null, external_notifications false, priority variations)

---

## Architecture Decisions

1. **Scheduling:** CoreScheduler discovers tool schedulers via ToolTypeContract.getScheduler()
2. **Notifications:** 3 app-wide channels (assistant_high/default/low), priority routes to channel
3. **Data structure:** Config = message templates, Data = sent instances (audit trail)
4. **Schedule reuse:** Messages reuse ScheduleConfig infrastructure (6 patterns, ScheduleCalculator, UI)
5. **On-demand generation:** Data entries created at tick time (not pre-generated)
6. **Schema reusability:** ScheduleConfig schema inlined (no definitions) for simple embedding via placeholder replacement

---

**Next:** Complete Phase 3 - MessagesSchemas.kt, MessageData entity, DAO, and database migration
