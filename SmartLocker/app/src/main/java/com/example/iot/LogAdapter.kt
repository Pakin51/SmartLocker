package com.example.iot

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import android.graphics.Color

class LogAdapter(private val logList: List<LogItem>) : RecyclerView.Adapter<LogAdapter.LogViewHolder>() {

    // เชื่อมกับไฟล์ XML ที่สร้างไว้
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_log, parent, false)
        return LogViewHolder(view)
    }

    // เอาข้อมูลแต่ละrow ยัดใส่ Text ในการ์ด
    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        val currentItem = logList[position]

        holder.tvTime.text = "🕒 " + currentItem.time

        when (currentItem.event) {
            "พัสดุมาส่ง" -> {
                holder.tvEvent.text = "\uD83D\uDCE6 " + currentItem.event
                holder.tvEvent.setTextColor(Color.parseColor("#333333"))
            }
            "พัสดุถูกนำออกจากตู้" -> {
                holder.tvEvent.text = "\uD83D\uDCE6 " + currentItem.event
                holder.tvEvent.setTextColor(Color.parseColor("#333333"))
            }
            "ปลดล็อกตู้" -> {
                holder.tvEvent.text = "✔\uFE0F " + currentItem.event
                holder.tvEvent.setTextColor(Color.parseColor("#008000"))
            }
            "เตือนภัย: ประตูถูกงัด!" -> {
                holder.tvEvent.text = "⚠\uFE0F " + currentItem.event
                holder.tvEvent.setTextColor(Color.parseColor("#ff0000"))
            }
        }
    }

    // บอกจำนวนแถวที่มี
    override fun getItemCount(): Int {
        return logList.size
    }

    // ตัวแทนของ View ในแต่ละการ์ด
    class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTime: TextView = itemView.findViewById(R.id.tvItemTime)
        val tvEvent: TextView = itemView.findViewById(R.id.tvItemEvent)
    }
}