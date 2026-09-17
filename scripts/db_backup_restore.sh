#!/bin/bash

# ======================================
# DB BACKUP/RESTORE pour Tests Migration
# ======================================

PACKAGE_NAME="com.assistant.debug"
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
        echo -e "${RED} Aucun appareil Android connecté${NC}"
        echo "Connectez votre appareil et activez le débogage USB"
        exit 1
    fi
    echo -e "${GREEN} Appareil Android détecté${NC}"
}

# Créer dossier backup
setup_backup_dir() {
    mkdir -p "$BACKUP_DIR"
    echo -e "${BLUE} Dossier backup: $BACKUP_DIR${NC}"
}

# Backup DB
backup_db() {
    local backup_name="${1:-$TIMESTAMP}"

    echo -e "${BLUE} Sauvegarde DBs + WAL files...${NC}"

    # Backup tracking_database + ses fichiers WAL (vraies données)
    local tracking_path="$BACKUP_DIR/tracking_database_$backup_name"
    local tracking_wal="$BACKUP_DIR/tracking_database-wal_$backup_name"
    local tracking_shm="$BACKUP_DIR/tracking_database-shm_$backup_name"

    adb exec-out run-as $PACKAGE_NAME cat databases/tracking_database > "$tracking_path"
    adb exec-out run-as $PACKAGE_NAME cat databases/tracking_database-wal > "$tracking_wal" 2>/dev/null
    adb exec-out run-as $PACKAGE_NAME cat databases/tracking_database-shm > "$tracking_shm" 2>/dev/null

    # Backup assistant_database + ses fichiers WAL
    local assistant_path="$BACKUP_DIR/assistant_database_$backup_name"
    local assistant_wal="$BACKUP_DIR/assistant_database-wal_$backup_name"
    local assistant_shm="$BACKUP_DIR/assistant_database-shm_$backup_name"

    adb exec-out run-as $PACKAGE_NAME cat databases/assistant_database > "$assistant_path"
    adb exec-out run-as $PACKAGE_NAME cat databases/assistant_database-wal > "$assistant_wal" 2>/dev/null
    adb exec-out run-as $PACKAGE_NAME cat databases/assistant_database-shm > "$assistant_shm" 2>/dev/null

    # Vérification
    echo -e "${GREEN} DBs + WAL sauvegardées:${NC}"
    echo " Tracking DB: $(ls -lh "$tracking_path" | awk '{print $5}')"
    if [ -f "$tracking_wal" ] && [ -s "$tracking_wal" ]; then
        echo " Tracking WAL: $(ls -lh "$tracking_wal" | awk '{print $5}') ← VRAIES DONNÉES TRACKING"
    fi
    echo " Assistant DB: $(ls -lh "$assistant_path" | awk '{print $5}')"
    if [ -f "$assistant_wal" ] && [ -s "$assistant_wal" ]; then
        echo " Assistant WAL: $(ls -lh "$assistant_wal" | awk '{print $5}') ← VRAIES DONNÉES ASSISTANT"
    fi
    return 0
}

