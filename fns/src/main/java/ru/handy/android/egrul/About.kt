package ru.handy.android.egrul

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import ru.handy.android.egrul.utils.LOG


class About : AppCompatActivity() {
    private lateinit var mTextView: TextView

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.about)
        supportActionBar?.setDisplayHomeAsUpEnabled(true) // устанавливаем actionbar
        mTextView = findViewById(R.id.tvAbout)
        var strAbout: String = s(R.string.about_desc)
        try {
            val version: String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0)).versionName
            } else {
                @Suppress("DEPRECATION") packageManager.getPackageInfo(packageName, 0).versionName
            }
            strAbout = strAbout.replace("versionName", version)
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        } finally {
            mTextView.text = HtmlCompat.fromHtml(strAbout, HtmlCompat.FROM_HTML_MODE_LEGACY)
        }
    }

    // обрабатываем кнопку "назад" в ActionBar
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Операции для выбранного пункта меню
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun s(res: Int): String {
        return resources.getString(res)
    }

    public override fun onResume() {
        super.onResume()
    }

    override fun onDestroy() {
        Log.d(LOG, "onDestroy About")
        super.onDestroy()
    }
}
