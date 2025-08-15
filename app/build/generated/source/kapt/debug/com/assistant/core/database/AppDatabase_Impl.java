package com.assistant.core.database;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import com.assistant.core.database.dao.ToolInstanceDao;
import com.assistant.core.database.dao.ToolInstanceDao_Impl;
import com.assistant.core.database.dao.ZoneDao;
import com.assistant.core.database.dao.ZoneDao_Impl;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SuppressWarnings({"unchecked", "deprecation"})
public final class AppDatabase_Impl extends AppDatabase {
  private volatile ZoneDao _zoneDao;

  private volatile ToolInstanceDao _toolInstanceDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(1) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `zones` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `description` TEXT, `color` TEXT, `order_index` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `tool_instances` (`id` TEXT NOT NULL, `zone_id` TEXT NOT NULL, `tool_type` TEXT NOT NULL, `config_json` TEXT NOT NULL, `config_metadata_json` TEXT NOT NULL, `order_index` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`zone_id`) REFERENCES `zones`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '1c572e406a4c4226b0fd18edbc803ab3')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `zones`");
        db.execSQL("DROP TABLE IF EXISTS `tool_instances`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        db.execSQL("PRAGMA foreign_keys = ON");
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsZones = new HashMap<String, TableInfo.Column>(7);
        _columnsZones.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsZones.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsZones.put("description", new TableInfo.Column("description", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsZones.put("color", new TableInfo.Column("color", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsZones.put("order_index", new TableInfo.Column("order_index", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsZones.put("created_at", new TableInfo.Column("created_at", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsZones.put("updated_at", new TableInfo.Column("updated_at", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysZones = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesZones = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoZones = new TableInfo("zones", _columnsZones, _foreignKeysZones, _indicesZones);
        final TableInfo _existingZones = TableInfo.read(db, "zones");
        if (!_infoZones.equals(_existingZones)) {
          return new RoomOpenHelper.ValidationResult(false, "zones(com.assistant.core.database.entities.Zone).\n"
                  + " Expected:\n" + _infoZones + "\n"
                  + " Found:\n" + _existingZones);
        }
        final HashMap<String, TableInfo.Column> _columnsToolInstances = new HashMap<String, TableInfo.Column>(8);
        _columnsToolInstances.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsToolInstances.put("zone_id", new TableInfo.Column("zone_id", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsToolInstances.put("tool_type", new TableInfo.Column("tool_type", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsToolInstances.put("config_json", new TableInfo.Column("config_json", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsToolInstances.put("config_metadata_json", new TableInfo.Column("config_metadata_json", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsToolInstances.put("order_index", new TableInfo.Column("order_index", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsToolInstances.put("created_at", new TableInfo.Column("created_at", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsToolInstances.put("updated_at", new TableInfo.Column("updated_at", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysToolInstances = new HashSet<TableInfo.ForeignKey>(1);
        _foreignKeysToolInstances.add(new TableInfo.ForeignKey("zones", "CASCADE", "NO ACTION", Arrays.asList("zone_id"), Arrays.asList("id")));
        final HashSet<TableInfo.Index> _indicesToolInstances = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoToolInstances = new TableInfo("tool_instances", _columnsToolInstances, _foreignKeysToolInstances, _indicesToolInstances);
        final TableInfo _existingToolInstances = TableInfo.read(db, "tool_instances");
        if (!_infoToolInstances.equals(_existingToolInstances)) {
          return new RoomOpenHelper.ValidationResult(false, "tool_instances(com.assistant.core.database.entities.ToolInstance).\n"
                  + " Expected:\n" + _infoToolInstances + "\n"
                  + " Found:\n" + _existingToolInstances);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "1c572e406a4c4226b0fd18edbc803ab3", "272ac28a2718e132a99a594812c6912e");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "zones","tool_instances");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    final boolean _supportsDeferForeignKeys = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP;
    try {
      if (!_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA foreign_keys = FALSE");
      }
      super.beginTransaction();
      if (_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA defer_foreign_keys = TRUE");
      }
      _db.execSQL("DELETE FROM `zones`");
      _db.execSQL("DELETE FROM `tool_instances`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      if (!_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA foreign_keys = TRUE");
      }
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(ZoneDao.class, ZoneDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(ToolInstanceDao.class, ToolInstanceDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public ZoneDao zoneDao() {
    if (_zoneDao != null) {
      return _zoneDao;
    } else {
      synchronized(this) {
        if(_zoneDao == null) {
          _zoneDao = new ZoneDao_Impl(this);
        }
        return _zoneDao;
      }
    }
  }

  @Override
  public ToolInstanceDao toolInstanceDao() {
    if (_toolInstanceDao != null) {
      return _toolInstanceDao;
    } else {
      synchronized(this) {
        if(_toolInstanceDao == null) {
          _toolInstanceDao = new ToolInstanceDao_Impl(this);
        }
        return _toolInstanceDao;
      }
    }
  }
}
