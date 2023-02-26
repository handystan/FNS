package ru.handy.android.egrul.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.util.SparseArray
import androidx.core.util.getOrElse
import kotlin.math.min

/**
 * класс с пулом растровых изображений, чтобы не сохранять их каждый раз при перелистывании, а уже иметь сохраненные.
 * При этом пир перелистывании автоматически загружаются соседнии страницы
 */
class PdfBitmapPool(val pdfRenderer: PdfRenderer, val config: Bitmap.Config, val densityDpi: Int) {

    val bitmaps: SparseArray<Bitmap> = SparseArray() //масси, где хранятся изображения
    var currentIndex = 0 // текущая страницы

    init {
        val initialLoadCount = min(pdfRenderer.pageCount, POOL_SIZE)
        for (i in 0 until initialLoadCount) {
            bitmaps.append(i, loadPage(i))
        }
    }

    companion object {
        const val POOL_SIZE = 5 // кол-во загружаемых страниц
        const val PDF_RESOLUTION_DPI = 72
    }

    fun getPage(index: Int): Bitmap {
        return bitmaps.getOrElse(index) {
            loadPage(index)
        }
    }

    fun loadMore(newLimitIndex: Int) {

        val newRange = getCurrentRange(newLimitIndex)
        removeOutOfRangeElements(newRange)

        for (i in newRange) {
            if (i != newLimitIndex && i in 0 until bitmaps.size() && bitmaps.indexOfKey(i) < 0)
                bitmaps.append(i, loadPage(i))
        }

        currentIndex = newLimitIndex
    }

    fun getCurrentRange(currentIndex: Int): IntProgression {
        val sectionSize = (POOL_SIZE - 1) / 2
        return (currentIndex - sectionSize)..(currentIndex + sectionSize)
    }

    fun removeOutOfRangeElements(newRange: IntProgression) {
        //Removing elements that are out of range, the bitmap is cleared and pushed back to the unused bitmaps stack
        getCurrentRange(currentIndex).filter { !newRange.contains(it) }.forEach {
            val removingBitmap = bitmaps[it]
            removingBitmap?.let { bitmap ->
                bitmaps.remove(it)
            }
        }
    }

    fun loadPage(pageIndex: Int): Bitmap {
        val page = pdfRenderer.openPage(pageIndex)
        val bitmap = newWhiteBitmap(page.width.toPixelDimension(densityDpi), page.height.toPixelDimension(densityDpi))
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        return bitmap
    }

    fun newWhiteBitmap(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, config)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        return bitmap
    }

    fun Int.toPixelDimension(densityDpi: Int, scaleFactor: Float = 0.45f): Int {
        return ((densityDpi * this / PDF_RESOLUTION_DPI) * scaleFactor).toInt()
    }
}