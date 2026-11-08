package com.hwanyu365.pagetune

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hwanyu365.pagetune.databinding.ActivitySettingsBinding
import com.hwanyu365.pagetune.util.enableDarkEdgeToEdge
import com.hwanyu365.pagetune.util.openInBrowser
import com.hwanyu365.pagetune.util.padForInsets

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableDarkEdgeToEdge()
        val binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.padForInsets()
        binding.toolbar.setTitle(R.string.settings)
        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
        binding.toolbar.setNavigationContentDescription(R.string.quick_back)
        binding.toolbar.setNavigationOnClickListener { finish() }

        @Suppress("DEPRECATION")
        val version = packageManager.getPackageInfo(packageName, 0).versionName
        binding.tvAppVersion.text = getString(R.string.settings_app_version, getString(R.string.app_name), version)

        binding.itemLicenses.setOnClickListener {
            val text = resources.openRawResource(R.raw.open_source_licenses).bufferedReader().use { it.readText() }
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.menu_licenses)
                .setMessage(text)
                .setPositiveButton(R.string.ok, null)
                .show()
        }
        binding.itemPrivacy.setOnClickListener {
            val url = getString(R.string.privacy_policy_url)
            if (!openInBrowser(url)) {
                MaterialAlertDialogBuilder(this)
                    .setMessage(getString(R.string.settings_no_browser, url))
                    .setPositiveButton(R.string.ok, null)
                    .show()
            }
        }
    }
}
