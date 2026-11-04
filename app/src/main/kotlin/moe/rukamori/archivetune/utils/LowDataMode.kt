/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import androidx.compose.runtime.Composable

/**
 * ArchiveTune is fully local: there is no metered network connection to watch,
 * so low-data mode is always off.
 */
@Composable
fun rememberLowDataModeActive(): Boolean = false
