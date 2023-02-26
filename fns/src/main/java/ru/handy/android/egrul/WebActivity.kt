package ru.handy.android.egrul

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.*
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import ru.handy.android.egrul.pdf.PdfActivity
import ru.handy.android.egrul.utils.*
import java.io.File
import java.util.*

class WebActivity : AppCompatActivity(), DownloadListener {
    lateinit var rlProgressBar: RelativeLayout
    lateinit var tvConnecting: TextView
    private lateinit var webView: WebView
    lateinit var js: MyJavaScriptInterface
    private lateinit var wvClient: WVClient
    private val rcpWriteExternalStorage = 0 // идентификатор запроса разрешения на загрузку файлов
    private var argsForDowload =
        arrayOf("", "", "", "", 0) as Array<*> // массив для сохранения аргументов метода downloadFile
    private lateinit var fileLauncher: ActivityResultLauncher<Intent>
    lateinit var sharedPref: SharedPreferences
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    val startUrl: String by lazy { s(R.string.start_url) } // url стартовой страницы
    private val pageHelp by lazy { s(R.string.page_help) } // адрес html-страницы с помощью
    private val patternForStartPage by lazy { s(R.string.pattern_for_start_page) } // часть html-страницы подтверждающая, что это начальная страница
    private val patternForRepairPage by lazy { s(R.string.pattern_for_repair_page) } // часть html-страницы подтверждающая, что это на сайте ведутся технические работы

    // шаблоаны текстов для определения, нужно ли какое-то окно закрывать при нажатии кнопки "назад"
    private val patternsForClose: ArrayList<String> by lazy {
        arrayListOf(
            s(R.string.pattern_for_close_1), //открыта ли расшир. форма поиска (для bfo) или форма выбора регионов (для egrul)
            s(R.string.pattern_for_close_2), //открыта ли форма выгрузки отчетности (только для bfo)
            s(R.string.pattern_for_close_3), //открыто ли меню (только для bfo)
            s(R.string.pattern_for_close_4)  //открыто ли сообщение о составителях отчетности (только для bfo)
        )
    }

    // скрипты для закрытия соответствующих окон при нажатии кнопки "назад"
    private val scriptsForClose: ArrayList<String> by lazy {
        arrayListOf(
            s(R.string.script_for_close_1), //для закрытия расшир. формы поиска (для bfo) или формы выбора регионов (для egrul)
            s(R.string.script_for_close_2), //для закрытия формы выгрузки отчетности (только для bfo)
            s(R.string.script_for_close_3), //для закрытия меню (только для bfo)
            s(R.string.script_for_close_4)  //для закрытия сообщения о составителях отчетности (только для bfo)
        )
    }

