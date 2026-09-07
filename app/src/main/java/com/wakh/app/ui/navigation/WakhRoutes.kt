package com.wakh.app.ui.navigation

object WakhRoutes {
    const val WELCOME = "welcome"
    const val REGISTER = "register"
    const val LOGIN = "login"
    const val CONVERSATIONS = "conversations"
    const val ADD_CONTACT = "add_contact"
    const val CREATE_GROUP = "create_group"
    const val SETTINGS = "settings"
    const val CHAT = "chat/{conversationId}"

    fun chat(conversationId: String) = "chat/$conversationId"
}
