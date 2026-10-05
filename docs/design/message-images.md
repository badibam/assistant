# Images dans un message

Conçu le 2026-10-05 : joindre une photo à un message, prise avec l'appareil photo ou choisie dans la galerie. L'image devient un type de bloc du composeur à blocs indépendants (branche `composer-blocks`, où texte et enrichissement ne sont plus associés).

## Les décisions

- **L'image fait partie de la conversation** : gardée avec sa session, affichée quand on la rouvre, renvoyée au modèle à chaque tour comme le reste de l'historique. Un modèle qui la perdrait au tour suivant ne pourrait pas répondre à « et sur la photo, en bas à gauche ? ». Chez Claude, le renvoi passe par le cache que l'app pose déjà sur le dernier message. Écarté : une entrée consommée à l'envoi, dont ne resterait qu'une trace.
- **On garde l'image réduite, jamais l'original** : ce que le modèle a vu est ce qui est gardé. 1 568 px sur le grand côté, JPEG qualité 85, soit 200 à 400 Ko et environ 1 600 tokens chez Claude. Tous les modèles la lisent sans la réduire. Écarté : 2 576 px, que lisent les Claude 5.x, jusqu'à 4 784 tokens payés à chaque tour pour un gain réservé au texte très serré. Si le ticket illisible devient un cas réel, ce sera une « haute définition » choisie image par image dans le bloc.
- **La préparation** : l'image est tournée d'après l'orientation notée par l'appareil, puis toutes ses métadonnées EXIF sont retirées, dont la position GPS.
- **La prise de vue** : l'appareil photo du téléphone (`ACTION_IMAGE_CAPTURE`, vers un fichier préparé par l'app) et le sélecteur de photos d'Android (`PickVisualMedia`, qui passe par le sélecteur de documents sans les services Google). L'app ne déclare pas l'autorisation `CAMERA`, donc Android ne demande rien. Deux boutons dans le composeur, « Photo » et « Galerie ». Écarté : un appareil photo dans l'app (CameraX), un écran entier pour un cadrage dont seul l'OCR aurait l'usage.

## Le stockage

- **Un fichier par image** dans le dossier privé de l'app, `files/attachments/<id>.jpg`, à plat, nommé par l'id ; le chemin se déduit de l'id, la base n'en stocke pas. Écrit en `<id>.jpg.part` puis renommé : un fichier final est complet. Écarté : les octets en base, qui la font grossir sans rendre la place sans `VACUUM`.
- **Une table `attached_images`** décrit chaque image : `id`, `session_id` (clé étrangère, suppression en cascade), `size_bytes`, `width`, `height`, `created_at`. Elle est tenue par le service `files`, comme `attached_files`.
- **La suppression** : la cascade ne supprime que la ligne ; le fichier est supprimé par le service qui supprime une session, seul chemin de toute suppression de session. Une image retirée du composeur avant l'envoi est supprimée, comme un fichier joint.
- **Un ordre fixe, pour qu'un orphelin ne puisse être qu'un fichier** : le fichier est écrit avant la ligne, la ligne est supprimée avant le fichier. Une ligne sans fichier est une vraie erreur : l'image l'affiche, l'envoi est refusé, rien ne la répare.
- **Un ménage au démarrage, avant tout écran** (rien ne peut alors être en train de joindre une image) : il confronte le dossier et la table, et supprime les fichiers qu'aucune ligne ne désigne. Il termine une opération interrompue, l'app tuée entre les deux gestes, et ne cache pas d'erreur : chaque suppression s'écrit au journal, en INFO pour un `.part`, cas attendu, en WARN avec son nom pour un `.jpg` sans ligne, qui vient d'un arrêt brutal ou d'un chemin de suppression qui a oublié le fichier.
- **L'import d'une sauvegarde gère lui-même le dossier** : il le vide et y dépose les images du zip, dans le même geste que les tables, pour ne pas laisser au ménage suivant des centaines de fichiers devenus orphelins.

## Dans le message

