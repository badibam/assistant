# Tests Migration DB - Mode Réversible

## Guide d'Utilisation

### **Prérequis**
1. **Appareil Android connecté** avec débogage USB activé
2. **App installée** avec données existantes
3. **ADB fonctionnel** (`adb devices` doit montrer votre appareil)

### **Usage du Script**

```bash
# Lancer le script interactif
./scripts/db_backup_restore.sh
```

### **Menu Options**

#### **1⃣ Backup DB Actuelle**
- Sauvegarde la DB avant tests
- Nommage automatique avec timestamp
- Stockage dans `./db_backups/`

#### **2⃣ Lister Backups**
- Affiche tous les backups disponibles
- Tailles et dates de création

#### **3⃣ Restaurer Backup**
- Restaure une DB depuis backup
- Redémarre automatiquement l'app

#### **4⃣ Test Migration Sécurisé**
- **Backup automatique** pré-test
- Lance l'app pour test migration
- **Choix post-test** :
  - `r` : Restaurer si problème
  - `c` : Confirmer si succès

---

## **Workflow Recommandé**

### **Phase 1 : Backup Initial**
```bash
./scripts/db_backup_restore.sh
# → Choisir option 1
# → Nommer "avant_migration_unifiee"
```

### **Phase 2 : Test Migration**
```bash
./scripts/db_backup_restore.sh
# → Choisir option 4 (Test sécurisé)
# → Tester sur appareil
# → Choisir 'r' si problème, 'c' si OK
```

### **Phase 3 : Re-test si Nécessaire**
- Si restauration faite → Re-test possible
- Si succès → Backup de la DB migrée
- Tests multiples possibles !

---

## **Sécurités Intégrées**

 **Backup automatique** avant chaque test
 **Vérification appareil** connecté
 **Arrêt app** avant restauration
 **Validation fichiers** backup
 **Rollback immédiat** si problème

---

## **Structure Fichiers**

```
./db_backups/
├── treelune_database_20241207_143022 # Backup timestamp
├── treelune_database_avant_migration # Backup nommé
└── treelune_database_pre_migration_test # Auto-backup test
```

---

## **Troubleshooting**

### **Appareil non détecté**
```bash
adb kill-server
adb start-server
adb devices
```

### **Erreur permissions**
```bash
# Vérifier débogage USB activé
# Réinstaller app si nécessaire
```

### **Backup vide**
```bash
# Vérifier que l'app a créé des données
# Lancer l'app une fois avant backup
```