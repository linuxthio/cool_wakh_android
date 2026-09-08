package com.wakh.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wakh.app.AppContainer
import com.wakh.app.data.local.datastore.UserProfile
import com.wakh.app.ui.addcontact.AddContactScreen
import com.wakh.app.ui.auth.LoginScreen
import com.wakh.app.ui.auth.RegisterScreen
import com.wakh.app.ui.auth.WelcomeScreen
import com.wakh.app.ui.chat.ChatScreen
import com.wakh.app.ui.conversations.ConversationsListScreen
import com.wakh.app.ui.creategroup.CreateGroupScreen
import com.wakh.app.ui.groupinfo.GroupInfoScreen
import com.wakh.app.ui.settings.SettingsScreen
import com.wakh.app.util.ConversationId

@Composable
fun WakhNavGraph(container: AppContainer) {
    val navController = rememberNavController()

    var isReady by remember { mutableStateOf(false) }
    val profile by produceState<UserProfile?>(initialValue = null) {
        container.userPreferences.profile.collect {
            value = it
            isReady = true
        }
    }

    if (!isReady) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    // Un compte peut disparaître à tout moment après le premier affichage
    // (déconnexion manuelle, ou jeton invalidé par le serveur — voir
    // SignalingClient.authInvalidated dans AppContainer) : dès que c'est
    // le cas, on ramène systématiquement à l'écran d'accueil.
    LaunchedEffect(profile) {
        if (profile == null) {
            val current = navController.currentDestination?.route
            if (current != null && current != WakhRoutes.WELCOME) {
                navController.navigate(WakhRoutes.WELCOME) {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = if (profile == null) WakhRoutes.WELCOME else WakhRoutes.CONVERSATIONS,
    ) {
        composable(WakhRoutes.WELCOME) {
            WelcomeScreen(
                onCreateAccount = { navController.navigate(WakhRoutes.REGISTER) },
                onLogin = { navController.navigate(WakhRoutes.LOGIN) },
            )
        }

        composable(WakhRoutes.REGISTER) {
            RegisterScreen(
                authRepository = container.authRepository,
                onBack = { navController.popBackStack() },
                onRegistered = {
                    navController.navigate(WakhRoutes.CONVERSATIONS) {
                        popUpTo(WakhRoutes.WELCOME) { inclusive = true }
                    }
                },
            )
        }

        composable(WakhRoutes.LOGIN) {
            LoginScreen(
                authRepository = container.authRepository,
                userPreferences = container.userPreferences,
                onBack = { navController.popBackStack() },
                onLoggedIn = {
                    navController.navigate(WakhRoutes.CONVERSATIONS) {
                        popUpTo(WakhRoutes.WELCOME) { inclusive = true }
                    }
                },
            )
        }

        composable(WakhRoutes.CONVERSATIONS) {
            ConversationsListScreen(
                contactRepository = container.contactRepository,
                messageRepository = container.messageRepository,
                groupRepository = container.groupRepository,
                userPreferences = container.userPreferences,
                onOpenChat = { conversationId -> navController.navigate(WakhRoutes.chat(conversationId)) },
                onAddContact = { navController.navigate(WakhRoutes.ADD_CONTACT) },
                onCreateGroup = { navController.navigate(WakhRoutes.CREATE_GROUP) },
                onOpenSettings = { navController.navigate(WakhRoutes.SETTINGS) },
            )
        }

        composable(WakhRoutes.SETTINGS) {
            SettingsScreen(
                userPreferences = container.userPreferences,
                authRepository = container.authRepository,
                contactRepository = container.contactRepository,
                onBack = { navController.popBackStack() },
            )
        }

        composable(WakhRoutes.ADD_CONTACT) {
            AddContactScreen(
                contactRepository = container.contactRepository,
                onBack = { navController.popBackStack() },
                onDone = { navController.popBackStack() },
            )
        }

        composable(WakhRoutes.CREATE_GROUP) {
            CreateGroupScreen(
                contactRepository = container.contactRepository,
                groupRepository = container.groupRepository,
                onBack = { navController.popBackStack() },
                onCreated = { groupId ->
                    navController.navigate(WakhRoutes.chat(ConversationId.forGroup(groupId))) {
                        popUpTo(WakhRoutes.CONVERSATIONS)
                    }
                },
            )
        }

        composable(
            route = WakhRoutes.CHAT,
            arguments = listOf(navArgument("conversationId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getString("conversationId").orEmpty()
            ChatScreen(
                conversationId = conversationId,
                messageRepository = container.messageRepository,
                contactRepository = container.contactRepository,
                groupRepository = container.groupRepository,
                signalingClient = container.signalingClient,
                onBack = { navController.popBackStack() },
                onOpenGroupInfo = { groupId -> navController.navigate(WakhRoutes.groupInfo(groupId)) },
            )
        }

        composable(
            route = WakhRoutes.GROUP_INFO,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId").orEmpty()
            GroupInfoScreen(
                groupId = groupId,
                groupRepository = container.groupRepository,
                contactRepository = container.contactRepository,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
