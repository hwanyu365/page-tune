package com.hwanyu365.pagetune

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import com.hwanyu365.pagetune.ui.fragment.PluginsFragment
import com.hwanyu365.pagetune.ui.fragment.WebPagesFragment
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        view_pager.adapter = MainPagerAdapter(supportFragmentManager)
        tab_layout.setupWithViewPager(view_pager)
    }

    private inner class MainPagerAdapter(fm: FragmentManager) : FragmentPagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
        override fun getCount(): Int = 2

        override fun getItem(position: Int): Fragment =
            if (position == 0) WebPagesFragment() else PluginsFragment()

        override fun getPageTitle(position: Int): CharSequence =
            getString(if (position == 0) R.string.tab_webpages else R.string.tab_plugins)
    }
}
