---
argument-hint: [additional-files...]
description: Initialize development context by reading all documentation and specified files
allowed-tools: Read
---

# L'app a été créée avec l'architecture finale souhaitée, il est important de la respecter.

# Quand je te demande de commit :
- Tu commites SANS MENTIONNER CLAUDE dans le message de commit.

# Tu vas lire TOUS les fichiers de doc :
- @README.md
- @CORE.md
- @TOOLS.md
- @UI.md
- @DATA.md
- @AI.md

# Tu vas aussi lire les fichiers suivants :
$ARGUMENTS

# Tu vas également regarder en détail les commits récents pour comprendre où en est le développement.

# Règles d'or:
- Ne fais jamais d'hypothèses sur ce que je pense ou ressens. Réponds factuellement sans interpréter mes intentions ou valider mes supposées opinions. Si je pose une question "pourquoi", c'est pour savoir pourquoi, pas pour remettre en cause ce que tu dis. Affirme-toi et assume tes choix s'il te plaît. N'hésite pas à me contredire. Vraiment.
- Ne JAMAIS utiliser de mécanisme fallback sans valider explicitement avec moi
- TOUJOURS vérifier les patterns dans la doc et / ou dans les autres fichiers avant d'implémenter une logique...
- Commentaires, debug et commits : toujours en anglais
- Utilise SYSTEMATIQUEMENT le système de strings (cf CORE.md) : s.tool(), s.shared() : pas de 'string' hardcodée. JAMAIS. AUCUNE.
- Ne jamais laisser/créer de code 'legacy'
- Tu commentes extensivement le code pour pouvoir s'y référer pour générer une doc de l'app ultérieurement
- Tu es très patient et tu fais toutes les modifications nécessaires, dans les règles de l'art, pour arriver au résultat souhaité tout en respectant scrupuleusement les règles d'or.
- Pour compiler tu utilises compileDebugKotlin avec un grep sur les "^e:|^Error:|^ERROR:|BUILD SUCCESSFUL|BUILD FAILED".
- La comande pour générer les strings (gradle) c'est generateStringResources
- Tu commites SANS MENTIONNER CLAUDE dans le message de commit. En anglais (avant c'était en français, now c'est en anglais.)
- N'utilise JAMAIS de Task agent ni de "explore"
