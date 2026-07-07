package zhu.filer

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity

object EditorSettings {
    private const val PREF_NAME = "editor_settings"
    private const val KEY_FONT_SIZE = "font_size"
    private const val KEY_TAB_SIZE = "tab_size"
    private const val KEY_SHOW_LINE_NUMBERS = "show_line_numbers"
    private const val KEY_WORD_WRAP = "word_wrap"
    private const val KEY_HIGHLIGHT_LINE = "highlight_line"
    private const val KEY_AUTO_SAVE = "auto_save"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_INDENT_WITH_SPACES = "indent_with_spaces"
    private const val KEY_EDITOR_THEME = "editor_theme"

    private var prefs: SharedPreferences? = null

    private fun getPrefs(context: Context): SharedPreferences {
        if (prefs == null) {
            prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        }
        return prefs!!
    }

    fun getFontSize(context: Context): Float {
        return getPrefs(context).getFloat(KEY_FONT_SIZE, 14f)
    }

    fun setFontSize(context: Context, size: Float) {
        getPrefs(context).edit().putFloat(KEY_FONT_SIZE, size).apply()
    }

    fun getTabSize(context: Context): Int {
        return getPrefs(context).getInt(KEY_TAB_SIZE, 4)
    }

    fun setTabSize(context: Context, size: Int) {
        getPrefs(context).edit().putInt(KEY_TAB_SIZE, size).apply()
    }

    fun isShowLineNumbers(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SHOW_LINE_NUMBERS, true)
    }

    fun setShowLineNumbers(context: Context, show: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SHOW_LINE_NUMBERS, show).apply()
    }

    fun isWordWrap(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_WORD_WRAP, false)
    }

    fun setWordWrap(context: Context, wrap: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_WORD_WRAP, wrap).apply()
    }

    fun isHighlightLine(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_HIGHLIGHT_LINE, true)
    }

    fun setHighlightLine(context: Context, highlight: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_HIGHLIGHT_LINE, highlight).apply()
    }

    fun isAutoSave(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_SAVE, false)
    }

    fun setAutoSave(context: Context, autoSave: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_SAVE, autoSave).apply()
    }

    fun getThemeMode(context: Context): Int {
        return getPrefs(context).getInt(KEY_THEME_MODE, 2)
    }

    fun setThemeMode(context: Context, mode: Int) {
        getPrefs(context).edit().putInt(KEY_THEME_MODE, mode).apply()
    }

    fun isIndentWithSpaces(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_INDENT_WITH_SPACES, false)
    }

    fun setIndentWithSpaces(context: Context, indentWithSpaces: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_INDENT_WITH_SPACES, indentWithSpaces).apply()
    }

    fun getEditorTheme(context: Context): String {
        return getPrefs(context).getString(KEY_EDITOR_THEME, "darcula") ?: "darcula"
    }

    fun setEditorTheme(context: Context, theme: String) {
        getPrefs(context).edit().putString(KEY_EDITOR_THEME, theme).apply()
    }
}