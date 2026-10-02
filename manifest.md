# manifest — assistant

## dev_base @ b263dd7
## universel @ 5f92093
- f6a60f3  generated files stay versioned (icon drawables and index, from scripts/generate_icons.py over third_party/lucide): the build must never need the generator or its inputs
## android @ a500d4f
! 9b59e58  targetSdk stays 34: target 35+ forces edge-to-edge, every screen to rework and check on a phone
- 9b59e58  screens call the command dispatcher (Coordinator) directly and keep only view state; the dispatcher and its services are the controller layer
## fdroid @ bf72ce6
! b2320db  no fastlane store listing yet; en-US text and screenshots come with the English default, before the first submission
## pixel-ui @ 0db9979
! 95688669  the retro theme is designed (docs/design/retro-theme.md), not built: nothing of the module is applied yet
## ia-service-distant @ 80c1a43
