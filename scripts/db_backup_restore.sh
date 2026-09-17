#!/bin/bash

# ======================================
# DB BACKUP/RESTORE for migration testing
# ======================================

PACKAGE_NAME="com.assistant.debug"
DB_NAME="assistant_database"
BACKUP_DIR="./db_backups"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

# Output colors
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Is a device connected
check_device() {
    if ! adb devices | grep -q "device$"; then
        echo -e "${RED}No Android device connected${NC}"
        echo "Connect the device and enable USB debugging"
        exit 1
    fi
    echo -e "${GREEN}Android device found${NC}"
}

# Create the backup directory
setup_backup_dir() {
    mkdir -p "$BACKUP_DIR"
    echo -e "${BLUE}Backup directory: $BACKUP_DIR${NC}"
}

# Backup DB
backup_db() {
    local backup_name="${1:-$TIMESTAMP}"

    echo -e "${BLUE}Backing up the databases and their WAL files...${NC}"

    # tracking_database and its WAL files (the real data)
    local tracking_path="$BACKUP_DIR/tracking_database_$backup_name"
    local tracking_wal="$BACKUP_DIR/tracking_database-wal_$backup_name"
    local tracking_shm="$BACKUP_DIR/tracking_database-shm_$backup_name"

    adb exec-out run-as $PACKAGE_NAME cat databases/tracking_database > "$tracking_path"
    adb exec-out run-as $PACKAGE_NAME cat databases/tracking_database-wal > "$tracking_wal" 2>/dev/null
    adb exec-out run-as $PACKAGE_NAME cat databases/tracking_database-shm > "$tracking_shm" 2>/dev/null

    # assistant_database and its WAL files
    local assistant_path="$BACKUP_DIR/assistant_database_$backup_name"
    local assistant_wal="$BACKUP_DIR/assistant_database-wal_$backup_name"
    local assistant_shm="$BACKUP_DIR/assistant_database-shm_$backup_name"

    adb exec-out run-as $PACKAGE_NAME cat databases/assistant_database > "$assistant_path"
    adb exec-out run-as $PACKAGE_NAME cat databases/assistant_database-wal > "$assistant_wal" 2>/dev/null
    adb exec-out run-as $PACKAGE_NAME cat databases/assistant_database-shm > "$assistant_shm" 2>/dev/null

    # Report what was written
    echo -e "${GREEN}Databases and WAL files saved:${NC}"
    echo "Tracking DB: $(ls -lh "$tracking_path" | awk '{print $5}')"
    if [ -f "$tracking_wal" ] && [ -s "$tracking_wal" ]; then
        echo "Tracking WAL: $(ls -lh "$tracking_wal" | awk '{print $5}') <- REAL TRACKING DATA"
    fi
    echo "Assistant DB: $(ls -lh "$assistant_path" | awk '{print $5}')"
    if [ -f "$assistant_wal" ] && [ -s "$assistant_wal" ]; then
        echo "Assistant WAL: $(ls -lh "$assistant_wal" | awk '{print $5}') <- REAL ASSISTANT DATA"
    fi
    return 0
}

