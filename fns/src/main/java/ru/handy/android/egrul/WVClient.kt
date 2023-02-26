package ru.handy.android.egrul

import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.CountDownTimer
import android.util.Log
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import ru.handy.android.egrul.utils.LOG
import ru.handy.android.egrul.utils.closeFirstMessage

/**
 * класс ответственный за поэтапную работу WebView
 */
class WVClient(_activity: WebActivity) : WebViewClient() {
    private val activity: WebActivity = _activity
    private var disconnectHtml: String? = null
    var runningCounter: Int = 0 // запущен pageDownloadTimer или нет (0 - не запущен, >0 - запущен)
    var htmlLength = 0 // длина html-страницы (нужно для того, чтобы понимать, запускать ли в ней js по стиранию ненужной инфы)
    var urlLast: String? = null // url последней загруженной страницы
    var pageFilteredTimer: CountDownTimer? = null // таймер для постоянного стирания ненужной инфы со страницы
    private val errorPage by lazy { activity.s(R.string.error_page) } // страница с ошибкой
    private val pbPage by lazy { activity.s(R.string.pb_page) } // сайт Прозрачного бизнеса

    // java script по очистке html-страницы от лишней информации (в основном от заголовка и окончания страницы ФНС)
    private val jsTextFilteredHtml by lazy { activity.s(R.string.jstext_filtered_html) }

    // java script по очистке html-страницы от лишней информации для страницы Прозрачный бизнес (pb.nalog.ru)
    private val jsTextFilteredHtmlPB by lazy { activity.s(R.string.jstext_filtered_html_pb) }

    // таймер для отображения разного текста при загрузке страницы
    var pageDownloadTimer: CountDownTimer = object : CountDownTimer(60000, 30000) {
        override fun onTick(millisUntilFinished: Long) {
            if (millisUntilFinished in 1..30000L)
                activity.tvConnecting.text = activity.s(R.string.connect_service_FNS)
        }

        override fun onFinish() {
            activity.tvConnecting.text = activity.s(R.string.service_FNS_not_available)
        }
    }

    fun getHtml(view: WebView?) {
        view?.loadUrl("javascript:window.HtmlViewer.showHTML(document.documentElement.outerHTML);")
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        Log.d(LOG, "shouldOverrideUrlLoading runningCounter = $runningCounter")
        if (runningCounter == 0) {
            activity.rlProgressBar.visibility = View.VISIBLE
            activity.tvConnecting.text = activity.s(R.string.connect_FNS)
            pageDownloadTimer.start() // таймер для отображения разной информации о долгой загрузке страницы
        }
        runningCounter++
        return false
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        if (!isNetworkAvailable()) { // если нет интернета
            view?.stopLoading()
            if (disconnectHtml == null) {
                disconnectHtml = "<!DOCTYPE html><head></head><body>" +
                        "<h1 style='text-align:center;font-size:400%;padding-top:100px'>${activity.s(R.string.need_internet)}</h1>" +
                        "</body></html>"
                view?.loadData(disconnectHtml!!, "text/html", "UTF-8")
            } else {
                disconnectHtml = null
            }
        } else if (url != null && url == errorPage) {// обработка со страницей об ошибке
            view?.stopLoading()
            view?.loadUrl(activity.startUrl)
            Log.d(LOG, "Страница перенаправлена со страницы с ошибкой $errorPage")
        } else {
            Log.d(LOG, "onPageStarted runningCounter = $runningCounter")
            if (runningCounter == 0) {
                activity.rlProgressBar.visibility = View.VISIBLE
                activity.tvConnecting.text = activity.s(R.string.connect_FNS)
                pageDownloadTimer.start() // таймер для отображения разной информации о долгой загрузке страницы
            }
            runningCounter = 1
        }
        Log.d(LOG, "end of onPageStarted")
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        urlLast = url
        getHtml(view)
        Log.d(LOG, "Page is loaded. Url = $urlLast")
        Log.d(LOG, "onPageFinished runningCounter = $runningCounter")
        runningCounter = Integer.max(runningCounter - 1, 0)
        if (runningCounter == 0) {
            activity.rlProgressBar.visibility = View.INVISIBLE
            pageDownloadTimer.cancel()
        }
        // удаляем не нужные элементы (причем для Прозрачного бизнеса свои фильтры)
        view?.loadUrl(if (urlLast?.contains(pbPage) == true) jsTextFilteredHtmlPB else jsTextFilteredHtml)
        // закрываем сообщения о составителях отчетности, если приложение открывается уже не первый раз (только для bfo)
        closeFirstMessage(activity, view)
        // таймер для постоянного стирания ненужной инфы со страницы
        if (pageFilteredTimer == null) {
            pageFilteredTimer = object : CountDownTimer(3600000, 200) {
                override fun onTick(millisUntilFinished: Long) {
                    getHtml(view)
                    if (activity.js.html != null && activity.js.html?.length != htmlLength) {
                        htmlLength = activity.js.html!!.length
                        view?.loadUrl(if (urlLast?.contains(pbPage) == true) jsTextFilteredHtmlPB else jsTextFilteredHtml)
                    }
                }

                override fun onFinish() {
                }
            }
            pageFilteredTimer?.start()
        }
        super.onPageFinished(view, url)
        Log.d(LOG, "end of onPageFinished")
    }

    /**
     * функция, определяющия есть интернет или нет
     */
    @Suppress("DEPRECATION")
    fun isNetworkAvailable(): Boolean {
        val connectivityManager =
            activity.getSystemService(AppCompatActivity.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val capabilities =
                connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
            if (capabilities != null) {
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    return true
                }
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    return true
                }
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                    return true
                }
            }
        } else {
            val activeNetworkInfo = connectivityManager.activeNetworkInfo
            if (activeNetworkInfo != null && activeNetworkInfo.isConnected) {
                return true
            }
        }
        return false
    }
}