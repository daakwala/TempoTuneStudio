package com.tempotunestudio.ui.editor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.tempotunestudio.databinding.FragmentEditorBinding
import com.tempotunestudio.processing.ProcessingState
import com.tempotunestudio.utils.FileUtils
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.pow

class EditorFragment : Fragment() {

    private var _binding: FragmentEditorBinding? = null
    private val binding get() = _binding!!
    private val viewModel: EditorViewModel by viewModels()
    private var player: ExoPlayer? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupPlayer()
        setupSliders()
        setupButtons()
        observeState()

        if (viewModel.localVideoPath.value == null) {
            val videoUri = arguments?.getString("videoUri")
            val videoUrl = arguments?.getString("videoUrl")
            when {
                videoUri != null -> viewModel.loadLocalVideo(videoUri)
                videoUrl != null -> viewModel.loadVideo(videoUrl)
                else -> return
            }
        }
    }

    private fun setupPlayer() {
        player = ExoPlayer.Builder(requireContext()).build()
        binding.playerView.player = player
    }

    private fun setupSliders() {
        binding.sliderPitch.addOnChangeListener(Slider.OnChangeListener { _, value, _ ->
            viewModel.setPitch(value)
            val label = if (value >= 0) "+%.1f st".format(value) else "%.1f st".format(value)
            binding.tvPitchValue.text = label
        })

        binding.sliderTempo.addOnChangeListener(Slider.OnChangeListener { _, value, _ ->
            viewModel.setTempo(value)
            binding.tvTempoValue.text = "%.0f%%".format(value * 100)
        })
    }

    private fun setupButtons() {
        binding.btnExport.setOnClickListener { viewModel.exportVideo() }

        binding.btnResetPitch.setOnClickListener {
            binding.sliderPitch.value = 0f
            viewModel.setPitch(0f)
        }

        binding.btnResetTempo.setOnClickListener {
            binding.sliderTempo.value = 1f
            viewModel.setTempo(1f)
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.localVideoPath.collect { path ->
                if (path != null) {
                    player?.apply {
                        setMediaItem(MediaItem.fromUri(android.net.Uri.parse(path)))
                        prepare()
                        playWhenReady = true  // start playing as soon as buffered
                    }
                }
            }
        }

        // Keep preview pitch/tempo in sync with sliders
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.pitch.collect { semitones ->
                val pitchFactor = 2.0.pow(semitones / 12.0).toFloat()
                val currentSpeed = viewModel.tempo.value
                player?.playbackParameters = PlaybackParameters(currentSpeed, pitchFactor)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.tempo.collect { speed ->
                val semitones = viewModel.pitch.value
                val pitchFactor = 2.0.pow(semitones / 12.0).toFloat()
                player?.playbackParameters = PlaybackParameters(speed, pitchFactor)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.processingState.collect { state ->
                when (state) {
                    is ProcessingState.Idle -> showIdle()
                    is ProcessingState.Downloading -> showDownloading(state.progress)
                    is ProcessingState.Processing -> showProcessing(state.progress, state.message)
                    is ProcessingState.Success -> showSuccess(state.outputPath)
                    is ProcessingState.Error -> showError(state.message)
                }
            }
        }
    }

    private fun showIdle() {
        binding.progressBar.visibility = View.GONE
        binding.tvStatus.visibility = View.GONE
        binding.btnExport.isEnabled = viewModel.localVideoPath.value != null
        binding.controlsCard.alpha = 1f
    }

    private fun showDownloading(progress: Int) {
        binding.progressBar.visibility = View.VISIBLE
        binding.progressBar.progress = progress
        binding.tvStatus.visibility = View.VISIBLE
        binding.tvStatus.text = "Downloading… $progress%"
        binding.btnExport.isEnabled = false
        binding.controlsCard.alpha = 0.5f
    }

    private fun showProcessing(progress: Int, message: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.progressBar.progress = progress
        binding.tvStatus.visibility = View.VISIBLE
        binding.tvStatus.text = message
        binding.btnExport.isEnabled = false
        binding.controlsCard.alpha = 0.5f
    }

    private fun showSuccess(outputPath: String) {
        binding.progressBar.visibility = View.GONE
        binding.tvStatus.visibility = View.GONE
        binding.btnExport.isEnabled = true
        binding.controlsCard.alpha = 1f

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Export Complete")
            .setMessage("Video saved to your Movies gallery.")
            .setPositiveButton("Share") { _, _ ->
                startActivity(FileUtils.buildShareIntent(requireContext(), outputPath))
                viewModel.resetState()
            }
            .setNegativeButton("Done") { _, _ -> viewModel.resetState() }
            .show()
    }

    private fun showError(message: String) {
        binding.progressBar.visibility = View.GONE
        binding.tvStatus.visibility = View.GONE
        binding.btnExport.isEnabled = viewModel.localVideoPath.value != null
        binding.controlsCard.alpha = 1f
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
        viewModel.resetState()
    }

    override fun onStart() {
        super.onStart()
        player?.play()
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        player?.release()
        player = null
        _binding = null
    }
}
