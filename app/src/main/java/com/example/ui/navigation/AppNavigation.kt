package com.example.ui.navigation

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.data.auth.authStateFlow
import com.example.data.repository.FirestoreRepository
import com.example.data.repository.GeminiAiRepository
import com.example.ui.screens.AgentsPromptsScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.viewmodel.AgentsPromptsViewModel
import com.example.ui.viewmodel.ChatViewModel
import com.example.ui.viewmodel.DashboardViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

sealed class PlatformRoute(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Dashboard : PlatformRoute(
        route = "dashboard",
        labelRes = R.string.nav_dashboard,
        selectedIcon = Icons.Filled.Dashboard,
        unselectedIcon = Icons.Outlined.Dashboard
    )

    data object Chat : PlatformRoute(
        route = "chat",
        labelRes = R.string.nav_chat,
        selectedIcon = Icons.AutoMirrored.Filled.Chat,
        unselectedIcon = Icons.AutoMirrored.Outlined.Chat
    )

    data object Agents : PlatformRoute(
        route = "agents",
        labelRes = R.string.nav_agents,
        selectedIcon = Icons.Filled.SmartToy,
        unselectedIcon = Icons.Outlined.SmartToy
    )

    data object Profile : PlatformRoute(
        route = "profile",
        labelRes = R.string.nav_profile,
        selectedIcon = Icons.Filled.Person,
        unselectedIcon = Icons.Outlined.Person
    )

    companion object {
        val tabs = listOf(Dashboard, Chat, Agents, Profile)
    }
}

@Composable
fun AppNavigation(
    isDarkTheme: Boolean,
    onToggleTheme: (Boolean) -> Unit,
    auth: FirebaseAuth = Firebase.auth
) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = { /* authStateFlow updates automatically */ })
    } else {
        key(user.uid) {
            AuthenticatedPlatformShell(
                currentUserId = user.uid,
                userDisplayName = user.displayName?.takeIf { it.isNotBlank() } ?: "کاربر نوا",
                userEmail = user.email,
                userPhotoUrl = user.photoUrl?.toString(),
                isDarkTheme = isDarkTheme,
                onToggleTheme = onToggleTheme
            )
        }
    }
}

@Composable
private fun AuthenticatedPlatformShell(
    currentUserId: String,
    userDisplayName: String,
    userEmail: String?,
    userPhotoUrl: String?,
    isDarkTheme: Boolean,
    onToggleTheme: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as Application

    val firestoreRepo = remember(currentUserId) {
        val databaseId = app.getString(R.string.firestore_database_id)
        val db = FirebaseFirestore.getInstance(databaseId)
        FirestoreRepository(db)
    }
    val geminiRepo = remember { GeminiAiRepository() }

    val dashboardViewModel: DashboardViewModel = viewModel(
        key = "dash_$currentUserId",
        factory = DashboardViewModel.provideFactory(firestoreRepo, currentUserId)
    )
    val chatViewModel: ChatViewModel = viewModel(
        key = "chat_$currentUserId",
        factory = ChatViewModel.provideFactory(firestoreRepo, geminiRepo, currentUserId)
    )
    val agentsViewModel: AgentsPromptsViewModel = viewModel(
        key = "agents_$currentUserId",
        factory = AgentsPromptsViewModel.provideFactory(firestoreRepo, geminiRepo, currentUserId)
    )

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 680.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar(
                        windowInsets = WindowInsets.navigationBars,
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        PlatformRoute.tabs.forEach { tab ->
                            val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = {
                                    Text(
                                        text = stringResource(tab.labelRes),
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                modifier = Modifier.testTag("nav_${tab.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        PlatformRoute.tabs.forEach { tab ->
                            val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                modifier = Modifier.testTag("rail_${tab.route}")
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    NavHost(
                        navController = navController,
                        startDestination = PlatformRoute.Dashboard.route
                    ) {
                        composable(PlatformRoute.Dashboard.route) {
                            DashboardScreen(
                                viewModel = dashboardViewModel,
                                userDisplayName = userDisplayName,
                                userPhotoUrl = userPhotoUrl,
                                onOpenNewChat = { persona ->
                                    chatViewModel.startNewChat(persona)
                                    navigateToTab(PlatformRoute.Chat.route)
                                },
                                onOpenCustomAssistantChat = { assistant ->
                                    chatViewModel.startChatWithCustomAssistant(assistant)
                                    navigateToTab(PlatformRoute.Chat.route)
                                },
                                onOpenConversation = { conversation ->
                                    chatViewModel.selectConversation(conversation)
                                    navigateToTab(PlatformRoute.Chat.route)
                                },
                                onOpenAgentsHub = {
                                    navigateToTab(PlatformRoute.Agents.route)
                                }
                            )
                        }
                        composable(PlatformRoute.Chat.route) {
                            ChatScreen(viewModel = chatViewModel)
                        }
                        composable(PlatformRoute.Agents.route) {
                            AgentsPromptsScreen(
                                viewModel = agentsViewModel,
                                onRunAssistant = { assistant ->
                                    chatViewModel.startChatWithCustomAssistant(assistant)
                                    navigateToTab(PlatformRoute.Chat.route)
                                },
                                onRunPrompt = { promptText ->
                                    chatViewModel.startNewChat(initialPrompt = promptText)
                                    navigateToTab(PlatformRoute.Chat.route)
                                }
                            )
                        }
                        composable(PlatformRoute.Profile.route) {
                            ProfileScreen(
                                dashboardViewModel = dashboardViewModel,
                                userDisplayName = userDisplayName,
                                userEmail = userEmail,
                                userPhotoUrl = userPhotoUrl,
                                isDarkTheme = isDarkTheme,
                                onToggleTheme = onToggleTheme
                            )
                        }
                    }
                }
            }
        }
    }
}
