
// DayAdapter.kt
package com.example.sumdays.calendar

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.sumdays.CalendarActivity
import com.example.sumdays.DailyReadActivity
import com.example.sumdays.R
import org.threeten.bp.LocalDate
import org.threeten.bp.DayOfWeek
import org.threeten.bp.YearMonth
import com.example.sumdays.DailyWriteActivity
import android.graphics.drawable.GradientDrawable

class DayAdapter(
    private val days: List<DateCell>,
    private val activity: CalendarActivity,
    private val today: LocalDate = LocalDate.now(),
    private val maxYearMonth: YearMonth = YearMonth.now()
) : RecyclerView.Adapter<DayAdapter.DayViewHolder>() {

    override fun getItemCount(): Int = days.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_day_cell, parent, false)
        return DayViewHolder(view)
    }

    override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
        val cell = days[position]
        holder.bind(cell, activity, today, maxYearMonth)
    }

    class DayViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvCircle: TextView = itemView.findViewById(R.id.tv_circle)
        private val tvDayNumber: TextView = itemView.findViewById(R.id.tv_day_number)
        private val tvEmoji: TextView = itemView.findViewById(R.id.tv_emoji)

        fun bind(
            cell: DateCell,
            activity: CalendarActivity,
            today: LocalDate,
            maxYearMonth: YearMonth
        ) {
            if (cell.day <= 0 || cell.day > 31) {
                tvCircle.background = null
                tvDayNumber.text = ""
                tvEmoji.visibility = View.GONE
                itemView.isClickable = false
                return
            }

            tvDayNumber.text = cell.day.toString()
            itemView.isClickable = true

            val date = LocalDate.parse(cell.dateString)
            val isToday = date.isEqual(today)
            val isFutureDay = date.isAfter(today)

            val dayOfWeek = date.dayOfWeek
            val textColor = when {
                dayOfWeek == DayOfWeek.SUNDAY -> Color.RED
                dayOfWeek == DayOfWeek.SATURDAY -> Color.parseColor("#0095FF")
                else -> ContextCompat.getColor(itemView.context, R.color.textColor)
            }
            tvDayNumber.setTextColor(textColor)

            val hasDiary = activity.currentStatusMap[cell.dateString]?.first ?: false
            val emoji = activity.currentStatusMap[cell.dateString]?.second

            when {
                isToday -> {
                    tvCircle.background = ContextCompat.getDrawable(itemView.context, R.drawable.calendar_shape_fox_today)
                    tvDayNumber.setTypeface(null, Typeface.BOLD)
                }
                hasDiary -> {
                    tvCircle.background = ContextCompat.getDrawable(itemView.context, R.drawable.calendar_shape_fox_date_gray_completed)
                    tvDayNumber.setTypeface(null, Typeface.NORMAL)
                }
                else -> {
                    tvCircle.background = ContextCompat.getDrawable(itemView.context, R.drawable.calendar_shape_fox_date_gray)
                    tvDayNumber.setTypeface(null, Typeface.NORMAL)
                }
            }

            if (!emoji.isNullOrEmpty()) {
                tvEmoji.text = emoji
                tvEmoji.visibility = View.VISIBLE
            } else {
                tvEmoji.visibility = View.GONE
            }

            if (!cell.isCurrentMonth) {
                tvEmoji.visibility = View.GONE
                tvDayNumber.alpha = 0.4f
                tvCircle.alpha = 0.4f
                tvDayNumber.setTextColor(Color.GRAY)
            } else {
                tvDayNumber.alpha = 1.0f
                tvCircle.alpha = 1.0f
            }
            if (isToday) {
                val density = itemView.context.resources.displayMetrics.density
                val width = (20 * density).toInt()
                val height = (17 * density).toInt()

                val params = tvDayNumber.layoutParams
                params.width = width
                params.height = height
                tvDayNumber.layoutParams = params

                val drawable = GradientDrawable()
                drawable.shape = GradientDrawable.RECTANGLE
                drawable.setColor(Color.WHITE)
                drawable.cornerRadius = 5 * density

                tvDayNumber.background = drawable

                // 글자색 검은색, 굵게
                tvDayNumber.setTextColor(Color.BLACK)
                tvDayNumber.setTypeface(null, Typeface.BOLD)
            }
            if (isFutureDay) {
                itemView.isClickable = false
                itemView.isFocusable = false
                tvEmoji.visibility = View.GONE
                tvDayNumber.setTypeface(null, Typeface.NORMAL)
                itemView.setOnClickListener {
                    Toast.makeText(activity, "미래의 일기로는 이동할 수 없습니다.", Toast.LENGTH_SHORT).show()
                }
            } else {
                // 과거/오늘만 클릭 허용
                if (cell.dateString.isNotEmpty()) {
                    itemView.setOnClickListener {
                        // 캘린더에서 가져온 '일기 존재 여부' 플래그 확인
                        if (hasDiary) {
                            // 일기가 있으면 DailyReadActivity로
                            val intent = Intent(activity, DailyReadActivity::class.java)
                            intent.putExtra("date", cell.dateString)
                            activity.startActivity(intent)
                        } else {
                            // 일기가 없으면 DailyWriteActivity로
                            Toast.makeText(activity, "이 날의 일기가 없습니다.", Toast.LENGTH_SHORT).show()
                            val intent = Intent(activity, DailyWriteActivity::class.java)
                            intent.putExtra("date", cell.dateString)
                            activity.startActivity(intent)
                        }
                    }
                }
            }
        }
    }
}