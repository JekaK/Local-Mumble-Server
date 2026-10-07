package ua.school.localmumble.presentation.main

import android.os.Bundle
import android.view.ViewGroup
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commit
import ua.school.localmumble.R
import ua.school.localmumble.presentation.server.ServerFragment

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        setContentView(FragmentContainerView(this).apply {
            id = R.id.fragment_container
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        })
        if (savedInstanceState == null) {
            supportFragmentManager.commit { setReorderingAllowed(true); replace(R.id.fragment_container, ServerFragment()) }
        }
    }
}
