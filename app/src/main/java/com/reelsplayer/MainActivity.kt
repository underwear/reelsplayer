package com.reelsplayer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.viewpager2.widget.ViewPager2

class MainActivity : AppCompatActivity() {

    private lateinit var landing: LinearLayout
    private lateinit var viewPager: ViewPager2
    private lateinit var hud: LinearLayout
    private lateinit var hudName: TextView
    private lateinit var hudCounter: TextView

    private var videoFiles = mutableListOf<Pair<String, Uri>>()

    private val folderPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { loadFolder(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        landing = findViewById(R.id.landing)
        viewPager = findViewById(R.id.viewPager)
        hud = findViewById(R.id.hud)
        hudName = findViewById(R.id.hudName)
        hudCounter = findViewById(R.id.hudCounter)

        findViewById<Button>(R.id.pickBtn).setOnClickListener {
            folderPicker.launch(null)
        }

        viewPager.offscreenPageLimit = 1
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateHud(position)
                (viewPager.adapter as? VideoAdapter)?.onPageSelected(position)
            }
        })
    }

    private fun loadFolder(treeUri: Uri) {
        // Release old players before loading new folder
        (viewPager.adapter as? VideoAdapter)?.releaseAll()

        try {
            contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Permission usable for this session, just not persistable
        }

        val tree = DocumentFile.fromTreeUri(this, treeUri) ?: return
        val newFiles = mutableListOf<Pair<String, Uri>>()

        tree.listFiles()
            .filter { it.isFile && it.type?.startsWith("video/") == true }
            .sortedBy { it.name ?: "" }
            .forEach { file ->
                newFiles.add(Pair(file.name ?: "Unknown", file.uri))
            }

        if (newFiles.isEmpty()) return
        videoFiles = newFiles

        landing.visibility = View.GONE
        viewPager.visibility = View.VISIBLE
        hud.visibility = View.VISIBLE

        val adapter = VideoAdapter(videoFiles)
        viewPager.adapter = adapter
        viewPager.post { adapter.onPageSelected(0) }
        updateHud(0)
    }

    private fun updateHud(position: Int) {
        if (position < 0 || position >= videoFiles.size) return
        hudName.text = videoFiles[position].first
        hudCounter.text = "${position + 1} / ${videoFiles.size}"
    }

    override fun onPause() {
        super.onPause()
        (viewPager.adapter as? VideoAdapter)?.pauseCurrent()
    }

    override fun onResume() {
        super.onResume()
        (viewPager.adapter as? VideoAdapter)?.resumeCurrent()
    }

    override fun onDestroy() {
        super.onDestroy()
        (viewPager.adapter as? VideoAdapter)?.releaseAll()
    }
}
