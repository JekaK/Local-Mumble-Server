package ua.school.localmumble.presentation.server

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ua.school.localmumble.LocalMumbleApplication
import ua.school.localmumble.R
import ua.school.localmumble.presentation.theme.LocalMumbleTheme

class ServerFragment : Fragment() {
    private val viewModel: ServerViewModel by viewModels {
        viewModelFactory {
            initializer {
                val app = requireActivity().application as LocalMumbleApplication
                ServerViewModel(app.container.serverRepository, createSavedStateHandle())
            }
        }
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(requireContext()).apply {
            id = R.id.server_compose_view
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                LocalMumbleTheme {
                    ServerScreen(
                        state = state,
                        onInput = viewModel::update,
                        onStart = viewModel::start,
                        onStop = viewModel::stop,
                        onShowLogs = viewModel::showLogs,
                        onDismissLogs = viewModel::dismissLogs,
                        onDismissFailure = viewModel::dismissFailure,
                        onCopy = ::copy,
                        onHotspotSettings = ::openHotspotSettings,
                    )
                }
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (savedInstanceState == null && Build.VERSION.SDK_INT >= 33 &&
            requireContext().checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun copy(text: String) {
        requireContext().getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), text))
        Toast.makeText(requireContext(), R.string.copied, Toast.LENGTH_SHORT).show()
    }

    private fun openHotspotSettings() {
        try { startActivity(Intent("android.settings.TETHER_SETTINGS")) }
        catch (_: RuntimeException) {
            try { startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) }
            catch (_: RuntimeException) {
                Toast.makeText(requireContext(), R.string.hotspot_fallback, Toast.LENGTH_LONG).show()
            }
        }
    }
}
