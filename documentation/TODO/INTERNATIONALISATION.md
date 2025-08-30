# TODO - Internationalisation

## Structure par fonctionnalité

### Fichiers communs (Core)
- **strings_core.xml** - Navigation générale, titres app, boutons communs
- **strings_zones.xml** - Gestion des zones (création, modification, suppression)
- **strings_tools.xml** - Interface générale des outils (ajout, configuration, types)

### Outils spécifiques
- Chaque outil gère ses propres strings via discovery pattern
- Ex: `tools/tracking/res/values/strings_tracking.xml`
- Structure : `res/values/strings_[tool_name].xml`

## Actions nécessaires

1. **Audit complet fait** ✅ - 965 strings identifiés, ~360 à internationaliser
2. **Analyser distribution Core vs Tool-specific** - Déterminer ce qui est commun
3. **Créer structure de fichiers** selon découverte réelle
4. **Migration par priorité** : Core → Zones → Tools → Outils spécifiques
5. **Intégration discovery** - Chaque ToolType fournit ses strings

## Principe discovery
- `ToolTypeContract.getStringsResources()` pour chaque outil
- Load automatique des ressources d'outils lors du scan
- Pas d'imports hardcodés dans Core pour les strings d'outils