- **Un segment à part**, `MessageSegment.Image(image_id)`, à côté de `Text` et `EnrichmentBlock`, sérialisé `{"type": "image", "image_id": …}`. Il est posé à l'endroit où l'utilisateur l'a mis : « celle-ci [photo] coûte moins que celle-là [photo] » dépend de l'ordre. Écarté : un enrichissement `IMAGE`, fait pour devenir du texte par `EnrichmentProcessor`, ce qu'une image ne devient pas.
- **Vers le fournisseur**, un message n'est plus une liste de textes (`contentBlocks: List<String>`, `ClaudeExtensions.kt`) mais une liste de morceaux texte ou image, chacun à sa place : un bloc `image` en base64 chez Claude, une partie `image_url` en `data:` chez OpenAI et les fournisseurs compatibles. Le fichier est lu au moment de construire la requête.
- **À l'écran**, la session montre l'image en vignette dans le message, en grand au toucher.

## Le modèle qui ne lit pas les images

- **Refusé, et dit** : le bloc porte « ce modèle ne lit pas les images » dès qu'on le joint, et l'envoi est refusé tant qu'il est là. Jamais de remplacement silencieux par un texte.
- **Un historique qui contient des images** : le fournisseur d'une session est fixé (`provider_id`), son modèle peut changer dans les réglages. Si le nouveau modèle ne lit pas les images, l'envoi est refusé avec un message qui le dit. Jamais d'historique envoyé sans ses images.
- **Comment l'app le sait** : par `/models` quand l'API le dit (Anthropic : `capabilities.image_input.supported` ; DeepSeek : `input_modalities` au premier niveau ; OpenRouter : `architecture.input_modalities`), gardé dans la config sous `reads_images` à son enregistrement, sinon par un fait de `provider-facts`, nouveau type `input` (`images: true|false`), puisque ce fichier ne porte que ce que les API taisent.
- **Un modèle sur lequel rien n'est connu est refusé**, comme celui qui ne lit pas les images. Des serveurs compatibles OpenAI jettent sans rien dire une partie qu'ils ne comprennent pas : le modèle répondrait sur une photo qu'il n'a pas vue. La sortie est d'ajouter le fait dans `provider-facts`.
- **Le nombre d'images d'une requête** : Claude refuse au-delà de 100. Une session dont l'historique dépasse la limite est refusée à l'envoi, en le disant.

## L'OCR

- **Aucun pour l'instant.** Le jour où le besoin viendra (un ticket pour un modèle qui ne lit pas les images) : une transcription par un modèle qui lit les images, désigné dans les réglages ; le texte s'affiche dans le bloc, modifiable, et part à la place de l'image. Le bloc est pensé pour porter ce texte plus tard.
- **Écarté : Tesseract** dans le téléphone, plusieurs Mo de bibliothèques natives par type de processeur et de données par langue, médiocre sur une photo prise de biais. ML Kit est propriétaire, donc exclu par F-Droid.

## La sauvegarde

- **L'export produit toujours un zip** : `backup.json`, le JSON d'aujourd'hui, et `images/<id>.jpg`, copiées fichier par fichier sans passer par le JSON. Le JSON tenu en mémoire d'un seul bloc (`BackupService`) ne peut pas porter les images : 200 photos en base64 dépasseraient la limite de mémoire de 256 Mo.
- **L'import accepte le zip et l'ancien `.json`** : une sauvegarde déjà faite reste lisible, comme l'import rejoue déjà les migrations des anciennes versions.
- **Une image sans fichier ne bloque pas la sauvegarde** : l'export part quand même, la ligne sans fichier nommée dans `missing_images`, et dit combien ; l'import accepte une ligne sans fichier que la sauvegarde nomme ainsi, et refuse toute autre comme une sauvegarde abîmée.

## Hors du périmètre

- Une image dans le message de départ d'une automation, qui n'a pas de session (comme le fichier joint, `TODO.md`).
- Une image dans les données d'un outil (une photo jointe à une note) : ce n'est pas un message.
