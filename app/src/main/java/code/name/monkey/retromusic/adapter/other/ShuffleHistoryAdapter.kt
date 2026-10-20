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
import androidx.recyclerview.widget.RecyclerView
import code.name.monkey.retromusic.databinding.ItemShuffleRoundBinding
import code.name.monkey.retromusic.helper.ShuffleAuditLogger.ShuffleRoundMetrics
import java.util.Locale

class ShuffleHistoryAdapter(
    private var items: List<ShuffleRoundMetrics> = emptyList()
) : RecyclerView.Adapter<ShuffleHistoryAdapter.ViewHolder>() {

    fun swapDataset(newItems: List<ShuffleRoundMetrics>) {
        items = newItems
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
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(private val binding: ItemShuffleRoundBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ShuffleRoundMetrics) {
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
        }
    }
}
