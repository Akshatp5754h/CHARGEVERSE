package com.akshat.chargeverse.ui.nav

import com.akshat.chargeverse.R
import com.akshat.chargeverse.core.icons.CvIcons

/** Top-level destinations — the 7 sections required by CHARGEVERSE design. */
enum class CvDestination(val route: String, val label: String, val iconRes: Int) {
    HOME("home", "Home", CvIcons.Home),
    ANIMATIONS("animations", "Animations", CvIcons.Animations),
    STUDIO("studio", "Studio", CvIcons.Studio),
    BATTERY("battery", "Battery", CvIcons.Battery),
    SOUNDS("sounds", "Sounds", CvIcons.Sounds),
    AUTOMATION("automation", "Auto", CvIcons.Automation),
    SETTINGS("settings", "Settings", CvIcons.Settings);

    companion object {
        fun fromRoute(route: String?): CvDestination =
            entries.firstOrNull { route == it.route } ?: HOME
    }
}
