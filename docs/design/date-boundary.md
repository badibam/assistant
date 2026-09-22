# La frontière des dates

Décision du 2026-09-22, à implémenter. À élaguer une fois les cinq étapes faites — le code et les commits deviennent le registre.

## Ce qui est décidé

Une date circule en millisecondes partout dans l'app : interface, dispatcher, services, base. Elle ne prend une forme textuelle — ISO 8601, ou une période relative — qu'en face de l'IA.

Le critère : une conversion vit là où l'information pour la faire existe, et nulle part ailleurs. Résoudre une date venue de l'IA demande le texte, un instant de référence, le fuseau de l'app et le type déclaré du champ. Une période relative le montre bien : `-7_DAY` n'a aucun sens sans référence ni fuseau, ne peut pas être stockée, ne fait pas d'aller-retour. Elle n'existe qu'au moment où on lit la commande. L'interface n'a besoin d'aucune des quatre : elle tient déjà un Long.

Cela remplace le contrat écrit en tête de `DateTimeConverter.kt` à la construction de l'infrastructure, qui plaçait la frontière sur « UI + IA ».

## Ce qui est mesuré aujourd'hui

`ToolDataService` est la frontière, pour tout le monde et dans les deux sens : quatre conversions de charge utile à l'entrée (`data` et `custom_fields`, création et mise à jour), quatre à la sortie, plus six conversions de `timestamp`, `created_at` et `updated_at`.

Conséquences observées :

- Le service teste le type du paramètre `timestamp` reçu : Long pour l'interface, chaîne ISO pour l'IA. Le repli silencieux sur l'instant présent qu'un troisième type déclenchait est parti avec `9c28106` ; le test de type, lui, reste jusqu'à l'étape 2.
- Le service rend de l'ISO à l'interface, qui le reparse aussitôt en Long : `JournalEntryScreen`, `JournalScreen`, `NotesScreen`, `TrackingHistory`, `MessagesScreen`. L'aller-retour existe déjà, sur cinq écrans.
- `DateTimeFormatter.formatForDisplay` accepte Long ou chaîne et lève sur le reste, parce qu'il reçoit les deux.
- La conversion de sortie ne se déclenche que sur un nom de clé connu, là où l'entrée se déclenche aussi sur la forme. D'où le sens unique mesuré par `1a9890c` : un champ DATETIME entre en ISO et ressort en nombre. La sortie ne peut pas se corriger par la forme — un timestamp est un Long, et aucun Long ne se distingue d'un autre. C'est le schéma qui sait quels champs sont des dates.

La frontière IA, elle, existe déjà pour une famille de dates : `CommandTransformer.resolvePeriodBound` reconnaît le relatif, `NOW` et l'ISO, résout contre un instant de référence injecté, et lève une erreur lisible sur le reste. Le travail consiste à l'étendre, pas à l'inventer.

## Ordre d'implémentation

Chaque étape laisse l'app fonctionnelle. Les étapes 1 et 2 vont ensemble, 3 et 4 aussi.

1. La conversion des charges utiles de l'IA, ISO vers millisecondes, en réutilisant `isoToTimestamps`. Elle va dans `AICommandProcessor.transformActionCommand`, sur les branches `CREATE_DATA` et `UPDATE_DATA` qui passent déjà par `enrichWithSchemaId` — et non dans `CommandTransformer`, qui sert aussi les commandes que l'utilisateur compose dans ses blocs pointer et ne verrait donc pas que l'IA. Les écritures ne viennent que du chemin IA ; les blocs pointer ne portent que des lectures. Reste à établir la forme des paramètres de `batch_create` et `batch_update` pour convertir à l'intérieur des entrées. Le service reçoit alors des millisecondes de tout le monde.
2. `ToolDataService` cesse de convertir à l'entrée. Bloqué : voir « Le champ DATETIME » ci-dessous. La moitié qui ne dépendait de rien est faite (`9c28106`).
3. La sérialisation des résultats destinés à l'IA convertit millisecondes vers ISO, pilotée par le schéma pour les champs personnalisés. Point d'entrée à localiser dans `AIEventProcessor`.
4. `ToolDataService` cesse de convertir à la sortie. Les cinq écrans qui reparsent l'ISO reçoivent des Longs et cessent de parser. La branche chaîne de `DateTimeFormatter.formatForDisplay` devient morte et part.
5. L'en-tête de `DateTimeConverter` est réécrit : les interfaces externes ne sont plus « UI + IA », seulement l'IA. Les deux items de `TODO.md` — la conversion à sens unique, et le contrat de `timestamp` sur `tool_data` — sortent.

## Ce que ça déplace ailleurs

`CommandTransformer` appelle `Strings.for(context)`, donc il figure parmi les fichiers que `TODO.md` dit bloqués pour les tests unitaires. Y concentrer davantage de conversion rend la décision Robolectric plus pressante : les vingt-deux cas de `DateTimeConverter` couvrent les fonctions, pas le choix de qui les appelle, et ce choix est ce que ces étapes changent.

## Le champ DATETIME, découvert à l'étape 1

Mesuré : l'éditeur de champ personnalisé DATETIME rend une chaîne ISO. `FieldInputRenderer` lit la valeur comme `String`, et ses deux sélecteurs écrivent `DateUtils.timestampToIso8601DateTime(...)`. L'interface est donc une seconde source d'ISO, à côté de l'IA, et retirer la conversion d'entrée du service ferait stocker ces valeurs en texte dans une colonne de nombres.

Mesuré aussi : `FieldTypeSchemaProvider` déclare DATETIME en `"type": "string", "format": "date-time"`, alors que ce qui est rangé en base est un nombre, parce que le service a converti après validation. Le schéma et le stockage ne disent pas la même chose, et `FieldValueValidator` laisse passer DATETIME sans rien vérifier en s'en remettant au schéma. C'est de là que vient le sens unique : la valeur ressort en nombre parce qu'elle est rangée en nombre, sous un schéma qui annonce une chaîne.

La question à trancher avant l'étape 2 : que déclare le schéma d'un champ DATETIME ? Les deux réponses tiennent, et elles ne coûtent pas la même chose.

- Un nombre. Le schéma dit ce qui est rangé, l'éditeur rend un Long, la validation porte sur des millisecondes, et l'ISO n'apparaît que dans ce que lit l'IA — donc le schéma que l'IA lit n'est plus celui du stockage.
- Une chaîne. Le stockage garde l'ISO pour ce type de champ, le service cesse de le convertir, et une date de champ personnalisé est du texte partout. Cohérent avec ce que le schéma annonce déjà, mais ça met une famille de dates hors du contrat général.

Les valeurs déjà en base sont des nombres sous un schéma qui annonce une chaîne : les deux réponses demandent de regarder ce qui est stocké avant de changer quoi que ce soit.
