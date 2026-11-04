/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.viewmodels.MainMenuDestination

@Composable
fun TopAppBarOverflowMenu(
    expanded: Boolean,
    hazeState: HazeState,
    pureBlack: Boolean,
    onDismissRequest: () -> Unit,
    onSelect: (MainMenuDestination) -> Unit,
) {
    val contentColor = if (pureBlack) Color.White else MaterialTheme.colorScheme.onSurface
    BlurredOverflowMenu(
        expanded = expanded,
        hazeState = hazeState,
        pureBlack = pureBlack,
        onDismissRequest = onDismissRequest,
    ) {
        OverflowAction(
            label = stringResource(R.string.history),
            iconRes = R.drawable.history,
            contentColor = contentColor,
            onClick = remember(onSelect) { { onSelect(MainMenuDestination.History) } },
        )
        OverflowMenuDivider()
        OverflowAction(
            label = stringResource(R.string.settings),
            iconRes = R.drawable.settings,
            contentColor = contentColor,
            onClick = remember(onSelect) { { onSelect(MainMenuDestination.Settings) } },
        )
    }
}

@Composable
private fun OverflowAction(
    label: String,
    @DrawableRes iconRes: Int,
    contentColor: Color,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        text = { Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        leadingIcon = {
            Icon(painterResource(iconRes), null, modifier = Modifier.size(24.dp))
        },
        colors = MenuDefaults.itemColors(textColor = contentColor, leadingIconColor = contentColor),
        onClick = onClick,
    )
}
