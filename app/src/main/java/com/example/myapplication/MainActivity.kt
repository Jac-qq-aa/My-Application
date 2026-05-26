package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.myapplication.tracking.AdTracker
import com.example.myapplication.ui.detail.DetailScreen
import com.example.myapplication.ui.feed.FeedScreen
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.viewmodel.FeedViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val feedViewModel: FeedViewModel = viewModel()
                val navController = rememberNavController()
                val tracker = remember { AdTracker() }

                NavHost(
                    navController = navController,
                    startDestination = "feed"
                ) {
                    composable(
                        route = "feed",
                        enterTransition = {
                            slideInHorizontally(
                                initialOffsetX = { -it / 4 },
                                animationSpec = tween(260)
                            ) + fadeIn(animationSpec = tween(260))
                        },
                        exitTransition = { ExitTransition.None },
                        popEnterTransition = { EnterTransition.None },
                        popExitTransition = {
                            slideOutHorizontally(
                                targetOffsetX = { -it / 4 },
                                animationSpec = tween(220)
                            ) + fadeOut(animationSpec = tween(220))
                        }
                    ) {
                        FeedScreen(
                            viewModel = feedViewModel,
                            tracker = tracker,
                            onNavigateToDetail = { itemId ->
                                navController.navigate("detail/$itemId")
                            }
                        )
                    }
                    composable(
                        route = "detail/{itemId}",
                        arguments = listOf(navArgument("itemId") { type = NavType.StringType }),
                        enterTransition = {
                            slideInHorizontally(
                                initialOffsetX = { it },
                                animationSpec = tween(280)
                            ) + fadeIn(animationSpec = tween(180))
                        },
                        exitTransition = {
                            slideOutHorizontally(
                                targetOffsetX = { -it / 5 },
                                animationSpec = tween(220)
                            ) + fadeOut(animationSpec = tween(220))
                        },
                        popEnterTransition = {
                            slideInHorizontally(
                                initialOffsetX = { -it / 5 },
                                animationSpec = tween(220)
                            ) + fadeIn(animationSpec = tween(180))
                        },
                        popExitTransition = {
                            slideOutHorizontally(
                                targetOffsetX = { it },
                                animationSpec = tween(260)
                            ) + fadeOut(animationSpec = tween(180))
                        }
                    ) { backStackEntry ->
                        val itemId = backStackEntry.arguments?.getString("itemId").orEmpty()
                        DetailScreen(
                            itemId = itemId,
                            viewModel = feedViewModel,
                            tracker = tracker,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
