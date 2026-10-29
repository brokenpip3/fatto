package com.brokenpip3.fatto.data.calendar

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** One lock for configuration and provider passes, including multiple store instances. */
object CalendarExportLock {
    val mutex = Mutex()
}

class CalendarExportStore(context: Context, private val databaseName: String = "calendar-export.db") :
    SQLiteOpenHelper(context, File(context.noBackupFilesDir, databaseName).absolutePath, null, 1) {
    private val preferences = context.getSharedPreferences(databaseName, Context.MODE_PRIVATE)
    private val activation = File(context.noBackupFilesDir, "$databaseName.activation")
    private val mutableStatus = MutableStateFlow(readPersistedStatus())
    val status: StateFlow<ExportStatus> = mutableStatus

    init {
        synchronized(preferenceLock) {
            if (!preferences.contains("namespace")) check(preferences.edit().putString("namespace", UUID.randomUUID().toString()).commit())
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE records (identity TEXT PRIMARY KEY, target TEXT NOT NULL, data TEXT NOT NULL)")
        db.execSQL("CREATE TABLE cleanup (target TEXT PRIMARY KEY)")
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int,
    ) = Unit

    fun readConfig(): ExportConfig =
        synchronized(preferenceLock) {
            ExportConfig(
                preferences.getString("namespace", "")!!, preferences.getBoolean("enabled", false),
                preferences.getBoolean("respectWait", true),
                preferences.getString("target", null)?.let(CalendarTarget::decode)?.also {
                    it.displayName =
                        preferences.getString("targetLabel", it.accountName)!!
                },
                preferences.getLong("generation", 0),
            )
        }

    fun updateConfig(
        enabled: Boolean,
        respectWait: Boolean,
        target: CalendarTarget?,
    ): ExportConfig =
        synchronized(preferenceLock) {
            check(
                preferences.edit().putBoolean("enabled", enabled).putBoolean("respectWait", respectWait)
                    .putString("target", target?.encode()).putString("targetLabel", target?.displayName).putLong(
                        "generation",
                        readConfig().generation + 1,
                    ).commit(),
            )
            readConfig()
        }

    fun activate() {
        val token = UUID.randomUUID().toString()
        activation.writeText(token)
        check(preferences.edit().putString("activation", token).commit())
    }

    fun isActivated(): Boolean = activation.exists() && activation.readText() == preferences.getString("activation", null)

    fun request(): Long =
        synchronized(preferenceLock) {
            val value = readRequestedGeneration() + 1
            check(preferences.edit().putLong("requested", value).commit())
            value
        }

    fun readRequestedGeneration(): Long = preferences.getLong("requested", 0)

    fun records(target: CalendarTarget): List<ExportRecord> =
        readableDatabase.query(
            "records", arrayOf("data"), "target=?",
            arrayOf(target.encode()), null, null, null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val raw = JSONObject(cursor.getString(0))
                    add(
                        ExportRecord(
                            EventKey.decode(raw.getString("key")), target, if (raw.isNull("id")) null else raw.getLong("id"),
                            raw.getString("fingerprint"), ExportOperation.valueOf(raw.getString("operation")),
                        ),
                    )
                }
            }
        }

    fun saveRecord(record: ExportRecord) {
        val raw =
            JSONObject().put("key", record.key.encode()).put("id", record.eventId ?: JSONObject.NULL)
                .put("fingerprint", record.fingerprint).put("operation", record.operation.name)
        writableDatabase.insertWithOnConflict(
            "records", null,
            ContentValues().apply {
                put("identity", record.target.encode() + record.key.encode())
                put("target", record.target.encode())
                put("data", raw.toString())
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        ).also { check(it != -1L) }
    }

    fun removeRecord(
        key: EventKey,
        target: CalendarTarget,
    ) {
        writableDatabase.delete(
            "records", "identity=?",
            arrayOf(
                target.encode() + key.encode(),
            ),
        )
    }

    fun setCleanupPending(target: CalendarTarget) {
        writableDatabase.insertWithOnConflict(
            "cleanup", null,
            ContentValues().apply { put("target", target.encode()) }, SQLiteDatabase.CONFLICT_IGNORE,
        )
    }

    fun cleanupTargets(): List<CalendarTarget> =
        readableDatabase.rawQuery("SELECT target FROM cleanup", null).use { cursor ->
            buildList { while (cursor.moveToNext()) add(CalendarTarget.decode(cursor.getString(0))) }
        }

    fun completeCleanup(target: CalendarTarget) {
        writableDatabase.delete("cleanup", "target=?", arrayOf(target.encode()))
    }

    fun readStatus(): ExportStatus = readPersistedStatus()

    private fun readPersistedStatus(): ExportStatus =
        ExportStatus(
            ExportPhase.valueOf(preferences.getString("phase", ExportPhase.DISABLED.name)!!),
            preferences.getLong("lastSuccess", 0).takeIf { it != 0L }, preferences.getString("message", null),
        )

    fun writeStatus(status: ExportStatus) {
        check(
            preferences.edit().putString("phase", status.phase.name).putLong("lastSuccess", status.lastSuccessMillis ?: 0)
                .putString("message", status.message).commit(),
        )
        mutableStatus.value = status
    }

    companion object {
        private val preferenceLock = Any()
    }
}
