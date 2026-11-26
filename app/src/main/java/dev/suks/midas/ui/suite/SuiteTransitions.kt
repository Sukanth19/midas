package dev.suks.midas.ui.suite

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

/** Velto's navigation transitions, shared by pupdr, Midas and Velto. */
object SuiteTransitions {
    // Tabs fade through: the old one gets out of the way fast, the new one grows in slightly.
    // The navigation default is a 700ms crossfade, which is what made switching feel sluggish.
    val tabEnter: EnterTransition =
        fadeIn(tween(200, delayMillis = 50)) + scaleIn(tween(250, delayMillis = 50, easing = FastOutSlowInEasing), initialScale = 0.96f)
    val tabExit: ExitTransition = fadeOut(tween(70))
    
    // Screens opened on top slide in from the right, and back reverses it
    val pushEnter: EnterTransition =
        slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { it / 5 } + fadeIn(tween(200))
    val pushExit: ExitTransition =
        slideOutHorizontally(tween(280, easing = FastOutSlowInEasing)) { -it / 10 } + fadeOut(tween(120))
    val popEnter: EnterTransition =
        slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { -it / 10 } + fadeIn(tween(200))
    val popExit: ExitTransition =
        slideOutHorizontally(tween(250, easing = FastOutSlowInEasing)) { it / 5 } + fadeOut(tween(150))
}
