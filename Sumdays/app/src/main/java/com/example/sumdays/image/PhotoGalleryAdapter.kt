package com.example.sumdays.image

import android.net.Uri
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.sumdays.R

/**
 * 갤러리 썸네일 어댑터
 *
 * - GalleryItem.Photo(url) : 실제 사진
 *      - url 이 "content://", "file://", "http", "/" 로 시작하면 → Uri/경로로 처리
 *      - 그 외는 → Base64 로 처리 시도
 * - GalleryItem.Add        : 마지막 + 카드
 *
 * @param onPhotoClick  : 사진 눌렀을 때 (큰 이미지 보기용)
 * @param onDeleteClick : 사진 삭제 버튼 눌렀을 때 (position 기준)
 * @param onAddClick    : + 카드 눌렀을 때 (갤러리 열기)
 */
class PhotoGalleryAdapter(
    private val onPhotoClick: (String) -> Unit,
    private val onDeleteClick: (Int) -> Unit,
    private val onAddClick: () -> Unit
) : ListAdapter<GalleryItem, RecyclerView.ViewHolder>(GalleryDiffCallback()) {

    companion object {
        private const val VIEW_TYPE_PHOTO = 1
        private const val VIEW_TYPE_ADD = 2
    }

    override fun getItemViewType(position: Int): Int =
        when (getItem(position)) {
            is GalleryItem.Photo -> VIEW_TYPE_PHOTO
            is GalleryItem.Add -> VIEW_TYPE_ADD
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_PHOTO -> {
                val view = inflater.inflate(R.layout.item_photo_gallery, parent, false)
                PhotoViewHolder(view)
            }
            VIEW_TYPE_ADD -> {
                val view = inflater.inflate(R.layout.item_photo_gallery_add, parent, false)
                AddViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is GalleryItem.Photo -> (holder as PhotoViewHolder).bind(
                url = item.url,
                position = position,
                onClick = onPhotoClick,
                onDeleteClick = onDeleteClick
            )
            is GalleryItem.Add -> (holder as AddViewHolder).bind(onAddClick)
        }
    }

    /**
     * 실제 사진 썸네일 셀
     */
    class PhotoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val imageView: ImageView = itemView.findViewById(R.id.gallery_image)
        private val deleteButton: ImageView = itemView.findViewById(R.id.btn_delete_image)

        fun bind(
            url: String,
            position: Int,
            onClick: (String) -> Unit,
            onDeleteClick: (Int) -> Unit
        ) {
            // 🔹 썸네일을 정사각형으로 맞추고 싶을 때 (선택 사항)
            itemView.post {
                val params = itemView.layoutParams
                val w = itemView.width
                if (params != null && w > 0) {
                    params.height = w
                    itemView.layoutParams = params
                }
            }

            // 🔹 1) Uri / 파일 / http 인지 먼저 체크
            val isUriOrPath = url.startsWith("content://") ||
                    url.startsWith("file://") ||
                    url.startsWith("http://") ||
                    url.startsWith("https://") ||
                    url.startsWith("/")

            if (isUriOrPath) {
                // 👉 StyleExtractionActivity 처럼 Uri.toString() 으로 넘긴 경우 여기로 옴
                Glide.with(itemView.context)
                    .load(Uri.parse(url))
                    .centerCrop()
                    .error(android.R.drawable.ic_menu_report_image)
                    .into(imageView)
            } else {
                // 👉 DailyReadActivity 처럼 Base64 문자열을 넘긴 경우
                try {
                    val imageBytes = Base64.decode(url, Base64.DEFAULT)
                    Glide.with(itemView.context)
                        .load(imageBytes)
                        .centerCrop()
                        .error(android.R.drawable.ic_menu_report_image)
                        .into(imageView)
                } catch (e: IllegalArgumentException) {
                    // 혹시 Base64 디코딩이 안 되면, 마지막으로 그냥 문자열 로드 시도
                    Glide.with(itemView.context)
                        .load(url)
                        .centerCrop()
                        .error(android.R.drawable.ic_menu_report_image)
                        .fallback(android.R.drawable.ic_menu_report_image)
                        .into(imageView)
                }
            }

            // 사진 클릭 → 크게 보기
            imageView.setOnClickListener {
                onClick(url)
            }

            // 삭제 버튼 클릭 → position 기반 콜백
            deleteButton.setOnClickListener {
                onDeleteClick(position)
            }
        }
    }

    /**
     * 마지막에 들어가는 + 카드 셀
     */
    class AddViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(onClick: () -> Unit) {
            itemView.setOnClickListener {
                onClick()
            }
        }
    }
}