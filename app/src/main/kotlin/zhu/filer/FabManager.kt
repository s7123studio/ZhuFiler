package zhu.filer

import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.File

class FabManager(private val activity: AppCompatActivity) {

    private lateinit var fabPaste: FloatingActionButton
    private lateinit var fabCancel: FloatingActionButton
    private var onPaste: ((List<File>, Boolean, Boolean) -> Unit)? = null
    private var onCancel: (() -> Unit)? = null
    private var targetDirProvider: (() -> File)? = null

    fun setup(
        fabPaste: FloatingActionButton,
        fabCancel: FloatingActionButton,
        clipboard: ClipboardManager,
        targetDirProvider: () -> File,
        onPaste: (List<File>, Boolean, Boolean) -> Unit,
        onCancel: () -> Unit
    ) {
        this.fabPaste = fabPaste
        this.fabCancel = fabCancel
        this.targetDirProvider = targetDirProvider
        this.onPaste = onPaste
        this.onCancel = onCancel

        fabPaste.setOnClickListener {
            val files = clipboard.getFiles()
            if (files.isEmpty()) return@setOnClickListener
            val targetDir = targetDirProvider()
            val isMove = clipboard.isCut()
            val conflicts = files.filter { File(targetDir, it.name).exists() }
            if (conflicts.isNotEmpty()) {
                MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.target_exists)
                    .setMessage(activity.getString(R.string.overwrite_conflict, conflicts.size))
                    .setPositiveButton(R.string.overwrite) { _, _ ->
                        onPaste(files, isMove, true)
                    }
                    .setNegativeButton(R.string.skip) { _, _ ->
                        onPaste(files, isMove, false)
                    }
                    .show()
            } else {
                onPaste(files, isMove, false)
            }
        }

        fabCancel.setOnClickListener { onCancel() }
    }

    fun updatePasteButtons(clipboard: ClipboardManager) {
        if (clipboard.hasContent()) {
            val count = clipboard.getFiles().size
            fabPaste.isVisible = true
            fabCancel.isVisible = true
            fabPaste.alpha = 0f
            fabCancel.alpha = 0f
            fabPaste.scaleX = 0.8f
            fabPaste.scaleY = 0.8f
            fabCancel.scaleX = 0.8f
            fabCancel.scaleY = 0.8f
            fabPaste.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(200)
                .setInterpolator(DecelerateInterpolator())
                .start()
            fabCancel.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(200)
                .setStartDelay(50)
                .setInterpolator(DecelerateInterpolator())
                .start()
            fabPaste.contentDescription = activity.getString(R.string.paste_count, count)
        } else {
            fabPaste.animate()
                .alpha(0f)
                .scaleX(0.8f)
                .scaleY(0.8f)
                .setDuration(150)
                .setInterpolator(AccelerateInterpolator())
                .withEndAction {
                    fabPaste.isVisible = false
                    fabPaste.alpha = 1f
                    fabPaste.scaleX = 1f
                    fabPaste.scaleY = 1f
                }
                .start()
            fabCancel.animate()
                .alpha(0f)
                .scaleX(0.8f)
                .scaleY(0.8f)
                .setDuration(150)
                .setInterpolator(AccelerateInterpolator())
                .withEndAction {
                    fabCancel.isVisible = false
                    fabCancel.alpha = 1f
                    fabCancel.scaleX = 1f
                    fabCancel.scaleY = 1f
                }
                .start()
            fabPaste.contentDescription = activity.getString(R.string.paste)
        }
    }
}