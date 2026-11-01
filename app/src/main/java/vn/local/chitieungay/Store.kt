package vn.local.chitieungay

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

data class MoneyEvent(
    val id: Long,
    val createdAt: Long,
    val bank: String,
    val amount: Long,
    val direction: String,
    val description: String,
    val category: String,
    val note: String,
    val status: String
)

class Store(context: Context) : SQLiteOpenHelper(context.applicationContext, "local_finance.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE events (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            created_at INTEGER NOT NULL,
            source_package TEXT NOT NULL,
            amount INTEGER NOT NULL,
            direction TEXT NOT NULL,
            description TEXT NOT NULL,
            category TEXT NOT NULL DEFAULT '',
            note TEXT NOT NULL DEFAULT '',
            status TEXT NOT NULL DEFAULT 'pending',
            fingerprint TEXT NOT NULL UNIQUE
        )""")
        db.execSQL("CREATE INDEX idx_events_status ON events(status, created_at DESC)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) { /* v1 */ }

    @Synchronized fun insert(pkg: String, parsed: ParsedBankTx, fingerprint: String, time: Long): Long {
        val values = ContentValues().apply {
            put("created_at", time)
            put("source_package", pkg)
            put("amount", parsed.amount)
            put("direction", parsed.direction)
            put("description", parsed.description.take(1200))
            put("fingerprint", fingerprint)
        }
        return writableDatabase.insertWithOnConflict("events", null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }
    @Synchronized fun pending(): List<MoneyEvent> = readEvents("status='pending'", emptyArray(), "created_at DESC", 1000)
    @Synchronized fun recent(): List<MoneyEvent> = readEvents("status='confirmed'", emptyArray(), "created_at DESC", 30)
    @Synchronized fun get(id: Long): MoneyEvent? = readEvents("id=?", arrayOf(id.toString()), "id DESC", 1).firstOrNull()

    private fun readEvents(where: String, args: Array<String>, sort: String, limit: Int): List<MoneyEvent> {
        val result = mutableListOf<MoneyEvent>()
        readableDatabase.query("events", arrayOf("id", "created_at", "source_package", "amount", "direction", "description", "category", "note", "status"),
            where, args, null, null, sort, limit.toString()).use { c ->
            while (c.moveToNext()) result.add(MoneyEvent(c.getLong(0), c.getLong(1), c.getString(2), c.getLong(3), c.getString(4), c.getString(5), c.getString(6), c.getString(7), c.getString(8)))
        }
        return result
    }

    @Synchronized fun confirm(id: Long, direction: String, category: String, note: String): Boolean {
        if (direction !in setOf("income", "expense", "transfer") || category.isBlank() || note.isBlank()) return false
        val v = ContentValues().apply {
            put("status", "confirmed"); put("direction", direction); put("category", category); put("note", note.take(300))
        }
        return writableDatabase.update("events", v, "id=? AND status='pending'", arrayOf(id.toString())) == 1
    }
    @Synchronized fun ignore(id: Long): Boolean {
        return writableDatabase.update("events", ContentValues().apply { put("status", "ignored") }, "id=? AND status='pending'", arrayOf(id.toString())) == 1
    }
    @Synchronized fun monthSums(): Pair<Long,Long> {
        val start = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        var income = 0L; var expense = 0L
        readableDatabase.rawQuery("SELECT direction, COALESCE(SUM(amount), 0) FROM events WHERE status='confirmed' AND created_at>=? GROUP BY direction", arrayOf(start.toString())).use { c ->
            while (c.moveToNext()) when(c.getString(0)) { "income" -> income = c.getLong(1); "expense" -> expense = c.getLong(1) }
        }
        return income to expense
    }
    @Synchronized fun categoriesThisMonth(): List<Pair<String, Long>> {
        val start = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        val out = mutableListOf<Pair<String, Long>>()
        readableDatabase.rawQuery("SELECT category, SUM(amount) FROM events WHERE status='confirmed' AND direction='expense' AND created_at>=? GROUP BY category ORDER BY SUM(amount) DESC", arrayOf(start.toString())).use { c ->
            while (c.moveToNext()) out.add(c.getString(0) to c.getLong(1))
        }
        return out
    }
    companion object {
        fun money(v: Long): String = NumberFormat.getNumberInstance(Locale("vi", "VN")).format(v) + " đ"
    }
}
