package vn.local.chitieungay

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val db by lazy { Store(this) }
    private var visiblePendingIds = listOf<Long>()
    private var requestedEventId: Long = -1L
    private val poller = object : Runnable {
        override fun run() {
            if (!isFinishing) {
                val latest = db.pending().map { it.id }
                if (latest != visiblePendingIds) render()
                handler.postDelayed(this, 2500)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedEventId = intent.getLongExtra("event_id", -1L)
        requestNotificationPermission()
        render()
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedEventId = intent.getLongExtra("event_id", -1L)
        render()
    }
    override fun onResume() { super.onResume(); render(); handler.removeCallbacks(poller); handler.postDelayed(poller, 2500) }
    override fun onPause() { handler.removeCallbacks(poller); super.onPause() }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 501)
    }
    private fun pad(v: View, vertical: Int = 12) { v.setPadding(dp(14), dp(vertical), dp(14), dp(vertical)) }
    private fun dp(x: Int): Int = (resources.displayMetrics.density * x + 0.5f).toInt()
    private fun block(text: String, size: Float = 15f, bold: Boolean = false): TextView = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(Color.rgb(28, 43, 48))
        if (bold) setTypeface(null, Typeface.BOLD)
        setPadding(dp(5), dp(6), dp(5), dp(6))
    }
    private fun button(text: String, onClick: () -> Unit): Button = Button(this).apply {
        this.text = text; setAllCaps(false); setOnClickListener { onClick() }
    }
    private fun section(parent: LinearLayout, title: String) { parent.addView(block(title, 19f, true)) }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(244, 248, 247))
            setPadding(dp(16), dp(20), dp(16), dp(20))
        }
        val scroller = ScrollView(this)
        scroller.addView(root)
        setContentView(scroller)
        root.addView(block("CHI TIÊU NGAY", 26f, true))
        root.addView(block("Tự đọc biến động số dư • Nhắc phân loại tức thì", 13f))

        root.addView(button("Cấp quyền đọc thông báo ngân hàng") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        })
        root.addView(button("Chọn ứng dụng ngân hàng được theo dõi") { editSources() })
        root.addView(button("Thử giao dịch giả (không dùng tiền thật)") {
            val now = System.currentTimeMillis()
            val parsed = ParsedBankTx(250000L, "expense", "DEMO: GD -250.000 VND — Cơm trưa")
            val id = db.insert("DEMO / THỬ NGHIỆM", parsed, "demo:$now", now)
            if (id > 0) {
                requestedEventId = id
                db.get(id)?.let { Reminder.show(this, it); Reminder.schedule(this, id) }
            }
            render()
        })
        root.addView(block("Chỉ lưu giao dịch trên điện thoại. App không đăng nhập ngân hàng.", 12f))

        val sums = db.monthSums()
        section(root, "THỐNG KÊ THÁNG NÀY")
        root.addView(block("Thu: ${Store.money(sums.first)}", 18f, true))
        root.addView(block("Chi: ${Store.money(sums.second)}", 18f, true))
        root.addView(block("Chênh lệch: ${Store.money(sums.first - sums.second)}"))
        db.categoriesThisMonth().forEach { (name, amount) -> root.addView(block("• $name: ${Store.money(amount)}")) }

        val pending = db.pending()
        visiblePendingIds = pending.map { it.id }
        section(root, "CHƯA PHÂN LOẠI (${pending.size})")
        if (pending.isEmpty()) root.addView(block("Không có giao dịch đang chờ."))
        else {
            // Notification deep link chooses a specific pending transaction, otherwise oldest first.
            val target = pending.firstOrNull { it.id == requestedEventId } ?: pending.last()
            root.addView(block("Cần xác nhận giao dịch trước khi tính vào thống kê danh mục.", 13f))
            addEditor(root, target)
            if (pending.size > 1) root.addView(block("Còn ${pending.size - 1} khoản đang chờ. Xử lý xong khoản này sẽ hiện khoản tiếp theo."))
        }

        section(root, "GIAO DỊCH ĐÃ GHI")
        db.recent().forEach { e ->
            val type = when(e.direction) { "income" -> "THU"; "expense" -> "CHI"; else -> "CHUYỂN NỘI BỘ" }
            val whenText = SimpleDateFormat("dd/MM HH:mm", Locale("vi", "VN")).format(Date(e.createdAt))
            root.addView(block("$whenText  •  $type  •  ${Store.money(e.amount)}\n${e.category}: ${e.note}", 14f))
        }
    }

    private fun addEditor(root: LinearLayout, e: MoneyEvent) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            pad(this, 14)
        }
        root.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin=dp(10); bottomMargin=dp(10) })
        card.addView(block("${Store.money(e.amount)}", 24f, true))
        card.addView(block("Nguồn: ${e.bank} • ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("vi", "VN")).format(Date(e.createdAt))}", 12f))
        card.addView(block("Dữ liệu nhận được: ${e.description.take(260)}", 12f))
        card.addView(block("1. Đây là khoản nào?", 16f, true))
        val radio = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        val types = listOf("expense" to "Chi", "income" to "Thu", "transfer" to "Chuyển nội bộ")
        types.forEachIndexed { idx, (_, name) ->
            radio.addView(RadioButton(this).apply { id = 3000 + idx; text = name; textSize = 13f })
        }
        card.addView(radio)
        card.addView(block("2. Danh mục", 16f, true))
        val spinner = Spinner(this)
        card.addView(spinner)
        val categories = mapOf(
            "expense" to listOf("— Chọn danh mục —", "Ăn uống", "Đi chợ", "Gia đình", "Mua sắm", "Di chuyển", "Hóa đơn", "Y tế", "Giải trí", "Học tập", "Kinh doanh", "Khác"),
            "income" to listOf("— Chọn danh mục —", "Lương", "Bán hàng", "Kinh doanh", "Lãi", "Hoàn tiền", "Quà tặng", "Khác"),
            "transfer" to listOf("— Chọn danh mục —", "Chuyển giữa tài khoản", "Nạp ví", "Rút tiết kiệm", "Khác")
        )
        var chosenType = ""
        fun updateSpinner(t: String) {
            chosenType = t
            val values = categories[t].orEmpty()
            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, values)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinner.adapter = adapter
            spinner.setSelection(0)
        }
        updateSpinner(e.direction.takeIf { it in categories } ?: "expense")
        radio.setOnCheckedChangeListener { _, checkedId ->
            val found = types.getOrNull(checkedId - 3000)?.first ?: return@setOnCheckedChangeListener
            updateSpinner(found)
        }
        if (e.direction in categories) radio.check(3000 + types.indexOfFirst { it.first == e.direction })
        card.addView(block("3. Ghi rõ nội dung (bắt buộc)", 16f, true))
        val note = EditText(this).apply {
            hint = "Ví dụ: Cơm trưa, mua sữa cho bé..."
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine(false)
            minLines = 2
        }
        card.addView(note)
        card.addView(button("LƯU PHÂN LOẠI") {
            val category = spinner.selectedItem?.toString().orEmpty()
            when {
                radio.checkedRadioButtonId == -1 -> alert("Hãy chọn Thu, Chi hoặc Chuyển nội bộ.")
                category.startsWith("—") || category.isBlank() -> alert("Bạn cần chọn danh mục cụ thể.")
                note.text.toString().isBlank() -> { note.error = "Phải nhập nội dung khoản này"; note.requestFocus() }
                db.confirm(e.id, chosenType, category, note.text.toString().trim()) -> {
                    Reminder.clear(this, e.id)
                    requestedEventId = -1L
                    render()
                }
                else -> alert("Không thể lưu. Kiểm tra lại giao dịch.")
            }
        })
        card.addView(button("Đây không phải giao dịch (bỏ qua)") {
            AlertDialog.Builder(this).setTitle("Bỏ qua giao dịch?")
                .setMessage("Chỉ chọn nếu đây là thông báo nhầm hoặc giao dịch trùng. Dữ liệu được đánh dấu bỏ qua, không tính báo cáo.")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xác nhận") { _, _ ->
                    if (db.ignore(e.id)) Reminder.clear(this, e.id)
                    requestedEventId = -1L
                    render()
                }.show()
        })
    }

    private fun editSources() {
        val pref = getSharedPreferences("sources", MODE_PRIVATE)
        val input = EditText(this).apply {
            setText(pref.getString("patterns", "mbbank,vietcombank,vcb,techcombank,bidv,acb,sacombank,tpbank,vpbank,momo"))
            setSingleLine(false)
            minLines = 3
        }
        AlertDialog.Builder(this)
            .setTitle("Lọc tên gói ứng dụng")
            .setMessage("Danh sách từ khóa package, cách nhau dấu phẩy. Chỉ theo dõi các app có tên gói khớp. Cần kiểm tra trên điện thoại thực tế; tên gói có thể khác.")
            .setView(input)
            .setNegativeButton("Hủy", null)
            .setPositiveButton("Lưu") { _, _ -> pref.edit().putString("patterns", input.text.toString()).apply() }
            .show()
    }
    private fun alert(message: String) { AlertDialog.Builder(this).setMessage(message).setPositiveButton("Đã hiểu", null).show() }
}
