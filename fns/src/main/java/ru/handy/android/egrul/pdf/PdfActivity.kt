package ru.handy.android.egrul.pdf

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.viewpager2.widget.ViewPager2
import com.itextpdf.text.pdf.*
import ru.handy.android.egrul.R
import ru.handy.android.egrul.utils.LOG
import ru.handy.android.egrul.utils.UriPathHelper
import java.io.*
import java.util.*


class PdfActivity : AppCompatActivity() {
    private lateinit var uriFile: Uri
    private lateinit var llPdf: LinearLayout
    private lateinit var pageViewPager: ViewPager2
    private lateinit var baseProgressBar: ProgressBar
    private var parcelFileDescriptor: ParcelFileDescriptor? = null
    private var pdfAdapter: PdfAdapter? = null
    private var marginPhotoView: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pdf)
        supportActionBar?.setDisplayHomeAsUpEnabled(true) // устанавливаем actionbar

        llPdf = findViewById(R.id.llPdf)
        pageViewPager = findViewById(R.id.pageViewPager)
        baseProgressBar = findViewById(R.id.baseProgressBar)
        pageViewPager.offscreenPageLimit = 3 // максимальное кол-во страниц отображаемых единомоментно на экране
        // настройка правильного отображения каждой страницы, причем marginPhotoView предварительно уже долждна быть вычислена
        pageViewPager.setPageTransformer { page, position ->
            val viewPager = page.parent.parent as ViewPager2
            val offset = position * -(2 * marginPhotoView)
            if (viewPager.orientation == ViewPager2.ORIENTATION_VERTICAL) {
                page.translationY = offset
            } else {
                if (ViewCompat.getLayoutDirection(viewPager) == ViewCompat.LAYOUT_DIRECTION_RTL) {
                    page.translationX = -offset
                } else {
                    page.translationX = offset
                }
            }
        }

        // получаем Uri файла
        uriFile = if (Build.VERSION.SDK_INT >= 33) {
            (intent.getParcelableExtra("uriFile", Uri::class.java))!!
        } else {
            @Suppress("DEPRECATION")
            (intent.getParcelableExtra("uriFile"))!!
        }
        val uriPathHelper = UriPathHelper(this, uriFile)
        uriPathHelper.path?.let { Log.d(LOG, it) }

        // сначала нужно узнать размеры нашего Activity, чтобы привильно распололжить страницы и только после этого производить дальнейщие действия
        llPdf.post {
            Log.d(LOG, "llPdf.height = ${llPdf.height}, llPdf.width = ${llPdf.width}")
            //если отоношение высоты экрана к ширине больше, чем это cоотношение стандартной страницы формата A4 (297х210)
            if (llPdf.height.toDouble() / llPdf.width.toDouble() > 297.0 / 210.0) {
                marginPhotoView = ((llPdf.height - llPdf.width * 297.0 / 210.0) / 2).toInt()
            }
            openPdf()
        }
    }

    // Открытие pdf-файла разными способами в зависмости от того, читаемые у него шрифты или нет
    private fun openPdf() {
        try {
            // если шрифты в pdf-файле не читаемые, то встраиваем в pdf-файл файлы со шрифтам и читаем уже новый файл
            if (!isReadableFonts(contentResolver.openInputStream(uriFile))) {
                // https://itextpdf.com/sites/default/files/attachments/PR%20-%20iText%20in%20Action%20-%20Second%20edition%20e-book_0.pdf (стр.563)
                val reader = PdfReader(contentResolver.openInputStream(uriFile))
                var pdfObject: PdfObject?
                var font: PdfDictionary
                val tempFile = File(filesDir, "temp.pdf")
                val stamper = PdfStamper(reader, FileOutputStream(tempFile))
                for (i in 0 until reader.xrefSize) {
                    pdfObject = reader.getPdfObject(i)
                    if (pdfObject == null || !pdfObject.isDictionary) continue
                    font = pdfObject as PdfDictionary
                    if (PdfName.FONTDESCRIPTOR == font.get(PdfName.TYPE)) {
                        val stream = when (font.get(PdfName.FONTNAME)) {
                            PdfName("TimesNewRomanPSMT") -> pdfStreamFromFile("fonts/tnr.ttf")
                            PdfName("TimesNewRomanPS-BoldMT") -> pdfStreamFromFile("fonts/tnrb.ttf")
                            PdfName("TimesNewRomanPS-ItalicMT") -> pdfStreamFromFile("fonts/tnri.ttf")
                            PdfName("TimesNewRomanPS-BoldItalicMT") -> pdfStreamFromFile("fonts/tnrbi.ttf")
                            else -> null
                        }
                        if (stream != null) {
                            val objref = stamper.writer.addToBody(stream)
                            font.put(PdfName.FONTFILE2, objref.indirectReference)
                        }
                    }
                }
                stamper.setFormFlattening(true) // позволяет отображать ЭП (без этого она исчезала)
                stamper.close()
                reader.close()
                initPdfAdapter(ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY))
                Log.d(LOG, "не читаемый pdf-файл (шрифты не встроены)")
            } else {
                // если же шрифты читаемые, то просто открываем pdf-файл
                initPdfAdapter(contentResolver.openFileDescriptor(uriFile, "r"))
                Log.d(LOG, "читаемый pdf-файл (шрифты встроены)")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // инициация pdfAdapter
    private fun initPdfAdapter(pfd: ParcelFileDescriptor?) {
        if (pfd != null) {
            parcelFileDescriptor = pfd
            pageViewPager.visibility = View.VISIBLE
            baseProgressBar.visibility = View.GONE
            pdfAdapter = PdfAdapter(parcelFileDescriptor!!, this)
            pdfAdapter?.marginPhotoView = marginPhotoView
            pageViewPager.adapter = pdfAdapter
        }
    }

    /**
     * читаемые ли шрифты в данном файле источнике
     * https://itextpdf.com/sites/default/files/attachments/PR%20-%20iText%20in%20Action%20-%20Second%20edition%20e-book_0.pdf (стр. 561)
     */
    private fun isReadableFonts(src: InputStream?): Boolean {
        if (src == null) return true
        val reader = PdfReader(src)
        for (k in 1..reader.numberOfPages) {
            if (isReadableFontsOfPdfDictionary(reader.getPageN(k).getAsDict(PdfName.RESOURCES)))
                return true
        }
        return false
    }

    // читаемые ли шрифты к данного PdfDictionary
    private fun isReadableFontsOfPdfDictionary(resource: PdfDictionary?): Boolean {
        if (resource == null) return false
        val xobjects = resource.getAsDict(PdfName.XOBJECT)
        if (xobjects != null) {
            for (key in xobjects.keys) {
                if (isReadableFontsOfPdfDictionary(xobjects.getAsDict(key)))
                    return true
            }
        }
        val fonts = resource.getAsDict(PdfName.FONT) ?: return false
        var font: PdfDictionary
        for (key in fonts.keys) {
            font = fonts.getAsDict(key)
            val name = font.getAsName(PdfName.BASEFONT).toString()
            if (name.length > 8 && name[7] == '+') {
                return true
            } else {
                val desc = font.getAsDict(PdfName.FONTDESCRIPTOR)
                if (desc != null && (desc[PdfName.FONTFILE] != null || desc[PdfName.FONTFILE2] != null || desc[PdfName.FONTFILE3] != null))
                    return true
            }
        }
        return false
    }

    // создаем pdfStream из файла со шрифтами
    private fun pdfStreamFromFile(fileName: String): PdfStream {
        val inputStream: InputStream = assets.open(fileName)
        val buffer = ByteArray(inputStream.available())
        inputStream.read(buffer)
        inputStream.close()
        val pdfStream = PdfStream(buffer)
        pdfStream.flateCompress()
        pdfStream.put(PdfName.LENGTH1, PdfNumber(buffer.size))
        return pdfStream
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu, menu)
        menu.setGroupVisible(R.id.group_share, true)
        return true
    }

    // обрабатываем кнопки пунктов меню
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Операции для выбранного пункта меню
        return when (item.itemId) {
            android.R.id.home -> { // обработка кнопки "назад"
                finish()
                true
            }
            R.id.share -> { // обработка кнопки "поделиться"
                val sendIntent = Intent()
                sendIntent.action = Intent.ACTION_SEND
                sendIntent.putExtra(Intent.EXTRA_STREAM, uriFile)
                sendIntent.type = "application/*"
                //это делаем для того, чтобы не возникала ошибка java.lang.SecurityException
                val chooser = Intent.createChooser(sendIntent, s(R.string.share))
                @SuppressLint("QueryPermissionsNeeded") val resInfoList: List<ResolveInfo> =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        packageManager.queryIntentActivities(
                            chooser,
                            PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        packageManager.queryIntentActivities(chooser, PackageManager.MATCH_DEFAULT_ONLY)
                    }
                for (resolveInfo in resInfoList) {
                    this.grantUriPermission(
                        resolveInfo.activityInfo.packageName,
                        uriFile, Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                startActivity(chooser)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * метод для получаения всех шрифтов в pdf-файле. Нужен только для получения информации. Пример:
     * val fonts = listFonts(getContentResolver().openInputStream(uriFile))
     * for (font in fonts) Log.d(LOG, font)
     */
    @Throws(IOException::class)
    fun listFonts(src: InputStream?): MutableSet<String> {
        val set: MutableSet<String> = TreeSet()
        val reader = PdfReader(src)
        var resources: PdfDictionary?
        for (k in 1..reader.numberOfPages) {
            resources = reader.getPageN(k).getAsDict(PdfName.RESOURCES)
            processResource(set, resources)
        }
        return set
    }

    // вспомогательный метод для listFonts
    private fun processResource(set: MutableSet<String>, resource: PdfDictionary?) {
        if (resource == null) return
        val xobjects = resource.getAsDict(PdfName.XOBJECT)
        if (xobjects != null) {
            for (key in xobjects.keys) {
                processResource(set, xobjects.getAsDict(key))
            }
        }
        val fonts = resource.getAsDict(PdfName.FONT) ?: return
        var font: PdfDictionary
        for (key in fonts.keys) {
            font = fonts.getAsDict(key)
            var name = font.getAsName(PdfName.BASEFONT).toString()
            if (name.length > 8 && name[7] == '+') {
                name = String.format("%s subset (%s)", name.substring(8), name.substring(1, 7))
            } else {
                name = name.substring(1)
                val desc = font.getAsDict(PdfName.FONTDESCRIPTOR)
                if (desc == null) name += " nofontdescriptor"
                else if (desc[PdfName.FONTFILE] != null)
                    name += " (Type 1) embedded"
                else if (desc[PdfName.FONTFILE2] != null)
                    name += " (TrueType) embedded"
                else if (desc[PdfName.FONTFILE3] != null)
                    name += " (" + font.getAsName(PdfName.SUBTYPE).toString().substring(1) + ") embedded"
            }
            set.add(name)
        }
    }

    fun s(res: Int): String {
        return resources.getString(res)
    }

    override fun onDestroy() {
        Log.d(LOG, "onDestroy PdfActivity")
        super.onDestroy()
        parcelFileDescriptor?.close()
        pdfAdapter?.clear()
    }
}
