package com.example.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

interface OnlineMatchSession {
    val sessionState: StateFlow<LanSessionState>
    fun createLobby(displayName: String)
    fun joinLobby(code: String, displayName: String)
    fun startWatchingLobbies()
    fun refreshLobbies()
    fun stopWatchingLobbies()
    fun sendLine(line: String)
    fun leave()
    fun shutdown()
}

/**
 * Process-local rooms so two view models can sync the same way Firestore does,
 * without a network. Used by unit tests.
 */
class OnlineRoomRegistry {
    class Room(
        val code: String,
        val hostName: String,
        val createdAtMs: Long,
        @Volatile var host: InMemoryOnlineSession? = null,
        @Volatile var guest: InMemoryOnlineSession? = null,
        @Volatile var closed: Boolean = false
    )

    private val rooms = ConcurrentHashMap<String, Room>()
    private val listeners = ConcurrentHashMap<Int, (List<OnlineLobbySummary>) -> Unit>()
    private var nextListenerId = 0

    fun create(code: String, hostName: String, host: InMemoryOnlineSession): Boolean {
        val room = Room(
            code = code,
            hostName = hostName,
            createdAtMs = System.currentTimeMillis(),
            host = host
        )
        val created = rooms.putIfAbsent(code, room) == null
        if (created) notifyListeners()
        return created
    }

    fun join(code: String, guest: InMemoryOnlineSession): Room? {
        val room = rooms[code] ?: return null
        synchronized(room) {
            if (room.closed || room.guest != null || room.host == null) return null
            room.guest = guest
        }
        notifyListeners()
        return room
    }

    fun peerOf(session: InMemoryOnlineSession): InMemoryOnlineSession? {
        val room = rooms.values.firstOrNull {
            !it.closed && (it.host === session || it.guest === session)
        } ?: return null
        return if (room.host === session) room.guest else room.host
    }

    fun leave(session: InMemoryOnlineSession) {
        val room = rooms.values.firstOrNull {
            it.host === session || it.guest === session
        } ?: return
        val peer = synchronized(room) {
            val other = if (room.host === session) room.guest else room.host
            if (room.host === session) {
                room.closed = true
                room.host = null
            } else if (room.guest === session) {
                room.guest = null
                if (room.host != null) {
                    room.closed = true
                }
            }
            other
        }
        notifyListeners()
        peer?.onPeerLeft()
    }

    fun openLobbies(): List<OnlineLobbySummary> {
        return rooms.values
            .filter { !it.closed && it.guest == null && it.host != null }
            .sortedByDescending { it.createdAtMs }
            .map { OnlineLobbySummary(it.code, it.hostName, it.createdAtMs) }
    }

    fun addListener(listener: (List<OnlineLobbySummary>) -> Unit): Int {
        val id = synchronized(this) { nextListenerId++ }
        listeners[id] = listener
        listener(openLobbies())
        return id
    }

    fun removeListener(id: Int) {
        listeners.remove(id)
    }

    private fun notifyListeners() {
        val snapshot = openLobbies()
        listeners.values.forEach { it(snapshot) }
    }
}

class InMemoryOnlineSession(
    private val registry: OnlineRoomRegistry,
    private val onMessageReceived: (String) -> Unit,
    private val onGuestConnected: () -> Unit
) : OnlineMatchSession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val _sessionState = MutableStateFlow(LanSessionState())
    override val sessionState: StateFlow<LanSessionState> = _sessionState.asStateFlow()

    private var listListenerId: Int? = null
    private var guestNotified = false

    override fun createLobby(displayName: String) {
        registry.leave(this)
        leaveQuiet()
        val name = displayName.trim().ifBlank { "Player 1" }.take(24)
        var code = ""
        var created = false
        repeat(8) {
            if (created) return@repeat
            val candidate = randomRoomCode()
            if (registry.create(candidate, name, this)) {
                code = candidate
                created = true
            }
        }
        if (!created) {
            _sessionState.value = _sessionState.value.copy(
                status = LanConnectionStatus.ERROR,
                errorMessage = "Could not create a lobby."
            )
            return
        }
        guestNotified = false
        _sessionState.value = LanSessionState(
            role = NetworkRole.ONLINE_HOST,
            status = LanConnectionStatus.HOSTING_WAITING,
            roomCode = code,
            peerAddress = code
        )
    }

    override fun joinLobby(code: String, displayName: String) {
        val normalized = code.trim().uppercase()
        if (normalized.length !in 4..8) {
            _sessionState.value = _sessionState.value.copy(
                role = NetworkRole.ONLINE_GUEST,
                status = LanConnectionStatus.ERROR,
                errorMessage = "Enter the lobby code."
            )
            return
        }
        registry.leave(this)
        leaveQuiet()
        _sessionState.value = LanSessionState(
            role = NetworkRole.ONLINE_GUEST,
            status = LanConnectionStatus.CONNECTING,
            roomCode = normalized,
            peerAddress = normalized
        )
        val room = registry.join(normalized, this)
        if (room == null) {
            _sessionState.value = _sessionState.value.copy(
                status = LanConnectionStatus.ERROR,
                errorMessage = "Lobby $normalized is not open."
            )
            return
        }
        _sessionState.value = _sessionState.value.copy(
            status = LanConnectionStatus.CONNECTED,
            errorMessage = null
        )
        room.host?.onGuestArrived(displayName.trim().ifBlank { "Player 2" }.take(24))
    }

    override fun startWatchingLobbies() {
        if (listListenerId != null) return
        listListenerId = registry.addListener { lobbies ->
            val mine = _sessionState.value.roomCode
            _sessionState.value = _sessionState.value.copy(
                openLobbies = lobbies.filterNot { it.code == mine },
                isListingLobbies = false,
                isOnlineServerReachable = true
            )
        }
    }

    override fun refreshLobbies() {
        val mine = _sessionState.value.roomCode
        _sessionState.value = _sessionState.value.copy(
            openLobbies = registry.openLobbies().filterNot { it.code == mine },
            isListingLobbies = false,
            isOnlineServerReachable = true,
            onlineErrorMessage = null
        )
    }

    override fun stopWatchingLobbies() {
        listListenerId?.let { registry.removeListener(it) }
        listListenerId = null
        _sessionState.value = _sessionState.value.copy(isListingLobbies = false)
    }

    override fun sendLine(line: String) {
        val peer = registry.peerOf(this) ?: return
        peer.scope.launch { peer.onMessageReceived(line) }
    }

    override fun leave() {
        registry.leave(this)
        leaveQuiet()
        _sessionState.value = LanSessionState()
    }

    override fun shutdown() {
        leave()
        scope.cancel()
    }

    fun onGuestArrived(guestName: String) {
        if (guestNotified) return
        guestNotified = true
        _sessionState.value = _sessionState.value.copy(
            status = LanConnectionStatus.CONNECTED,
            peerAddress = guestName,
            errorMessage = null
        )
        scope.launch { onGuestConnected() }
    }

    fun onPeerLeft() {
        if (_sessionState.value.status == LanConnectionStatus.CONNECTED ||
            _sessionState.value.status == LanConnectionStatus.HOSTING_WAITING
        ) {
            _sessionState.value = _sessionState.value.copy(
                status = LanConnectionStatus.ERROR,
                errorMessage = "Opponent disconnected."
            )
        }
    }

    private fun leaveQuiet() {
        stopWatchingLobbies()
        guestNotified = false
    }

    private fun randomRoomCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return buildString(6) {
            repeat(6) { append(alphabet.random()) }
        }
    }
}
