package com.assistant.core.database.dao;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.assistant.core.database.entities.ToolInstance;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@SuppressWarnings({"unchecked", "deprecation"})
public final class ToolInstanceDao_Impl implements ToolInstanceDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<ToolInstance> __insertionAdapterOfToolInstance;

  private final EntityDeletionOrUpdateAdapter<ToolInstance> __deletionAdapterOfToolInstance;

  private final EntityDeletionOrUpdateAdapter<ToolInstance> __updateAdapterOfToolInstance;

  private final SharedSQLiteStatement __preparedStmtOfDeleteToolInstanceById;

  public ToolInstanceDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfToolInstance = new EntityInsertionAdapter<ToolInstance>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `tool_instances` (`id`,`zone_id`,`tool_type`,`config_json`,`config_metadata_json`,`order_index`,`created_at`,`updated_at`) VALUES (?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ToolInstance entity) {
        if (entity.getId() == null) {
          statement.bindNull(1);
        } else {
          statement.bindString(1, entity.getId());
        }
        if (entity.getZone_id() == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.getZone_id());
        }
        if (entity.getTool_type() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getTool_type());
        }
        if (entity.getConfig_json() == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.getConfig_json());
        }
        if (entity.getConfig_metadata_json() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getConfig_metadata_json());
        }
        statement.bindLong(6, entity.getOrder_index());
        statement.bindLong(7, entity.getCreated_at());
        statement.bindLong(8, entity.getUpdated_at());
      }
    };
    this.__deletionAdapterOfToolInstance = new EntityDeletionOrUpdateAdapter<ToolInstance>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `tool_instances` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ToolInstance entity) {
        if (entity.getId() == null) {
          statement.bindNull(1);
        } else {
          statement.bindString(1, entity.getId());
        }
      }
    };
    this.__updateAdapterOfToolInstance = new EntityDeletionOrUpdateAdapter<ToolInstance>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `tool_instances` SET `id` = ?,`zone_id` = ?,`tool_type` = ?,`config_json` = ?,`config_metadata_json` = ?,`order_index` = ?,`created_at` = ?,`updated_at` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ToolInstance entity) {
        if (entity.getId() == null) {
          statement.bindNull(1);
        } else {
          statement.bindString(1, entity.getId());
        }
        if (entity.getZone_id() == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.getZone_id());
        }
        if (entity.getTool_type() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getTool_type());
        }
        if (entity.getConfig_json() == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.getConfig_json());
        }
        if (entity.getConfig_metadata_json() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getConfig_metadata_json());
        }
        statement.bindLong(6, entity.getOrder_index());
        statement.bindLong(7, entity.getCreated_at());
        statement.bindLong(8, entity.getUpdated_at());
        if (entity.getId() == null) {
          statement.bindNull(9);
        } else {
          statement.bindString(9, entity.getId());
        }
      }
    };
    this.__preparedStmtOfDeleteToolInstanceById = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM tool_instances WHERE id = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insertToolInstance(final ToolInstance toolInstance,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfToolInstance.insert(toolInstance);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteToolInstance(final ToolInstance toolInstance,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfToolInstance.handle(toolInstance);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object updateToolInstance(final ToolInstance toolInstance,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfToolInstance.handle(toolInstance);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteToolInstanceById(final String id,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteToolInstanceById.acquire();
        int _argIndex = 1;
        if (id == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, id);
        }
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteToolInstanceById.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<ToolInstance>> getToolInstancesByZone(final String zoneId) {
    final String _sql = "SELECT * FROM tool_instances WHERE zone_id = ? ORDER BY order_index ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    if (zoneId == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, zoneId);
    }
    return CoroutinesRoom.createFlow(__db, false, new String[] {"tool_instances"}, new Callable<List<ToolInstance>>() {
      @Override
      @NonNull
      public List<ToolInstance> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfZoneId = CursorUtil.getColumnIndexOrThrow(_cursor, "zone_id");
          final int _cursorIndexOfToolType = CursorUtil.getColumnIndexOrThrow(_cursor, "tool_type");
          final int _cursorIndexOfConfigJson = CursorUtil.getColumnIndexOrThrow(_cursor, "config_json");
          final int _cursorIndexOfConfigMetadataJson = CursorUtil.getColumnIndexOrThrow(_cursor, "config_metadata_json");
          final int _cursorIndexOfOrderIndex = CursorUtil.getColumnIndexOrThrow(_cursor, "order_index");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "created_at");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updated_at");
          final List<ToolInstance> _result = new ArrayList<ToolInstance>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ToolInstance _item;
            final String _tmpId;
            if (_cursor.isNull(_cursorIndexOfId)) {
              _tmpId = null;
            } else {
              _tmpId = _cursor.getString(_cursorIndexOfId);
            }
            final String _tmpZone_id;
            if (_cursor.isNull(_cursorIndexOfZoneId)) {
              _tmpZone_id = null;
            } else {
              _tmpZone_id = _cursor.getString(_cursorIndexOfZoneId);
            }
            final String _tmpTool_type;
            if (_cursor.isNull(_cursorIndexOfToolType)) {
              _tmpTool_type = null;
            } else {
              _tmpTool_type = _cursor.getString(_cursorIndexOfToolType);
            }
            final String _tmpConfig_json;
            if (_cursor.isNull(_cursorIndexOfConfigJson)) {
              _tmpConfig_json = null;
            } else {
              _tmpConfig_json = _cursor.getString(_cursorIndexOfConfigJson);
            }
            final String _tmpConfig_metadata_json;
            if (_cursor.isNull(_cursorIndexOfConfigMetadataJson)) {
              _tmpConfig_metadata_json = null;
            } else {
              _tmpConfig_metadata_json = _cursor.getString(_cursorIndexOfConfigMetadataJson);
            }
            final int _tmpOrder_index;
            _tmpOrder_index = _cursor.getInt(_cursorIndexOfOrderIndex);
            final long _tmpCreated_at;
            _tmpCreated_at = _cursor.getLong(_cursorIndexOfCreatedAt);
            final long _tmpUpdated_at;
            _tmpUpdated_at = _cursor.getLong(_cursorIndexOfUpdatedAt);
            _item = new ToolInstance(_tmpId,_tmpZone_id,_tmpTool_type,_tmpConfig_json,_tmpConfig_metadata_json,_tmpOrder_index,_tmpCreated_at,_tmpUpdated_at);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getToolInstanceById(final String id,
      final Continuation<? super ToolInstance> $completion) {
    final String _sql = "SELECT * FROM tool_instances WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    if (id == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, id);
    }
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<ToolInstance>() {
      @Override
      @Nullable
      public ToolInstance call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfZoneId = CursorUtil.getColumnIndexOrThrow(_cursor, "zone_id");
          final int _cursorIndexOfToolType = CursorUtil.getColumnIndexOrThrow(_cursor, "tool_type");
          final int _cursorIndexOfConfigJson = CursorUtil.getColumnIndexOrThrow(_cursor, "config_json");
          final int _cursorIndexOfConfigMetadataJson = CursorUtil.getColumnIndexOrThrow(_cursor, "config_metadata_json");
          final int _cursorIndexOfOrderIndex = CursorUtil.getColumnIndexOrThrow(_cursor, "order_index");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "created_at");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updated_at");
          final ToolInstance _result;
          if (_cursor.moveToFirst()) {
            final String _tmpId;
            if (_cursor.isNull(_cursorIndexOfId)) {
              _tmpId = null;
            } else {
              _tmpId = _cursor.getString(_cursorIndexOfId);
            }
            final String _tmpZone_id;
            if (_cursor.isNull(_cursorIndexOfZoneId)) {
              _tmpZone_id = null;
            } else {
              _tmpZone_id = _cursor.getString(_cursorIndexOfZoneId);
            }
            final String _tmpTool_type;
            if (_cursor.isNull(_cursorIndexOfToolType)) {
              _tmpTool_type = null;
            } else {
              _tmpTool_type = _cursor.getString(_cursorIndexOfToolType);
            }
            final String _tmpConfig_json;
            if (_cursor.isNull(_cursorIndexOfConfigJson)) {
              _tmpConfig_json = null;
            } else {
              _tmpConfig_json = _cursor.getString(_cursorIndexOfConfigJson);
            }
            final String _tmpConfig_metadata_json;
            if (_cursor.isNull(_cursorIndexOfConfigMetadataJson)) {
              _tmpConfig_metadata_json = null;
            } else {
              _tmpConfig_metadata_json = _cursor.getString(_cursorIndexOfConfigMetadataJson);
            }
            final int _tmpOrder_index;
            _tmpOrder_index = _cursor.getInt(_cursorIndexOfOrderIndex);
            final long _tmpCreated_at;
            _tmpCreated_at = _cursor.getLong(_cursorIndexOfCreatedAt);
            final long _tmpUpdated_at;
            _tmpUpdated_at = _cursor.getLong(_cursorIndexOfUpdatedAt);
            _result = new ToolInstance(_tmpId,_tmpZone_id,_tmpTool_type,_tmpConfig_json,_tmpConfig_metadata_json,_tmpOrder_index,_tmpCreated_at,_tmpUpdated_at);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
