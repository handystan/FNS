package ru.handy.android.egrul.utils

import java.nio.charset.StandardCharsets
import java.util.*
import java.util.regex.Pattern
import kotlin.collections.ArrayList

const val PREFS_NAME: String = "sharedPrefEGRUL" //название хранилища SharedPreferences
const val LOG: String = "myLogs" // логирование


/**
 * преобразовыввает строку, в которой только шестнадцатиричные цифры с %, в строку
 */
fun hexStringToString(hex: String): String {
    val str: String = hex.replace("%", "").trim()
    val l = str.length
    val data = ByteArray(l / 2)
    var i = 0
    while (i < l) {
        data[i / 2] = ((Character.digit(str[i], 16) shl 4) + Character.digit(str[i + 1], 16)).toByte()
        i += 2
    }
    return String(data, StandardCharsets.UTF_8)
}

/**
 * преобразовывает строку, в которой могут встречаться шестнадцатиричные цифры, в строку
 */
fun replaceHexToStrInStr(initStr: String): String {
    // с %00 по %7F идут 8-битные символы, а с %C2%80 и до конца идут 16 битные символы
    val pattern = Pattern.compile("%[0-7][0-9A-Fa-f]|%[C-Fc-f][0-9A-Fa-f]%[0-9A-Fa-f]{2}")
    var resStr = ""
    val matcher = pattern.matcher(initStr)
    var lastStart = 0
    while (matcher.find()) {
        val start = matcher.start()
        val end = matcher.end()
        if (start > lastStart) {
            resStr = resStr + initStr.substring(lastStart, start) + hexStringToString(initStr.substring(start, end))
        } else {
            resStr += hexStringToString(initStr.substring(start, end))
        }
        lastStart = end
    }
    if (lastStart < initStr.length) {
        resStr += initStr.substring(lastStart, initStr.length)
    }
    return resStr
}

/**
 * разделяет сроку на элементы на основе данного разделителя
 *
 * @param str         строка, котороая преобразуется в ArrayList
 * @param delimiter   разделитель, по которому строка делится на элементы
 * @param deleteBlank удалять пробелы в начале и конце каждого элемента или нет
 * @return Array<String>
 */
fun strToList(str: String, delimiter: String, deleteBlank: Boolean): Array<String> {
    val arr = str.split(delimiter.toRegex()).toTypedArray()
    val list: MutableList<String> = ArrayList() //список со всеми категориями в данном слове
    for (s in arr) {
        list.add(if (deleteBlank) s.trim() else s)
    }
    return list.toTypedArray()
}

/**
 * объединяет список в одну строку на основе разделителя
 *
 * @param list      массив, который преобразуется в строку я использованием разделителя
 * @param delimiter разделитель, по которому строка делится на элементы
 * @return строка
 */
fun listToStr(list: Array<String>, delimiter: String): String {
    var str = ""
    for (i in list.indices) {
        str = str + list[i] + if (i == list.size - 1) "" else delimiter + " "
    }
    return str
}

/**
 * вовращает расширение файла из полного пути файла
 * @param src полный путь файла
 * @return расширение файла
 */
fun fileExt(src: String): String? {
    var ext = src
    if (ext.indexOf("?") > -1) {
        ext = ext.substring(0, ext.indexOf("?"))
    }
    return if (ext.lastIndexOf(".") == -1) {
        null
    } else {
        ext = ext.substring(ext.lastIndexOf(".") + 1)
        if (ext.indexOf("%") > -1) {
            ext = ext.substring(0, ext.indexOf("%"))
        }
        if (ext.indexOf("/") > -1) {
            ext = ext.substring(0, ext.indexOf("/"))
        }
        ext.lowercase(Locale.getDefault())
    }
}