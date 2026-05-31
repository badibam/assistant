@/mnt/data/OUTILS/socle/dev_base.md

## Où on en est

App Android (assistant personnel Claude). Architecture définie et stable — lire les docs de référence et les commits récents pour le contexte détaillé.

## Fichiers essentiels

@README.md
@CORE.md
@TOOLS.md
@UI.md
@DATA.md
@AI.md

## Fichiers de référence

## Règles du projet

- Commits en anglais (exception aux règles universelles — anciennement en français)
- Commentaires et debug en anglais
- Utiliser SYSTÉMATIQUEMENT le système de strings : `s.tool()`, `s.shared()` — aucune string hardcodée
- Ne JAMAIS implémenter de mécanisme fallback sans validation explicite
- Toujours vérifier les patterns dans la doc avant d'implémenter
- Ne jamais laisser de code legacy
- Commenter extensivement le code pour référence ultérieure
- Compiler avec `compileDebugKotlin` (grep `^e:|^Error:|^ERROR:|BUILD SUCCESSFUL|BUILD FAILED`)
- Générer les strings (gradle) : `generateStringResources`
- Respecter l'architecture finale définie dans les docs

$ARGUMENTS
