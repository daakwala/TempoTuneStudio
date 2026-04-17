package com.tempotunestudio.ui.home

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.tempotunestudio.R
import com.tempotunestudio.databinding.FragmentHomeBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HomeViewModel by viewModels()

    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        viewLifecycleOwner.lifecycleScope.launch {
            setValidating(true)
            val error = withContext(Dispatchers.IO) { audioValidationError(uri) }
            setValidating(false)
            if (error != null) {
                Snackbar.make(binding.root, error, Snackbar.LENGTH_LONG).show()
            } else {
                val bundle = Bundle().apply { putString("videoUri", uri.toString()) }
                findNavController().navigate(R.id.action_home_to_editor, bundle)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.etUrl.doAfterTextChanged { text ->
            viewModel.validateUrl(text?.toString() ?: "")
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.urlState.collect { state ->
                when (state) {
                    is UrlState.Empty -> {
                        binding.tilUrl.error = null
                        binding.btnLoad.isEnabled = false
                    }
                    is UrlState.Valid -> {
                        binding.tilUrl.error = null
                        binding.btnLoad.isEnabled = true
                    }
                    is UrlState.Invalid -> {
                        binding.tilUrl.error = state.reason
                        binding.btnLoad.isEnabled = false
                    }
                }
            }
        }

        binding.btnLoad.setOnClickListener {
            val url = binding.etUrl.text?.toString()?.trim() ?: return@setOnClickListener
            val bundle = Bundle().apply { putString("videoUrl", url) }
            findNavController().navigate(R.id.action_home_to_editor, bundle)
        }

        binding.btnPaste.setOnClickListener {
            val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                    as android.content.ClipboardManager
            val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
            if (!text.isNullOrBlank()) binding.etUrl.setText(text)
        }

        binding.btnPickFile.setOnClickListener {
            pickFile.launch(arrayOf("*/*"))
        }
    }

    private fun setValidating(active: Boolean) {
        binding.btnPickFile.isEnabled = !active
        binding.layoutValidating.visibility = if (active) View.VISIBLE else View.GONE
    }

    /**
     * Returns null if the file has an audio track, or an error string if it does not
     * (or cannot be read). Must be called on a background thread.
     */
    private fun audioValidationError(uri: Uri): String? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(requireContext(), uri)
            val hasAudio = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO
            ) == "yes"
            if (hasAudio) null else getString(R.string.error_no_audio)
        } catch (e: Exception) {
            getString(R.string.error_unreadable)
        } finally {
            retriever.release()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