# Restore DB
restore_db() {
    local backup_file="$1"

    # Si le fichier ne contient pas le chemin complet, chercher dans BACKUP_DIR
    if [[ "$backup_file" != /* ]] && [[ "$backup_file" != ./* ]]; then
        backup_file="$BACKUP_DIR/$backup_file"
    fi

    if [ ! -f "$backup_file" ]; then
        echo -e "${RED} Fichier backup introuvable: $backup_file${NC}"
        list_backups
        return 1
    fi

    echo -e "${BLUE} Restauration complète depuis: $backup_file${NC}"

    # Arrêter l'app
    adb shell am force-stop $PACKAGE_NAME

    # Détecter le type de fichier à partir du nom
    local backup_base=$(basename "$backup_file")
    local timestamp=""

    if [[ "$backup_base" =~ tracking_database_(.+)$ ]]; then
        timestamp="${BASH_REMATCH[1]}"
        echo -e "${BLUE} Restauration tracking_database + fichiers WAL (timestamp: $timestamp)${NC}"

        # Restaurer tracking_database principal
        adb push "$backup_file" /sdcard/tmp_db
        adb shell run-as $PACKAGE_NAME sh -c "cat /sdcard/tmp_db > databases/tracking_database"
        adb shell rm /sdcard/tmp_db

        # Restaurer fichiers WAL s'ils existent
        local wal_file="$BACKUP_DIR/tracking_database-wal_$timestamp"
        local shm_file="$BACKUP_DIR/tracking_database-shm_$timestamp"

        if [ -f "$wal_file" ]; then
            echo -e "${BLUE} Restauration WAL file...${NC}"
            adb push "$wal_file" /sdcard/tmp_wal
            adb shell run-as $PACKAGE_NAME sh -c "cat /sdcard/tmp_wal > databases/tracking_database-wal"
            adb shell rm /sdcard/tmp_wal
        fi

        if [ -f "$shm_file" ]; then
            echo -e "${BLUE} Restauration SHM file...${NC}"
            adb push "$shm_file" /sdcard/tmp_shm
            adb shell run-as $PACKAGE_NAME sh -c "cat /sdcard/tmp_shm > databases/tracking_database-shm"
            adb shell rm /sdcard/tmp_shm
        fi

        # Restaurer aussi assistant_database du même timestamp (contient zones, tool_instances, etc.)
        local assistant_file="$BACKUP_DIR/assistant_database_$timestamp"
        if [ -f "$assistant_file" ]; then
            echo -e "${BLUE} Restauration assistant_database (zones, tool_instances)...${NC}"
            adb push "$assistant_file" /sdcard/tmp_assistant
            adb shell run-as $PACKAGE_NAME sh -c "cat /sdcard/tmp_assistant > databases/assistant_database"
            adb shell rm /sdcard/tmp_assistant

            # Restaurer fichiers WAL assistant s'ils existent
            local assistant_wal_file="$BACKUP_DIR/assistant_database-wal_$timestamp"
            local assistant_shm_file="$BACKUP_DIR/assistant_database-shm_$timestamp"

            if [ -f "$assistant_wal_file" ] && [ -s "$assistant_wal_file" ]; then
                echo -e "${BLUE} Restauration assistant WAL file...${NC}"
                adb push "$assistant_wal_file" /sdcard/tmp_assistant_wal
                adb shell run-as $PACKAGE_NAME sh -c "cat /sdcard/tmp_assistant_wal > databases/assistant_database-wal"
                adb shell rm /sdcard/tmp_assistant_wal
            fi

            if [ -f "$assistant_shm_file" ] && [ -s "$assistant_shm_file" ]; then
                echo -e "${BLUE} Restauration assistant SHM file...${NC}"
                adb push "$assistant_shm_file" /sdcard/tmp_assistant_shm
                adb shell run-as $PACKAGE_NAME sh -c "cat /sdcard/tmp_assistant_shm > databases/assistant_database-shm"
                adb shell rm /sdcard/tmp_assistant_shm
            fi
        else
            echo -e "${RED} assistant_database_$timestamp introuvable - métadonnées manquantes${NC}"
        fi

    elif [[ "$backup_base" =~ assistant_database_(.+)$ ]]; then
        timestamp="${BASH_REMATCH[1]}"
        echo -e "${BLUE} Restauration assistant_database (timestamp: $timestamp)${NC}"

        adb push "$backup_file" /sdcard/tmp_db
        adb shell run-as $PACKAGE_NAME sh -c "cat /sdcard/tmp_db > databases/assistant_database"
        adb shell rm /sdcard/tmp_db

        # Restaurer fichiers WAL assistant du même timestamp
        local assistant_wal_file="$BACKUP_DIR/assistant_database-wal_$timestamp"
        local assistant_shm_file="$BACKUP_DIR/assistant_database-shm_$timestamp"

        if [ -f "$assistant_wal_file" ] && [ -s "$assistant_wal_file" ]; then
            echo -e "${BLUE} Restauration assistant WAL file...${NC}"
            adb push "$assistant_wal_file" /sdcard/tmp_assistant_wal
            adb shell run-as $PACKAGE_NAME sh -c "cat /sdcard/tmp_assistant_wal > databases/assistant_database-wal"
            adb shell rm /sdcard/tmp_assistant_wal
        fi

        if [ -f "$assistant_shm_file" ] && [ -s "$assistant_shm_file" ]; then
            echo -e "${BLUE} Restauration assistant SHM file...${NC}"
            adb push "$assistant_shm_file" /sdcard/tmp_assistant_shm
            adb shell run-as $PACKAGE_NAME sh -c "cat /sdcard/tmp_assistant_shm > databases/assistant_database-shm"
            adb shell rm /sdcard/tmp_assistant_shm
        fi

        # Restaurer aussi tracking_database + WAL du même timestamp (données réelles)
        local tracking_file="$BACKUP_DIR/tracking_database_$timestamp"
        if [ -f "$tracking_file" ]; then
            echo -e "${BLUE} Restauration tracking_database (données réelles)...${NC}"
            adb push "$tracking_file" /sdcard/tmp_tracking
            adb shell run-as $PACKAGE_NAME sh -c "cat /sdcard/tmp_tracking > databases/tracking_database"
            adb shell rm /sdcard/tmp_tracking

            # Restaurer fichiers WAL tracking
            local wal_file="$BACKUP_DIR/tracking_database-wal_$timestamp"
            local shm_file="$BACKUP_DIR/tracking_database-shm_$timestamp"

            if [ -f "$wal_file" ]; then
                echo -e "${BLUE} Restauration tracking WAL file...${NC}"
                adb push "$wal_file" /sdcard/tmp_wal
                adb shell run-as $PACKAGE_NAME sh -c "cat /sdcard/tmp_wal > databases/tracking_database-wal"
                adb shell rm /sdcard/tmp_wal
            fi

            if [ -f "$shm_file" ]; then
                echo -e "${BLUE} Restauration tracking SHM file...${NC}"
                adb push "$shm_file" /sdcard/tmp_shm
                adb shell run-as $PACKAGE_NAME sh -c "cat /sdcard/tmp_shm > databases/tracking_database-shm"
                adb shell rm /sdcard/tmp_shm
            fi
        fi
    else
        echo -e "${RED} Format de fichier backup non reconnu: $backup_base${NC}"
        return 1
    fi

    if [ $? -eq 0 ]; then
        echo -e "${GREEN} DB + fichiers WAL restaurés avec succès${NC}"
        echo -e "${BLUE} Redémarrez l'app pour voir les données restaurées${NC}"
        return 0
    else
        echo -e "${RED} Échec restauration DB${NC}"
        return 1
    fi
}

# Lister backups disponibles
list_backups() {
    echo -e "${BLUE} Backups disponibles:${NC}"
    if [ -d "$BACKUP_DIR" ] && [ "$(ls -A $BACKUP_DIR 2>/dev/null)" ]; then
        ls -lht "$BACKUP_DIR"
    else
        echo -e "${RED} Aucun backup trouvé${NC}"
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
    echo -e "${BLUE} TEST MIGRATION SÉCURISÉ${NC}"

    # Backup automatique pré-test
    if backup_db "pre_migration_test"; then
        echo -e "${GREEN} Backup pré-test créé${NC}"

        echo -e "${BLUE} Lancement app pour test migration...${NC}"
        echo " → Testez la migration sur l'appareil"
        echo " → Tapez 'r' pour restaurer si problème"
        echo " → Tapez 'c' pour confirmer succès"

        read -n 1 -p "Action (r/c): " action
        echo ""

        case $action in
            r|R)
                echo -e "${BLUE} Restauration backup pré-test...${NC}"
                restore_db "$BACKUP_DIR/assistant_database_pre_migration_test"
                ;;
            c|C)
                echo -e "${GREEN} Migration confirmée réussie !${NC}"
                ;;
            *)
                echo -e "${RED}Action invalide${NC}"
                ;;
        esac
    else
        echo -e "${RED} Échec backup pré-test - abandon${NC}"
    fi
}

# Main
main() {
    echo -e "${GREEN} DB Backup/Restore Tool - Architecture Unifiée${NC}"

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
                echo -e "${GREEN} Au revoir !${NC}"
                exit 0
                ;;
            *)
                echo -e "${RED} Choix invalide${NC}"
                ;;
        esac
    done
}

main "$@"