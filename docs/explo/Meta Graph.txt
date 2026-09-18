# Meta-Graph - Architecture

## Concept général

**Meta-Graph** = outil par zone, peut référencer d'autres zones
**Data-Graph** = abandonné (trop complexe, peu de valeur)

## Nœuds adressables

Tout peut être nœud: Zone, ToolInstance, Entry, Field. éventuellement data spécifique (dans ce cas ajouter simplement le tool_data_id.)
- Références explicites (adressage type "zones/id", "tools/instance_id", etc.)
- Pas de distinction automatique entre "structure" et "data".
- User/IA décide quoi mettre dans le graphe (pas de logique automatique)

## Types de liens

**Définis dans config du Meta-Graph tool**
- Chaque type a des custom fields (réutilise pattern existant)
- Exemple: type "correlation" avec fields {r, p_value, source}

**Directionalité**
- Liens directionnels par défaut
- Config du type définit:
  - is_symmetric: boolean
  - forward_label: string (ex: "influence")
  - backward_label: string (ex: "influencé par")
- Storage: un seul lien (source → target)
- Queries: filtrent par source OU target
- UI: affiche le bon label selon sens de navigation

## Meta-Graph vs Stats

**Stats** = exploration, calcul, découverte
**Meta-Graph** = registre patterns validés/actionnables
**Flow**: Stats découvre → User/IA ajoute → Meta-Graph documente

## Gestion et exploitation

**Aucune logique hardcodée**
- Automations AI (pas APP) consultent/modifient le graphe
- Meta-Graph = donnée passive
- Accès via commandes IA standard: dataCommands (queries) et actionCommands (actions)
- L'IA décide elle-même comment interpréter et exploiter le graphe

## UI

Deux modes: liste + visualisation graphe

## Insights

Pas d'objet dédié
= Actions sur data existantes via automations AI basées sur Meta-Graph
