#!/bin/bash

# ======================================
# DB BACKUP/RESTORE pour Tests Migration
# ======================================

PACKAGE_NAME="com.assistant"
DB_NAME="assistant_database"
BACKUP_DIR="./db_backups"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

# Couleurs pour output
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Vérifier si appareil connecté
check_device() {
    if ! adb devices | grep -q "device$"; then
        echo -e "${RED}❌ Aucun appareil Android connecté${NC}"
        echo "Connectez votre appareil et activez le débogage USB"
        exit 1
    fi
    echo -e "${GREEN}✅ Appareil Android détecté${NC}"
}

# Créer dossier backup
setup_backup_dir() {
    mkdir -p "$BACKUP_DIR"
    echo -e "${BLUE}📁 Dossier backup: $BACKUP_DIR${NC}"
}

# Backup DB
backup_db() {
    local backup_name="${1:-$TIMESTAMP}"
    local backup_path="$BACKUP_DIR/assistant_database_$backup_name"
    
    echo -e "${BLUE}💾 Sauvegarde DB...${NC}"
    
    # Pull DB depuis appareil
    adb exec-out run-as $PACKAGE_NAME cat databases/$DB_NAME > "$backup_path"
    
    if [ $? -eq 0 ] && [ -f "$backup_path" ] && [ -s "$backup_path" ]; then
        echo -e "${GREEN}✅ DB sauvegardée: $backup_path${NC}"
        ls -lh "$backup_path"
        return 0
    else
        echo -e "${RED}❌ Échec sauvegarde DB${NC}"
        return 1
    fi
}

# Restore DB  
restore_db() {
    local backup_file="$1"
    
    if [ ! -f "$backup_file" ]; then
        echo -e "${RED}❌ Fichier backup introuvable: $backup_file${NC}"
        list_backups
        return 1
    fi
    
    echo -e "${BLUE}🔄 Restauration DB depuis: $backup_file${NC}"
    
    # Arrêter l'app
    adb shell am force-stop $PACKAGE_NAME
    
    # Push DB vers appareil
    adb push "$backup_file" /sdcard/tmp_db
    adb shell run-as $PACKAGE_NAME sh -c "cat /sdcard/tmp_db > databases/$DB_NAME"
    adb shell rm /sdcard/tmp_db
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✅ DB restaurée avec succès${NC}"
        return 0
    else
        echo -e "${RED}❌ Échec restauration DB${NC}"
        return 1
    fi
}

# Lister backups disponibles
list_backups() {
    echo -e "${BLUE}📋 Backups disponibles:${NC}"
    if [ -d "$BACKUP_DIR" ] && [ "$(ls -A $BACKUP_DIR 2>/dev/null)" ]; then
        ls -lht "$BACKUP_DIR"
    else
        echo -e "${RED}   Aucun backup trouvé${NC}"
    fi
}

# Menu principal
show_menu() {
    echo -e "\n${BLUE}=== DB BACKUP/RESTORE MENU ===${NC}"
    echo "1) Backup DB actuelle"
    echo "2) Lister backups"
    echo "3) Restaurer backup"
    echo "4) Test migration (backup auto + restore si échec)"
    echo "q) Quitter"
    echo -n "Choix: "
}

# Test migration avec sécurité
test_migration() {
    echo -e "${BLUE}🧪 TEST MIGRATION SÉCURISÉ${NC}"
    
    # Backup automatique pré-test
    if backup_db "pre_migration_test"; then
        echo -e "${GREEN}✅ Backup pré-test créé${NC}"
        
        echo -e "${BLUE}🚀 Lancement app pour test migration...${NC}"
        echo "   → Testez la migration sur l'appareil"
        echo "   → Tapez 'r' pour restaurer si problème"
        echo "   → Tapez 'c' pour confirmer succès"
        
        read -n 1 -p "Action (r/c): " action
        echo ""
        
        case $action in
            r|R)
                echo -e "${BLUE}🔄 Restauration backup pré-test...${NC}"
                restore_db "$BACKUP_DIR/assistant_database_pre_migration_test"
                ;;
            c|C)
                echo -e "${GREEN}✅ Migration confirmée réussie !${NC}"
                ;;
            *)
                echo -e "${RED}Action invalide${NC}"
                ;;
        esac
    else
        echo -e "${RED}❌ Échec backup pré-test - abandon${NC}"
    fi
}

# Main
main() {
    echo -e "${GREEN}🔧 DB Backup/Restore Tool - Architecture Unifiée${NC}"
    
    check_device
    setup_backup_dir
    
    while true; do
        show_menu
        read choice
        
        case $choice in
            1)
                echo -n "Nom backup (optionnel): "
                read backup_name
                backup_db "$backup_name"
                ;;
            2)
                list_backups
                ;;
            3)
                list_backups
                echo -n "Chemin backup à restaurer: "
                read backup_path
                restore_db "$backup_path"
                ;;
            4)
                test_migration
                ;;
            q|Q)
                echo -e "${GREEN}👋 Au revoir !${NC}"
                exit 0
                ;;
            *)
                echo -e "${RED}❌ Choix invalide${NC}"
                ;;
        esac
    done
}

main "$@"