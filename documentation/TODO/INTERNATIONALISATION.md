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
