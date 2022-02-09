package com.neeva.app.appnav

import androidx.compose.animation.AnimatedContentScope.SlideDirection
import androidx.compose.animation.ExperimentalAnimationApi
import java.lang.IllegalArgumentException

/** Identifiers for the possible destinations a user can be sent to via the Navigation library. */
@OptIn(ExperimentalAnimationApi::class)
enum class AppNavDestination(
    val parent: AppNavDestination? = null,
    val fadesOut: Boolean = false,
    val slidesOutToward: SlideDirection? = null
) {
    BROWSER,

    ADD_TO_SPACE(parent = BROWSER, slidesOutToward = SlideDirection.Down),
    NEEVA_MENU(parent = BROWSER, slidesOutToward = SlideDirection.Down),
    HISTORY(parent = BROWSER, slidesOutToward = SlideDirection.End),
    CARD_GRID(parent = BROWSER, fadesOut = true),
    FIRST_RUN(parent = BROWSER, fadesOut = true),

    SETTINGS(parent = BROWSER, slidesOutToward = SlideDirection.End),
    PROFILE_SETTINGS(parent = SETTINGS, slidesOutToward = SlideDirection.End),
    CLEAR_BROWSING_SETTINGS(parent = SETTINGS, slidesOutToward = SlideDirection.End);

    val route: String = this.name

    /** Calculates which direction the UI should slide in towards from off screen. */
    val slidesInToward: SlideDirection?
        get() = when (slidesOutToward) {
            SlideDirection.Up -> SlideDirection.Down
            SlideDirection.Down -> SlideDirection.Up
            SlideDirection.Start -> SlideDirection.End
            SlideDirection.End -> SlideDirection.Start

            SlideDirection.Left -> throw IllegalArgumentException()
            SlideDirection.Right -> throw IllegalArgumentException()
            else -> null
        }

    companion object {
        fun fromRouteName(route: String?): AppNavDestination? = route?.let { valueOf(it) }
    }
}
