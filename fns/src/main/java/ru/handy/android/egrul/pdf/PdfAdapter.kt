package ru.handy.android.egrul.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.github.chrisbanes.photoview.PhotoView
import ru.handy.android.egrul.R

/**
 * класс-адаптер для ViewPager2
 */
class PdfAdapter(pdfParcelDescriptor: ParcelFileDescriptor, context: Context) :
    RecyclerView.Adapter<PdfAdapter.PdfPageViewHolder>() {

    private var bitmapPool: PdfBitmapPool? = null
    private val pdfRenderer: PdfRenderer = PdfRenderer(pdfParcelDescriptor)
    var marginPhotoView: Int = 0

    init {
        bitmapPool = PdfBitmapPool(pdfRenderer, Bitmap.Config.ARGB_8888, context.resources.displayMetrics.densityDpi)
    }

    override fun getItemCount(): Int = pdfRenderer.pageCount

    override fun onBindViewHolder(holder: PdfPageViewHolder, position: Int) {
        holder.bind(position)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PdfPageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.pdf_page_item, parent, false)
        return PdfPageViewHolder(view)
    }

    fun clear() {
        pdfRenderer.close()
        bitmapPool?.bitmaps?.clear()
    }


    inner class PdfPageViewHolder(val view: View) : RecyclerView.ViewHolder(view) {

        fun bind(pagePosition: Int) {
            if (bitmapPool != null) {
                val photoView = view.findViewById<PhotoView>(R.id.photoView)
                photoView.setImageBitmap(bitmapPool!!.getPage(pagePosition))
                // делаем отступ вверзу и внизу страницы, чтобы были видны предыдущая и послудующая страницы
                val param = photoView.layoutParams as ViewGroup.MarginLayoutParams
                param.setMargins(0, marginPhotoView, 0, marginPhotoView)
                photoView.layoutParams = param
                // добавляем новое изображение в bitmapPool
                bitmapPool!!.loadMore(pagePosition)
            }
        }
    }
}