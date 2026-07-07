package zhu.filer

import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.widget.CodeEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.regex.Pattern
import zhu.filer.databinding.ActivityTextEditorBinding
import zhu.filer.databinding.DialogFindReplaceBinding
import zhu.filer.databinding.DialogSettingsBinding

import com.google.android.material.R as materialR

class TextEditorActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FILE_PATH = "extra_file_path"
    }

    private lateinit var binding: ActivityTextEditorBinding
    private lateinit var file: File
    private var isModified = false
    private var initialContent: String = ""

    private var findDialogBinding: DialogFindReplaceBinding? = null
    private var currentMatchIndex = -1
    private var matchPositions = mutableListOf<Pair<Int, Int>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityTextEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestHighRefreshRate()

        val filePath = intent.getStringExtra(EXTRA_FILE_PATH)
        val f = filePath?.let { File(it) }
        if (f == null || !f.canRead()) {
            finish()
            return
        }
        file = f

        binding.toolbar.setBackgroundColor(getThemeColor(this, materialR.attr.colorPrimaryContainer))

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val sb = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            binding.toolbar.updatePadding(top = sb.top)
            val bottomPad = maxOf(sb.bottom, ime.bottom)
            binding.symbolBarScroll.updatePadding(bottom = bottomPad)
            insets
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = file.name
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            @Suppress("DEPRECATION")
            onBackPressed()
        }
        applyToolbarTitleName(binding.toolbar, file.name)
        updateSubtitle()

        binding.editor.isEditable = true

        binding.editor.setDisplayLnPanel(false)

        setupSymbolBar()

        binding.editor.isVerticalScrollBarEnabled = true
        binding.editor.isHorizontalScrollBarEnabled = true
        binding.editor.verticalScrollbarThumbDrawable =
            androidx.core.content.ContextCompat.getDrawable(this, R.drawable.scrollbar_thumb)!!
        binding.editor.verticalScrollbarTrackDrawable =
            androidx.core.content.ContextCompat.getDrawable(this, R.drawable.scrollbar_track)!!
        binding.editor.horizontalScrollbarThumbDrawable =
            androidx.core.content.ContextCompat.getDrawable(this, R.drawable.scrollbar_thumb_horizontal)!!
        binding.editor.horizontalScrollbarTrackDrawable =
            androidx.core.content.ContextCompat.getDrawable(this, R.drawable.scrollbar_track_horizontal)!!

        val ext = file.extension.lowercase()

        CodeEditorTextMate.init(applicationContext)
        CodeEditorTextMate.applyTheme(this)

        binding.editor.colorScheme = CodeEditorTextMate.createColorScheme(this)
        CodeEditorTextMate.applyEditorColors(binding.editor, this)

        binding.editor.setEditorLanguage(CodeEditorTextMate.languageForExtension(ext))

        applySettings()

        lifecycleScope.launch {
            val content = withContext(Dispatchers.IO) {
                runCatching { file.bufferedReader().use { it.readText() } }
                    .getOrDefault(getString(R.string.read_failed))
            }

            initialContent = content
            binding.editor.setText(content)

            binding.editor.subscribeAlways(ContentChangeEvent::class.java) {
                isModified = binding.editor.text.toString() != initialContent
                updateSubtitle()
            }
            binding.editor.subscribeAlways(SelectionChangeEvent::class.java) {
                updateSubtitle()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun requestHighRefreshRate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val display = windowManager.defaultDisplay ?: return
            val modes = display.supportedModes
            val highRefreshMode = modes.maxByOrNull { it.refreshRate } ?: return
            val params = window.attributes
            params.preferredDisplayModeId = highRefreshMode.modeId
            window.attributes = params
        }
    }

    private fun applySettings() {
        val fontSize = EditorSettings.getFontSize(this)
        binding.editor.setTextSize(fontSize)

        val tabSize = EditorSettings.getTabSize(this)
        binding.editor.tabWidth = tabSize

        binding.editor.isLineNumberEnabled = EditorSettings.isShowLineNumbers(this)
        binding.editor.setWordwrap(EditorSettings.isWordWrap(this))
        binding.editor.isHighlightCurrentLine = EditorSettings.isHighlightLine(this)
    }

    private fun updateSubtitle() {
        val cursor = binding.editor.cursor
        val line = cursor.leftLine + 1
        val col = cursor.leftColumn + 1
        val selected = if (cursor.isSelected()) cursor.right - cursor.left else 0
        val selectedTag = if (selected > 0) " ($selected)" else ""
        supportActionBar?.subtitle = "$line:$col$selectedTag"
        applyToolbarTitleName(binding.toolbar, if (isModified) "*${file.name}" else file.name)
    }

    private fun setupSymbolBar() {
        val symbols = listOf(
            "→" to "  ",
            ";", "=", ",", ".",
            "(", ")", "{", "}", "[", "]",
            "+", "-", "*", "/",
            ":", "\"", "'",
            "<", ">", "#", "@"
        )
        val container = binding.symbolBar
        val density = resources.displayMetrics.density
        val textColor = getThemeColor(this, materialR.attr.colorOnSurface)

        for (entry in symbols) {
            val display: String
            val insert: String
            when (entry) {
                is String -> { display = entry; insert = entry }
                is Pair<*, *> -> { display = entry.first as String; insert = entry.second as String }
                else -> continue
            }
            val tv = TextView(this).apply {
                text = display
                textSize = 18f
                gravity = Gravity.CENTER
                setTextColor(textColor)
                val padH = (density * 12).toInt()
                val padV = (density * 4).toInt()
                setPadding(padH, padV, padH, padV)
                isClickable = true
                isFocusable = true
                setBackgroundResource(R.drawable.symbol_key_background)
            }
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.gravity = Gravity.CENTER_VERTICAL
            tv.layoutParams = lp
            tv.setOnClickListener {
                binding.editor.insertText(insert, insert.length)
            }
            container.addView(tv)
        }
    }

    private fun showFindReplaceDialog() {
        findDialogBinding = DialogFindReplaceBinding.inflate(layoutInflater)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.find)
            .setView(findDialogBinding!!.root)
            .setNegativeButton(R.string.close, null)
            .create()

        dialog.show()

        val findInput = findDialogBinding!!.findInput
        val replaceInput = findDialogBinding!!.replaceInput
        val resultText = findDialogBinding!!.resultText
        val regexSwitch = findDialogBinding!!.regexSwitch
        val caseSensitiveSwitch = findDialogBinding!!.caseSensitiveSwitch
        val wholeWordSwitch = findDialogBinding!!.wholeWordSwitch
        val btnFindNext = findDialogBinding!!.btnFindNext
        val btnFindPrev = findDialogBinding!!.btnFindPrev
        val btnReplace = findDialogBinding!!.btnReplace
        val btnReplaceAll = findDialogBinding!!.btnReplaceAll

        fun doSearch(): Boolean {
            return performSearch(findInput, resultText, regexSwitch, caseSensitiveSwitch, wholeWordSwitch)
        }

        findInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                doSearch()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        regexSwitch.setOnCheckedChangeListener { _, _ -> doSearch() }
        caseSensitiveSwitch.setOnCheckedChangeListener { _, _ -> doSearch() }
        wholeWordSwitch.setOnCheckedChangeListener { _, _ -> doSearch() }

        btnFindNext.setOnClickListener {
            if (doSearch()) goToNextMatch()
        }

        btnFindPrev.setOnClickListener {
            if (doSearch()) goToPreviousMatch()
        }

        btnReplace.setOnClickListener {
            if (matchPositions.isEmpty() && !doSearch()) return@setOnClickListener
            if (currentMatchIndex < 0 || currentMatchIndex >= matchPositions.size) {
                if (doSearch()) goToNextMatch()
            }
            replaceCurrentMatch()
        }

        btnReplaceAll.setOnClickListener {
            if (matchPositions.isEmpty() && !doSearch()) return@setOnClickListener
            replaceAllMatches()
            doSearch()
        }
    }

    private fun performSearch(
        findInput: EditText,
        resultText: TextView,
        regexSwitch: android.widget.CheckBox,
        caseSensitiveSwitch: android.widget.CheckBox,
        wholeWordSwitch: android.widget.CheckBox
    ): Boolean {
        val query = findInput.text.toString()
        if (query.isEmpty()) {
            resultText.text = ""
            return false
        }

        val text = binding.editor.text.toString()
        val isRegex = regexSwitch.isChecked
        val isCaseSensitive = caseSensitiveSwitch.isChecked
        val isWholeWord = wholeWordSwitch.isChecked

        matchPositions.clear()
        currentMatchIndex = -1

        try {
            val pattern = if (isRegex) {
                val flags = if (isCaseSensitive) 0 else Pattern.CASE_INSENSITIVE
                Pattern.compile(query, flags)
            } else {
                val escaped = Pattern.quote(query)
                val pattern = if (isWholeWord) "\\b$escaped\\b" else escaped
                val flags = if (isCaseSensitive) 0 else Pattern.CASE_INSENSITIVE
                Pattern.compile(pattern, flags)
            }

            val matcher = pattern.matcher(text)
            while (matcher.find()) {
                matchPositions.add(Pair(matcher.start(), matcher.end()))
            }

            if (matchPositions.isEmpty()) {
                resultText.text = getString(R.string.no_match)
                return false
            } else {
                resultText.text = getString(R.string.match_count, matchPositions.size)
                return true
            }
        } catch (e: Exception) {
            resultText.text = e.message
            return false
        }
    }

    private fun goToNextMatch() {
        if (matchPositions.isEmpty()) return

        currentMatchIndex = (currentMatchIndex + 1) % matchPositions.size
        highlightCurrentMatch()
    }

    private fun goToPreviousMatch() {
        if (matchPositions.isEmpty()) return

        currentMatchIndex = if (currentMatchIndex <= 0) matchPositions.size - 1 else currentMatchIndex - 1
        highlightCurrentMatch()
    }

    private fun highlightCurrentMatch() {
        if (currentMatchIndex < 0 || currentMatchIndex >= matchPositions.size) return

        val (start, end) = matchPositions[currentMatchIndex]
        val startIndexer = binding.editor.text.getIndexer()
        val startPos = startIndexer.getCharPosition(start)
        binding.editor.setSelection(startPos.line, startPos.column)
    }

    private fun replaceCurrentMatch() {
        if (currentMatchIndex < 0 || currentMatchIndex >= matchPositions.size) return

        val replaceText = findDialogBinding?.replaceInput?.text.toString()
        val (start, end) = matchPositions[currentMatchIndex]
        val indexer = binding.editor.text.getIndexer()
        val startPos = indexer.getCharPosition(start)
        val endPos = indexer.getCharPosition(end)

        binding.editor.text.delete(startPos.line, startPos.column, endPos.line, endPos.column)
        binding.editor.text.insert(startPos.line, startPos.column, replaceText)

        matchPositions.removeAt(currentMatchIndex)
        if (currentMatchIndex >= matchPositions.size) {
            currentMatchIndex = 0
        }
    }

    private fun replaceAllMatches() {
        if (matchPositions.isEmpty()) return

        val query = findDialogBinding?.findInput?.text.toString()
        val replaceText = findDialogBinding?.replaceInput?.text.toString()
        val isRegex = findDialogBinding?.regexSwitch?.isChecked ?: false
        val isCaseSensitive = findDialogBinding?.caseSensitiveSwitch?.isChecked ?: false
        val isWholeWord = findDialogBinding?.wholeWordSwitch?.isChecked ?: false

        val text = binding.editor.text.toString()
        val result = try {
            val pattern = if (isRegex) {
                val flags = if (isCaseSensitive) 0 else Pattern.CASE_INSENSITIVE
                Pattern.compile(query, flags)
            } else {
                val escaped = Pattern.quote(query)
                val pattern = if (isWholeWord) "\\b$escaped\\b" else escaped
                val flags = if (isCaseSensitive) 0 else Pattern.CASE_INSENSITIVE
                Pattern.compile(pattern, flags)
            }

            val matcher = pattern.matcher(text)
            matcher.replaceAll(replaceText)
        } catch (e: Exception) {
            Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
            return
        }

        binding.editor.setText(result)
        matchPositions.clear()
        currentMatchIndex = -1
        isModified = true
        updateSubtitle()
    }

    private fun showSettingsDialog() {
        val settingsBinding = DialogSettingsBinding.inflate(layoutInflater)

        val currentFontSize = EditorSettings.getFontSize(this)
        settingsBinding.fontSizeSlider.value = currentFontSize
        settingsBinding.fontSizeText.text = currentFontSize.toInt().toString()

        settingsBinding.fontSizePlus.setOnClickListener {
            val current = settingsBinding.fontSizeSlider.value
            if (current < 32) {
                settingsBinding.fontSizeSlider.value = current + 1
            }
        }

        settingsBinding.fontSizeMinus.setOnClickListener {
            val current = settingsBinding.fontSizeSlider.value
            if (current > 8) {
                settingsBinding.fontSizeSlider.value = current - 1
            }
        }

        settingsBinding.fontSizeSlider.addOnChangeListener { slider, value, fromUser ->
            settingsBinding.fontSizeText.text = value.toInt().toString()
            binding.editor.setTextSize(value)
            EditorSettings.setFontSize(this, value)
        }

        settingsBinding.tabSizeSlider.value = EditorSettings.getTabSize(this).toFloat()
        settingsBinding.tabSizeSlider.addOnChangeListener { _, value, _ ->
            binding.editor.tabWidth = value.toInt()
            EditorSettings.setTabSize(this, value.toInt())
        }

        settingsBinding.switchShowLineNumbers.isChecked = EditorSettings.isShowLineNumbers(this)
        settingsBinding.switchShowLineNumbers.setOnCheckedChangeListener { _, isChecked ->
            binding.editor.isLineNumberEnabled = isChecked
            EditorSettings.setShowLineNumbers(this, isChecked)
        }

        settingsBinding.switchWordWrap.isChecked = EditorSettings.isWordWrap(this)
        settingsBinding.switchWordWrap.setOnCheckedChangeListener { _, isChecked ->
            binding.editor.setWordwrap(isChecked)
            EditorSettings.setWordWrap(this, isChecked)
        }

        settingsBinding.switchHighlightLine.isChecked = EditorSettings.isHighlightLine(this)
        settingsBinding.switchHighlightLine.setOnCheckedChangeListener { _, isChecked ->
            binding.editor.isHighlightCurrentLine = isChecked
            EditorSettings.setHighlightLine(this, isChecked)
        }

        settingsBinding.switchAutoSave.isChecked = EditorSettings.isAutoSave(this)
        settingsBinding.switchAutoSave.setOnCheckedChangeListener { _, isChecked ->
            EditorSettings.setAutoSave(this, isChecked)
        }

        settingsBinding.switchIndentWithSpaces.isChecked = EditorSettings.isIndentWithSpaces(this)
        settingsBinding.switchIndentWithSpaces.setOnCheckedChangeListener { _, isChecked ->
            EditorSettings.setIndentWithSpaces(this, isChecked)
        }

        val currentTheme = EditorSettings.getThemeMode(this)
        when (currentTheme) {
            0 -> settingsBinding.themeToggle.check(R.id.themeDark)
            1 -> settingsBinding.themeToggle.check(R.id.themeLight)
            2 -> settingsBinding.themeToggle.check(R.id.themeSystem)
        }

        settingsBinding.themeToggle.addOnButtonCheckedListener { _: MaterialButtonToggleGroup, checkedId: Int, isChecked: Boolean ->
            if (isChecked) {
                val mode = when (checkedId) {
                    R.id.themeDark -> 0
                    R.id.themeLight -> 1
                    R.id.themeSystem -> 2
                    else -> 2
                }
                EditorSettings.setThemeMode(this, mode)
                applyThemeMode(mode)
            }
        }

        val editorThemes = listOf("darcula", "ayu-dark", "quietlight", "solarized_dark")
        val currentEditorTheme = EditorSettings.getEditorTheme(this)
        val themeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, editorThemes)
        settingsBinding.editorThemeSpinner.adapter = themeAdapter
        val currentIndex = editorThemes.indexOf(currentEditorTheme)
        if (currentIndex >= 0) {
            settingsBinding.editorThemeSpinner.setSelection(currentIndex)
        }

        settingsBinding.editorThemeSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val selectedTheme = editorThemes[position]
                EditorSettings.setEditorTheme(this@TextEditorActivity, selectedTheme)
                applyEditorTheme(selectedTheme)
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings)
            .setView(settingsBinding.root)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun applyEditorTheme(themeName: String) {
        val themeRegistry = io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry.getInstance()
        themeRegistry.setTheme(themeName)
        binding.editor.colorScheme = CodeEditorTextMate.createColorScheme(this)
        CodeEditorTextMate.applyEditorColors(binding.editor, this)
        val ext = file.extension.lowercase()
        binding.editor.setEditorLanguage(CodeEditorTextMate.languageForExtension(ext))
    }

    private fun applyThemeMode(mode: Int) {
        when (mode) {
            0 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            1 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            2 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    private fun saveFile(): Boolean {
        return try {
            val content = binding.editor.text.toString()
            file.writeText(content)
            initialContent = content
            isModified = false
            updateSubtitle()
            Toast.makeText(this, R.string.save_success, Toast.LENGTH_SHORT).show()
            true
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.save_failed, e.message), Toast.LENGTH_LONG).show()
            false
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(Menu.NONE, Menu.FIRST, Menu.NONE, getString(R.string.find))
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        menu.add(Menu.NONE, Menu.FIRST + 1, Menu.NONE, getString(R.string.save))
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(Menu.NONE, Menu.FIRST + 2, Menu.NONE, getString(R.string.undo))
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(Menu.NONE, Menu.FIRST + 3, Menu.NONE, getString(R.string.redo))
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(Menu.NONE, Menu.FIRST + 4, Menu.NONE, getString(R.string.settings))
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            Menu.FIRST -> showFindReplaceDialog()
            Menu.FIRST + 1 -> saveFile()
            Menu.FIRST + 2 -> binding.editor.undo()
            Menu.FIRST + 3 -> binding.editor.redo()
            Menu.FIRST + 4 -> showSettingsDialog()
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (isModified) {
            if (EditorSettings.isAutoSave(this)) {
                saveFile()
                @Suppress("DEPRECATION")
                super.onBackPressed()
            } else {
                MaterialAlertDialogBuilder(this)
                    .setTitle(file.name)
                    .setMessage(R.string.unsaved_changes)
                    .setPositiveButton(R.string.save_and_exit) { _, _ ->
                        if (saveFile()) {
                            @Suppress("DEPRECATION")
                            super.onBackPressed()
                        }
                    }
                    .setNegativeButton(R.string.discard) { _, _ ->
                        @Suppress("DEPRECATION")
                        super.onBackPressed()
                    }
                    .setCancelable(false)
                    .show()
            }
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized) {
            refreshToolbarTitle(binding.toolbar)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && ::binding.isInitialized) {
            refreshToolbarTitle(binding.toolbar)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::binding.isInitialized) {
            binding.editor.release()
        }
    }
}