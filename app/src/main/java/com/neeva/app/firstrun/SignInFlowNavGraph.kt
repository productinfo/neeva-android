// Copyright 2022 Neeva Inc. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.neeva.app.firstrun

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.navigation
import com.google.accompanist.navigation.animation.composable
import com.neeva.app.LocalAppNavModel
import com.neeva.app.LocalClientLogger
import com.neeva.app.LocalFirstRunModel
import com.neeva.app.LocalNavHostController
import com.neeva.app.appnav.AppNavDestination
import com.neeva.app.appnav.AppNavModel
import com.neeva.app.appnav.Transitions
import com.neeva.app.firstrun.signin.SignInScreenContainer
import com.neeva.app.firstrun.signup.SignUpLandingContainer
import com.neeva.app.firstrun.signup.SignUpWithOtherContainer
import com.neeva.app.logging.LogConfig

enum class SignInFlowNavDestination {
    SIGN_UP_LANDING_PAGE, SIGN_UP_OTHER, SIGN_IN;

    val route: String get() { return name }
}

/** Manages navigation between the different screens of the sign-in flow. */
class SignInFlowNavModel(
    private val appNavModel: AppNavModel,
    private val navController: NavController
) {
    fun navigateBackToSignUpLandingPage() {
        navController.navigate(SignInFlowNavDestination.SIGN_UP_LANDING_PAGE.route) {
            launchSingleTop = true

            // Keep the back stack shallow by popping everything off back to the root when returning
            // to the landing page.
            popUpTo(AppNavDestination.SIGN_IN_FLOW.route)
        }
    }

    fun navigateToSignIn() {
        navController.navigate(SignInFlowNavDestination.SIGN_IN.route) {
            launchSingleTop = true
        }
    }

    fun navigateToSignUpWithOther() {
        navController.navigate(SignInFlowNavDestination.SIGN_UP_OTHER.route) {
            launchSingleTop = true
        }
    }

    fun exitSignInFlow() {
        appNavModel.showBrowser()
    }
}

@Composable
fun rememberSignInFlowNavModel(): SignInFlowNavModel {
    val appNavModel = LocalAppNavModel.current
    val navController = LocalNavHostController.current

    return remember(appNavModel, navController) {
        SignInFlowNavModel(appNavModel, navController)
    }
}

@OptIn(ExperimentalAnimationApi::class)
fun NavGraphBuilder.signInFlowNavGraph() {
    navigation(
        startDestination = SignInFlowNavDestination.SIGN_UP_LANDING_PAGE.route,
        route = AppNavDestination.SIGN_IN_FLOW.route
    ) {
        composable(
            route = SignInFlowNavDestination.SIGN_UP_LANDING_PAGE.route,
            enterTransition = { EnterTransition.None },
            exitTransition = { Transitions.fadeOutLambda() }
        ) {
            val clientLogger = LocalClientLogger.current
            val context = LocalContext.current
            val firstRunModel = LocalFirstRunModel.current
            val signInFlowNavModel = rememberSignInFlowNavModel()

            SignUpLandingContainer(
                onOpenUrl = {
                    firstRunModel.openSingleTabActivity(context, it)
                },
                onClose = firstRunModel.getOnCloseOnboarding(signInFlowNavModel::exitSignInFlow),
                navigateToSignIn = signInFlowNavModel::navigateToSignIn,
                showSignUpWithOther = signInFlowNavModel::navigateToSignUpWithOther
            )

            LaunchedEffect(true) {
                clientLogger.logCounter(LogConfig.Interaction.AUTH_IMPRESSION_LANDING, null)
            }
        }

        composable(
            route = SignInFlowNavDestination.SIGN_UP_OTHER.route,
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None }
        ) {
            val clientLogger = LocalClientLogger.current
            val firstRunModel = LocalFirstRunModel.current
            val signInFlowNavModel = rememberSignInFlowNavModel()
            SignUpWithOtherContainer(
                onClose = firstRunModel.getOnCloseOnboarding(signInFlowNavModel::exitSignInFlow),
                navigateToSignIn = signInFlowNavModel::navigateToSignIn
            )

            LaunchedEffect(true) {
                clientLogger.logCounter(LogConfig.Interaction.AUTH_IMPRESSION_OTHER, null)
            }
        }

        composable(
            route = SignInFlowNavDestination.SIGN_IN.route,
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None }
        ) {
            val clientLogger = LocalClientLogger.current
            val firstRunModel = LocalFirstRunModel.current
            val signInFlowNavModel = rememberSignInFlowNavModel()
            SignInScreenContainer(
                onClose = firstRunModel.getOnCloseOnboarding(signInFlowNavModel::exitSignInFlow),
                navigateToSignUp = signInFlowNavModel::navigateBackToSignUpLandingPage
            )

            LaunchedEffect(true) {
                clientLogger.logCounter(LogConfig.Interaction.AUTH_IMPRESSION_SIGN_IN, null)
            }
        }
    }
}
