package dev.suks.midas.ui.suite

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

data class NavBarItem(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val selected: Boolean,
    val onClick: () -> Unit
)

/**
 * Velto's floating pill navigation, shared by pupdr, Midas and Velto.
 * The selected tab grows into a filled pill with its label, the rest
 * stay as icons; switching springs the pill across and gives the new icon a little pop.
 */
@Composable
fun SuiteNavBar(items: List<NavBarItem>, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 8.dp,
            tonalElevation = 3.dp
        ) {
            Row(
                Modifier
                    .padding(6.dp)
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { NavPill(it) }
            }
        }
    }
}

@Composable
private fun NavPill(item: NavBarItem) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val springy = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
    val container by animateColorAsState(
        if (item.selected) scheme.primary else scheme.surfaceContainerHigh,
        spring(stiffness = Spring.StiffnessMediumLow),
        label = "pill"
    )
    val content by animateColorAsState(
        if (item.selected) scheme.onPrimary else scheme.onSurfaceVariant,
        spring(stiffness = Spring.StiffnessMediumLow),
        label = "pillContent"
    )
    // A quick squash-and-stretch on the icon when its tab is picked
    val pop = remember { Animatable(1f) }
    LaunchedEffect(item.selected) {
        if (item.selected) {
            pop.snapTo(0.7f)
            pop.animateTo(1f, springy)
        }
    }

    Row(
        Modifier
            .height(52.dp)
            .clip(CircleShape)
            .background(container)
            .selectable(
                selected = item.selected,
                onClick = {
                    if (!item.selected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    item.onClick()
                },
                role = Role.Tab,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple()
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (item.selected) item.selectedIcon else item.icon,
            contentDescription = if (item.selected) null else item.label,
            tint = content,
            modifier = Modifier.size(24.dp).scale(pop.value)
        )
        AnimatedVisibility(
            visible = item.selected,
            enter = expandHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
            exit = shrinkHorizontally(spring(stiffness = Spring.StiffnessMedium)) + fadeOut()
        ) {
            Text(
                item.label,
                style = MaterialTheme.typography.labelLarge,
                color = content,
                maxLines = 1,
                modifier = Modifier.padding(start = Spacing.sm)
            )
        }
    }
}
