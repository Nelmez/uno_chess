package com.example.network

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

class FirestoreOnlineSession(
    private val onMessageReceived: (String) -> Unit,
    private val onGuestConnected: () -> Unit,
    private val contextProvider: (() -> Context?)? = null
) : OnlineMatchSession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val _sessionState = MutableStateFlow(LanSessionState())
    override val sessionState: StateFlow<LanSessionState> = _sessionState.asStateFlow()

    private var uid: String = ""
    private var lobbyCode: String = ""
    private var lobbyListener: ListenerRegistration? = null
    private var actionListener: ListenerRegistration? = null
    private var listListener: ListenerRegistration? = null
    private var heartbeatJob: Job? = null
    private var stateSeq: Long = 0
    private var lastAppliedStateSeq: Long = -1
    @Volatile private var guestNotified = false
    @Volatile private var watching = false
    private val seenActionIds = mutableSetOf<String>()

    override fun createLobby(displayName: String) {
        scope.launch {
            try {
                stopListeners()
                uid = playerId()
                val name = displayName.trim().ifBlank { "Player 1" }.take(24)
                val db = firestore()
                var code = ""
                var created = false
                var lastError: Exception? = null
                repeat(8) {
                    if (created) return@repeat
                    val candidate = randomRoomCode()
                    val now = System.currentTimeMillis()
                    val data = hashMapOf<String, Any>(
                        "code" to candidate,
                        "hostId" to uid,
                        "hostName" to name,
                        "status" to "waiting",
                        "createdAt" to now,
                        "hostSeenAt" to now,
                        "stateSeq" to 0L
                    )
                    try {
                        val ref = db.collection(LOBBIES).document(candidate)
                        Tasks.await(db.runTransaction<Void> { tx ->
                            if (tx.get(ref).exists()) error("Lobby code already exists.")
                            tx.set(ref, data)
                            null
                        })
                        code = candidate
                        created = true
                    } catch (e: Exception) {
                        lastError = e
                    }
                }
                if (!created) {
                    throw lastError ?: IllegalStateException("Could not create a lobby.")
                }
                lobbyCode = code
                stateSeq = 0
                guestNotified = false
                _sessionState.value = LanSessionState(
                    role = NetworkRole.ONLINE_HOST,
                    status = LanConnectionStatus.HOSTING_WAITING,
                    roomCode = code,
                    peerAddress = code
                )
                watchLobby(code, asHost = true)
                startHeartbeat(code, asHost = true)
            } catch (e: Exception) {
                _sessionState.value = _sessionState.value.copy(
                    role = NetworkRole.ONLINE_HOST,
                    status = LanConnectionStatus.ERROR,
                    errorMessage = friendlyError(e)
                )
            }
        }
    }

    override fun joinLobby(code: String, displayName: String) {
        val normalized = code.trim().uppercase()
        if (!ROOM_CODE.matches(normalized)) {
            _sessionState.value = LanSessionState(
                role = NetworkRole.ONLINE_GUEST,
                status = LanConnectionStatus.ERROR,
                errorMessage = "Enter a valid lobby code."
            )
            return
        }
        scope.launch {
            try {
                stopListeners()
                uid = playerId()
                val name = displayName.trim().ifBlank { "Player 2" }.take(24)
                _sessionState.value = LanSessionState(
                    role = NetworkRole.ONLINE_GUEST,
                    status = LanConnectionStatus.CONNECTING,
                    roomCode = normalized,
                    peerAddress = normalized
                )
                val db = firestore()
                val ref = db.collection(LOBBIES).document(normalized)
                Tasks.await(db.runTransaction { tx ->
                    val snap = tx.get(ref)
                    if (!snap.exists()) error("Lobby $normalized was not found.")
                    val status = snap.getString("status")
                    val guestId = snap.getString("guestId")
                    val hostId = snap.getString("hostId")
                    if (status != "waiting" || !guestId.isNullOrEmpty()) {
                        error("Lobby $normalized is no longer open.")
                    }
                    if (hostId == uid) error("You are already hosting this lobby.")
                    tx.update(
                        ref,
                        mapOf(
                            "guestId" to uid,
                            "guestName" to name,
                            "status" to "playing",
                            "guestSeenAt" to System.currentTimeMillis()
                        )
                    )
                    null
                })
                lobbyCode = normalized
                lastAppliedStateSeq = -1
                _sessionState.value = _sessionState.value.copy(
                    status = LanConnectionStatus.CONNECTED,
                    errorMessage = null
                )
                watchLobby(normalized, asHost = false)
                startHeartbeat(normalized, asHost = false)
            } catch (e: Exception) {
                _sessionState.value = _sessionState.value.copy(
                    role = NetworkRole.ONLINE_GUEST,
                    status = LanConnectionStatus.ERROR,
                    roomCode = normalized,
                    errorMessage = friendlyError(e)
                )
            }
        }
    }

    override fun startWatchingLobbies() {
        if (watching) return
        watching = true
        scope.launch {
            try {
                uid = playerId()
                val query = firestore().collection(LOBBIES)
                    .whereEqualTo("status", "waiting")
                    .limit(50)
                listListener?.remove()
                listListener = query.addSnapshotListener { snap, error ->
                    if (!watching) return@addSnapshotListener
                    if (error != null) {
                        _sessionState.value = _sessionState.value.copy(
                            isListingLobbies = true,
                            errorMessage = friendlyError(error)
                        )
                        return@addSnapshotListener
                    }
                    val now = System.currentTimeMillis()
                    val lobbies = snap?.documents.orEmpty().mapNotNull { doc ->
                        val hostSeen = doc.getLong("hostSeenAt") ?: 0L
                        if (now - hostSeen > STALE_MS) return@mapNotNull null
                        if (doc.getString("hostId") == uid) return@mapNotNull null
                        val code = doc.getString("code") ?: doc.id
                        val hostName = doc.getString("hostName").orEmpty().ifBlank { "Host" }
                        OnlineLobbySummary(code, hostName, doc.getLong("createdAt") ?: 0L)
                    }.sortedByDescending { it.createdAtMs }
                    _sessionState.value = _sessionState.value.copy(
                        openLobbies = lobbies,
                        isListingLobbies = true,
                        errorMessage = if (_sessionState.value.status == LanConnectionStatus.ERROR) {
                            _sessionState.value.errorMessage
                        } else {
                            null
                        }
                    )
                }
            } catch (e: Exception) {
                _sessionState.value = _sessionState.value.copy(
                    isListingLobbies = false,
                    errorMessage = friendlyError(e)
                )
            }
        }
    }

    override fun stopWatchingLobbies() {
        watching = false
        listListener?.remove()
        listListener = null
        _sessionState.value = _sessionState.value.copy(isListingLobbies = false)
    }

    override fun sendLine(line: String) {
        val code = lobbyCode
        if (code.isEmpty() || uid.isEmpty()) return
        val role = _sessionState.value.role
        scope.launch {
            try {
                val ref = firestore().collection(LOBBIES).document(code)
                if (role.isHost) {
                    stateSeq += 1
                    val seq = stateSeq
                    Tasks.await(
                        ref.update(
                            mapOf(
                                "state" to line,
                                "stateSeq" to seq,
                                "hostSeenAt" to System.currentTimeMillis()
                            )
                        )
                    )
                } else if (role.isGuest) {
                    Tasks.await(
                        ref.collection(ACTIONS).add(
                            mapOf(
                                "fromId" to uid,
                                "line" to line,
                                "createdAt" to System.currentTimeMillis()
                            )
                        )
                    )
                }
            } catch (e: Exception) {
                if (_sessionState.value.status == LanConnectionStatus.CONNECTED) {
                    _sessionState.value = _sessionState.value.copy(
                        status = LanConnectionStatus.ERROR,
                        errorMessage = friendlyError(e)
                    )
                }
            }
        }
    }

    override fun leave() {
        val code = lobbyCode
        val role = _sessionState.value.role
        val signedIn = uid
        scope.launch {
            if (code.isNotEmpty() && signedIn.isNotEmpty()) {
                runCatching {
                    val ref = firestore().collection(LOBBIES).document(code)
                    if (role == NetworkRole.ONLINE_HOST) {
                        Tasks.await(ref.update("status", "closed"))
                    } else if (role == NetworkRole.ONLINE_GUEST) {
                        val snap = Tasks.await(ref.get())
                        if (snap.getString("status") == "waiting") {
                            Tasks.await(
                                ref.update(
                                    mapOf(
                                        "guestId" to FieldValue.delete(),
                                        "guestName" to FieldValue.delete(),
                                        "guestSeenAt" to FieldValue.delete()
                                    )
                                )
                            )
                        } else {
                            Tasks.await(ref.update("status", "closed"))
                        }
                    }
                }
            }
        }
        stopListeners()
        lobbyCode = ""
        _sessionState.value = LanSessionState(
            openLobbies = _sessionState.value.openLobbies,
            isListingLobbies = watching
        )
    }

    override fun shutdown() {
        leave()
        scope.cancel()
    }

    private fun watchLobby(code: String, asHost: Boolean) {
        lobbyListener?.remove()
        lobbyListener = firestore().collection(LOBBIES).document(code)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    _sessionState.value = _sessionState.value.copy(
                        status = LanConnectionStatus.ERROR,
                        errorMessage = friendlyError(error)
                    )
                    return@addSnapshotListener
                }
                if (snap == null || !snap.exists()) {
                    if (_sessionState.value.role.isOnline) {
                        _sessionState.value = _sessionState.value.copy(
                            status = LanConnectionStatus.ERROR,
                            errorMessage = "Lobby closed."
                        )
                    }
                    return@addSnapshotListener
                }
                val status = snap.getString("status")
                if (status == "closed" && _sessionState.value.status != LanConnectionStatus.OFFLINE) {
                    _sessionState.value = _sessionState.value.copy(
                        status = LanConnectionStatus.ERROR,
                        errorMessage = "Opponent disconnected."
                    )
                    return@addSnapshotListener
                }
                if (asHost) {
                    val guestId = snap.getString("guestId")
                    if (!guestId.isNullOrEmpty() && status == "playing" && !guestNotified) {
                        guestNotified = true
                        val guestName = snap.getString("guestName").orEmpty().ifBlank { "Player 2" }
                        _sessionState.value = _sessionState.value.copy(
                            status = LanConnectionStatus.CONNECTED,
                            peerAddress = guestName,
                            errorMessage = null
                        )
                        watchActions(code)
                        scope.launch { onGuestConnected() }
                    }
                } else {
                    val hostName = snap.getString("hostName").orEmpty().ifBlank { "Player 1" }
                    if (_sessionState.value.peerAddress != hostName) {
                        _sessionState.value = _sessionState.value.copy(peerAddress = hostName)
                    }
                    val seq = snap.getLong("stateSeq") ?: 0L
                    val stateLine = snap.getString("state")
                    if (!stateLine.isNullOrEmpty() && seq != lastAppliedStateSeq) {
                        lastAppliedStateSeq = seq
                        scope.launch { onMessageReceived(stateLine) }
                    }
                }
            }
    }

    private fun watchActions(code: String) {
        actionListener?.remove()
        actionListener = firestore().collection(LOBBIES).document(code).collection(ACTIONS)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, error ->
                if (error != null) return@addSnapshotListener
                val changes = snap?.documentChanges.orEmpty()
                scope.launch {
                    changes.forEach { change ->
                        if (change.type != DocumentChange.Type.ADDED) return@forEach
                        if (!seenActionIds.add(change.document.id)) return@forEach
                        val line = change.document.getString("line") ?: return@forEach
                        onMessageReceived(line)
                    }
                }
            }
    }

    private fun startHeartbeat(code: String, asHost: Boolean) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(HEARTBEAT_MS)
                val field = if (asHost) "hostSeenAt" else "guestSeenAt"
                runCatching {
                    val ref = firestore().collection(LOBBIES).document(code)
                    Tasks.await(ref.update(field, System.currentTimeMillis()))
                    if (_sessionState.value.status == LanConnectionStatus.CONNECTED) {
                        val snap = Tasks.await(ref.get())
                        val peerField = if (asHost) "guestSeenAt" else "hostSeenAt"
                        val peerSeen = snap.getLong(peerField) ?: 0L
                        if (peerSeen > 0L && System.currentTimeMillis() - peerSeen > STALE_MS) {
                            _sessionState.value = _sessionState.value.copy(
                                status = LanConnectionStatus.ERROR,
                                errorMessage = "Opponent timed out."
                            )
                        }
                    }
                }
            }
        }
    }

    private fun stopListeners() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        lobbyListener?.remove()
        lobbyListener = null
        actionListener?.remove()
        actionListener = null
        guestNotified = false
        stateSeq = 0
        lastAppliedStateSeq = -1
        seenActionIds.clear()
    }

    private fun playerId(): String {
        if (uid.isNotEmpty()) return uid
        val context = contextProvider?.invoke()
            ?: runCatching { FirebaseApp.getInstance().applicationContext }.getOrNull()
            ?: error("Online play is not configured yet.")
        runCatching {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_PLAYER_ID, null)
        if (!existing.isNullOrBlank()) {
            uid = existing
            return existing
        }
        val created = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_PLAYER_ID, created).apply()
        uid = created
        return created
    }

    private fun firestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    private fun friendlyError(error: Exception): String {
        val message = error.message.orEmpty()
        return when {
            message.contains("not configured", ignoreCase = true) ->
                "Online play needs Firebase configured on this build."
            message.contains("CONFIGURATION_NOT_FOUND", ignoreCase = true) ->
                "Online play is not configured on the server yet."
            message.contains("PERMISSION_DENIED", ignoreCase = true) ->
                "Lobby server rejected the request."
            message.isBlank() -> "Online lobby failed."
            else -> message.substringBefore('\n').take(180)
        }
    }

    private fun randomRoomCode(): String {
        return buildString(6) {
            repeat(6) { append(ALPHABET.random()) }
        }
    }

    private companion object {
        const val LOBBIES = "lobbies"
        const val ACTIONS = "actions"
        const val PREFS_NAME = "uno_chess_online"
        const val KEY_PLAYER_ID = "player_id"
        const val HEARTBEAT_MS = 20_000L
        const val STALE_MS = 120_000L
        const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val ROOM_CODE = Regex("^[A-Z0-9]{4,8}$")
    }
}
