# Le formulaire de config en pages

Conçu le 2026-10-01. Aujourd'hui, `SettingsForm` dessine l'arbre des réglages d'une config dans une seule page, chaque niveau en retrait du précédent. Dans le Graphique, le filtre d'une colonne de grille est à six niveaux (couche › colonnes › colonne › lu › Filtres), sans signe en surface, et chaque niveau retire de la largeur des deux côtés. Cette spec dit comment le formulaire présente cette profondeur. Elle vaut pour toute config déclarée en `SettingNode`, pas pour le seul Graphique.

## Une page par niveau

- **Un groupe qui contient un groupe ou une liste est une page** : dans la page de son parent, une ligne qui le résume ; la toucher ouvre sa page, en pleine largeur. Un groupe qui ne contient que des champs simples reste sur place, dans la page de son parent (l'échelle et l'axe d'un canal, le style d'une marque). La règle se lit dans la déclaration, sans rien déclarer de plus.
- **Un élément de liste suit la même règle** : un élément fait de groupes est une ligne qui ouvre sa page, un élément simple (une valeur d'un `fold`) se modifie sur place.
- **Une liste n'a pas de page à elle** : sous son titre, dans la page de son parent, une ligne par élément, puis « Ajouter » ; on y ajoute, retire et réordonne. Une colonne est donc à Couche › kcal, pas à Couche › Colonnes › kcal.
- **Les sélecteurs des briques restent sur place** (terme, sélection d'entrées, condition, période) : ils n'ont qu'un niveau, leurs filtres sont déjà une fenêtre (`PointerFiltersDialog`). Le filtre d'une colonne est donc à deux pages et une fenêtre : Couche › kcal, puis « Filtres ».
- **En haut de chaque page, le chemin** depuis la racine (Couche 1 › kcal) ; toucher un de ses éléments remonte à ce niveau. Le retour du téléphone remonte d'un niveau.
- Une recréation de l'écran garde la page ouverte et le brouillon.

## La ligne de résumé

- **Le résumé déclaré, complété par le formulaire** : les réglages que nomme `summary` (exigé aujourd'hui pour une liste de groupes, permis aussi pour un groupe), puis, de lui-même, le nombre d'éléments de chaque liste qu'il contient (« 2 filtres »), et la phrase de chaque sélecteur qu'il contient. Une colonne : « kcal — lit Repas › kcal, somme · 2 filtres ».
- **Chaque sélecteur de brique donne sa phrase** : ce qu'il désigne, et ce qui le restreint (`PointerDescription` le dit déjà pour une sélection).
- Deux lignes au plus, coupées sur « … ».

## Le brouillon et l'enregistrement

- **Un seul brouillon pour toute la config** : une page modifie le brouillon ; remonter garde ce qui a changé.
- **« Enregistrer » au bas de chaque page**, qui enregistre toute la config.
- **Quitter la racine avec des changements non enregistrés demande confirmation.**

## Les problèmes enfouis

- **Ce que le formulaire juge seul marque la ligne** de la page où il est, et chaque ligne au-dessus jusqu'à la racine : un réglage obligatoire vide, une valeur hors de ses bornes, une liste sous son nombre minimal d'éléments. On suit la marque jusqu'au réglage.
- **Un refus à l'enregistrement reste un message**, comme aujourd'hui : les refus du service (`ChartCheck` et les autres) sont des phrases sans chemin. Les faire désigner leur réglage, et ouvrir sa page, est un chantier à part, s'il manque.

## Hors de cette spec

- La config elle-même et son schéma : rien ne change pour l'IA ni pour le stockage.
- L'empilement affiché sur les deux axes d'une couche, alors qu'un seul compte (`ChartSceneBuilder`) : à revoir avec la page d'un canal, si ça trompe encore.
