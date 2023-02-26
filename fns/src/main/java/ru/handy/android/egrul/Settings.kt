package ru.handy.android.egrul

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import ru.handy.android.egrul.utils.*


class Settings : AppCompatActivity(), View.OnClickListener {
    private lateinit var etFolder: EditText
    private lateinit var bChangeDir: Button
    private lateinit var sharedPref: SharedPreferences //хранилище с настройками
    private lateinit var dirLauncher: ActivityResultLauncher<Intent>
    private val standardDirectories = arrayOf(
        Environment.DIRECTORY_ALARMS,
        Environment.DIRECTORY_DCIM,
        Environment.DIRECTORY_DOCUMENTS,
        Environment.DIRECTORY_DOWNLOADS,
        Environment.DIRECTORY_MOVIES,
        Environment.DIRECTORY_MUSIC,
        Environment.DIRECTORY_NOTIFICATIONS,
        Environment.DIRECTORY_PICTURES,
        Environment.DIRECTORY_PODCASTS,
        Environment.DIRECTORY_RINGTONES
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true) // устанавливаем actionbar
        sharedPref = getSharedPreferences(PREFS_NAME, MODE_PRIVATE) //хранилище с настройками
        etFolder = findViewById(R.id.etFolder)
        bChangeDir = findViewById(R.id.bChangeDir)
        etFolder.setText(sharedPref.getString("folder", "Documents"))
        bChangeDir.setOnClickListener(this)
        // переменная, которая обрабатыавет получение папки из диалогового окна
        dirLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uriFolder: Uri? = result.data?.data
                var folder: String = uriFolder.toString()
                folder = folder.replaceBefore("home%3A", "") // для случая, когда не показывается папка Documents
                folder =
                    folder.replace(
                        "home%3A",
                        "Documents" + File.separator
                    ) // для случая, когда не показывается папка Documents
                folder = folder.replaceBefore("%3A", "")
                folder = folder.replace("%3A", "").replace("%2F", File.separator)
                val rootFolder: String = folder.split(File.separator)[0] // корневая папка
                if (isStandardDirectory(rootFolder)[0] as Boolean) {
                    val editor: SharedPreferences.Editor = sharedPref.edit()
                    editor.putString("uriFolder", uriFolder.toString())
                    folder =
                        replaceHexToStrInStr(folder.replace(rootFolder, isStandardDirectory(rootFolder)[1] as String))
                    editor.putString("folder", folder)
                    editor.apply()
                    etFolder.setText(folder)
                } else {
                    AlertDialog.Builder(this)
                        .setTitle(R.string.folder_not_changed)
                        .setMessage(s(R.string.should_standard_folder) + "\n" + listToStr(standardDirectories, ","))
                        .setPositiveButton(s(R.string.close), null)
                        .create()
                        .show()
                }
            }
        }
    }

    /**
     * Called when a view has been clicked.
     *
     * @param v The view that was clicked.
     */
    override fun onClick(v: View?) {
        if (v?.id == R.id.bChangeDir) {
            openDir()
        }
    }

    /**
     * Открывает папку, где созраняются выписки
     */
    fun openDir() {
        var uri: Uri?
        val uriFolderStr: String? = sharedPref.getString("uriFolder", null)
        if (uriFolderStr == null) { // если в настройках еще не было сохранено uri начальной папки
            val startDir = "Documents"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // 10 версия и выше
                val sm = getSystemService(Context.STORAGE_SERVICE) as StorageManager
                val intent1 = sm.primaryStorageVolume.createOpenDocumentTreeIntent()
                uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent1.getParcelableExtra("android.provider.extra.INITIAL_URI", Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent1.getParcelableExtra("android.provider.extra.INITIAL_URI")
                }
                val scheme = uri.toString().replace("/root/", "/document/") + "%3A$startDir"
                uri = Uri.parse(scheme)
            } else {
                uri = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:$startDir")
            }
        } else {
            uri = Uri.parse(uriFolderStr)
        }
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(""))
        intent.putExtra("android.provider.extra.INITIAL_URI", uri)
        dirLauncher.launch(intent)
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

    /**
     * проверяет, входит ли папка в число стандартных папок
     * @param folder искомая папка
     * @return возвращается массив: 1-ый элемент - является ли папка одной из станд. папок, 2-ой - наимен-е станд. папки, 3-й - папка полностью соответствует стандартной или только без учета регистра
     */
    fun isStandardDirectory(folder: String): Array<Any> {
        for (valid in standardDirectories) {
            if (valid.equals(folder, true)) {
                return arrayOf(true, valid, valid.equals(folder))
            }
        }
        return arrayOf(false, "", false)
    }

    private fun s(res: Int): String {
        return resources.getString(res)
    }

    override fun onDestroy() {
        Log.d(LOG, "onDestroy Settings")
        super.onDestroy()
    }
}