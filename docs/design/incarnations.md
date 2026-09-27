# Les incarnations — conception

Conçu le 2026-09-27. Une incarnation, c'est l'IA de l'app qui parle en tant que quelqu'un : Jung pour l'analyse des rêves, par exemple. L'identité vient du projet personae (`/mnt/data/OUTILS/personae/docs/reference.md`), qui publie pour chaque identité un fichier d'export JSON figé : un nom, trois aspects (`voix`, un texte à imiter ; `esprit`, sa façon de penser ; `portrait`, des faits sur la personne), un bagage (des ressources `{titre, description, contenu}`), une section par langue, et le champ `distribuable`. personae dit **qui** parle ; l'assistant décide **où** et **comment**.

## Arrivée dans l'app

- **Import d'un fichier d'export**, dans les réglages de l'app, et nulle part ailleurs. L'app en garde une copie ; mettre à jour une identité = réimporter le fichier.
- Rien n'est livré avec l'app : une identité non `distribuable` (Stan Prokopenko, par exemple) s'importe comme une autre, puisque c'est l'utilisateur qui l'apporte sur son téléphone.
- **Langue** : on règle une fois par identité la langue dans laquelle elle parle, parmi les sections de l'export.

## Où elle intervient

- **Associée à une zone**, dans les réglages de la zone. Une ligne en haut de la zone la montre — une icône fixe, la même pour toute identité, son nom, un bouton de chat — et ouvre une session CHAT avec elle. L'ouverture ne joint pas les données de la zone : le chat ne se contextualise pas tout seul.
- **Dans la barre du chat**, à droite des enrichissements : une puce montre l'identité de la session et permet de la choisir.
- **Une automation** choisit son identité, comme toute session ; elle n'hérite pas de celle de la zone.
- **L'identité se fixe au premier message** d'une session. Pour changer d'interlocuteur, on ouvre une autre session. Ce qui se fixe, c'est qui parle, pas une version : une identité réimportée vaut dès le tour suivant, dans les sessions en cours aussi.

## Ce qu'elle peut faire

- Les mêmes capacités que l'IA ordinaire, sur **toute l'app** : lire, créer, modifier, avec les validations habituelles. L'association à une zone ne limite rien.
- **Sa mémoire, ce sont les données** : ce qu'elle écrit (une interprétation de rêve dans une note) elle le relit aux sessions suivantes. Pas de mémoire d'identité à part.

## Deux rôles dans une réponse

- **L'IA de l'app opère** : elle produit le JSON, choisit les commandes, demande les validations, construit les formulaires. Le prompt garde son ouverture actuelle ; l'identité s'y ajoute, en substance : « dans cette session, tout ce que l'utilisateur lit est écrit par <nom> ».
- **L'identité est l'auteur de ce que l'utilisateur lit** : `pre_text`, `post_text`, les questions d'un module de communication, et le texte qu'elle écrit dans les données.
- **Les noms restent à l'IA de l'app** : un outil, une zone, un champ qu'elle crée portent un nom neutre, lisible dans toute l'app.

## Ce que porte le prompt

La voix, l'esprit et le portrait, toujours : le portrait (court) dit qui parle, et c'est ce que l'identité ne penserait pas à aller chercher.

## Le bagage

Le prompt ne porte que la liste des ressources (titre + description neutre de l'export). L'identité lit le contenu d'une ressource par une commande de lecture quand elle en a besoin. La description de personae suffit pour commencer ; la réécrire pour l'usage de l'app se décidera à l'usage.

## Suppression

- **Refusée tant qu'une zone ou une automation utilise l'identité**, en les nommant.
- **Une session passée** reste lisible mais ne peut plus continuer, avec un message qui dit pourquoi. Jamais de reprise sans voix.
- Le même contrôle manque aux fournisseurs d'IA (`TODO.md`) : il s'écrit une fois pour les deux.

## Déclencheur

Un premier export publié par personae (aucun n'existe au 2026-09-27).