    @SuppressLint("ClickableViewAccessibility", "SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_web)
        sharedPref = getSharedPreferences(PREFS_NAME, MODE_PRIVATE) // хранилище с настройками
        rlProgressBar = findViewById(R.id.rlProgressBar)
        tvConnecting = findViewById(R.id.tvConnecting)
        webView = findViewById(R.id.webView)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        js = MyJavaScriptInterface(webView)
        webView.addJavascriptInterface(js, "HtmlViewer")
        wvClient = WVClient(this)
        webView.webViewClient = wvClient
        webView.setDownloadListener(this)
        //очистка всякой истории
        try {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            WebStorage.getInstance().deleteAllData()
            webView.clearHistory()
            webView.clearFormData()
            webView.clearCache(true)
            webView.clearSslPreferences()
        } catch (e: Exception) {
            Log.e(LOG, e.toString())
        }

        webView.settings.loadWithOverviewMode = true //масштаб по ширине страницы
        webView.settings.useWideViewPort = true //масштаб по ширине страницы
        webView.settings.setSupportZoom(true) // set can support zoom
        webView.settings.builtInZoomControls = true // Set the zoom tool appears
        webView.settings.displayZoomControls = false // set the zoom controls hidden
        webView.loadUrl(startUrl)
        // переменная, которая обрабатыавет получение файла из диалогового окна
        fileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uriFile: Uri? = result.data?.data
                openFile(uriFile)
            }
        }
        // обновление страницы через движение пальца вниз
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        swipeRefreshLayout.setColorSchemeColors(Color.RED, Color.GREEN, Color.BLUE, Color.CYAN)
        swipeRefreshLayout.viewTreeObserver.addOnDrawListener {
            wvClient.getHtml(webView)
            val len = js.html?.length
            //обновление страницы доступно только, если она не загрузилась (html-код меньше 1000 знаков) и верх страницы
            swipeRefreshLayout.isEnabled = (len != null && len < 1000 && webView.scrollY == 0)
        }
        swipeRefreshLayout.setOnRefreshListener {
            webView.loadUrl(startUrl)
            swipeRefreshLayout.isRefreshing = false
        }
    }

    // класс, позволяющий получить html-код страницы
    class MyJavaScriptInterface constructor(private val wv: WebView) {
        var html: String? = null

        @JavascriptInterface
        fun showHTML(_html: String?) {
            html = _html
        }
    }

    /**
     * Уведомляет приложение о том, что файл должен быть загружен
     * @param url The full url to the content that should be downloaded
     * @param userAgent the user agent to be used for the download.
     * @param contentDisposition Content-disposition http header, if
     * present.
     * @param mimetype The mimetype of the content reported by the server
     * @param contentLength The file size reported by the server
     */
    override fun onDownloadStart(
        url: String?, userAgent: String?, contentDisposition: String?, mimetype: String?, contentLength: Long
    ) {
        // сначала закрывает окно с загрузкой
        Log.d(LOG, "onDownloadStart runningCounter = ${wvClient.runningCounter}")
        wvClient.runningCounter = 0
        rlProgressBar.visibility = View.INVISIBLE
        wvClient.pageDownloadTimer.cancel()
        //а потом производим все дальнейшие действия
        try { //с 10 версии Android разреший на загрузку файлов вроде как не нужно
            downloadFile(url, userAgent, contentDisposition, mimetype, contentLength)
        } catch (e: SecurityException) {
            // а вот с 6 по 10 версии Android разрешения на сохранение файла уже потребуются
            val permissionStatus = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            if (permissionStatus == PackageManager.PERMISSION_GRANTED) {
                downloadFile(url, userAgent, contentDisposition, mimetype, contentLength)
            } else {
                argsForDowload = arrayOf(url, userAgent, contentDisposition, mimetype, contentLength)
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), rcpWriteExternalStorage
                )
            }
        }
    }

    /**
     * Загружает файл
     */
    private fun downloadFile(
        url: String?,
        userAgent: String?,
        contentDisposition: String?,
        mimetype: String?,
        contentLength: Long
    ) {
        val fileName: String = URLUtil.guessFileName(url, contentDisposition, mimetype)
        Log.d(LOG, "fileName = $fileName")
        val request = DownloadManager.Request(Uri.parse(url))
        request.setMimeType(mimetype)
        request.addRequestHeader("cookie", CookieManager.getInstance().getCookie(url))
        request.addRequestHeader("User-Agent", userAgent)
        request.setDescription("Downloading file...")
        request.setTitle(fileName)
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED) //Notify client once download is completed!
        val folder: String? = sharedPref.getString("folder", null)
        // если в настройках еще не было сохранено uri начальной папки или папка Documents
        if (folder == null || folder.equals(Environment.DIRECTORY_DOCUMENTS, true)) {
            try {
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOCUMENTS, fileName)
            } catch (e: Exception) {
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            }
        } else {
            request.setDestinationUri(
                Uri.fromFile(
                    File(
                        Environment.getExternalStoragePublicDirectory(folder),
                        fileName
                    )
                )
            )
        }
        val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        val idFile: Long = dm.enqueue(request)
        val message = "<b>${s(R.string.file)} \"$fileName\" ${s(R.string.downloaded)}!</b>"
        AlertDialog.Builder(this)
            .setMessage(HtmlCompat.fromHtml(message, HtmlCompat.FROM_HTML_MODE_LEGACY))
            .setPositiveButton(R.string.open_file) { dialog, which ->
                Thread {
                    //открываем загруженный файл в новом потоке, чтобы подождать, пока можно получить uriFile
                    var uriFile: Uri? = null
                    val date: Long = Calendar.getInstance().timeInMillis
                    while (uriFile == null) { // ждем, когда файл полностью загрузится
                        if (Calendar.getInstance().timeInMillis - date > 15000) { // ждем не более 15 сек.
                            runOnUiThread {
                                Toast.makeText(this, s(R.string.file_is_not_loaded), Toast.LENGTH_LONG).show()
                            }
                            break
                        }
                        uriFile = dm.getUriForDownloadedFile(idFile)
                    }
                    uriFile?.let { openFile(it) }
                }.start()
            }
            .setNegativeButton(R.string.show_in_folder) { dialog, which ->
                openDir() // открываем директорию с нашими файлами
            }
            .setNeutralButton(R.string.close, null)
            .create()
            .show()
    }

    /**
     * открытие файла (либо через функцию pdf, либо как другие файлы)
     */
    fun openFile(uriFile: Uri?) {
        var ext: String? = fileExt(uriFile.toString()) // расширение файла
        if (ext == null) {
            ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(uriFile?.let { contentResolver.getType(it) })
        }
        Log.d(LOG, "ext = $ext")
        if (ext == "pdf") { // открытие pdf-файлов
            val intent = Intent(this, PdfActivity::class.java)
            intent.putExtra("uriFile", uriFile)
            startActivity(intent)
        } else { // открытие всех остальных файлов
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(uriFile, MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext))
            intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            try {
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Log.e(LOG, e.toString())
                Toast.makeText(this, s(R.string.cant_open_file), Toast.LENGTH_LONG).show()
            }
        }

    }

    /**
     * Возвращает результат с диалогового окна о предоставлении разрешений
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String?>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            rcpWriteExternalStorage -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission granted
                    downloadFile(
                        argsForDowload[0] as String?, argsForDowload[1] as String?,
                        argsForDowload[2] as String?, argsForDowload[3] as String?, argsForDowload[4] as Long
                    )
                }
                return
            }
        }
    }

    /**
     * обработка нажатия кнопки "назад"
     */
    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        // если идет процесс загрузки страницы, то его прерываем
        Log.d(LOG, "onBackPressed runningCounter = ${wvClient.runningCounter}")
        if (wvClient.runningCounter > 0) {
            webView.stopLoading()
            rlProgressBar.visibility = View.INVISIBLE
            wvClient.pageDownloadTimer.cancel()
            wvClient.runningCounter = 0
            return
        } else {
            if (!wvClient.isNetworkAvailable()) {
                super.onBackPressed()
                return
            } else {
                // по html-коду определяем дальнейшие действия
                wvClient.getHtml(webView)
                // сначала проверяем нужно ли закрыть какое-то окно в WebView
                for (i in patternsForClose.indices) {
                    if (!patternsForClose[i].equals("pattern is not applicable here", true)) {
                        val pattern = patternsForClose[i].toRegex()
                        val match = js.html?.let { pattern.find(it) }
                        if (match != null) {
                            webView.loadUrl(scriptsForClose[i])
                            return
                        }
                    }
                }
                // шаблон для проверки стартовой страницы
                val patternStartPage = patternForStartPage.toRegex()
                val matchStartPage = js.html?.let { patternStartPage.find(it) }
                // шаблон для проверки страницы ведутся работы на сайте или нет
                val patternRepPage = patternForRepairPage.toRegex()
                val matchRepPage = js.html?.let { patternRepPage.find(it) }
                // если ведутся ли работы на сайте или это стартовая страница, закрываем приложеине
                if (matchStartPage != null || matchRepPage != null || isStartUrl(webView, startUrl)) {
                    finish()
                }
                // если доступна кнопка "назад" в webView
                if (webView.canGoBack()) {
                    webView.goBack()// просто обрабатывается кнопка "назад"
                    return
                }
                // если не начальная страница и не ремонт сайта, то переводим на начальную
                if (matchStartPage == null && matchRepPage == null) {
                    webView.loadUrl(startUrl)
                    return
                }
            }
        }
        super.onBackPressed() // если ничего вышеуказнное не сработало, обрабатываем стандартное действие "назад"
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu, menu)
        menu.setGroupVisible(R.id.group_folder, true)
        menu.setGroupVisible(R.id.group_settings, true)
        menu.setGroupVisible(R.id.group_help, true)
        menu.setGroupVisible(R.id.group_evaluate, true)
        menu.setGroupVisible(R.id.group_about, true)
        menu.setGroupVisible(R.id.group_exit, true)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Операции для выбранного пункта меню
        return when (item.itemId) {
            R.id.folder -> {
                openDir()
                true
            }
            R.id.help -> {
                webView.loadUrl(pageHelp)
                true
            }
            R.id.settings -> {
                startActivity(Intent(this, Settings::class.java))
                true
            }
            R.id.evaluate -> {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${this.packageName}")))
                true
            }
            R.id.about -> {
                startActivity(Intent(this, About::class.java))
                true
            }
            R.id.exit -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Открывает папку, где сохраняются выписки
     */
    fun openDir() {
        var uri: Uri?
        val uriFolderStr: String? = sharedPref.getString("uriFolder", null)
        if (uriFolderStr == null) { // если в настройках еще не было сохранено uri начальной папки
            val startDir = "Documents"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // 10 версия и выше
                val sm = getSystemService(Context.STORAGE_SERVICE) as StorageManager
                val intent1 = sm.primaryStorageVolume.createOpenDocumentTreeIntent()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    uri = intent1.getParcelableExtra("android.provider.extra.INITIAL_URI", Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    uri = intent1.getParcelableExtra("android.provider.extra.INITIAL_URI")
                }
                val scheme = uri.toString().replace("/root/", "/document/") + "%3A$startDir"
                uri = Uri.parse(scheme)
            } else {
                uri = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:$startDir")
            }
        } else {
            uri = Uri.parse(uriFolderStr)
        }
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = if (packageName.contains("ru.handy.android.egrul", true)) "application/pdf" else "*/*"
        intent.putExtra("android.provider.extra.INITIAL_URI", uri)
        fileLauncher.launch(intent)
    }

    fun s(res: Int): String {
        return resources.getString(res)
    }

    override fun onResume() {
        if (wvClient.pageFilteredTimer != null) {
            wvClient.pageFilteredTimer?.start()
        }
        Log.d(LOG, "onResume WebActivity")
        super.onResume()
    }

    override fun onStop() {
        wvClient.pageFilteredTimer?.cancel()
        Log.d(LOG, "onStop WebActivity")
        super.onStop()
    }

    override fun onDestroy() {
        wvClient.pageFilteredTimer?.cancel()
        Log.d(LOG, "onDestroy WebActivity")
        super.onDestroy()
    }
}