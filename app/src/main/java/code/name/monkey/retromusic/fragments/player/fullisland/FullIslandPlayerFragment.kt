/*
 * Copyright (c) 2020 Hemanth Savarla.
 *
 * Licensed under the GNU General Public License v3
 *
 * This is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 */
package code.name.monkey.retromusic.fragments.player.fullisland

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.ImageButton
import android.widget.Toast
import java.io.File
import java.io.FileWriter
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import code.name.monkey.appthemehelper.util.ToolbarContentTintHelper
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.adapter.song.PlayingQueueAdapter
import code.name.monkey.retromusic.databinding.FragmentFullIslandBinding
import code.name.monkey.retromusic.extensions.applyColor
import code.name.monkey.retromusic.extensions.drawAboveSystemBars
import code.name.monkey.retromusic.extensions.getTintedDrawable
import code.name.monkey.retromusic.extensions.hide
import code.name.monkey.retromusic.extensions.setArtistLinks
import code.name.monkey.retromusic.extensions.show
import code.name.monkey.retromusic.extensions.whichFragment
import code.name.monkey.retromusic.fragments.MusicSeekSkipTouchListener
import code.name.monkey.retromusic.fragments.base.AbsPlayerFragment
import code.name.monkey.retromusic.fragments.base.goToAlbum
import code.name.monkey.retromusic.fragments.base.goToArtistByName
import code.name.monkey.retromusic.fragments.player.CoverLyricsFragment
import code.name.monkey.retromusic.fragments.player.PlayerAlbumCoverFragment
import code.name.monkey.retromusic.glide.RetroGlideExtension
import code.name.monkey.retromusic.glide.RetroGlideExtension.songCoverOptions
import code.name.monkey.retromusic.helper.MusicPlayerRemote
import code.name.monkey.retromusic.helper.MusicProgressViewUpdateHelper
import code.name.monkey.retromusic.helper.PlayPauseButtonOnClickHandler
import code.name.monkey.retromusic.model.Song
import code.name.monkey.retromusic.model.lyrics.AbsSynchronizedLyrics
import code.name.monkey.retromusic.model.lyrics.Lyrics
import code.name.monkey.retromusic.service.MusicService
import code.name.monkey.retromusic.util.LyricUtil
import code.name.monkey.retromusic.util.MusicUtil
import code.name.monkey.retromusic.util.color.FullIslandColorProcessor
import code.name.monkey.retromusic.util.color.IslandPalette
import code.name.monkey.retromusic.util.color.MediaNotificationProcessor
import com.bumptech.glide.Glide
import com.google.android.material.slider.Slider
import com.h6ah4i.android.widget.advrecyclerview.animator.DraggableItemAnimator
import com.h6ah4i.android.widget.advrecyclerview.draggable.RecyclerViewDragDropManager
import com.h6ah4i.android.widget.advrecyclerview.swipeable.RecyclerViewSwipeManager
import com.h6ah4i.android.widget.advrecyclerview.touchguard.RecyclerViewTouchActionGuardManager
import com.h6ah4i.android.widget.advrecyclerview.utils.WrapperAdapterUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FullIslandPlayerFragment : AbsPlayerFragment(R.layout.fragment_full_island),
    MusicProgressViewUpdateHelper.Callback, PopupMenu.OnMenuItemClickListener {

    private var _binding: FragmentFullIslandBinding? = null
    private val binding get() = _binding!!

    private var lastColor: Int = 0
    private var lastAccentColor: Int = Color.WHITE
    override val paletteColor: Int
        get() = lastColor

    private lateinit var progressViewUpdateHelper: MusicProgressViewUpdateHelper
    private var playingQueueAdapter: PlayingQueueAdapter? = null
    private var wrappedAdapter: RecyclerView.Adapter<*>? = null
    private var recyclerViewDragDropManager: RecyclerViewDragDropManager? = null
    private var recyclerViewSwipeManager: RecyclerViewSwipeManager? = null
    private var recyclerViewTouchActionGuardManager: RecyclerViewTouchActionGuardManager? = null

    private var lyrics: Lyrics? = null
    private var activeLineIndex: Int = -2
    private var isLyricsOverlayEnabled: Boolean = false
    private var wasLyricsOverlayActiveBeforeEditor: Boolean = false

    override fun playerToolbar(): Toolbar {
        return binding.playerToolbar
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        progressViewUpdateHelper = MusicProgressViewUpdateHelper(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentFullIslandBinding.bind(view)

        setUpPlayerToolbar()
        setupHeaderButtons()
        setupIslandInteractions()
        setUpMusicControllers()

        binding.nextSong.isSelected = true
        binding.dynamicIslandBlur.drawAboveSystemBars()

        val coverFragment: PlayerAlbumCoverFragment = whichFragment(R.id.playerAlbumCoverFragment)
        coverFragment.setCallbacks(this)
        coverFragment.removeSlideEffect()
    }

    private fun setUpPlayerToolbar() {
        binding.playerToolbar.apply {
            setNavigationOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        }
    }

    private fun setupHeaderButtons() {
        binding.albumButton.setOnClickListener { goToAlbum(requireActivity()) }
        binding.lyricsButton.setOnClickListener { toggleLyricsOverlay() }
    }

    private fun setupIslandInteractions() {
        binding.upNextPill.setOnClickListener { toggleQueueIsland() }
        binding.closeQueueButton.setOnClickListener { toggleQueueIsland(false) }
        binding.saveLyricsButton.setOnClickListener { saveEditedLyrics() }
        binding.closeLyricsEditorButton.setOnClickListener { toggleLyricsEditor(false) }
        binding.searchLyricsOnlineButton.setOnClickListener { searchLyricsOnline() }
        binding.runSearchReplaceButton.setOnClickListener { runSearchReplaceInLyrics() }
        binding.title.setOnClickListener { goToAlbum(requireActivity()) }
    }

    private fun toggleQueueIsland(show: Boolean? = null) {
        val targetVisibility = show ?: !binding.queueIslandContainer.isVisible
        if (targetVisibility) {
            binding.lyricsEditorIslandContainer.hide()
            setupQueueAdapter()
            binding.queueIslandContainer.show()
            binding.upNextIcon.animate().rotation(90f).setDuration(250).start()
        } else {
            binding.queueIslandContainer.hide()
            binding.upNextIcon.animate().rotation(0f).setDuration(250).start()
        }
    }

    private fun toggleLyricsOverlay(show: Boolean? = null) {
        val targetState = show ?: !isLyricsOverlayEnabled
        isLyricsOverlayEnabled = targetState

        binding.lyricsButton.isEnabled = true
        binding.lyricsButton.alpha = 1.0f

        if (isLyricsOverlayEnabled) {
            val isSyncedAvailable = lyrics is AbsSynchronizedLyrics && (lyrics as AbsSynchronizedLyrics).isSynchronized
            binding.lyricsEditorIslandContainer.hide()
            binding.lyricsButton.setImageResource(R.drawable.ic_lyrics)
            binding.lyricsButton.backgroundTintList = ColorStateList.valueOf(lastAccentColor)

            if (isSyncedAvailable) {
                binding.lyricsIslandContainer.show()
                binding.lyricsButton.animate().rotationBy(360f).scaleX(1.15f).scaleY(1.15f).setDuration(200).withEndAction {
                    if (_binding != null) {
                        binding.lyricsButton.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                    }
                }.start()
                activeLineIndex = -2
                updateLyricsDisplay(MusicPlayerRemote.songProgressMillis)
            } else {
                binding.lyricsIslandContainer.hide()
                Toast.makeText(requireContext(), R.string.no_lyrics_found, Toast.LENGTH_SHORT).show()
            }
        } else {
            binding.lyricsIslandContainer.hide()
            binding.lyricsButton.setImageResource(R.drawable.ic_lyrics_outline)
            binding.lyricsButton.backgroundTintList = null
            binding.lyricsButton.animate().rotationBy(-360f).setDuration(200).start()
        }
    }

    private fun toggleLyricsEditor(show: Boolean? = null) {
        val targetVisibility = show ?: !binding.lyricsEditorIslandContainer.isVisible
        if (targetVisibility) {
            wasLyricsOverlayActiveBeforeEditor = isLyricsOverlayEnabled
            binding.queueIslandContainer.hide()
            binding.lyricsIslandContainer.hide()
            binding.lyricsEditorIslandContainer.show()

            val song = MusicPlayerRemote.currentSong
            binding.lyricsEditorSubtitle.text = "${song.title} • ${song.artistName}"
            binding.lyricsTitleInput.setText(song.title)
            binding.lyricsArtistInput.setText(song.artistName)
            binding.lyricsAlbumInput.setText(song.albumName)

            lifecycleScope.launch(Dispatchers.IO) {
                val rawLyrics = try {
                    val lrcFile = LyricUtil.getSyncedLyricsFile(song)
                    val data = LyricUtil.getStringFromLrc(lrcFile)
                    data.ifEmpty { LyricUtil.getEmbeddedSyncedLyrics(song.data) ?: "" }
                } catch (e: Exception) {
                    ""
                }
                withContext(Dispatchers.Main) {
                    if (_binding == null) return@withContext
                    binding.lyricsEditText.setText(rawLyrics)
                    if (rawLyrics.isBlank()) {
                        binding.lyricsSearchPanel.show()
                    } else {
                        binding.lyricsSearchPanel.hide()
                    }
                }
            }
        } else {
            binding.lyricsEditorIslandContainer.hide()
            if (wasLyricsOverlayActiveBeforeEditor) {
                toggleLyricsOverlay(true)
            }
        }
    }

    private fun searchLyricsOnline() {
        val title = binding.lyricsTitleInput.text?.toString()?.trim() ?: ""
        val artist = binding.lyricsArtistInput.text?.toString()?.trim() ?: ""
        val album = binding.lyricsAlbumInput.text?.toString()?.trim() ?: ""
        val song = MusicPlayerRemote.currentSong

        if (title.isBlank() && artist.isBlank()) {
            Toast.makeText(requireContext(), R.string.no_search_results, Toast.LENGTH_SHORT).show()
            return
        }

        binding.searchLyricsOnlineButton.hide()
        binding.lyricsSearchProgress.show()

        lifecycleScope.launch(Dispatchers.IO) {
            val repository = code.name.monkey.retromusic.lyrics.network.LrclibRepository()
            val candidates = repository.findCandidates(
                title = title,
                artist = artist,
                album = album,
                durationSec = song.duration / 1000.0
            )

            withContext(Dispatchers.Main) {
                if (_binding == null) return@withContext
                binding.lyricsSearchProgress.hide()
                binding.searchLyricsOnlineButton.show()

                if (candidates.isNotEmpty()) {
                    val bestMatch = candidates.first()
                    val transliterated = code.name.monkey.retromusic.lyrics.transliteration.LyricsTransliterator.transliterateLrc(bestMatch.syncedLyrics)
                    binding.lyricsEditText.setText(transliterated.text)
                    Toast.makeText(requireContext(), R.string.save, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), R.string.no_search_results, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun saveEditedLyrics() {
        val newText = binding.lyricsEditText.text?.toString() ?: ""
        val song = MusicPlayerRemote.currentSong
        lifecycleScope.launch(Dispatchers.IO) {
            LyricUtil.writeLrc(song, newText)
            val parsedLyrics = Lyrics.parse(song, newText)
            withContext(Dispatchers.Main) {
                if (_binding == null) return@withContext
                lyrics = parsedLyrics
                activeLineIndex = -2
                Toast.makeText(requireContext(), R.string.save, Toast.LENGTH_SHORT).show()
                binding.lyricsEditorIslandContainer.hide()
                toggleLyricsOverlay(true)
            }
        }
    }

    private fun runSearchReplaceInLyrics() {
        val findText = binding.lyricsSearchWordInput.text?.toString() ?: ""
        val replaceText = binding.lyricsReplaceWordInput.text?.toString() ?: ""
        val currentLyrics = binding.lyricsEditText.text?.toString() ?: ""

        if (findText.isBlank()) {
            binding.lyricsSearchReplaceStatus.show()
            binding.lyricsSearchReplaceStatus.text = "Enter search word"
            return
        }

        val count = Regex(Regex.escape(findText), RegexOption.IGNORE_CASE).findAll(currentLyrics).count()
        if (count == 0) {
            binding.lyricsSearchReplaceStatus.show()
            binding.lyricsSearchReplaceStatus.text = "No occurrences found"
            return
        }

        val updatedLyrics = currentLyrics.replace(findText, replaceText, ignoreCase = true)
        binding.lyricsEditText.setText(updatedLyrics)
        binding.lyricsSearchWordInput.setText("")
        binding.lyricsReplaceWordInput.setText("")

        binding.lyricsSearchReplaceStatus.show()
        binding.lyricsSearchReplaceStatus.text = "Replaced $count occurrence(s) in current track. Scanning background files..."

        lifecycleScope.launch(Dispatchers.IO) {
            var updatedFilesCount = 0
            val currentSong = MusicPlayerRemote.currentSong

            val rootPath = File(android.os.Environment.getExternalStorageDirectory().toString() + "/RetroMusic/lyrics/")
            if (rootPath.exists() && rootPath.isDirectory) {
                val files = rootPath.listFiles() ?: emptyArray<File>()
                for (file in files) {
                    if (file.isFile && file.extension.lowercase() == "lrc") {
                        val text = LyricUtil.getStringFromLrc(file)
                        if (text.contains(findText, ignoreCase = true)) {
                            val newLrc = text.replace(findText, replaceText, ignoreCase = true)
                            val writer = FileWriter(file)
                            writer.write(newLrc)
                            writer.close()
                            updatedFilesCount++
                        }
                    }
                }
            }

            val allSongs = libraryViewModel.getSongs().value ?: emptyList()
            for (s in allSongs) {
                if (s.id == currentSong.id || s.data.isBlank()) continue
                val audioFile = File(s.data)
                if (audioFile.exists()) {
                    val embedded = LyricUtil.getEmbeddedSyncedLyrics(s.data)
                    if (embedded != null && embedded.contains(findText, ignoreCase = true)) {
                        val newEmbedded = embedded.replace(findText, replaceText, ignoreCase = true)
                        code.name.monkey.retromusic.lyrics.tagger.AudioTaggerHelper.embedLyrics(requireContext(), audioFile, newEmbedded)
                        updatedFilesCount++
                    }
                }
            }

            withContext(Dispatchers.Main) {
                if (_binding == null) return@withContext
                binding.lyricsSearchReplaceStatus.text = "Replaced $count occurrence(s) in current track & updated $updatedFilesCount other file(s) in background."
            }
        }
    }

    private fun setupQueueAdapter() {
        val queue = MusicPlayerRemote.playingQueue
        val pos = MusicPlayerRemote.position
        binding.queueCount.text = resources.getQuantityString(R.plurals.albumSongs, queue.size, queue.size)

        if (playingQueueAdapter == null) {
            playingQueueAdapter = PlayingQueueAdapter(
                requireActivity() as AppCompatActivity,
                queue.toMutableList(),
                pos,
                R.layout.item_queue
            )
            recyclerViewTouchActionGuardManager = RecyclerViewTouchActionGuardManager()
            recyclerViewDragDropManager = RecyclerViewDragDropManager()
            recyclerViewSwipeManager = RecyclerViewSwipeManager()

            val animator = DraggableItemAnimator()
            animator.supportsChangeAnimations = false

            var wrapped: RecyclerView.Adapter<*> = recyclerViewDragDropManager!!.createWrappedAdapter(playingQueueAdapter!!)
            wrapped = recyclerViewSwipeManager!!.createWrappedAdapter(wrapped)
            wrappedAdapter = wrapped

            binding.queueRecyclerView.layoutManager = LinearLayoutManager(requireContext())
            binding.queueRecyclerView.adapter = wrappedAdapter
            binding.queueRecyclerView.itemAnimator = animator

            recyclerViewTouchActionGuardManager?.attachRecyclerView(binding.queueRecyclerView)
            recyclerViewDragDropManager?.attachRecyclerView(binding.queueRecyclerView)
            recyclerViewSwipeManager?.attachRecyclerView(binding.queueRecyclerView)
        } else {
            playingQueueAdapter?.swapDataSet(queue, pos)
        }
        if (pos in queue.indices) {
            (binding.queueRecyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(pos, 0)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setUpMusicControllers() {
        binding.playPauseButton.setOnClickListener(PlayPauseButtonOnClickHandler())
        binding.nextButton.setOnTouchListener(MusicSeekSkipTouchListener(requireActivity(), true))
        binding.previousButton.setOnTouchListener(MusicSeekSkipTouchListener(requireActivity(), false))
        binding.shuffleButton.setOnClickListener { MusicPlayerRemote.toggleShuffleMode() }
        repeatButton().setOnClickListener { MusicPlayerRemote.cycleRepeatMode() }
        setupFavourite()
        setupMenu()
        setUpProgressSlider()
    }

    private fun repeatButton(): ImageButton = binding.repeatButton

    private fun setupFavourite() {
        binding.songFavourite.setOnClickListener {
            toggleFavorite(MusicPlayerRemote.currentSong)
        }
    }

    private fun setupMenu() {
        binding.playerMenu.setOnClickListener {
            val popupMenu = PopupMenu(requireContext(), it)
            popupMenu.setOnMenuItemClickListener(this)
            popupMenu.inflate(R.menu.menu_player)
            popupMenu.menu.findItem(R.id.action_toggle_favorite).isVisible = false
            popupMenu.menu.findItem(R.id.action_toggle_lyrics).isChecked = binding.lyricsIslandContainer.isVisible
            popupMenu.show()
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.now_playing -> {
                toggleQueueIsland()
                return true
            }
            R.id.action_toggle_lyrics -> {
                toggleLyricsOverlay()
                return true
            }
            R.id.action_go_to_lyrics -> {
                toggleLyricsEditor(true)
                return true
            }
        }
        return super.onMenuItemClick(item)
    }

    private fun setUpProgressSlider() {
        binding.progressSlider.addOnChangeListener(Slider.OnChangeListener { _, value, fromUser ->
            if (fromUser) {
                onUpdateProgressViews(value.toInt(), MusicPlayerRemote.songDurationMillis)
            }
        })
        binding.progressSlider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {
                progressViewUpdateHelper.stop()
            }

            override fun onStopTrackingTouch(slider: Slider) {
                MusicPlayerRemote.seekTo(slider.value.toInt())
                progressViewUpdateHelper.start()
            }
        })
    }

    override fun onUpdateProgressViews(progress: Int, total: Int) {
        binding.progressSlider.valueTo = total.toFloat()
        binding.progressSlider.value = progress.toFloat().coerceIn(
            binding.progressSlider.valueFrom,
            binding.progressSlider.valueTo
        )
        binding.songTotalTime.text = MusicUtil.getReadableDurationString(total.toLong())
        binding.songCurrentProgress.text = MusicUtil.getReadableDurationString(progress.toLong())

        updateLyricsDisplay(progress)
    }

    private fun findActiveLineIndex(timeMs: Int, syncLyrics: AbsSynchronizedLyrics): Int {
        val count = syncLyrics.lineCount
        if (count == 0) return -1
        var active = -1
        var low = 0
        var high = count - 1
        while (low <= high) {
            val mid = (low + high) / 2
            if (syncLyrics.getLineTime(mid) <= timeMs + 500) {
                active = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return active
    }

    private fun cleanLyricText(raw: String): String {
        var text = raw.trim()
        if (text.startsWith("\"") && text.endsWith("\"") && text.length >= 2) {
            text = text.substring(1, text.length - 1).trim()
        }
        if (text.startsWith("“") && text.endsWith("”") && text.length >= 2) {
            text = text.substring(1, text.length - 1).trim()
        }
        if (text == "-" || text == "—" || text == "_" || text == "..." || text.isBlank()) {
            return ""
        }
        if (!text.any { it.isLetterOrDigit() }) {
            return ""
        }
        return text
    }

    private fun updateLyricsDisplay(progressMs: Int) {
        if (_binding == null || !binding.lyricsIslandContainer.isVisible || lyrics !is AbsSynchronizedLyrics) return
        val syncLyrics = lyrics as AbsSynchronizedLyrics
        val count = syncLyrics.lineCount
        if (count == 0) return

        val idx = findActiveLineIndex(progressMs, syncLyrics)
        if (idx != activeLineIndex) {
            activeLineIndex = idx
            val prevText = if (idx > 0) cleanLyricText(syncLyrics.getLineText(idx - 1)) else ""
            val activeText = if (idx in 0 until count) cleanLyricText(syncLyrics.getLineText(idx)) else ""
            val nextText = if (idx + 1 in 0 until count) cleanLyricText(syncLyrics.getLineText(idx + 1)) else ""

            binding.lyricsPrevLine.text = prevText
            binding.lyricsActiveLine.text = activeText
            binding.lyricsNextLine.text = nextText

            val interpolator = PathInterpolator(0.2f, 0.0f, 0.0f, 1.0f)
            binding.lyricsActiveLine.alpha = 0f
            binding.lyricsActiveLine.translationY = 12f
            binding.lyricsActiveLine.animate()
                .alpha(0.94f)
                .translationY(0f)
                .setDuration(350)
                .setInterpolator(interpolator)
                .start()

            binding.lyricsNextLine.alpha = 0f
            binding.lyricsNextLine.animate()
                .alpha(0.60f)
                .setDuration(350)
                .setInterpolator(interpolator)
                .start()

            binding.lyricsPrevLine.alpha = 0f
            binding.lyricsPrevLine.animate()
                .alpha(0.35f)
                .setDuration(350)
                .setInterpolator(interpolator)
                .start()
        }
    }

    override fun onShow() {}
    override fun onHide() {}

    override fun toolbarIconColor(): Int = Color.WHITE

    override fun onColorChanged(color: MediaNotificationProcessor) {
        lastColor = color.backgroundColor
        val islandPalette = FullIslandColorProcessor.process(color)
        applyPalette(islandPalette)

        libraryViewModel.updateColor(color.backgroundColor)
        ToolbarContentTintHelper.colorizeToolbar(binding.playerToolbar, Color.WHITE, activity)
        binding.coverLyrics.getFragment<CoverLyricsFragment>().setColors(color)
    }

    fun applyPalette(palette: IslandPalette) {
        lastAccentColor = palette.accentColor
        val currentBgColor = binding.ambientGlow.backgroundTintList?.defaultColor ?: Color.TRANSPARENT
        ValueAnimator.ofObject(ArgbEvaluator(), currentBgColor, palette.bgDark).apply {
            duration = 450
            addUpdateListener { animator ->
                binding.ambientGlow.backgroundTintList =
                    ColorStateList.valueOf(animator.animatedValue as Int)
            }
            start()
        }

        val drawable = binding.dynamicIslandBlur.background as? GradientDrawable
        drawable?.setColor(palette.islandBg)

        val upNextBg = binding.upNextPill.background as? GradientDrawable
        upNextBg?.setColor(palette.islandBg)

        val queueBg = binding.queueIslandContainer.background as? GradientDrawable
        queueBg?.setColor(palette.islandBg)

        val editorBg = binding.lyricsEditorIslandContainer.background as? GradientDrawable
        editorBg?.setColor(palette.islandBg)

        binding.title.setTextColor(palette.textPrimary)
        binding.text.setTextColor(palette.textSecondary)
        binding.songCurrentProgress.setTextColor(palette.textSecondary)
        binding.songTotalTime.setTextColor(palette.textSecondary)

        binding.queueTitle.setTextColor(palette.textPrimary)
        binding.queueCount.setTextColor(palette.textSecondary)
        binding.closeQueueButton.imageTintList = ColorStateList.valueOf(palette.textPrimary)

        binding.lyricsEditorTitle.setTextColor(palette.textPrimary)
        binding.lyricsEditorSubtitle.setTextColor(palette.textSecondary)
        binding.saveLyricsButton.imageTintList = ColorStateList.valueOf(palette.textPrimary)
        binding.closeLyricsEditorButton.imageTintList = ColorStateList.valueOf(palette.textPrimary)

        val activeColor = ColorUtils.setAlphaComponent(Color.WHITE, (255 * 0.94).toInt())
        val nextColor = ColorUtils.setAlphaComponent(Color.WHITE, (255 * 0.60).toInt())
        val prevColor = ColorUtils.setAlphaComponent(Color.WHITE, (255 * 0.35).toInt())

        binding.lyricsActiveLine.setTextColor(activeColor)
        binding.lyricsNextLine.setTextColor(nextColor)
        binding.lyricsPrevLine.setTextColor(prevColor)

        binding.upNextIcon.imageTintList = ColorStateList.valueOf(palette.accentColor)

        if (binding.lyricsIslandContainer.isVisible) {
            binding.lyricsButton.backgroundTintList = ColorStateList.valueOf(palette.accentColor)
        }

        binding.playPauseButton.backgroundTintList = ColorStateList.valueOf(palette.fabColor)
        binding.playPauseButton.imageTintList = ColorStateList.valueOf(palette.fabIconColor)

        binding.progressSlider.applyColor(palette.accentColor)
        val tintList = ColorStateList.valueOf(palette.textPrimary)
        binding.songFavourite.imageTintList = tintList
        binding.playerMenu.imageTintList = tintList
        binding.previousButton.imageTintList = tintList
        binding.nextButton.imageTintList = tintList

        updateRepeatState(palette.textPrimary, palette.accentColor)
        updateShuffleState(palette.textPrimary, palette.accentColor)
    }

    private fun updateRepeatState(primaryColor: Int, accentColor: Int) {
        val disabledColor = ColorUtils.setAlphaComponent(primaryColor, 0x66)
        when (MusicPlayerRemote.repeatMode) {
            MusicService.REPEAT_MODE_NONE -> {
                binding.repeatButton.setImageResource(R.drawable.ic_repeat)
                binding.repeatButton.imageTintList = ColorStateList.valueOf(disabledColor)
            }
            MusicService.REPEAT_MODE_ALL -> {
                binding.repeatButton.setImageResource(R.drawable.ic_repeat)
                binding.repeatButton.imageTintList = ColorStateList.valueOf(accentColor)
            }
            MusicService.REPEAT_MODE_THIS -> {
                binding.repeatButton.setImageResource(R.drawable.ic_repeat_one)
                binding.repeatButton.imageTintList = ColorStateList.valueOf(accentColor)
            }
        }
    }

    private fun updateShuffleState(primaryColor: Int, accentColor: Int) {
        val disabledColor = ColorUtils.setAlphaComponent(primaryColor, 0x66)
        binding.shuffleButton.imageTintList = ColorStateList.valueOf(
            if (MusicPlayerRemote.shuffleMode == MusicService.SHUFFLE_MODE_SHUFFLE) accentColor else disabledColor
        )
    }

    override fun onFavoriteToggled() {
        toggleFavorite(MusicPlayerRemote.currentSong)
    }

    override fun toggleFavorite(song: Song) {
        super.toggleFavorite(song)
        if (song.id == MusicPlayerRemote.currentSong.id) {
            updateIsFavoriteIcon()
        }
    }

    private fun updateIsFavoriteIcon(animate: Boolean = false) {
        lifecycleScope.launch(Dispatchers.IO) {
            val isFavorite: Boolean =
                libraryViewModel.isSongFavorite(MusicPlayerRemote.currentSong.id)
            withContext(Dispatchers.Main) {
                val icon = if (animate) {
                    if (isFavorite) R.drawable.avd_favorite else R.drawable.avd_unfavorite
                } else {
                    if (isFavorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border
                }
                val drawable = requireContext().getTintedDrawable(
                    icon,
                    Color.WHITE
                )
                binding.songFavourite.apply {
                    setImageDrawable(drawable)
                    if (drawable is AnimatedVectorDrawable) {
                        drawable.start()
                    }
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        updateAlbumImage()
        updateLabel()
        updateSong()
    }

    override fun onPlayingMetaChanged() {
        super.onPlayingMetaChanged()
        updateAlbumImage()
        updateLabel()
        updateSong()
        if (_binding != null && binding.queueIslandContainer.isVisible) {
            playingQueueAdapter?.setCurrent(MusicPlayerRemote.position)
            (binding.queueRecyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(MusicPlayerRemote.position, 0)
        }
    }

    override fun onPlayStateChanged() {
        updatePlayPauseState()
    }

    private fun updatePlayPauseState() {
        if (MusicPlayerRemote.isPlaying) {
            binding.playPauseButton.setImageResource(R.drawable.ic_pause)
        } else {
            binding.playPauseButton.setImageResource(R.drawable.ic_play_arrow_white_32dp)
        }
    }

    private fun updateSong() {
        val song = MusicPlayerRemote.currentSong
        binding.title.text = song.title
        binding.text.setArtistLinks(song.artistName) { artistName ->
            goToArtistByName(requireActivity(), artistName)
        }
        updatePlayPauseState()
        updateIsFavoriteIcon()
        updateLyrics()
    }

    private fun updateLyrics() {
        lyrics = null
        activeLineIndex = -2
        if (_binding != null) {
            binding.lyricsPrevLine.text = ""
            binding.lyricsActiveLine.text = ""
            binding.lyricsNextLine.text = ""
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val song = MusicPlayerRemote.currentSong
            val loadedLyrics = try {
                val lrcFile = LyricUtil.getSyncedLyricsFile(song)
                val data = LyricUtil.getStringFromLrc(lrcFile)
                Lyrics.parse(song, data.ifEmpty { LyricUtil.getEmbeddedSyncedLyrics(song.data) })
            } catch (e: Exception) {
                null
            }
            withContext(Dispatchers.Main) {
                if (_binding == null) return@withContext
                lyrics = loadedLyrics
                val isSyncedAvailable = lyrics is AbsSynchronizedLyrics && (lyrics as AbsSynchronizedLyrics).isSynchronized
                binding.lyricsButton.isEnabled = true
                binding.lyricsButton.alpha = 1.0f

                if (isLyricsOverlayEnabled) {
                    binding.lyricsButton.setImageResource(R.drawable.ic_lyrics)
                    binding.lyricsButton.backgroundTintList = ColorStateList.valueOf(lastAccentColor)
                    if (isSyncedAvailable) {
                        binding.lyricsIslandContainer.show()
                        activeLineIndex = -2
                        updateLyricsDisplay(MusicPlayerRemote.songProgressMillis)
                    } else {
                        binding.lyricsIslandContainer.hide()
                    }
                } else {
                    binding.lyricsIslandContainer.hide()
                    binding.lyricsButton.setImageResource(R.drawable.ic_lyrics_outline)
                    binding.lyricsButton.backgroundTintList = null
                }
            }
        }
    }

    private fun updateAlbumImage() {
        val song = MusicPlayerRemote.currentSong
        Glide.with(requireActivity())
            .load(RetroGlideExtension.getSongModel(song))
            .songCoverOptions(song)
            .into(binding.albumButton)
    }

    override fun onQueueChanged() {
        super.onQueueChanged()
        if (MusicPlayerRemote.playingQueue.isNotEmpty()) {
            updateLabel()
            if (binding.queueIslandContainer.isVisible) {
                setupQueueAdapter()
            }
        }
    }

    private fun updateLabel() {
        if ((MusicPlayerRemote.playingQueue.size - 1) == (MusicPlayerRemote.position)) {
            binding.nextSong.setText(R.string.last_song)
        } else {
            val title = MusicPlayerRemote.playingQueue[MusicPlayerRemote.position + 1].title
            binding.nextSong.apply {
                text = title
                show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        progressViewUpdateHelper.start()
    }

    override fun onPause() {
        recyclerViewDragDropManager?.cancelDrag()
        super.onPause()
        progressViewUpdateHelper.stop()
    }

    override fun onDestroyView() {
        if (recyclerViewDragDropManager != null) {
            recyclerViewDragDropManager?.release()
            recyclerViewDragDropManager = null
        }
        if (recyclerViewSwipeManager != null) {
            recyclerViewSwipeManager?.release()
            recyclerViewSwipeManager = null
        }
        if (wrappedAdapter != null) {
            WrapperAdapterUtils.releaseAll(wrappedAdapter)
            wrappedAdapter = null
        }
        playingQueueAdapter = null
        super.onDestroyView()
        _binding = null
    }
}
