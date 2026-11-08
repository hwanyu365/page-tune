package com.hwanyu365.pagetune

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayoutMediator
import com.hwanyu365.pagetune.databinding.ActivityMainBinding
import com.hwanyu365.pagetune.ui.showIntroIfNeeded
import com.hwanyu365.pagetune.ui.fragment.PluginsFragment
import com.hwanyu365.pagetune.ui.fragment.TabPage
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

        binding.viewPager.adapter = MainPagerAdapter()
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.setText(if (position == 0) R.string.tab_webpages else R.string.tab_tunes)
        }.attach()
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                supportFragmentManager.fragments.filterIsInstance<TabPage>()
                    .filterNot { it.javaClass == tabClass(position) }
                    .forEach { it.onTabLeft() }
            }
        })
        showIntroIfNeeded(this)
    }

    private fun tabClass(position: Int): Class<out Fragment> =
        if (position == 0) WebPagesFragment::class.java else PluginsFragment::class.java

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId != R.id.action_licenses) return super.onOptionsItemSelected(item)
        val text = resources.openRawResource(R.raw.open_source_licenses).bufferedReader().use { it.readText() }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.menu_licenses)
            .setMessage(text)
            .setPositiveButton(R.string.ok, null)
            .show()
        return true
    }

    private inner class MainPagerAdapter : FragmentStateAdapter(this) {
        override fun getItemCount(): Int = 2

        override fun createFragment(position: Int): Fragment =
            if (position == 0) WebPagesFragment() else PluginsFragment()
    }
}
