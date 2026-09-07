package com.wakh.app.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * Factory générique qui délègue simplement à un lambda de création — évite
 * d'introduire Hilt/Dagger pour un projet de cette taille tout en gardant
 * les ViewModels correctement scoppés au cycle de vie Compose.
 */
class SimpleViewModelFactory(
    private val creator: () -> ViewModel,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = creator() as T
}
