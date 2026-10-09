# L'accès externe par Tailscale Funnel — conception

Conçu le 2026-10-09, après le prototype (`docs/design/funnel-poc.md` : mesures, les trois corrections pour Android). L'accès externe (`docs/design/mcp-server.md`) gagne un deuxième chemin : l'app publie elle-même son adresse HTTPS par un nœud Tailscale embarqué, sans serveur à soi. Le relais reste, à côté.

## Deux modes

- **Tailscale** : un nœud `tsnet` dans l'app ouvre un Funnel ; l'adresse est `https://treelune.<tailnet>.ts.net/mcp`, le HTTPS s'arrête sur le téléphone. Demande un compte Tailscale, gratuit.
- **Relais** : le chemin d'aujourd'hui, inchangé — l'adresse et le secret d'un relais qu'on héberge.
- Le relais reste la porte de sortie si Tailscale change Funnel (en bêta), et le seul chemin qui ne dépende d'aucun service tiers. Il n'évite pas l'anti-feature `NonFreeNet`, déjà due aux fournisseurs d'IA (`fdroid-compliance.md`) : Tailscale s'y ajoute.

## Le réglage et l'écran

- `access_mode` dans la catégorie Accès externe : `TAILSCALE` ou `RELAY`. Nouvelle installation : `TAILSCALE`. Migration : `RELAY` si une adresse et un secret de relais sont réglés, `TAILSCALE` sinon.
- L'écran Accès externe commence par le choix du mode, puis montre la partie du mode : pour Tailscale l'état du nœud, l'adresse, « Déconnecter de Tailscale » ; pour le relais ses deux champs. Ouvrir, fermer, les clients autorisés et leur révocation ne changent pas — l'OAuth est servi par l'app, un client autorisé le reste d'un mode à l'autre.
- Un client déjà branché garde l'adresse avec laquelle il a été ajouté : changer de mode demande de l'ajouter à nouveau dans le client, avec la nouvelle adresse (à vérifier sur claude.ai : sans doute une nouvelle autorisation).
- La bibliothèque n'existe que pour arm64 : ailleurs, le choix du mode n'apparaît pas et seul le relais est proposé. Un réglage à `TAILSCALE` sans bibliothèque chargée (une sauvegarde venue d'un autre téléphone) fait échouer l'ouverture, message à l'appui — jamais de bascule seule vers le relais.

## La mise en route, réactive

Pas de liste d'étapes affichée d'avance : l'écran dit ce que le nœud répond à l'ouverture, une étape manquante à la fois, et « Réessayer » relance après chaque geste dans la console.

1. Nœud pas inscrit : « Connecter l'app à Tailscale » ouvre l'URL de connexion (`login.tailscale.com/a/…`) dans le navigateur ; la page de Tailscale y crée aussi le compte.
2. `ListenFunnel` répond « HTTPS must be enabled » : une phrase, et un bouton vers la page DNS de la console.
3. Il répond « "funnel" node attribute not set » : une phrase, le bloc `nodeAttrs` avec Copier, et un bouton vers l'éditeur JSON de la politique d'accès (`login.tailscale.com/admin/acls/file`).
4. Prêt : l'adresse `…/mcp`, à copier dans le client.

Le premier appel après l'inscription attend le certificat (environ 40 s) ; l'écran le dit tant qu'aucun appel n'a abouti. Un chapitre du Guide donnera la vue d'ensemble, une fois l'écran stable.

## La vie du nœud

- Il ne tourne que pendant que l'accès est ouvert : démarré à l'ouverture (1 s, inscrit), arrêté à la fermeture, à la main ou après 30 minutes sans appel. Fermé, l'adresse ne mène nulle part.
- Son identité (clé, certificat, état) vit dans `files/tailscale/`, jamais dans la base, donc jamais dans l'export de Treelune ; exclue aussi de la sauvegarde et du transfert Android (`backup_rules.xml`, `data_extraction_rules.xml`), qui la dupliqueraient sur un autre téléphone — `files/mcp/` avec, dont la clé des jetons de contexte part aujourd'hui dans la sauvegarde Android.
- Son nom est `treelune` ; s'il est pris, Tailscale ajoute un suffixe, et l'app montre l'adresse que le nœud reçoit (`CertDomains`).
- « Déconnecter de Tailscale » retire le nœud du compte et efface `files/tailscale/` ; l'ouverture suivante redemande la connexion.
- Le certificat se renouvelle pendant que le nœud tourne ; expiré pendant une longue fermeture, le premier appel reprend l'attente du certificat.
- Les changements de réseau d'Android (Wi-Fi, 4G, perte) sont transmis au nœud depuis Kotlin (`ConnectivityManager`), comme le fait l'app de Tailscale : la piste contre le blocage du premier lancement que le prototype a vu une fois.

## La frontière Go / Kotlin

- Le Go parle le contrat du relais (`mcp-server.md`, « Le contrat du relais »), dans le même JSON : `next(wait)` rend la plus ancienne requête reçue par le Funnel, ou rien après `wait` secondes ; `reply(id, json)` rend la réponse. Sans réponse en 25 s, le Go répond 503 au client, comme le relais.
- `FunnelTransport` implémente `RelayTransport` avec ces deux appels : `RelayLoop`, `McpHttp`, l'OAuth et la fermeture après 30 minutes ne voient pas la différence. `McpHttp` reçoit l'adresse du nœud comme base, une fois le nœud prêt.
- Le pilotage : `start(dossier, nom)`, `state()` (à inscrire et son URL, HTTPS manquant, droit Funnel manquant, prêt et son adresse, échec et son message), `stop()`, `logout()`, `networkChanged(interface)`.
- JNI écrit à la main en cgo, comme le prototype ; pas de gomobile.
- Ce que le prototype a appris, à garder : la liste d'interfaces fournie par l'app (`netmon.RegisterInterfaceGetter`), `HOME`, `XDG_CACHE_HOME` et `TMPDIR` dans les fichiers de l'app avant le démarrage, `envknob.SetNoLogsNoSupport()`, `FunnelOnly()`, et une panique Go écrite dans un fichier (`debug.SetCrashOutput`) puisqu'Android ne la montre pas.

## Les journaux

- Les messages pour l'utilisateur (URL de connexion, Funnel ouvert, erreurs) vont au journal de l'app, « External access: », niveau INFO.
- Le journal technique de Tailscale va dans `files/tailscale/`, deux fichiers de 1 Mo en rotation, jamais envoyé ; le rapport de bug pourra l'emporter. Rien à `log.tailscale.com`.

## Le code et son build

- Le module Go dans `app/src/main/go/` ; ses dépendances copiées dans `app/src/main/go/vendor/` (33 Mo, 2 878 fichiers, mesuré le 2026-10-09), commitées, pour un build sans téléchargement.
- La correction de Tailscale (retirer `android` des conditions de `ipn/localapi/cert.go` et `disabled_stubs.go`) est appliquée dans `vendor/`. `scripts/vendor_go.py` refait tout : `go mod vendor`, la correction, sa vérification. Mettre Tailscale à jour = changer sa version dans `go.mod`, relancer le script, commiter. Un contrôle de `./run test` vérifie que la correction est présente. Si Tailscale l'intègre, elle sort du script (`TODO.md`).
- Une tâche Gradle compile `libtreelune_funnel.so` pour arm64 avec le NDK avant l'assemblage, dans `build/`, jamais dans `src/`. Go se trouve par `go.dir` dans `local.properties` ou par le `PATH` ; le NDK par une version fixée dans `build.gradle.kts`. L'un manque : le build échoue en le nommant.
- Reproductible : `toolchain` de Go fixé dans `go.mod`, `-trimpath -buildvcs=false`, identifiant de build vide, symboles retirés.
- `./run compile` ne compile pas le Go ; `./run install` et `./run release` le compilent.
- La bibliothèque reste non compressée dans l'APK (le défaut) : l'APK de release passe d'environ 9 à 31 Mo (calcul).

## Ce qui reste ouvert

- La veille profonde, non mesurée : point d'attention, pas rédhibitoire.
- La recette F-Droid : Go dans la bonne version sur la machine de build, le NDK ; au moment de la fiche, avec le reste de `fdroid-compliance.md`.
- La sécurité : du Go reçoit directement ce qui vient d'internet, et l'adresse est visitée par des robots dès la sortie de son certificat — suivre les versions de `tsnet` comme une dépendance de sécurité ; à relire au chantier de la validation.
- Le relais, pour servir à d'autres : sans licence aujourd'hui.
