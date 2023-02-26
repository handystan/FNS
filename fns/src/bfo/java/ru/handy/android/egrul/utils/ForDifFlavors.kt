package ru.handy.android.egrul.utils

import android.content.SharedPreferences
import android.webkit.WebView
import ru.handy.android.egrul.R
import ru.handy.android.egrul.WebActivity

/**
 * файл, в котором перечислены методы, у которых разная реализация для разных flavors
 */

/**
 * закрываем сообщения о составителях отчетности, если приложение открывается уже не первый раз (только для bfo)
 */
fun closeFirstMessage(activity: WebActivity, view: WebView?) {
    if (!activity.sharedPref.getBoolean("alreadyOpened", false)) {
        val editor: SharedPreferences.Editor = activity.sharedPref.edit()
        editor.putBoolean("alreadyOpened", true)
        editor.apply()
    } else {
        view?.loadUrl(activity.s(R.string.script_for_close_4))
    }
}

/**
 * является ли страница стартовой (только для bfo)
 */
fun isStartUrl(webView: WebView, startUrl: String): Boolean {
    return webView.url.equals(startUrl)
}