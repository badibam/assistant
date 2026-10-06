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
! 8542e364  the grid's edit mode fades the other tiles by transparency (FADED in GridLayout), in the retro theme too: to be said by the theme (docs/design/retro-theme.md)
## ia-service-distant @ 85554e4
