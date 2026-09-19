# manifest — assistant

## dev_base @ b263dd7
## universel @ 02360ea
! 2142abc  tracking stores its derived 'raw' display text in data; moving it to read time touches every read path (AI queries, backup), deferred to the tracking rewrite
! 75da8fc  OpenAI base URL and LiteLLM price list are hardcoded; a configurable host needs a decision on model listing and pricing for unknown hosts
- f6a60f3  generated files that only a tool outside the repo can rebuild (theme drawables and GeneratedThemeResources.kt, via npx) stay versioned: the build must never need that tool
## android @ bf72ce6
! 9b59e58  targetSdk stays 34: target 35+ forces edge-to-edge, every screen to rework and check on a phone
! 9b59e58  string resources default to French; English default and translation of ~1500 strings to do before the first F-Droid release
! 9b59e58  UpdateManager keeps its 3 values in SharedPreferences; converting to DataStore waits for the updater's move into its own build flavor (F-Droid spec)
- 9b59e58  screens call the command dispatcher (Coordinator) directly and keep only view state; the dispatcher and its services are the controller layer
## fdroid @ bf72ce6
! b2320db  the in-app updater downloads APKs from GitHub; it moves to its own build flavor, left out of the F-Droid build (F-Droid spec §1)
! b2320db  no fastlane store listing yet; en-US text and screenshots come with the English default, before the first submission
