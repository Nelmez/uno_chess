package com.example.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket

enum class NetworkRole {
    PASS_AND_PLAY,
    LAN_HOST,
    LAN_GUEST
}

enum class LanConnectionStatus {
    OFFLINE,
    HOSTING_WAITING,
    CONNECTING,
    CONNECTED,
    ERROR
}

data class DiscoveredHost(
    val serviceName: String,
    val hostAddress: String,
    val port: Int
)

data class LanSessionState(
    val role: NetworkRole = NetworkRole.PASS_AND_PLAY,
    val status: LanConnectionStatus = LanConnectionStatus.OFFLINE,
    val localIp: String = "",
    val hostPort: Int = 0,
    val peerAddress: String = "",
    val errorMessage: String? = null,
    val discoveredHosts: List<DiscoveredHost> = emptyList(),
    val isDiscovering: Boolean = false
) {
    val isLanConnected: Boolean
        get() = status == LanConnectionStatus.CONNECTED && role != NetworkRole.PASS_AND_PLAY
}

class LanSessionManager(
    private val onMessageReceived: (String) -> Unit,
    private val onGuestConnected: () -> Unit
) {
    companion object {
        private const val SERVICE_TYPE = "_unochess._tcp."
        private const val DEFAULT_SERVICE_NAME = "UNOChess"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _sessionState = MutableStateFlow(LanSessionState(localIp = findLocalIpv4Address()))
    val sessionState: StateFlow<LanSessionState> = _sessionState.asStateFlow()

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var clientSocket: Socket? = null

    @Volatile
    private var writer: BufferedWriter? = null

    private var connectionJob: Job? = null

    private var nsdManager: NsdManager? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    fun refreshLocalIp() {
        _sessionState.value = _sessionState.value.copy(localIp = findLocalIpv4Address())
    }

    fun startHosting(context: Context? = null, preferredPort: Int = 0) {
        stopConnectionOnly()
        stopNsdDiscovery()

        val ip = findLocalIpv4Address()
        _sessionState.value = _sessionState.value.copy(
            role = NetworkRole.LAN_HOST,
            status = LanConnectionStatus.HOSTING_WAITING,
            localIp = ip,
            errorMessage = null,
            peerAddress = ""
        )

        connectionJob = scope.launch {
            try {
                val server = ServerSocket(preferredPort)
                serverSocket = server
                val boundPort = server.localPort
                _sessionState.value = _sessionState.value.copy(hostPort = boundPort)

                context?.let { registerNsdService(it, boundPort, ip) }

                val accepted = server.accept()
                clientSocket = accepted
                accepted.tcpNoDelay = true
                val remoteAddr = accepted.inetAddress?.hostAddress.orEmpty()
                writer = BufferedWriter(OutputStreamWriter(accepted.getOutputStream(), Charsets.UTF_8))

                _sessionState.value = _sessionState.value.copy(
                    status = LanConnectionStatus.CONNECTED,
                    peerAddress = remoteAddr,
                    errorMessage = null
                )
                onGuestConnected()
                readLoop(accepted)
            } catch (e: Exception) {
                if (isActive && _sessionState.value.role == NetworkRole.LAN_HOST) {
                    _sessionState.value = _sessionState.value.copy(
                        status = LanConnectionStatus.ERROR,
                        errorMessage = e.localizedMessage ?: "Host connection closed"
                    )
                }
            } finally {
                unregisterNsdService()
            }
        }
    }

    fun connectToHost(hostIp: String, port: Int) {
        val trimmedIp = hostIp.trim()
        if (trimmedIp.isEmpty() || port !in 1..65535) {
            _sessionState.value = _sessionState.value.copy(
                status = LanConnectionStatus.ERROR,
                errorMessage = "Enter a valid IP address and port."
            )
            return
        }

        stopConnectionOnly()
        stopNsdDiscovery()

        _sessionState.value = _sessionState.value.copy(
            role = NetworkRole.LAN_GUEST,
            status = LanConnectionStatus.CONNECTING,
            peerAddress = "$trimmedIp:$port",
            errorMessage = null
        )

        connectionJob = scope.launch {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(trimmedIp, port), 5000)
                socket.tcpNoDelay = true
                clientSocket = socket
                writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))

                _sessionState.value = _sessionState.value.copy(
                    status = LanConnectionStatus.CONNECTED,
                    peerAddress = "$trimmedIp:$port",
                    errorMessage = null
                )
                readLoop(socket)
            } catch (e: Exception) {
                if (isActive && _sessionState.value.role == NetworkRole.LAN_GUEST) {
                    _sessionState.value = _sessionState.value.copy(
                        status = LanConnectionStatus.ERROR,
                        errorMessage = "Could not connect to $trimmedIp:$port (${e.localizedMessage ?: "timeout"})"
                    )
                }
            }
        }
    }

    fun sendLine(line: String) {
        scope.launch {
            try {
                val w = writer ?: return@launch
                synchronized(w) {
                    w.write(line)
                    w.newLine()
                    w.flush()
                }
            } catch (e: Exception) {
                if (_sessionState.value.status == LanConnectionStatus.CONNECTED) {
                    _sessionState.value = _sessionState.value.copy(
                        status = LanConnectionStatus.ERROR,
                        errorMessage = "Connection lost."
                    )
                }
            }
        }
    }

    fun startDiscovery(context: Context) {
        stopNsdDiscovery()
        val manager = context.applicationContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
            ?: return
        nsdManager = manager

        _sessionState.value = _sessionState.value.copy(
            discoveredHosts = emptyList(),
            isDiscovering = true
        )

        val listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                _sessionState.value = _sessionState.value.copy(isDiscovering = false)
            }

            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                _sessionState.value = _sessionState.value.copy(isDiscovering = false)
            }

            override fun onDiscoveryStarted(serviceType: String?) {
                _sessionState.value = _sessionState.value.copy(isDiscovering = true)
            }

            override fun onDiscoveryStopped(serviceType: String?) {
                _sessionState.value = _sessionState.value.copy(isDiscovering = false)
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType != SERVICE_TYPE) return
                resolveServiceCompat(manager, serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                _sessionState.value = _sessionState.value.copy(
                    discoveredHosts = _sessionState.value.discoveredHosts.filterNot {
                        it.serviceName == serviceInfo.serviceName
                    }
                )
            }
        }
        discoveryListener = listener
        runCatching {
            manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        }.onFailure {
            _sessionState.value = _sessionState.value.copy(isDiscovering = false)
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveServiceCompat(manager: NsdManager, serviceInfo: NsdServiceInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            runCatching {
                manager.registerServiceInfoCallback(
                    serviceInfo,
                    { runnable -> scope.launch { runnable.run() } },
                    object : NsdManager.ServiceInfoCallback {
                        override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) = Unit
                        override fun onServiceUpdated(info: NsdServiceInfo) {
                            val ipv4 = info.hostAddresses
                                .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
                                ?.hostAddress ?: return
                            addDiscoveredHost(DiscoveredHost(info.serviceName, ipv4, info.port))
                            runCatching { manager.unregisterServiceInfoCallback(this) }
                        }
                        override fun onServiceLost() = Unit
                        override fun onServiceInfoCallbackUnregistered() = Unit
                    }
                )
            }
        } else {
            runCatching {
                manager.resolveService(
                    serviceInfo,
                    object : NsdManager.ResolveListener {
                        override fun onResolveFailed(info: NsdServiceInfo?, errorCode: Int) = Unit
                        override fun onServiceResolved(info: NsdServiceInfo) {
                            val addr = info.host?.hostAddress ?: return
                            addDiscoveredHost(DiscoveredHost(info.serviceName, addr, info.port))
                        }
                    }
                )
            }
        }
    }

    private fun addDiscoveredHost(host: DiscoveredHost) {
        val localIp = _sessionState.value.localIp
        if (host.hostAddress == localIp && host.port == _sessionState.value.hostPort) return
        val existing = _sessionState.value.discoveredHosts
        val updated = existing.filterNot {
            it.hostAddress == host.hostAddress && it.port == host.port
        } + host
        _sessionState.value = _sessionState.value.copy(discoveredHosts = updated)
    }

    fun stopNsdDiscovery() {
        val manager = nsdManager
        val listener = discoveryListener
        discoveryListener = null
        if (manager != null && listener != null) {
            runCatching { manager.stopServiceDiscovery(listener) }
        }
        _sessionState.value = _sessionState.value.copy(isDiscovering = false)
    }

    private fun registerNsdService(context: Context, port: Int, ip: String) {
        unregisterNsdService()
        val manager = context.applicationContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
            ?: return
        nsdManager = manager

        val suffix = ip.substringAfterLast('.', "Host")
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "$DEFAULT_SERVICE_NAME-$suffix"
            serviceType = SERVICE_TYPE
            setPort(port)
        }

        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
        }
        registrationListener = listener
        runCatching {
            manager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        }
    }

    private fun unregisterNsdService() {
        val manager = nsdManager
        val listener = registrationListener
        registrationListener = null
        if (manager != null && listener != null) {
            runCatching { manager.unregisterService(listener) }
        }
    }

    fun disconnectToPassAndPlay() {
        stopConnectionOnly()
        stopNsdDiscovery()
        _sessionState.value = LanSessionState(
            role = NetworkRole.PASS_AND_PLAY,
            status = LanConnectionStatus.OFFLINE,
            localIp = findLocalIpv4Address()
        )
    }

    fun shutdown() {
        stopConnectionOnly()
        stopNsdDiscovery()
        scope.cancel()
    }

    private fun stopConnectionOnly() {
        connectionJob?.cancel()
        connectionJob = null
        unregisterNsdService()
        runCatching { writer?.close() }
        writer = null
        runCatching { clientSocket?.close() }
        clientSocket = null
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    private fun readLoop(socket: Socket) {
        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
        while (!socket.isClosed) {
            val line = reader.readLine() ?: break
            onMessageReceived(line)
        }
        if (_sessionState.value.status == LanConnectionStatus.CONNECTED) {
            _sessionState.value = _sessionState.value.copy(
                status = LanConnectionStatus.ERROR,
                errorMessage = "Opponent disconnected."
            )
        }
    }

    private fun findLocalIpv4Address(): String {
        return runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            interfaces
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { addr ->
                    addr is Inet4Address && !addr.isLoopbackAddress && addr.isSiteLocalAddress
                }
                ?.hostAddress
                ?: "127.0.0.1"
        }.getOrDefault("127.0.0.1")
    }
}
