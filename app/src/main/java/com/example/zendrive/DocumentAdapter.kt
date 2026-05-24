package com.example.zendrive

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class DocumentAdapter : ListAdapter<VehicleDocument, DocumentAdapter.VH>(DIFF) {

    var onDocumentLongClick: ((VehicleDocument) -> Unit)? = null

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<VehicleDocument>() {
            override fun areItemsTheSame(a: VehicleDocument, b: VehicleDocument) = a.id == b.id
            override fun areContentsTheSame(a: VehicleDocument, b: VehicleDocument) = a == b
        }

        private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        private const val EXPIRY_SOON_DAYS = 30L
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val icon: TextView = view.findViewById(R.id.tvDocIcon)
        val type: TextView = view.findViewById(R.id.tvDocType)
        val title: TextView = view.findViewById(R.id.tvDocTitle)
        val fileName: TextView = view.findViewById(R.id.tvDocFileName)
        val expiry: TextView = view.findViewById(R.id.tvDocExpiry)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_document, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val doc = getItem(position)
        val ctx = holder.itemView.context

        holder.icon.text = getDocTypeEmoji(doc.documentType)
        holder.type.text = doc.documentType.uppercase()
        holder.title.text = doc.title
        holder.fileName.text = doc.fileName

        if (doc.expiresAt != null) {
            holder.expiry.visibility = View.VISIBLE
            val now = System.currentTimeMillis()
            val daysLeft = TimeUnit.MILLISECONDS.toDays(doc.expiresAt - now)

            if (daysLeft < 0) {
                holder.expiry.text = ctx.getString(R.string.document_expired)
                holder.expiry.setTextColor(ctx.getColor(R.color.error))
            } else if (daysLeft <= EXPIRY_SOON_DAYS) {
                holder.expiry.text = ctx.getString(R.string.document_expiring_soon)
                holder.expiry.setTextColor(ctx.getColor(R.color.warning))
            } else {
                holder.expiry.text = dateFormat.format(Date(doc.expiresAt))
                holder.expiry.setTextColor(ctx.getColor(R.color.text_hint))
            }
        } else {
            holder.expiry.visibility = View.GONE
        }

        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
            val item = getItem(pos)
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse(item.storageUri), item.mimeType ?: "*/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                ctx.startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(ctx, R.string.cannot_open_file, Toast.LENGTH_SHORT).show()
            }
        }

        holder.itemView.setOnLongClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                onDocumentLongClick?.invoke(getItem(pos))
            }
            true
        }
    }

    private fun getDocTypeEmoji(type: String): String = when (type.lowercase()) {
        "insurance" -> "\uD83D\uDEE1\uFE0F"
        "registration" -> "\uD83D\uDCCB"
        "puc" -> "\uD83C\uDF3F"
        "invoice" -> "\uD83E\uDDFE"
        "warranty" -> "\u2705"
        else -> "\uD83D\uDCC4"
    }
}
