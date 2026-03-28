package com.example.sumdays.image

import android.net.Uri
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.sumdays.R
import com.example.sumdays.theme.ThemePrefs
import com.example.sumdays.theme.ThemeRepository

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
            is GalleryItem.Photo -> {
                (holder as PhotoViewHolder).bind(
                    url = item.url,
                    position = position,
                    onClick = onPhotoClick,
                    onDeleteClick = onDeleteClick
                )
            }
            is GalleryItem.Add -> {
                (holder as AddViewHolder).bind(onAddClick)
            }
        }
    }

    class PhotoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: CardView = itemView as CardView
        private val imageView: ImageView = itemView.findViewById(R.id.gallery_image)
        private val deleteButton: ImageView = itemView.findViewById(R.id.btn_delete_image)

        fun bind(
            url: String,
            position: Int,
            onClick: (String) -> Unit,
            onDeleteClick: (Int) -> Unit
        ) {
            applyTheme()

            val isUriOrPath = url.startsWith("content://") ||
                    url.startsWith("file://") ||
                    url.startsWith("http://") ||
                    url.startsWith("https://") ||
                    url.startsWith("/")

            if (isUriOrPath) {
                Glide.with(itemView.context)
                    .load(Uri.parse(url))
                    .centerCrop()
                    .error(android.R.drawable.ic_menu_report_image)
                    .into(imageView)
            } else {
                try {
                    val imageBytes = Base64.decode(url, Base64.DEFAULT)
                    Glide.with(itemView.context)
                        .load(imageBytes)
                        .centerCrop()
                        .error(android.R.drawable.ic_menu_report_image)
                        .into(imageView)
                } catch (_: IllegalArgumentException) {
                    Glide.with(itemView.context)
                        .load(url)
                        .centerCrop()
                        .error(android.R.drawable.ic_menu_report_image)
                        .into(imageView)
                }
            }

            imageView.setOnClickListener { onClick(url) }
            deleteButton.setOnClickListener { onDeleteClick(position) }
        }

        private fun applyTheme() {
            val context = itemView.context
            val themeKey = ThemePrefs.getTheme(context)
            val currentTheme = ThemeRepository.ownedThemes[themeKey] ?: return

            val blockColor = ContextCompat.getColor(context, currentTheme.blockColor)
            val primaryColor = ContextCompat.getColor(context, currentTheme.primaryColor)

            cardView.setCardBackgroundColor(blockColor)
            deleteButton.setColorFilter(primaryColor)
        }
    }

    class AddViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: CardView = itemView as CardView
        private val addIcon: ImageView = itemView.findViewById(R.id.add_icon)

        fun bind(onClick: () -> Unit) {
            applyTheme()
            itemView.setOnClickListener { onClick() }
        }

        private fun applyTheme() {
            val context = itemView.context
            val themeKey = ThemePrefs.getTheme(context)
            val currentTheme = ThemeRepository.ownedThemes[themeKey] ?: return

            val blockColor = ContextCompat.getColor(context, currentTheme.blockColor)
            val primaryColor = ContextCompat.getColor(context, currentTheme.primaryColor)

            cardView.setCardBackgroundColor(blockColor)
            addIcon.setColorFilter(primaryColor)
        }
    }
}