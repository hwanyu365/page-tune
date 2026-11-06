package com.hwanyu365.pagetune

import android.app.AlertDialog
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import com.hwanyu365.pagetune.databinding.ActivityMainBinding
import com.hwanyu365.pagetune.ui.fragment.PluginsFragment
import com.hwanyu365.pagetune.ui.fragment.WebPagesFragment
import com.hwanyu365.pagetune.util.enableDarkEdgeToEdge
import com.hwanyu365.pagetune.util.padForInsets

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableDarkEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.padForInsets()
        setSupportActionBar(binding.toolbar)

        binding.viewPager.adapter = MainPagerAdapter(supportFragmentManager)
        binding.tabLayout.setupWithViewPager(binding.viewPager)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId != R.id.action_licenses) return super.onOptionsItemSelected(item)
        val text = resources.openRawResource(R.raw.open_source_licenses).bufferedReader().use { it.readText() }
        AlertDialog.Builder(this)
            .setTitle(R.string.menu_licenses)
            .setMessage(text)
            .setPositiveButton(R.string.ok, null)
            .show()
        return true
    }

    private inner class MainPagerAdapter(fm: FragmentManager) : FragmentPagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
        override fun getCount(): Int = 2

        override fun getItem(position: Int): Fragment =
            if (position == 0) WebPagesFragment() else PluginsFragment()

        override fun getPageTitle(position: Int): CharSequence =
            getString(if (position == 0) R.string.tab_webpages else R.string.tab_tunes)
    }
}
