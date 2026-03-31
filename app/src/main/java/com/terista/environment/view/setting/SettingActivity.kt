package com.terista.environment.view.setting

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.util.Log
import com.afollestad.materialdialogs.MaterialDialog
import top.niunaijun.blackbox.BlackBoxCore
import com.terista.environment.R
import com.terista.environment.app.AppManager
import com.terista.environment.databinding.ActivitySettingBinding
import com.terista.environment.util.inflate
import com.terista.environment.util.toast
import com.terista.environment.view.base.BaseActivity
import com.terista.environment.view.gms.GmsManagerActivity

class SettingActivity : BaseActivity() {

    private val viewBinding: ActivitySettingBinding by inflate()

    companion object {
        private const val TAG = "SettingActivity"
        fun start(context: Context) {
            context.startActivity(Intent(context, SettingActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(viewBinding.root)
        viewBinding.btnBack.setOnClickListener { finish() }
        ensureAlwaysOnFeatures()
        initSwitches()
        initAboutInfo()
        initDangerZone()
    }

    /**
     * Features that must ALWAYS be active — enabled in backend on every launch.
     * Toggles for these are NOT shown in the UI.
     */
    private fun ensureAlwaysOnFeatures() {
        try {
            val loader = AppManager.mBlackBoxLoader
            // Daemon — keep virtual environment alive in background
            if (!loader.daemonEnable()) loader.invalidDaemonEnable(true)
            // Disable Flag Secure — allow screenshots inside virtual apps
            if (!loader.disableFlagSecure()) loader.invalidDisableFlagSecure(true)
            // Signature check bypass is handled at install time via BlackBox core
        } catch (e: Exception) {
            Log.e(TAG, "ensureAlwaysOnFeatures: ${e.message}")
        }
    }

    private fun initSwitches() {
        val loader = AppManager.mBlackBoxLoader
        val prefs = getSharedPreferences("TeristaSettings", MODE_PRIVATE)

        // ── Root Hide ────────────────────────────────────────────────────────
        // Hides REAL device root from virtual apps (useful if device IS rooted)
        viewBinding.switchRootHide.isChecked = loader.hideRoot()
        viewBinding.switchRootHide.setOnCheckedChangeListener { _, checked ->
            loader.invalidHideRoot(checked)
            toast(R.string.restart_module)
        }

        // ── Fake Virtual Root ────────────────────────────────────────────────
        // Simulates root INSIDE the virtual space only. Real device stays unrooted.
        viewBinding.switchFakeRoot.isChecked = loader.fakeRoot()
        viewBinding.switchFakeRoot.setOnCheckedChangeListener { _, checked ->
            loader.invalidFakeRoot(checked)
            toast(R.string.restart_module)
        }

        // ── GMS Spoofing ─────────────────────────────────────────────────────
        viewBinding.switchGmsSpoof.isChecked = BlackBoxCore.get().isSupportGms
        viewBinding.switchGmsSpoof.setOnCheckedChangeListener { _, checked ->
            if (checked) GmsManagerActivity.start(this)
            toast(R.string.restart_module)
        }

        // ── Use VPN Network ───────────────────────────────────────────────────
        viewBinding.switchVpnNetwork.isChecked = loader.useVpnNetwork()
        viewBinding.switchVpnNetwork.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                val vpnIntent = VpnService.prepare(this)
                if (vpnIntent != null) startActivity(vpnIntent)
            }
            loader.invalidUseVpnNetwork(checked)
            toast(R.string.restart_module)
        }

        // ── GPU Acceleration ─────────────────────────────────────────────────
        viewBinding.switchGpu.isChecked = prefs.getBoolean("gpu_acceleration", true)
        viewBinding.switchGpu.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("gpu_acceleration", checked).apply()
            toast(R.string.restart_module)
        }

        // ── Performance Monitor ───────────────────────────────────────────────
        viewBinding.switchPerfMonitor.isChecked = prefs.getBoolean("perf_monitor", false)
        viewBinding.switchPerfMonitor.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("perf_monitor", checked).apply()
        }

        // ── Debug Logging ─────────────────────────────────────────────────────
        viewBinding.switchDebugLogging.isChecked = prefs.getBoolean("debug_logging", false)
        viewBinding.switchDebugLogging.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("debug_logging", checked).apply()
        }
    }

    private fun initAboutInfo() {
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            viewBinding.tvVersion.text = pInfo.versionName ?: "2.0"
        } catch (e: Exception) {
            viewBinding.tvVersion.text = "2.0"
        }
        try {
            val count = BlackBoxCore.get().getInstalledPackages(0, 0)?.size ?: 0
            viewBinding.tvInstalledCount.text = count.toString()
        } catch (e: Exception) {
            viewBinding.tvInstalledCount.text = "0"
        }
    }

    private fun clearAppLogs() {
        try {
            filesDir.listFiles()?.filter {
                it.name.endsWith(".log") || it.name.contains("log")
            }?.forEach { it.delete() }
            externalCacheDir?.listFiles()?.filter {
                it.name.contains("log")
            }?.forEach { it.delete() }
        } catch (e: Exception) {
            Log.e(TAG, "clearAppLogs: ${e.message}")
        }
    }

    private fun initDangerZone() {
        viewBinding.btnClearLogs.setOnClickListener {
            MaterialDialog(this).show {
                title(text = "Clear All Logs")
                message(text = "This will delete all stored system logs. Continue?")
                positiveButton(text = "Clear") {
                    clearAppLogs()
                    toast("Logs cleared")
                }
                negativeButton(res = R.string.cancel)
            }
        }

        viewBinding.btnResetSettings.setOnClickListener {
            MaterialDialog(this).show {
                title(text = "Reset Settings")
                message(text = "All settings will be reset to defaults. This cannot be undone.")
                positiveButton(text = "Reset") {
                    try {
                        getSharedPreferences("TeristaSettings", MODE_PRIVATE).edit().clear().apply()
                        val loader = AppManager.mBlackBoxLoader
                        loader.invalidHideRoot(false)
                        loader.invalidFakeRoot(true)
                        loader.invalidUseVpnNetwork(false)
                        // Always-on features stay enabled
                        loader.invalidDaemonEnable(true)
                        loader.invalidDisableFlagSecure(true)
                        initSwitches()
                        toast("Settings reset")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error resetting settings: ${e.message}")
                    }
                }
                negativeButton(res = R.string.cancel)
            }
        }

        viewBinding.btnSendLogs.setOnClickListener {
            BlackBoxCore.get().sendLogs(
                "Manual Log Upload from Settings",
                true,
                object : BlackBoxCore.LogSendListener {
                    override fun onSuccess() { runOnUiThread { toast("Logs sent successfully") } }
                    override fun onFailure(error: String?) { runOnUiThread { toast("Failed to send logs") } }
                }
            )
            toast("Sending logs...")
        }
    }
}
