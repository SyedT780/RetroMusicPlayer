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
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.MenuItem
import android.view.View
import android.widget.ImageButton
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.lifecycleScope
import code.name.monkey.appthemehelper.util.ToolbarContentTintHelper
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.databinding.FragmentFullIslandBinding
import code.name.monkey.retromusic.extensions.applyColor
import code.name.monkey.retromusic.extensions.drawAboveSystemBars
import code.name.monkey.retromusic.extensions.getTintedDrawable
import code.name.monkey.retromusic.extensions.hide
import code.name.monkey.retromusic.extensions.show
import code.name.monkey.retromusic.extensions.whichFragment
import code.name.monkey.retromusic.fragments.base.AbsPlayerFragment
import code.name.monkey.retromusic.fragments.base.goToAlbum
import code.name.monkey.retromusic.fragments.base.goToArtist
import code.name.monkey.retromusic.fragments.player.CoverLyricsFragment
import code.name.monkey.retromusic.fragments.player.PlayerAlbumCoverFragment
import code.name.monkey.retromusic.glide.RetroGlideExtension
import code.name.monkey.retromusic.glide.RetroGlideExtension.artistImageOptions
import code.name.monkey.retromusic.helper.MusicPlayerRemote
import code.name.monkey.retromusic.helper.MusicProgressViewUpdateHelper
import code.name.monkey.retromusic.helper.PlayPauseButtonOnClickHandler
import code.name.monkey.retromusic.model.Song
import code.name.monkey.retromusic.service.MusicService
import code.name.monkey.retromusic.util.MusicUtil
import code.name.monkey.retromusic.util.PreferenceUtil
import code.name.monkey.retromusic.util.color.FullIslandColorProcessor
import code.name.monkey.retromusic.util.color.IslandPalette
import code.name.monkey.retromusic.util.color.MediaNotificationProcessor
import com.bumptech.glide.Glide
import com.google.android.material.slider.Slider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FullIslandPlayerFragment : AbsPlayerFragment(R.layout.fragment_full_island),
    MusicProgressViewUpdateHelper.Callback, PopupMenu.OnMenuItemClickListener {

    private var _binding: FragmentFullIslandBinding? = null
    private val binding get() = _binding!!

    private var lastColor: Int = 0
    override val paletteColor: Int
        get() = lastColor

    private lateinit var progressViewUpdateHelper: MusicProgressViewUpdateHelper

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
        setupArtist()
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

    private fun setupArtist() {
        binding.artistImage.setOnClickListener { goToArtist(mainActivity) }
    }

    private fun setupIslandInteractions() {
        binding.upNextPill.setOnClickListener { goToAlbum(requireActivity()) }
        binding.title.setOnClickListener { goToAlbum(requireActivity()) }
        binding.text.setOnClickListener { goToArtist(requireActivity()) }
    }

    private fun setUpMusicControllers() {
        binding.playPauseButton.setOnClickListener(PlayPauseButtonOnClickHandler())
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
            popupMenu.menu.findItem(R.id.action_toggle_lyrics).isChecked = PreferenceUtil.showLyrics
            popupMenu.show()
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
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
        drawable?.setStroke(
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1.2f, resources.displayMetrics).toInt(),
            palette.strokeGlowColor
        )

        binding.title.setTextColor(palette.textPrimary)
        binding.text.setTextColor(palette.textSecondary)
        binding.songCurrentProgress.setTextColor(palette.textSecondary)
        binding.songTotalTime.setTextColor(palette.textSecondary)

        binding.upNextDot.backgroundTintList = ColorStateList.valueOf(palette.accentColor)

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
        updateArtistImage()
        updateLabel()
        updateSong()
    }

    override fun onPlayingMetaChanged() {
        super.onPlayingMetaChanged()
        updateArtistImage()
        updateLabel()
        updateSong()
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
        binding.text.text = song.artistName
        updatePlayPauseState()
        updateIsFavoriteIcon()
    }

    private fun updateArtistImage() {
        libraryViewModel.artist(MusicPlayerRemote.currentSong.artistId)
            .observe(viewLifecycleOwner) { artist ->
                if (artist.id != -1L) {
                    Glide.with(requireActivity())
                        .load(RetroGlideExtension.getArtistModel(artist))
                        .artistImageOptions(artist)
                        .into(binding.artistImage)
                }
            }
    }

    override fun onQueueChanged() {
        super.onQueueChanged()
        if (MusicPlayerRemote.playingQueue.isNotEmpty()) updateLabel()
    }

    private fun updateLabel() {
        if ((MusicPlayerRemote.playingQueue.size - 1) == (MusicPlayerRemote.position)) {
            binding.nextSongLabel.setText(R.string.last_song)
            binding.nextSong.hide()
        } else {
            val title = MusicPlayerRemote.playingQueue[MusicPlayerRemote.position + 1].title
            binding.nextSongLabel.setText(R.string.next_song)
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
        super.onPause()
        progressViewUpdateHelper.stop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
