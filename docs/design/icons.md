# Icônes — conception

Spec transitoire : élaguée une fois le code en place, ses garanties devenues tests.

## Le problème

L'app n'embarque que deux icônes (`activity`, `trending-up`). Les défauts des outils (`book-open`, `bell`, `note`, `notification`) n'existent pas dans l'app, deux n'existent même pas dans Lucide, et s'affichent en deux lettres. Le prompt L1 dit à l'IA « toute icône Lucide », et son exemple de zone porte `currency_euro`, qui n'est pas un nom Lucide. Les zones, enfin, n'ont pas d'icône du tout : le schéma et le prompt la promettent, la table `zones` n'a pas de colonne, et `ZoneService` ignore le paramètre.

## Décisions

- **Vocabulaire = tout Lucide.** Un nom d'icône est un nom Lucide. Les ~1 600 icônes sont embarquées (≈ 650 Ko de SVG en source).
- **Source versionnée.** Lucide est copié dans `third_party/lucide/` : SVG, JSON par icône (tags, catégories, alias), JSON des catégories, `LICENSE`, et `VERSION` qui dit quelle release. Dernière release, telle quelle : les logos de marque, dépréciés pour la v1.0, partent avec Lucide lui-même.
- **Licence.** ISC (+ MIT pour la part Feather). La notice voyage dans l'APK, en asset.
- **Génération hors build.** Un script produit, depuis `third_party/lucide/`, ce que l'app lit ; le résultat est commité, le build ne génère rien (il tourne sans `npx`, exigence F-Droid). Il produit :
  - les drawables `lucide_<nom>` ;
  - l'index `assets/icons/index.json` : pour chaque icône, nom, tags, catégories ;
  - la table des alias `ancien nom → nom actuel`, tirée des `aliases` des JSON ;
  - les catégories (id, icône représentative).
  Il liste les noms disparus depuis la génération précédente.
- **Thèmes.** Un thème déclare sa source : `IconSource.LUCIDE` (les drawables `lucide_*`) ou `IconSource.OWN` (ses drawables `<thème>_*`). Un thème `OWN` fournit **toutes** les icônes : la génération échoue s'il en manque une ou s'il en porte une hors Lucide. Pas de jeu mêlé. Le thème par défaut est `LUCIDE` ; ses SVG copiés à la main et `standard_icons.txt` disparaissent.
- **Alias.** Un ancien nom est accepté à l'écriture et enregistré sous le nom actuel, et la réponse le dit. À l'affichage, un nom enregistré passe par la même table. Un nom ni actuel ni ancien est refusé à l'écriture.
- **Affichage d'un nom introuvable** : ses deux premières lettres, comme aujourd'hui.

## Recherche — un seul moteur

Partagé par le sélecteur et par l'IA : même index, même classement.

- Entrées : `categories` (liste, une icône convient si elle est dans l'une) et `query` (liste de mots, cherchés dans le nom et les tags ; une icône convient si l'un y est). Les deux ensemble : les deux conditions.
- Classement : nom exact, puis nom contenant le mot, puis tag ; à rang égal, le nombre de mots trouvés départage.

## Côté interface

Le sélecteur montre, dans l'ordre : les suggestions (celles de l'outil, une liste de départ pour une zone) ; un champ de recherche sur nom et tags ; sans recherche, les catégories avec leur icône, qui ouvrent leur grille, la recherche s'appliquant alors dans la catégorie. Les titres de catégorie passent par le système de strings, en français et en anglais. La recherche est en anglais : les tags de Lucide le sont.

## Côté IA — commande `ICONS`

Commande de lecture, comme `ZONES` ou `SCHEMA`.

- **Sans paramètre** : la vue d'ensemble — total, catégories (id, titre, nombre d'icônes), nombre maximum de résultats. Aucune icône listée.
- **Avec `categories` et/ou `query`** : les 30 meilleurs résultats (nom et tags), le total, et la répartition des résultats par catégorie, avec une note quand c'est tronqué.
- Une catégorie inconnue est refusée, en renvoyant à la vue d'ensemble.
- À l'écriture d'une zone ou d'un outil, un nom inconnu est refusé avec un renvoi à `ICONS`, sans suggestion.
- Le prompt L1 décrit la commande et la règle des noms ; l'exemple `currency_euro` devient `euro`.

## Zones

Colonne `icon_name` sur `zones` (migration), lue et écrite par `ZoneService`, rendue par `zones.get` et `zones.list`, exportée et importée par la sauvegarde, choisie dans le formulaire de zone par le sélecteur, affichée sur la zone à l'accueil.

## Hors portée

- Des tags en français : Lucide n'en fournit pas.
- Des suggestions dans le refus d'un nom inconnu : `ICONS` est là pour ça.
