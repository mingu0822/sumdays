package com.example.sumdays.daily.memo

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

// Room 데이터베이스 테이블을 정의하는 Entity 클래스
@Parcelize
@Entity(tableName = "memo_table")
data class Memo(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "content") val content: String,
    @ColumnInfo(name = "timestamp") val timestamp: String,
    @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "memo_order") val order: Int,
    @ColumnInfo(name = "type", defaultValue = "text") val type: String = "text",
    @ColumnInfo(name = "isEdited", defaultValue = "0") val isEdited: Boolean = false,
    @ColumnInfo(name = "isDeleted", defaultValue = "0") val isDeleted: Boolean = false
) : Parcelable