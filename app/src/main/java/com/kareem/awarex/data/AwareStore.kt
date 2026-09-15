package com.kareem.awarex.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.kareem.awarex.core.model.Observation
import com.kareem.awarex.core.model.OpenLoop

class AwareStore(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE observations(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                text TEXT NOT NULL,
                source TEXT NOT NULL,
                observed_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_observations_time ON observations(observed_at DESC)")
        db.execSQL(
            """
            CREATE TABLE open_loops(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                normalized_subject TEXT NOT NULL,
                created_from_observation_id INTEGER NOT NULL,
                due_at INTEGER,
                created_at INTEGER NOT NULL,
                resolved_at INTEGER,
                resolution_observation_id INTEGER,
                FOREIGN KEY(created_from_observation_id) REFERENCES observations(id),
                FOREIGN KEY(resolution_observation_id) REFERENCES observations(id)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_open_loops_active ON open_loops(resolved_at,due_at,created_at DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        error("No AWAREX migration exists from $oldVersion to $newVersion; clean-room schema is version $DATABASE_VERSION")
    }

    fun insertObservation(text: String, source: String, observedAt: Long): Observation {
        val clean = text.trim()
        require(clean.isNotEmpty()) { "Observation must not be empty" }
        val values = ContentValues().apply {
            put("text", clean)
            put("source", source)
            put("observed_at", observedAt)
        }
        val id = writableDatabase.insertOrThrow("observations", null, values)
        return Observation(id = id, text = clean, source = source, observedAt = observedAt)
    }

    fun insertOpenLoop(
        title: String,
        normalizedSubject: String,
        observationId: Long,
        dueAt: Long?,
        createdAt: Long
    ): OpenLoop {
        val values = ContentValues().apply {
            put("title", title)
            put("normalized_subject", normalizedSubject)
            put("created_from_observation_id", observationId)
            if (dueAt == null) putNull("due_at") else put("due_at", dueAt)
            put("created_at", createdAt)
        }
        val id = writableDatabase.insertOrThrow("open_loops", null, values)
        return OpenLoop(
            id = id,
            title = title,
            normalizedSubject = normalizedSubject,
            createdFromObservationId = observationId,
            dueAt = dueAt,
            createdAt = createdAt,
            resolvedAt = null,
            resolutionObservationId = null
        )
    }

    fun activeOpenLoops(): List<OpenLoop> {
        val out = mutableListOf<OpenLoop>()
        readableDatabase.rawQuery(
            """
            SELECT id,title,normalized_subject,created_from_observation_id,due_at,created_at,resolved_at,resolution_observation_id
            FROM open_loops
            WHERE resolved_at IS NULL
            ORDER BY CASE WHEN due_at IS NULL THEN 1 ELSE 0 END,due_at ASC,created_at DESC
            """.trimIndent(),
            null
        ).use { cursor ->
            while (cursor.moveToNext()) out += cursor.toOpenLoop()
        }
        return out
    }

    fun observation(id: Long): Observation? {
        readableDatabase.rawQuery(
            "SELECT id,text,source,observed_at FROM observations WHERE id=?",
            arrayOf(id.toString())
        ).use { cursor ->
            return if (cursor.moveToFirst()) {
                Observation(
                    id = cursor.getLong(0),
                    text = cursor.getString(1),
                    source = cursor.getString(2),
                    observedAt = cursor.getLong(3)
                )
            } else null
        }
    }

    fun resolveOpenLoop(loopId: Long, observationId: Long, resolvedAt: Long): Boolean {
        val values = ContentValues().apply {
            put("resolved_at", resolvedAt)
            put("resolution_observation_id", observationId)
        }
        return writableDatabase.update(
            "open_loops",
            values,
            "id=? AND resolved_at IS NULL",
            arrayOf(loopId.toString())
        ) == 1
    }

    fun recentObservations(limit: Int = 50): List<Observation> {
        val safeLimit = limit.coerceIn(1, 200)
        val out = mutableListOf<Observation>()
        readableDatabase.rawQuery(
            "SELECT id,text,source,observed_at FROM observations ORDER BY observed_at DESC,id DESC LIMIT $safeLimit",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                out += Observation(cursor.getLong(0), cursor.getString(1), cursor.getString(2), cursor.getLong(3))
            }
        }
        return out
    }

    private fun Cursor.toOpenLoop() = OpenLoop(
        id = getLong(0),
        title = getString(1),
        normalizedSubject = getString(2),
        createdFromObservationId = getLong(3),
        dueAt = if (isNull(4)) null else getLong(4),
        createdAt = getLong(5),
        resolvedAt = if (isNull(6)) null else getLong(6),
        resolutionObservationId = if (isNull(7)) null else getLong(7)
    )

    companion object {
        private const val DATABASE_NAME = "awarex.db"
        private const val DATABASE_VERSION = 1
    }
}
