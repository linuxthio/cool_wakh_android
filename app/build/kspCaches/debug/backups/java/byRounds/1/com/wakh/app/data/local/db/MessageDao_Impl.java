package com.wakh.app.data.local.db;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
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
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class MessageDao_Impl implements MessageDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<MessageEntity> __insertionAdapterOfMessageEntity;

  private final Converters __converters = new Converters();

  private final SharedSQLiteStatement __preparedStmtOfUpdateStatus;

  private final SharedSQLiteStatement __preparedStmtOfUpdateStatusWithQueuedId;

  private final SharedSQLiteStatement __preparedStmtOfMarkConversationRead;

  private final SharedSQLiteStatement __preparedStmtOfDeleteById;

  public MessageDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfMessageEntity = new EntityInsertionAdapter<MessageEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `messages` (`id`,`contactPhoneNumber`,`direction`,`kind`,`localFilePath`,`textContent`,`fileName`,`durationMs`,`sizeBytes`,`timestamp`,`status`,`isRead`,`senderPhoneNumber`,`queuedRemoteId`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final MessageEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getContactPhoneNumber());
        final String _tmp = __converters.fromMessageDirection(entity.getDirection());
        statement.bindString(3, _tmp);
        final String _tmp_1 = __converters.fromMessageKind(entity.getKind());
        statement.bindString(4, _tmp_1);
        if (entity.getLocalFilePath() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getLocalFilePath());
        }
        if (entity.getTextContent() == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.getTextContent());
        }
        if (entity.getFileName() == null) {
          statement.bindNull(7);
        } else {
          statement.bindString(7, entity.getFileName());
        }
        statement.bindLong(8, entity.getDurationMs());
        statement.bindLong(9, entity.getSizeBytes());
        statement.bindLong(10, entity.getTimestamp());
        final String _tmp_2 = __converters.fromMessageStatus(entity.getStatus());
        statement.bindString(11, _tmp_2);
        final int _tmp_3 = entity.isRead() ? 1 : 0;
        statement.bindLong(12, _tmp_3);
        if (entity.getSenderPhoneNumber() == null) {
          statement.bindNull(13);
        } else {
          statement.bindString(13, entity.getSenderPhoneNumber());
        }
        if (entity.getQueuedRemoteId() == null) {
          statement.bindNull(14);
        } else {
          statement.bindString(14, entity.getQueuedRemoteId());
        }
      }
    };
    this.__preparedStmtOfUpdateStatus = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE messages SET status = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfUpdateStatusWithQueuedId = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE messages SET status = ?, queuedRemoteId = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfMarkConversationRead = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE messages SET isRead = 1 WHERE contactPhoneNumber = ? AND direction = 'RECEIVED' AND isRead = 0";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteById = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM messages WHERE id = ?";
        return _query;
      }
    };
  }

  @Override
  public Object upsert(final MessageEntity message, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfMessageEntity.insert(message);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object updateStatus(final String id, final MessageStatus status,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateStatus.acquire();
        int _argIndex = 1;
        final String _tmp = __converters.fromMessageStatus(status);
        _stmt.bindString(_argIndex, _tmp);
        _argIndex = 2;
        _stmt.bindString(_argIndex, id);
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
          __preparedStmtOfUpdateStatus.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object updateStatusWithQueuedId(final String id, final MessageStatus status,
      final String queuedRemoteId, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateStatusWithQueuedId.acquire();
        int _argIndex = 1;
        final String _tmp = __converters.fromMessageStatus(status);
        _stmt.bindString(_argIndex, _tmp);
        _argIndex = 2;
        if (queuedRemoteId == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, queuedRemoteId);
        }
        _argIndex = 3;
        _stmt.bindString(_argIndex, id);
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
          __preparedStmtOfUpdateStatusWithQueuedId.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object markConversationRead(final String phoneNumber,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfMarkConversationRead.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, phoneNumber);
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
          __preparedStmtOfMarkConversationRead.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteById(final String id, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteById.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, id);
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
          __preparedStmtOfDeleteById.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<MessageEntity>> observeForContact(final String phoneNumber) {
    final String _sql = "SELECT * FROM messages WHERE contactPhoneNumber = ? ORDER BY timestamp ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, phoneNumber);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"messages"}, new Callable<List<MessageEntity>>() {
      @Override
      @NonNull
      public List<MessageEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfContactPhoneNumber = CursorUtil.getColumnIndexOrThrow(_cursor, "contactPhoneNumber");
          final int _cursorIndexOfDirection = CursorUtil.getColumnIndexOrThrow(_cursor, "direction");
          final int _cursorIndexOfKind = CursorUtil.getColumnIndexOrThrow(_cursor, "kind");
          final int _cursorIndexOfLocalFilePath = CursorUtil.getColumnIndexOrThrow(_cursor, "localFilePath");
          final int _cursorIndexOfTextContent = CursorUtil.getColumnIndexOrThrow(_cursor, "textContent");
          final int _cursorIndexOfFileName = CursorUtil.getColumnIndexOrThrow(_cursor, "fileName");
          final int _cursorIndexOfDurationMs = CursorUtil.getColumnIndexOrThrow(_cursor, "durationMs");
          final int _cursorIndexOfSizeBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "sizeBytes");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfIsRead = CursorUtil.getColumnIndexOrThrow(_cursor, "isRead");
          final int _cursorIndexOfSenderPhoneNumber = CursorUtil.getColumnIndexOrThrow(_cursor, "senderPhoneNumber");
          final int _cursorIndexOfQueuedRemoteId = CursorUtil.getColumnIndexOrThrow(_cursor, "queuedRemoteId");
          final List<MessageEntity> _result = new ArrayList<MessageEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final MessageEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpContactPhoneNumber;
            _tmpContactPhoneNumber = _cursor.getString(_cursorIndexOfContactPhoneNumber);
            final MessageDirection _tmpDirection;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfDirection);
            _tmpDirection = __converters.toMessageDirection(_tmp);
            final MessageKind _tmpKind;
            final String _tmp_1;
            _tmp_1 = _cursor.getString(_cursorIndexOfKind);
            _tmpKind = __converters.toMessageKind(_tmp_1);
            final String _tmpLocalFilePath;
            if (_cursor.isNull(_cursorIndexOfLocalFilePath)) {
              _tmpLocalFilePath = null;
            } else {
              _tmpLocalFilePath = _cursor.getString(_cursorIndexOfLocalFilePath);
            }
            final String _tmpTextContent;
            if (_cursor.isNull(_cursorIndexOfTextContent)) {
              _tmpTextContent = null;
            } else {
              _tmpTextContent = _cursor.getString(_cursorIndexOfTextContent);
            }
            final String _tmpFileName;
            if (_cursor.isNull(_cursorIndexOfFileName)) {
              _tmpFileName = null;
            } else {
              _tmpFileName = _cursor.getString(_cursorIndexOfFileName);
            }
            final int _tmpDurationMs;
            _tmpDurationMs = _cursor.getInt(_cursorIndexOfDurationMs);
            final long _tmpSizeBytes;
            _tmpSizeBytes = _cursor.getLong(_cursorIndexOfSizeBytes);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final MessageStatus _tmpStatus;
            final String _tmp_2;
            _tmp_2 = _cursor.getString(_cursorIndexOfStatus);
            _tmpStatus = __converters.toMessageStatus(_tmp_2);
            final boolean _tmpIsRead;
            final int _tmp_3;
            _tmp_3 = _cursor.getInt(_cursorIndexOfIsRead);
            _tmpIsRead = _tmp_3 != 0;
            final String _tmpSenderPhoneNumber;
            if (_cursor.isNull(_cursorIndexOfSenderPhoneNumber)) {
              _tmpSenderPhoneNumber = null;
            } else {
              _tmpSenderPhoneNumber = _cursor.getString(_cursorIndexOfSenderPhoneNumber);
            }
            final String _tmpQueuedRemoteId;
            if (_cursor.isNull(_cursorIndexOfQueuedRemoteId)) {
              _tmpQueuedRemoteId = null;
            } else {
              _tmpQueuedRemoteId = _cursor.getString(_cursorIndexOfQueuedRemoteId);
            }
            _item = new MessageEntity(_tmpId,_tmpContactPhoneNumber,_tmpDirection,_tmpKind,_tmpLocalFilePath,_tmpTextContent,_tmpFileName,_tmpDurationMs,_tmpSizeBytes,_tmpTimestamp,_tmpStatus,_tmpIsRead,_tmpSenderPhoneNumber,_tmpQueuedRemoteId);
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
  public Flow<List<MessageEntity>> observeLastMessagePerContact() {
    final String _sql = "\n"
            + "        SELECT m.* FROM messages m\n"
            + "        INNER JOIN (\n"
            + "            SELECT contactPhoneNumber, MAX(timestamp) AS maxTimestamp\n"
            + "            FROM messages\n"
            + "            GROUP BY contactPhoneNumber\n"
            + "        ) latest\n"
            + "        ON m.contactPhoneNumber = latest.contactPhoneNumber AND m.timestamp = latest.maxTimestamp\n"
            + "        ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"messages"}, new Callable<List<MessageEntity>>() {
      @Override
      @NonNull
      public List<MessageEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfContactPhoneNumber = CursorUtil.getColumnIndexOrThrow(_cursor, "contactPhoneNumber");
          final int _cursorIndexOfDirection = CursorUtil.getColumnIndexOrThrow(_cursor, "direction");
          final int _cursorIndexOfKind = CursorUtil.getColumnIndexOrThrow(_cursor, "kind");
          final int _cursorIndexOfLocalFilePath = CursorUtil.getColumnIndexOrThrow(_cursor, "localFilePath");
          final int _cursorIndexOfTextContent = CursorUtil.getColumnIndexOrThrow(_cursor, "textContent");
          final int _cursorIndexOfFileName = CursorUtil.getColumnIndexOrThrow(_cursor, "fileName");
          final int _cursorIndexOfDurationMs = CursorUtil.getColumnIndexOrThrow(_cursor, "durationMs");
          final int _cursorIndexOfSizeBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "sizeBytes");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfIsRead = CursorUtil.getColumnIndexOrThrow(_cursor, "isRead");
          final int _cursorIndexOfSenderPhoneNumber = CursorUtil.getColumnIndexOrThrow(_cursor, "senderPhoneNumber");
          final int _cursorIndexOfQueuedRemoteId = CursorUtil.getColumnIndexOrThrow(_cursor, "queuedRemoteId");
          final List<MessageEntity> _result = new ArrayList<MessageEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final MessageEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpContactPhoneNumber;
            _tmpContactPhoneNumber = _cursor.getString(_cursorIndexOfContactPhoneNumber);
            final MessageDirection _tmpDirection;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfDirection);
            _tmpDirection = __converters.toMessageDirection(_tmp);
            final MessageKind _tmpKind;
            final String _tmp_1;
            _tmp_1 = _cursor.getString(_cursorIndexOfKind);
            _tmpKind = __converters.toMessageKind(_tmp_1);
            final String _tmpLocalFilePath;
            if (_cursor.isNull(_cursorIndexOfLocalFilePath)) {
              _tmpLocalFilePath = null;
            } else {
              _tmpLocalFilePath = _cursor.getString(_cursorIndexOfLocalFilePath);
            }
            final String _tmpTextContent;
            if (_cursor.isNull(_cursorIndexOfTextContent)) {
              _tmpTextContent = null;
            } else {
              _tmpTextContent = _cursor.getString(_cursorIndexOfTextContent);
            }
            final String _tmpFileName;
            if (_cursor.isNull(_cursorIndexOfFileName)) {
              _tmpFileName = null;
            } else {
              _tmpFileName = _cursor.getString(_cursorIndexOfFileName);
            }
            final int _tmpDurationMs;
            _tmpDurationMs = _cursor.getInt(_cursorIndexOfDurationMs);
            final long _tmpSizeBytes;
            _tmpSizeBytes = _cursor.getLong(_cursorIndexOfSizeBytes);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final MessageStatus _tmpStatus;
            final String _tmp_2;
            _tmp_2 = _cursor.getString(_cursorIndexOfStatus);
            _tmpStatus = __converters.toMessageStatus(_tmp_2);
            final boolean _tmpIsRead;
            final int _tmp_3;
            _tmp_3 = _cursor.getInt(_cursorIndexOfIsRead);
            _tmpIsRead = _tmp_3 != 0;
            final String _tmpSenderPhoneNumber;
            if (_cursor.isNull(_cursorIndexOfSenderPhoneNumber)) {
              _tmpSenderPhoneNumber = null;
            } else {
              _tmpSenderPhoneNumber = _cursor.getString(_cursorIndexOfSenderPhoneNumber);
            }
            final String _tmpQueuedRemoteId;
            if (_cursor.isNull(_cursorIndexOfQueuedRemoteId)) {
              _tmpQueuedRemoteId = null;
            } else {
              _tmpQueuedRemoteId = _cursor.getString(_cursorIndexOfQueuedRemoteId);
            }
            _item = new MessageEntity(_tmpId,_tmpContactPhoneNumber,_tmpDirection,_tmpKind,_tmpLocalFilePath,_tmpTextContent,_tmpFileName,_tmpDurationMs,_tmpSizeBytes,_tmpTimestamp,_tmpStatus,_tmpIsRead,_tmpSenderPhoneNumber,_tmpQueuedRemoteId);
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
  public Flow<List<UnreadCount>> observeUnreadCounts() {
    final String _sql = "\n"
            + "        SELECT contactPhoneNumber, COUNT(*) AS unreadCount\n"
            + "        FROM messages\n"
            + "        WHERE direction = 'RECEIVED' AND isRead = 0\n"
            + "        GROUP BY contactPhoneNumber\n"
            + "        ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"messages"}, new Callable<List<UnreadCount>>() {
      @Override
      @NonNull
      public List<UnreadCount> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfContactPhoneNumber = 0;
          final int _cursorIndexOfUnreadCount = 1;
          final List<UnreadCount> _result = new ArrayList<UnreadCount>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final UnreadCount _item;
            final String _tmpContactPhoneNumber;
            _tmpContactPhoneNumber = _cursor.getString(_cursorIndexOfContactPhoneNumber);
            final int _tmpUnreadCount;
            _tmpUnreadCount = _cursor.getInt(_cursorIndexOfUnreadCount);
            _item = new UnreadCount(_tmpContactPhoneNumber,_tmpUnreadCount);
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
  public Object getById(final String id, final Continuation<? super MessageEntity> $completion) {
    final String _sql = "SELECT * FROM messages WHERE id = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<MessageEntity>() {
      @Override
      @Nullable
      public MessageEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfContactPhoneNumber = CursorUtil.getColumnIndexOrThrow(_cursor, "contactPhoneNumber");
          final int _cursorIndexOfDirection = CursorUtil.getColumnIndexOrThrow(_cursor, "direction");
          final int _cursorIndexOfKind = CursorUtil.getColumnIndexOrThrow(_cursor, "kind");
          final int _cursorIndexOfLocalFilePath = CursorUtil.getColumnIndexOrThrow(_cursor, "localFilePath");
          final int _cursorIndexOfTextContent = CursorUtil.getColumnIndexOrThrow(_cursor, "textContent");
          final int _cursorIndexOfFileName = CursorUtil.getColumnIndexOrThrow(_cursor, "fileName");
          final int _cursorIndexOfDurationMs = CursorUtil.getColumnIndexOrThrow(_cursor, "durationMs");
          final int _cursorIndexOfSizeBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "sizeBytes");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfIsRead = CursorUtil.getColumnIndexOrThrow(_cursor, "isRead");
          final int _cursorIndexOfSenderPhoneNumber = CursorUtil.getColumnIndexOrThrow(_cursor, "senderPhoneNumber");
          final int _cursorIndexOfQueuedRemoteId = CursorUtil.getColumnIndexOrThrow(_cursor, "queuedRemoteId");
          final MessageEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpContactPhoneNumber;
            _tmpContactPhoneNumber = _cursor.getString(_cursorIndexOfContactPhoneNumber);
            final MessageDirection _tmpDirection;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfDirection);
            _tmpDirection = __converters.toMessageDirection(_tmp);
            final MessageKind _tmpKind;
            final String _tmp_1;
            _tmp_1 = _cursor.getString(_cursorIndexOfKind);
            _tmpKind = __converters.toMessageKind(_tmp_1);
            final String _tmpLocalFilePath;
            if (_cursor.isNull(_cursorIndexOfLocalFilePath)) {
              _tmpLocalFilePath = null;
            } else {
              _tmpLocalFilePath = _cursor.getString(_cursorIndexOfLocalFilePath);
            }
            final String _tmpTextContent;
            if (_cursor.isNull(_cursorIndexOfTextContent)) {
              _tmpTextContent = null;
            } else {
              _tmpTextContent = _cursor.getString(_cursorIndexOfTextContent);
            }
            final String _tmpFileName;
            if (_cursor.isNull(_cursorIndexOfFileName)) {
              _tmpFileName = null;
            } else {
              _tmpFileName = _cursor.getString(_cursorIndexOfFileName);
            }
            final int _tmpDurationMs;
            _tmpDurationMs = _cursor.getInt(_cursorIndexOfDurationMs);
            final long _tmpSizeBytes;
            _tmpSizeBytes = _cursor.getLong(_cursorIndexOfSizeBytes);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final MessageStatus _tmpStatus;
            final String _tmp_2;
            _tmp_2 = _cursor.getString(_cursorIndexOfStatus);
            _tmpStatus = __converters.toMessageStatus(_tmp_2);
            final boolean _tmpIsRead;
            final int _tmp_3;
            _tmp_3 = _cursor.getInt(_cursorIndexOfIsRead);
            _tmpIsRead = _tmp_3 != 0;
            final String _tmpSenderPhoneNumber;
            if (_cursor.isNull(_cursorIndexOfSenderPhoneNumber)) {
              _tmpSenderPhoneNumber = null;
            } else {
              _tmpSenderPhoneNumber = _cursor.getString(_cursorIndexOfSenderPhoneNumber);
            }
            final String _tmpQueuedRemoteId;
            if (_cursor.isNull(_cursorIndexOfQueuedRemoteId)) {
              _tmpQueuedRemoteId = null;
            } else {
              _tmpQueuedRemoteId = _cursor.getString(_cursorIndexOfQueuedRemoteId);
            }
            _result = new MessageEntity(_tmpId,_tmpContactPhoneNumber,_tmpDirection,_tmpKind,_tmpLocalFilePath,_tmpTextContent,_tmpFileName,_tmpDurationMs,_tmpSizeBytes,_tmpTimestamp,_tmpStatus,_tmpIsRead,_tmpSenderPhoneNumber,_tmpQueuedRemoteId);
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
