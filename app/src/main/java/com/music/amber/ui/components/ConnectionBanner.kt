package com.music.amber.ui.components

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.music.amber.R

@Composable
fun ConnectionBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var online by remember { mutableStateOf(true) }
    var slow by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        fun read() {
            val caps = manager?.getNetworkCapabilities(manager.activeNetwork)
            online = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
            val down = caps?.linkDownstreamBandwidthKbps ?: Int.MAX_VALUE
            slow = online && down in 1..400
        }
        read()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = read()
            override fun onLost(network: Network) = read()
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = read()
        }
        runCatching { manager?.registerDefaultNetworkCallback(callback) }
        onDispose { runCatching { manager?.unregisterNetworkCallback(callback) } }
    }

    val message = when {
        !online -> R.string.connection_offline
        slow -> R.string.connection_slow
        else -> null
    }
    Box(modifier.fillMaxWidth().statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = message != null,
            enter = slideInVertically(spring(dampingRatio = 0.75f, stiffness = 380f)) { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
        ) {
            Surface(
                modifier = Modifier.padding(top = 6.dp),
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
            ) {
                Text(
                    text = message?.let { stringResource(it) } ?: "",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}
