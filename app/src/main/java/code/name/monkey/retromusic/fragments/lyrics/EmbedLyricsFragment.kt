package code.name.monkey.retromusic.fragments.lyrics

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import code.name.monkey.retromusic.lyrics.EmbedLyricsViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

class EmbedLyricsFragment : Fragment() {

    private val viewModel: EmbedLyricsViewModel by viewModel()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                EmbedLyricsScreen(
                    viewModel = viewModel,
                    onBackClick = { findNavController().navigateUp() }
                )
            }
        }
    }
}
