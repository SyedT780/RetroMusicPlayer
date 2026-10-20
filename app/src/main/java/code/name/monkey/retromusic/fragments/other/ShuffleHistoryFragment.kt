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

package code.name.monkey.retromusic.fragments.other

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.adapter.other.ShuffleHistoryAdapter
import code.name.monkey.retromusic.databinding.FragmentShuffleHistoryBinding
import code.name.monkey.retromusic.extensions.materialDialog
import code.name.monkey.retromusic.helper.ShuffleAuditLogger
import java.util.Locale

class ShuffleHistoryFragment : Fragment(R.layout.fragment_shuffle_history) {

    private var _binding: FragmentShuffleHistoryBinding? = null
    private val binding get() = _binding!!

    private var adapter: ShuffleHistoryAdapter? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentShuffleHistoryBinding.bind(view)

        adapter = ShuffleHistoryAdapter(fragment = this)
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.btnClearHistory.setOnClickListener {
            showClearHistoryDialog()
        }

        binding.btnViewReport.setOnClickListener {
            showRawReportDialog()
        }

        loadHistory()
    }

    private fun loadHistory() {
        val summary = ShuffleAuditLogger.getAuditSummary()
        val hasHistory = summary.totalShuffles > 0

        binding.summaryCard.isVisible = hasHistory
        binding.recyclerView.isVisible = hasHistory
        binding.emptyState.isVisible = !hasHistory

        if (hasHistory) {
            binding.summaryTitle.text = String.format(
                Locale.US,
                "Session Summary • %d Shuffles",
                summary.totalShuffles
            )
            binding.summaryMode.text = "Engine Mode: ${summary.activeMode}"
            binding.summaryStats.text = String.format(
                Locale.US,
                "Cumulative Slot Overlap: %.1f%% • Transition Overlap: %.1f%%",
                summary.cumulativeSlotPct,
                summary.cumulativeBigramPct
            )

            adapter?.swapDataset(summary.rounds.reversed())
        }
    }

    private fun showClearHistoryDialog() {
        materialDialog().show {
            title(res = R.string.shuffle_history_clear)
            message(text = "Are you sure you want to clear all recorded shuffle history?")
            positiveButton(android.R.string.ok) {
                ShuffleAuditLogger.resetHistory()
                loadHistory()
            }
            negativeButton(R.string.action_cancel)
        }
    }

    private fun showRawReportDialog() {
        val summary = ShuffleAuditLogger.getAuditSummary()
        val report = summary.rawReportText.ifEmpty { "No shuffle report generated yet." }

        materialDialog().show {
            title(res = R.string.shuffle_history_view_report)
            message(text = report)
            positiveButton(android.R.string.ok)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
