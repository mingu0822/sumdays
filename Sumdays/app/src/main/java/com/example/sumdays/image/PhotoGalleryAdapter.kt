package com.example.sumdays.image

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
            // 🔹 정사각형 강제: width 측정 후 height를 width에 맞춤
            itemView.post {
                val params = itemView.layoutParams
                val w = itemView.width
                if (params != null && w > 0) {
                    params.height = w
                    itemView.layoutParams = params
                }
            }

            // 🔹 이미지 로딩 (Base64 → 아니면 일반 URI/파일경로)
            try {
                val imageBytes = Base64.decode(url, Base64.DEFAULT)
                Glide.with(itemView.context)
                    .load(imageBytes)
                    .centerCrop()
                    .error(android.R.drawable.ic_menu_report_image)
                    .into(imageView)
            } catch (e: IllegalArgumentException) {
                Glide.with(itemView.context)
                    .load(url)
                    .centerCrop()
                    .error(android.R.drawable.ic_menu_report_image)
                    .fallback(android.R.drawable.ic_menu_report_image)
                    .into(imageView)
            }

            // 사진 클릭 → 확대 보기 등
            imageView.setOnClickListener {
                onClick(url)
            }

            // 삭제 버튼 클릭 → 해당 position 삭제 콜백
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
            // 🔹 + 카드도 정사각형으로
            itemView.post {
                val params = itemView.layoutParams
                val w = itemView.width
                if (params != null && w > 0) {
                    params.height = w
                    itemView.layoutParams = params
                }
            }

            itemView.setOnClickListener {
                onClick()
            }
        }
    }
}