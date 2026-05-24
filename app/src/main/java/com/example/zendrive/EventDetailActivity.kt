package com.example.zendrive

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EventDetailActivity : AppCompatActivity() {

    private var eventId: Int = -1
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private var currencyCode: String = "INR"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event_detail)

        eventId = intent.getIntExtra("eventId", -1)
        if (eventId == -1) {
            finish()
            return
        }

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_edit_event) {
                startActivity(
                    Intent(this, AddEventActivity::class.java).putExtra("eventId", eventId)
                )
                true
            } else {
                false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadEvent()
    }

    private fun loadEvent() {
        val db = AppDatabase.getInstance(this)
        lifecycleScope.launch {
            val profile = db.userProfileDao().getProfile()
            currencyCode = profile?.preferredCurrencyCode?.takeIf { it.isNotBlank() } ?: "INR"

            val event = db.vehicleEventDao().getEventById(eventId) ?: run {
                finish()
                return@launch
            }

            val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
            toolbar.title = event.title

            findViewById<TextView>(R.id.tvEventTypeBadge).text = event.eventType.uppercase()
            findViewById<TextView>(R.id.tvEventDate).text = dateFormat.format(Date(event.date))
            findViewById<TextView>(R.id.tvEventTitle).text = event.title

            val tvDescLabel = findViewById<TextView>(R.id.tvDescriptionLabel)
            val tvDesc = findViewById<TextView>(R.id.tvDescription)
            if (!event.description.isNullOrBlank()) {
                tvDescLabel.visibility = View.VISIBLE
                tvDesc.visibility = View.VISIBLE
                tvDesc.text = event.description
            } else {
                tvDescLabel.visibility = View.GONE
                tvDesc.visibility = View.GONE
            }

            val rowCost = findViewById<LinearLayout>(R.id.rowCost)
            val tvCost = findViewById<TextView>(R.id.tvCost)
            if (event.cost != null && event.cost > 0) {
                rowCost.visibility = View.VISIBLE
                tvCost.text = "$currencyCode ${String.format(Locale.getDefault(), "%,.0f", event.cost)}"
            } else {
                rowCost.visibility = View.GONE
            }

            val rowOdometer = findViewById<LinearLayout>(R.id.rowOdometer)
            val tvOdometer = findViewById<TextView>(R.id.tvOdometer)
            if (event.odometer != null && event.odometer > 0) {
                rowOdometer.visibility = View.VISIBLE
                tvOdometer.text = "${String.format(Locale.getDefault(), "%,.0f", event.odometer)} km"
            } else {
                rowOdometer.visibility = View.GONE
            }

            val rowNextDue = findViewById<LinearLayout>(R.id.rowNextDue)
            val tvNextDue = findViewById<TextView>(R.id.tvNextDue)
            if (event.nextDueDate != null) {
                rowNextDue.visibility = View.VISIBLE
                tvNextDue.text = dateFormat.format(Date(event.nextDueDate))
            } else {
                rowNextDue.visibility = View.GONE
            }

            val metaList = db.eventMetaDao().getMetaForEvent(eventId)
            val header = findViewById<TextView>(R.id.tvExtraFieldsHeader)
            val llMeta = findViewById<LinearLayout>(R.id.llMetaDetail)
            llMeta.removeAllViews()
            if (metaList.isNotEmpty()) {
                header.visibility = View.VISIBLE
                llMeta.visibility = View.VISIBLE
                val gap = (10 * resources.displayMetrics.density).toInt()
                metaList.forEach { m ->
                    val col = LinearLayout(this@EventDetailActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = gap }
                    }
                    val k = TextView(this@EventDetailActivity).apply {
                        text = m.key
                        setTextColor(ContextCompat.getColor(this@EventDetailActivity, R.color.text_hint))
                        textSize = 12f
                    }
                    val v = TextView(this@EventDetailActivity).apply {
                        text = m.value
                        setTextColor(ContextCompat.getColor(this@EventDetailActivity, R.color.text_primary))
                        textSize = 15f
                    }
                    col.addView(k)
                    col.addView(v)
                    llMeta.addView(col)
                }
            } else {
                header.visibility = View.GONE
                llMeta.visibility = View.GONE
            }
        }
    }
}
