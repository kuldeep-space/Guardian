package com.ai.guardian.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

enum class AuthStatus {
    INITIALIZING,
    AUTHENTICATED,
    FAILED,
    NETWORK_UNAVAILABLE
}

class FirebaseAuthManager private constructor(private val context: Context) {
    private val auth = FirebaseAuth.getInstance()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private val _authState = MutableStateFlow(AuthStatus.INITIALIZING)
    val authState: StateFlow<AuthStatus> = _authState.asStateFlow()
    
    val currentUserUid: String?
        get() = auth.currentUser?.uid

    init {
        monitorNetworkAndAuthenticate()
    }

    private fun monitorNetworkAndAuthenticate() {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (_authState.value != AuthStatus.AUTHENTICATED) {
                    authenticateAnonymously()
                }
            }
            override fun onLost(network: Network) {
                if (_authState.value == AuthStatus.INITIALIZING || _authState.value == AuthStatus.FAILED) {
                    _authState.value = AuthStatus.NETWORK_UNAVAILABLE
                }
            }
        }
        
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
        
        // Initial check
        val activeNetwork = connectivityManager.activeNetwork
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
        if (caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true) {
            authenticateAnonymously()
        } else {
            _authState.value = AuthStatus.NETWORK_UNAVAILABLE
        }
    }

    private fun authenticateAnonymously() {
        scope.launch {
            if (auth.currentUser != null) {
                Log.d("FirebaseAuthManager", "Already anonymously authenticated: ${auth.currentUser?.uid}")
                _authState.value = AuthStatus.AUTHENTICATED
                return@launch
            }
            
            Log.d("FirebaseAuthManager", "Anonymous authentication started.")
            var attempt = 0
            while (_authState.value != AuthStatus.AUTHENTICATED) {
                try {
                    val result = auth.signInAnonymously().await()
                    if (result.user != null) {
                        Log.d("FirebaseAuthManager", "Authentication succeeded: ${result.user?.uid}")
                        _authState.value = AuthStatus.AUTHENTICATED
                        break
                    }
                } catch (e: Exception) {
                    attempt++
                    Log.e("FirebaseAuthManager", "Authentication failed (Attempt $attempt). Retrying in 5s...", e)
                    _authState.value = AuthStatus.FAILED
                    delay(5000)
                }
            }
        }
    }

    companion object {
        @Volatile
        private var instance: FirebaseAuthManager? = null

        fun getInstance(context: Context): FirebaseAuthManager {
            return instance ?: synchronized(this) {
                instance ?: FirebaseAuthManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
