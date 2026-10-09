# L'accès externe par Tailscale Funnel — prototype

Prototype du 2026-10-09. La question : l'app peut-elle publier elle-même une adresse HTTPS publique, sans le relais OVH, pour que n'importe quel utilisateur branche un client MCP sur son téléphone ? Réponse : oui, en embarquant un nœud Tailscale (`tsnet`, Go, BSD-3) dans l'app. Le code du prototype est jetable (`tmp/funnel-poc/`, non versionné) ; ce qui doit survivre est ici.

## Le principe

- L'app embarque `tsnet` (Go, compilé par le NDK en bibliothèque native appelée par JNI) et devient un nœud du réseau Tailscale de l'utilisateur. `ListenFunnel(":443", FunnelOnly())` lui donne l'adresse `https://<nom>.<tailnet>.ts.net/`, joignable depuis internet.
- Le HTTPS s'arrête sur le téléphone, avec son propre certificat Let's Encrypt : les serveurs d'entrée de Tailscale font passer des octets chiffrés. Le relais OVH, lui, voit les données en clair.
- Aucun VPN système, aucun port ouvert sur le vrai réseau du téléphone : seule l'app écoute, par une connexion sortante vers Tailscale.
- Le contrat du relais (une requête HTTP en entrée, une réponse en sortie) peut rester la frontière : le côté Go reçoit la requête et la passe à Kotlin, MCP et OAuth ne bougent pas. Le relais devient un mode parmi deux.

## Ce que l'utilisateur fait, une fois

Compte Tailscale (plan Personal gratuit), puis dans la console :
1. MagicDNS — actif d'office sur un compte neuf.
2. HTTPS Certificates — à activer, page DNS.
3. Le droit au Funnel dans la politique d'accès : `"nodeAttrs": [{ "target": ["autogroup:member"], "attr": ["funnel"] }]`. C'est l'étape où l'on cherche : l'éditeur visuel n'y mène pas, il faut le mode JSON (`login.tailscale.com/admin/acls/file`).
4. Dans l'app : une URL de connexion `login.tailscale.com/a/…`, ouverte dans le navigateur, qui inscrit le nœud. Sans clé à coller. L'identité est gardée dans les fichiers de l'app : les démarrages suivants ne demandent rien.

## Mesures (Samsung S10, LineageOS 23.2, Android 16)

- Démarrage, nœud déjà inscrit : Funnel ouvert en 1,1 s.
- Publication de l'adresse dans le DNS public : 12 s après l'autorisation, pour une inscription dans l'app sans incident ; environ 6 min deux fois, sur des nœuds qui avaient tardé à rejoindre le réseau ; moins d'une minute pour le nœud du PC.
- Juste après, quelques secondes où l'entrée de Tailscale coupe la connexion sans joindre le téléphone.
- Premier appel : 37 à 42 s, l'obtention du certificat (quatre fois : 38,6 s, 36,8 s, 37,1 s, 41,5 s). Le client peut voir la connexion coupée pendant ce temps.
- De l'autorisation à la première réponse, inscription sans incident : environ 70 s.
- Appels suivants : entre 0,5 et 1,3 s, en Wi-Fi comme en 4G (SFR), écran allumé ; 20 sur 20 en 4G, 12 sur 12 par les trois points d'entrée de Tailscale.
- Bibliothèque Go : 21,7 Mo pour arm64 (sans symboles), 7,7 Mo compressée. L'APK la range sans compression : l'APK de release de Treelune (9,1 Mo) passerait à environ 31 Mo, ou environ 17 Mo avec `useLegacyPackaging` (calcul, pas mesure). Chaque architecture en plus ajoute autant.
- Mémoire de l'app prototype, nœud en ligne : 94 Mo (PSS).
- Des robots inconnus visitent l'adresse moins de 2 s après la sortie du certificat : les certificats sont publiés dans les registres publics (Certificate Transparency). L'adresse est connue de tous dès son ouverture.

## Les trois corrections

1. **Le certificat.** `tsnet` obtient son certificat par son API interne, dont la route `cert/` est exclue des versions Android (`ipn/localapi/cert.go` : `//go:build !ios && !android && !js && !ts_omit_acme` ; `disabled_stubs.go` : `ios || android || js`). Sans elle, chaque connexion échoue (« 404 page not found »). Retirer `android` des deux lignes suffit : une copie corrigée de `tailscale.com` (`replace` dans `go.mod`), à reporter à chaque mise à jour — ou un correctif à proposer à Tailscale.
2. **Les interfaces réseau.** Android refuse netlink aux apps (`route ip+net: netlinkrib: permission denied`). `feature/androidbin` le contourne mais se désactive dans une build `android && cgo` : l'app enregistre sa liste par `netmon.RegisterInterfaceGetter` — une interface synthétique portant l'adresse source qu'une socket UDP non envoyée reçoit du noyau, la méthode d'`androidbin`. L'app officielle fournit la vraie liste depuis Kotlin, et signale les changements de réseau (`netmon.UpdateLastKnownDefaultRouteInterface`).
3. **Les dossiers.** Une app n'a ni `HOME`, ni dossier de cache, ni `TMPDIR` dans son environnement : `logpolicy` panique (« no safe place found to store log state »). Les trois pointent dans les fichiers de l'app avant le démarrage.

Et : `envknob.SetNoLogsNoSupport()` coupe l'envoi des journaux à `log.tailscale.com` (lu dans `logpolicy`, non observé sur le réseau). Une panique Go ne s'affiche nulle part sous Android : `debug.SetCrashOutput` vers un fichier de l'app.

## Ce qui reste ouvert

- **Le blocage du premier lancement** : vu une fois sur deux inscriptions dans l'app, non reproduit. Juste après l'inscription, le nœud est resté 22 min sans joindre les relais de Tailscale ni publier son adresse ; une relance l'a débloqué. Il suivait plusieurs plantages et réinstallations, et un service relancé seul par Android. Non expliqué. Piste, non vérifiée : sous Android, le moniteur réseau ne repasse que toutes les 10 min et attend que l'app lui signale le réseau — ce que le prototype ne faisait pas.
- **La veille profonde** : non mesurée. Écran éteint, téléphone débranché, les appels passent pendant les 7 min mesurées (lancé par adb, pas en app) ; au-delà, inconnu. Point d'attention, pas rédhibitoire : l'accès se ferme seul après 30 min sans appel.
- **Les changements de réseau** (Wi-Fi ↔ 4G) avec l'interface synthétique : non testés.
- **F-Droid** : construire du Go et le NDK depuis les sources, de façon reproductible ; la copie corrigée de Tailscale avec. Anti-feature `NonFreeNet` probable (le service de Tailscale).
- **La taille de l'APK**, et le choix entre bibliothèque compressée ou non.
- **La sécurité** : du code Go reçoit directement ce qui arrive d'internet, dans l'app. Suivre les versions de `tsnet` comme une dépendance de sécurité. Le serveur de l'app refuse déjà ce qui n'est pas authentifié ; à relire au chantier de la validation.
- **L'interface** : deux modes dans « Accès externe » (Tailscale, ou mon adresse — le relais actuel), les étapes de la console guidées.
