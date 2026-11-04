/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.extensions

import android.content.Context

// ArchiveTune is fully local: there is no account or remote sync.
fun Context.isSyncEnabled(): Boolean = false

fun Context.isUserLoggedIn(): Boolean = false
