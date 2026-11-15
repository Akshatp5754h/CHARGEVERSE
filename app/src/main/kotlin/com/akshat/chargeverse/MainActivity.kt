package com.akshat.chargeverse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.akshat.chargeverse.core.theme.ChargeverseTheme
import com.akshat.chargeverse.core.theme.NeonBlue
import com.akshat.chargeverse.core.theme.NeonPurple
import com.akshat.chargeverse.core.theme.Void
import com.akshat.chargeverse.ui.animations.AnimationsScreen
import com.akshat.chargeverse.ui.automation.AutomationScreen
import com.akshat.chargeverse.ui.battery.BatteryScreen
import com.akshat.chargeverse.ui.home.HomeScreen
import com.akshat.chargeverse.ui.nav.CvDestination
import com.akshat.chargeverse.ui.settings.SettingsScreen
import com.akshat.chargeverse.ui.sounds.SoundsScreen
import com.akshat.chargeverse.ui.studio.StudioScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChargeverseTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val current = CvDestination.fromRoute(backStack?.destination?.route)

    // §4: 280 ms fade + 8 dp slide-up enter; 200 ms inverse on exit
    // (and the same treatment for back-navigation pops).
    val navSlide = with(LocalDensity.current) { 8.dp.roundToPx() }

    Box(Modifier.fillMaxSize().background(Void)) {
        NavHost(
            navController = navController,
            startDestination = CvDestination.HOME.route,
            modifier = Modifier.fillMaxSize().background(Void),
            enterTransition = {
                fadeIn(tween(280)) +
                    slideInVertically(tween(280, easing = FastOutSlowInEasing)) { navSlide }
            },
            exitTransition = {
                fadeOut(tween(200)) +
                    slideOutVertically(tween(200, easing = FastOutSlowInEasing)) { navSlide }
            },
            popEnterTransition = {
                fadeIn(tween(280)) +
                    slideInVertically(tween(280, easing = FastOutSlowInEasing)) { navSlide }
            },
            popExitTransition = {
                fadeOut(tween(200)) +
                    slideOutVertically(tween(200, easing = FastOutSlowInEasing)) { navSlide }
            },
        ) {
            composable(CvDestination.HOME.route) { HomeScreen(onNavigate = { navController.navigate(it.route) }) }
            composable(CvDestination.ANIMATIONS.route) { AnimationsScreen() }
            composable(CvDestination.STUDIO.route) { StudioScreen() }
            composable(CvDestination.BATTERY.route) { BatteryScreen() }
            composable(CvDestination.SOUNDS.route) { SoundsScreen() }
            composable(CvDestination.AUTOMATION.route) { AutomationScreen() }
            composable(CvDestination.SETTINGS.route) { SettingsScreen() }
        }
        CvBottomBar(
                current = current,
                onSelect = { dest ->
                    navController.navigate(dest.route) {
                        popUpTo(CvDestination.HOME.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
    }
}

/** Custom 7-item bottom bar — fits premium 1080px-wide phone displays. */
@Composable
fun CvBottomBar(
    current: CvDestination,
    onSelect: (CvDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val navPadding = WindowInsets.navigationBars.asPaddingValues()
    Column(modifier) {
        Box(
            Modifier
                .padding(horizontal = 10.dp)
                .padding(bottom = navPadding.calculateBottomPadding() + 8.dp)
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(NeonBlue.copy(alpha = 0.10f), NeonPurple.copy(alpha = 0.10f))))
        ) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CvDestination.entries.forEach { dest ->
                    val selected = dest == current
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clickable { onSelect(dest) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(Modifier.height(10.dp))
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (selected) Brush.linearGradient(listOf(NeonBlue.copy(alpha = 0.25f), NeonPurple.copy(alpha = 0.25f)))
                                    else Brush.linearGradient(listOf(androidx.compose.ui.graphics.Color.Transparent, androidx.compose.ui.graphics.Color.Transparent))
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(dest.iconRes),
                                contentDescription = dest.label,
                                tint = if (selected) NeonBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(21.dp),
                            )
                        }
                        Text(
                            text = dest.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) NeonBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
