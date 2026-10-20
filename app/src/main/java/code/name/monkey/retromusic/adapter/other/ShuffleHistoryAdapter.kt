/*
 * Copyright (c) 2024 Retro Music Player.
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

package code.name.monkey.retromusic.adapter.other

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.recyclerview.widget.RecyclerView
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import code.name.monkey.retromusic.databinding.ItemShuffleRoundBinding
import code.name.monkey.retromusic.helper.ShuffleAuditLogger
import code.name.monkey.retromusic.helper.ShuffleAuditLogger.ShuffleRoundMetrics
import code.name.monkey.retromusic.visualizer.InteractiveShuffleArtCanvas
import java.util.Locale

class ShuffleHistoryAdapter(
    private var items: List<ShuffleRoundMetrics> = emptyList(),
    private val fragment: Fragment? = null
) : RecyclerView.Adapter<ShuffleHistoryAdapter.ViewHolder>() {

    private var expandedPosition: Int = -1

    fun swapDataset(newItems: List<ShuffleRoundMetrics>) {
        items = newItems
        expandedPosition = -1
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemShuffleRoundBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val isExpanded = position == expandedPosition
        holder.bind(items[position], isExpanded, fragment) {
            val previousExpanded = expandedPosition
            expandedPosition = if (expandedPosition == position) -1 else position
            if (previousExpanded != -1) {
                notifyItemChanged(previousExpanded)
            }
            if (expandedPosition != -1) {
                notifyItemChanged(expandedPosition)
            }
        }
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(private val binding: ItemShuffleRoundBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(
            item: ShuffleRoundMetrics,
            isExpanded: Boolean,
            fragment: Fragment?,
            onToggleExpand: () -> Unit
        ) {
            val modeStr = if (item.isEnhanced) "Enhanced" else "Normal"
            binding.roundTitle.text = "Shuffle #${item.roundNumber} • $modeStr"

            val collText = if (item.artistCollisions == 0) {
                "${item.trackCount} tracks • 0 artist collisions"
            } else {
                "${item.trackCount} tracks • ${item.artistCollisions} artist collisions [!]"
            }
            binding.roundSubtitle.text = collText

            if (item.roundNumber == 1) {
                binding.scoreBadge.text = "Baseline Shuffle"
                binding.slotMetricsText.text = "Same-Slot Matches: ---"
                binding.bigramMetricsText.text = "Repeated Transitions: ---"
            } else {
                val scoreStr = String.format(Locale.US, "Repetitiveness: %.1f%%", item.repetitivenessScore)
                binding.scoreBadge.text = scoreStr

                binding.slotMetricsText.text = String.format(
                    Locale.US,
                    "Same-Slot Matches: %d/%d (%.1f%%)",
                    item.sameSlotMatches,
                    item.sameSlotTotal,
                    item.sameSlotPct
                )

                binding.bigramMetricsText.text = String.format(
                    Locale.US,
                    "Repeated Transitions: %d/%d (%.1f%%)",
                    item.repeatedBigramPairs,
                    item.repeatedBigramTotal,
                    item.repeatedBigramPct
                )
            }

            binding.canvasContainer.isVisible = isExpanded

            if (isExpanded) {
                binding.composeHost.removeAllViews()

                val context = binding.root.context
                val composeView = ComposeView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }

                val lifecycleOwner = fragment?.viewLifecycleOwner ?: binding.root.findViewTreeLifecycleOwner()
                val savedStateOwner = fragment ?: binding.root.findViewTreeSavedStateRegistryOwner()

                if (lifecycleOwner != null) {
                    composeView.setViewTreeLifecycleOwner(lifecycleOwner)
                }
                if (savedStateOwner != null) {
                    composeView.setViewTreeSavedStateRegistryOwner(savedStateOwner)
                }

                val visualRecords = ShuffleAuditLogger.getVisualRecords()
                val currentRecord = visualRecords.find { it.runId == item.roundNumber.toLong() }
                val historicalRecords = visualRecords.filter { it.runId < item.roundNumber.toLong() }.takeLast(4).reversed()

                if (currentRecord != null) {
                    binding.playNextLegendItem.isVisible = currentRecord.manualPlayNextIndices.isNotEmpty()

                    composeView.setContent {
                        InteractiveShuffleArtCanvas(
                            currentRecord = currentRecord,
                            historicalRecords = historicalRecords,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    binding.composeHost.addView(composeView)
                }
            }

            binding.cardRoot.setOnClickListener {
                onToggleExpand()
            }
        }
    }
}
