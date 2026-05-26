package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
                    composable("feed") {
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
                        arguments = listOf(navArgument("itemId") { type = NavType.StringType })
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
