# Conditions typées à l'écriture

Conçu le 2026-10-01, après la recette de la zone Panorama : un filtre `=` sur un champ à choix (graphique « Repas par moment ») et une constante « PT7H » face à une durée (critère d'Objectif « Sommeil moyen ≥ 7 h ») ont été acceptés à l'enregistrement, puis ont échoué à la lecture et à la validation.

## Le problème

Une condition (`{"left", "op", "right"}`, `docs/BRICKS.md`) n'a de type que par ce qu'elle compare : la constante prend celui d'en face. Rien n'applique cette règle quand une config est écrite. Le type n'intervient qu'au moment de juger la condition ou de lire le graphique. D'où deux défauts :

- **la conversion** : une constante écrite par l'IA dans son format (`"PT7H"`, une date ISO, une date relative) n'est jamais traduite dans la forme stockée, faute de savoir qu'elle fait face à une durée ou à un instant ;
- **la vérification** : un opérateur qui ne va pas avec le type, ou une constante du mauvais type, passe l'enregistrement.

## Les décisions

- **Le travail se fait à l'écriture**, une fois, pour toute config qui contient des conditions : ce qui est stocké est typé et juste, la lecture n'a plus à douter. Écarté : vérifier à la lecture seulement, qui laisse entrer des données fausses et fait payer chaque lecture.
- **Un seul parcours, dans le cœur, trouve les conditions d'une config par sa déclaration.** Il suit les `SettingNode` en même temps que la valeur, descend dans les termes, les lectures et les sélections (`SettingNode.Condition`, `SettingNode.Term`, `SettingNode.Selection`), et rend chaque condition avec le type de ses côtés. Un type d'outil qui déclare des conditions est vérifié sans rien écrire. Les conditions posées sur les lignes d'un tableau (`onRow`, les colonnes d'une couche de Graphique) prennent les types de leurs champs aux `RowFields` du type d'outil, comme le formulaire. Écarté : une vérification par type d'outil dans `refuseConfig`, répétée et à refaire pour chaque outil à conditions (l'Alerte, le relevé).
- **Le type d'un côté se connaît sans lire de données** : un champ par la déclaration de l'outil visé ; une variable par son type déclaré ; une lecture par son champ et sa réduction (un compte donne un nombre, une moyenne de durées une durée). C'est une pièce neuve de la brique Terme : `TermReader` ne connaît aujourd'hui le type qu'en lisant.
- **La traduction des valeurs de l'IA se fait à l'entrée**, comme toute date et toute durée (`docs/reference.md`) : quand une commande de l'IA crée ou modifie un outil, le parcours trouve chaque constante, et la traduit dans la forme stockée selon le type d'en face, avant que la config n'atteigne le service. Écarté : traduire dans le service selon l'origine, qui ferait entrer le format de l'IA au cœur de l'app.
- **Le service vérifie, quelle que soit l'origine**, dans le passage obligé de toute écriture de config (`ToolInstanceService.checkConfig`). Il refuse en nommant la condition :
  1. l'opérateur permis par le type (`EntryFilters.operatorsFor`) ;
  2. la constante lisible dans le type d'en face (un nombre face à un nombre, des millisecondes face à une durée, oui/non face à oui/non) ;
  3. deux côtés comparables quand aucun n'est une constante (une variable en kcal face à une durée est refusée) ;
  4. pour un choix, chaque valeur citée parmi les options du champ, choix ouvert compris : une valeur absente ne trouverait jamais rien, et c'est presque toujours une faute de frappe. Ne vaut que pour les conditions, pas pour les valeurs écrites dans les entrées (question laissée ouverte dans `NOTES.md`).
- **Un changement de l'outil visé est refusé** tant qu'une condition ailleurs dépend de ce qui disparaît ou change de type (un champ supprimé ou renommé, une option retirée, un type changé), en nommant les outils concernés. Il faut savoir, pour un outil, quelles configs le visent : le même parcours, sur toutes les configs de l'app. Écartés pour l'instant : laisser faire en signalant ; reporter automatiquement un renommage dans les conditions, qui pourra venir si refuser devient pénible.
- **Les configs déjà enregistrées** qui ne passeraient pas la vérification restent telles quelles : elles échouent à la lecture avec leur message, et leur prochaine modification est refusée tant qu'elles ne sont pas corrigées.

## À voir en implémentant

- Les variables : où leur type est déclaré, et si le parcours doit aussi couvrir le terme d'une variable (une lecture et ses filtres).
- Le prompt de l'IA : redire qu'une constante s'écrit dans le format de l'IA quel que soit l'endroit, durée comprise, et retirer ce que la conversation du 2026-09-29 a montré de contradictoire (l'IA a fini par écrire 25200000).
