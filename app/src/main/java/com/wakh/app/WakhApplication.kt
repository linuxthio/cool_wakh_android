package com.wakh.app

import android.app.Application
import com.wakh.app.service.SignalingForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WakhApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Dès que l'identité locale (nom + numéro + jeton) est connue —
        // juste après une création de compte ou une connexion réussie, ou
        // au redémarrage de l'app si déjà connecté — on se connecte au
        // serveur de signalisation et on démarre le service qui nous garde
        // joignables en arrière-plan pour recevoir des messages. Si le
        // profil redevient `null` (déconnexion manuelle ou jeton
        // invalidé par le serveur, voir AppContainer), on ne se reconnecte
        // simplement plus tant qu'un nouveau profil n'est pas disponible.
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        appScope.launch {
            container.userPreferences.profile.collect { profile ->
                if (profile != null) {
                    container.signalingClient.connect(profile.phoneNumber, profile.authToken)
                    container.webRtcManager.start(profile.phoneNumber)
                    SignalingForegroundService.start(this@WakhApplication)
                } else {
                    container.signalingClient.disconnect()
                }
            }
        }
    }
}
