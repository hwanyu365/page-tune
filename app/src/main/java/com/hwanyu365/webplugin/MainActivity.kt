package com.hwanyu365.webplugin

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import com.hwanyu365.webplugin.ui.fragment.PluginsFragment
import com.hwanyu365.webplugin.ui.fragment.WebPagesFragment
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        val adapter = MainPagerAdapter(supportFragmentManager)
        adapter.addFragment(WebPagesFragment(), getString(R.string.tab_webpages))
        adapter.addFragment(PluginsFragment(), getString(R.string.tab_plugins))

        view_pager.adapter = adapter
        tab_layout.setupWithViewPager(view_pager)
    }

    private class MainPagerAdapter(fm: FragmentManager) : FragmentPagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
        private val fragmentList = mutableListOf<Fragment>()
        private val fragmentTitleList = mutableListOf<String>()

        fun addFragment(fragment: Fragment, title: String) {
            fragmentList.add(fragment)
            fragmentTitleList.add(title)
        }

        override fun getItem(position: Int): Fragment = fragmentList[position]

        override fun getCount(): Int = fragmentList.size

        override fun getPageTitle(position: Int): CharSequence? = fragmentTitleList[position]
    }
}