# Restore DB
restore_db() {
    local backup_file="$1"

    # A bare name is looked up inside BACKUP_DIR
    if [[ "$backup_file" != /* ]] && [[ "$backup_file" != ./* ]]; then
        backup_file="$BACKUP_DIR/$backup_file"
    fi

    if [ ! -f "$backup_file" ]; then
        echo -e "${RED}Backup file not found: $backup_file${NC}"
        list_backups
        return 1
    fi

    echo -e "${BLUE}Full restore from: $backup_file${NC}"

    # Stop the app
    adb shell am force-stop $PACKAGE_NAME

    # The file name says which database it is
    local backup_base=$(basename "$backup_file")
    local timestamp=""

    if [[ "$backup_base" =~ tracking_database_(.+)$ ]]; then
        timestamp="${BASH_REMATCH[1]}"
        echo -e "${BLUE}Restoring tracking_database and its WAL files (timestamp: $timestamp)${NC}"

        # The tracking_database file itself
        adb push "$backup_file" /sdcard/tmp_db
        adb shell run-as $PACKAGE_NAME sh -c "cat /sdcard/tmp_db > databases/tracking_database"
        adb shell rm /sdcard/tmp_db

        # Its WAL files, when they are there
        local wal_file="$BACKUP_DIR/tracking_database-wal_$timestamp"
        local shm_file="$BACKUP_DIR/tracking_database-shm_$timestamp"

        if [ -f "$wal_file" ]; then
            echo -e "${BLUE}Restoring the WAL file...${NC}"
            adb push "$wal_file" /sdcard/tmp_wal
            adb shell run-as $PACKAGE_NAME sh -c "cat /sdcard/tmp_wal > databases/tracking_database-wal"
            adb shell rm /sdcard/tmp_wal
        fi

        if [ -f "$shm_file" ]; then
            echo -e "${BLUE}Restoring the SHM file...${NC}"
            adb push "$shm_file" /sdcard/tmp_shm
            adb shell run-as $PACKAGE_NAME sh -c "cat /sdcard/tmp_shm > databases/tracking_database-shm"
            adb shell rm /sdcard/tmp_shm
        fi

        # The assistant_database of the same timestamp too (zones, tool_instances, and the rest)
        local assistant_file="$BACKUP_DIR/assistant_database_$timestamp"
        if [ -f "$assistant_file" ]; then
            echo -e "${BLUE}Restoring assistant_database (zones, tool_instances)...${NC}"
            adb push "$assistant_file" /sdcard/tmp_assistant
            adb shell run-as $PACKAGE_NAME sh -c "cat /sdcard/tmp_assistant > databases/assistant_database"
            adb shell rm /sdcard/tmp_assistant

            # The assistant WAL files, when they are there
            local assistant_wal_file="$BACKUP_DIR/assistant_database-wal_$timestamp"
            local assistant_shm_file="$BACKUP_DIR/assistant_database-shm_$timestamp"

            if [ -f "$assistant_wal_file" ] && [ -s "$assistant_wal_file" ]; then
                echo -e "${BLUE}Restoring the assistant WAL file...${NC}"
                adb push "$assistant_wal_file" /sdcard/tmp_assistant_wal
                adb shell run-as $PACKAGE_NAME sh -c "cat /sdcard/tmp_assistant_wal > databases/assistant_database-wal"
                adb shell rm /sdcard/tmp_assistant_wal
            fi

            if [ -f "$assistant_shm_file" ] && [ -s "$assistant_shm_file" ]; then
                echo -e "${BLUE}Restoring the assistant SHM file...${NC}"
                adb push "$assistant_shm_file" /sdcard/tmp_assistant_shm
                adb shell run-as $PACKAGE_NAME sh -c "cat /sdcard/tmp_assistant_shm > databases/assistant_database-shm"
                adb shell rm /sdcard/tmp_assistant_shm
            fi
        else
            echo -e "${RED}assistant_database_$timestamp not found - the metadata is missing${NC}"
        fi

    elif [[ "$backup_base" =~ assistant_database_(.+)$ ]]; then
        timestamp="${BASH_REMATCH[1]}"
        echo -e "${BLUE}Restoring assistant_database (timestamp: $timestamp)${NC}"

        adb push "$backup_file" /sdcard/tmp_db
        adb shell run-as $PACKAGE_NAME sh -c "cat /sdcard/tmp_db > databases/assistant_database"
        adb shell rm /sdcard/tmp_db

        # The assistant WAL files of the same timestamp
        local assistant_wal_file="$BACKUP_DIR/assistant_database-wal_$timestamp"
        local assistant_shm_file="$BACKUP_DIR/assistant_database-shm_$timestamp"

        if [ -f "$assistant_wal_file" ] && [ -s "$assistant_wal_file" ]; then
            echo -e "${BLUE}Restoring the assistant WAL file...${NC}"
            adb push "$assistant_wal_file" /sdcard/tmp_assistant_wal
            adb shell run-as $PACKAGE_NAME sh -c "cat /sdcard/tmp_assistant_wal > databases/assistant_database-wal"
            adb shell rm /sdcard/tmp_assistant_wal
        fi

        if [ -f "$assistant_shm_file" ] && [ -s "$assistant_shm_file" ]; then
            echo -e "${BLUE}Restoring the assistant SHM file...${NC}"
            adb push "$assistant_shm_file" /sdcard/tmp_assistant_shm
            adb shell run-as $PACKAGE_NAME sh -c "cat /sdcard/tmp_assistant_shm > databases/assistant_database-shm"
            adb shell rm /sdcard/tmp_assistant_shm
        fi

        # The tracking_database and its WAL of the same timestamp (the real data)
        local tracking_file="$BACKUP_DIR/tracking_database_$timestamp"
        if [ -f "$tracking_file" ]; then
            echo -e "${BLUE}Restoring tracking_database (the real data)...${NC}"
            adb push "$tracking_file" /sdcard/tmp_tracking
            adb shell run-as $PACKAGE_NAME sh -c "cat /sdcard/tmp_tracking > databases/tracking_database"
            adb shell rm /sdcard/tmp_tracking

            # The tracking WAL files
            local wal_file="$BACKUP_DIR/tracking_database-wal_$timestamp"
            local shm_file="$BACKUP_DIR/tracking_database-shm_$timestamp"

            if [ -f "$wal_file" ]; then
                echo -e "${BLUE}Restoring the tracking WAL file...${NC}"
                adb push "$wal_file" /sdcard/tmp_wal
                adb shell run-as $PACKAGE_NAME sh -c "cat /sdcard/tmp_wal > databases/tracking_database-wal"
                adb shell rm /sdcard/tmp_wal
            fi

            if [ -f "$shm_file" ]; then
                echo -e "${BLUE}Restoring the tracking SHM file...${NC}"
                adb push "$shm_file" /sdcard/tmp_shm
                adb shell run-as $PACKAGE_NAME sh -c "cat /sdcard/tmp_shm > databases/tracking_database-shm"
                adb shell rm /sdcard/tmp_shm
            fi
        fi
    else
        echo -e "${RED}Unrecognized backup file name: $backup_base${NC}"
        return 1
    fi

    if [ $? -eq 0 ]; then
        echo -e "${GREEN}Database and WAL files restored${NC}"
        echo -e "${BLUE}Restart the app to see the restored data${NC}"
        return 0
    else
        echo -e "${RED}Database restore failed${NC}"
        return 1
    fi
}

# List the backups on hand
list_backups() {
    echo -e "${BLUE}Backups on hand:${NC}"
    if [ -d "$BACKUP_DIR" ] && [ "$(ls -A $BACKUP_DIR 2>/dev/null)" ]; then
        ls -lht "$BACKUP_DIR"
    else
        echo -e "${RED}No backup found${NC}"
    fi
}

# Main menu
show_menu() {
    echo -e "\n${BLUE}=== DB BACKUP/RESTORE MENU ===${NC}"
    echo "1) Back up the current database"
    echo "2) List the backups"
    echo "3) Restore a backup"
    echo "4) Test a migration (automatic backup, restore on failure)"
    echo "q) Quit"
    echo -n "Choice: "
}

# Migration test, with a safety net
test_migration() {
    echo -e "${BLUE}SAFE MIGRATION TEST${NC}"

    # Automatic pre-test backup
    if backup_db "pre_migration_test"; then
        echo -e "${GREEN}Pre-test backup created${NC}"

        echo -e "${BLUE}Start the app to test the migration...${NC}"
        echo "-> Run the migration on the device"
        echo "-> Press r to restore if it goes wrong"
        echo "-> Press c to confirm it worked"

        read -n 1 -p "Action (r/c): " action
        echo ""

        case $action in
            r|R)
                echo -e "${BLUE}Restoring the pre-test backup...${NC}"
                restore_db "$BACKUP_DIR/assistant_database_pre_migration_test"
                ;;
            c|C)
                echo -e "${GREEN}Migration confirmed${NC}"
                ;;
            *)
                echo -e "${RED}Invalid action${NC}"
                ;;
        esac
    else
        echo -e "${RED}Pre-test backup failed - stopping here${NC}"
    fi
}

# Main
main() {
    echo -e "${GREEN}DB Backup/Restore Tool${NC}"

    check_device
    setup_backup_dir

    while true; do
        show_menu
        read choice

        case $choice in
            1)
                echo -n "Backup name (optional): "
                read backup_name
                backup_db "$backup_name"
                ;;
            2)
                list_backups
                ;;
            3)
                list_backups
                echo -n "Backup to restore: "
                read backup_path
                restore_db "$backup_path"
                ;;
            4)
                test_migration
                ;;
            q|Q)
                echo -e "${GREEN}Bye${NC}"
                exit 0
                ;;
            *)
                echo -e "${RED}Invalid choice${NC}"
                ;;
        esac
    done
}

main "$@"