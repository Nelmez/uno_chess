package com.example.network

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.TransactionOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

class FirestoreOnlineSession(
    private val onMessageReceived: (String) -> Unit,
    private val onGuestConnected: () -> Unit,
    private val contextProvider: (() -> Context?)? = null
) : OnlineMatchSession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sendMutex = Mutex()
    private val operationSeq = AtomicLong(0L)
    private val _sessionState = MutableStateFlow(LanSessionState())
    override val sessionState: StateFlow<LanSessionState> = _sessionState.asStateFlow()

    @Volatile private var uid: String = ""
    @Volatile private var lobbyCode: String = ""
    private var lobbyListener: ListenerRegistration? = null
    private var actionListener: ListenerRegistration? = null
    private var listListener: ListenerRegistration? = null
    private var pendingLobbyJob: Job? = null
    private var watchLobbiesJob: Job? = null
    private var heartbeatJob: Job? = null
    private var stateSeq: Long = 0
    private var lastAppliedStateSeq: Long = -1
    @Volatile private var guestNotified = false
    @Volatile private var watching = false
    private val seenActionIds = mutableSetOf<String>()

    private class LobbyCodeTakenException : Exception("Lobby code already exists.")

    override fun createLobby(displayName: String) {
        val opId = operationSeq.incrementAndGet()
        pendingLobbyJob?.cancel()
        stopListeners()
        lobbyCode = ""
        _sessionState.value = _sessionState.value.copy(
            role = NetworkRole.ONLINE_HOST,
            status = LanConnectionStatus.CONNECTING,
            roomCode = "",
            peerAddress = "",
            errorMessage = null,
            onlineErrorMessage = null
        )

        pendingLobbyJob = scope.launch {
            try {
                val playerId = resolvePlayerId()
                val name = displayName.trim().ifBlank { "Player 1" }.take(24)
                val db = firestore()
                ensureNetworkEnabled(db)

                val txOptions = TransactionOptions.Builder()
                    .setMaxAttempts(2)
                    .build()

                var createdCode = ""
                var lastCollisionError: Exception? = null
                repeat(MAX_CODE_ATTEMPTS) {
                    if (createdCode.isNotEmpty() || operationSeq.get() != opId) return@repeat
                    val candidate = randomRoomCode()
                    val now = System.currentTimeMillis()
                    val data = hashMapOf<String, Any>(
                        "code" to candidate,
                        "hostId" to playerId,
                        "hostName" to name,
                        "status" to "waiting",
                        "createdAt" to now,
                        "hostSeenAt" to now,
                        "stateSeq" to 0L
                    )
                    try {
                        val ref = db.collection(LOBBIES).document(candidate)
                        withTimeout(SERVER_TIMEOUT_MS) {
                            db.runTransaction(txOptions) { tx ->
                                if (tx.get(ref).exists()) {
                                    throw LobbyCodeTakenException()
                                }
                                tx.set(ref, data)
                                null
                            }.await()
                        }
                        createdCode = candidate
                    } catch (e: Exception) {
                        if (e is LobbyCodeTakenException || e.cause is LobbyCodeTakenException) {
                            lastCollisionError = e
                        } else {
                            throw e
                        }
                    }
                }

                if (operationSeq.get() != opId) return@launch
                if (createdCode.isEmpty()) {
                    throw lastCollisionError ?: IllegalStateException("Could not allocate a unique lobby code.")
                }

                lobbyCode = createdCode
                stateSeq = 0
                guestNotified = false
                _sessionState.value = _sessionState.value.copy(
                    role = NetworkRole.ONLINE_HOST,
                    status = LanConnectionStatus.HOSTING_WAITING,
                    roomCode = createdCode,
                    peerAddress = createdCode,
                    errorMessage = null,
                    onlineErrorMessage = null,
                    isOnlineServerReachable = true
                )
                watchLobby(db, createdCode, asHost = true, opId = opId)
                startHeartbeat(db, createdCode, asHost = true, opId = opId)
            } catch (e: CancellationException) {
                if (e is TimeoutCancellationException && operationSeq.get() == opId) {
                    val msg = friendlyError(e)
                    _sessionState.value = _sessionState.value.copy(
                        role = NetworkRole.ONLINE_HOST,
                        status = LanConnectionStatus.ERROR,
                        errorMessage = msg,
                        onlineErrorMessage = msg
                    )
                } else {
                    throw e
                }
            } catch (e: Exception) {
                if (operationSeq.get() != opId) return@launch
                val msg = friendlyError(e)
                _sessionState.value = _sessionState.value.copy(
                    role = NetworkRole.ONLINE_HOST,
                    status = LanConnectionStatus.ERROR,
                    errorMessage = msg,
                    onlineErrorMessage = msg
                )
            }
        }
    }

    override fun joinLobby(code: String, displayName: String) {
        val normalized = code.trim().uppercase()
        if (!ROOM_CODE.matches(normalized)) {
            val msg = "Enter a valid 4–8 character lobby code."
            _sessionState.value = _sessionState.value.copy(
                role = NetworkRole.ONLINE_GUEST,
                status = LanConnectionStatus.ERROR,
                errorMessage = msg,
                onlineErrorMessage = msg
            )
            return
        }

        val opId = operationSeq.incrementAndGet()
        pendingLobbyJob?.cancel()
        stopListeners()
        lobbyCode = ""
        _sessionState.value = _sessionState.value.copy(
            role = NetworkRole.ONLINE_GUEST,
            status = LanConnectionStatus.CONNECTING,
            roomCode = normalized,
            peerAddress = normalized,
            errorMessage = null,
            onlineErrorMessage = null
        )

        pendingLobbyJob = scope.launch {
            try {
                val playerId = resolvePlayerId()
                val name = displayName.trim().ifBlank { "Player 2" }.take(24)
                val db = firestore()
                ensureNetworkEnabled(db)

                val txOptions = TransactionOptions.Builder()
                    .setMaxAttempts(2)
                    .build()
                val ref = db.collection(LOBBIES).document(normalized)

                withTimeout(SERVER_TIMEOUT_MS) {
                    db.runTransaction(txOptions) { tx ->
                        val snap = tx.get(ref)
                        if (!snap.exists()) error("Lobby $normalized was not found.")
                        val status = snap.getString("status")
                        val guestId = snap.getString("guestId")
                        val hostId = snap.getString("hostId")
                        if (status != "waiting" || !guestId.isNullOrEmpty()) {
                            error("Lobby $normalized is no longer open.")
                        }
                        if (hostId == playerId) error("You are already hosting this lobby.")
                        tx.update(
                            ref,
                            mapOf(
                                "guestId" to playerId,
                                "guestName" to name,
                                "status" to "playing",
                                "guestSeenAt" to System.currentTimeMillis()
                            )
                        )
                        null
                    }.await()
                }

                if (operationSeq.get() != opId) return@launch
                lobbyCode = normalized
                lastAppliedStateSeq = -1
                _sessionState.value = _sessionState.value.copy(
                    role = NetworkRole.ONLINE_GUEST,
                    status = LanConnectionStatus.CONNECTED,
                    roomCode = normalized,
                    errorMessage = null,
                    onlineErrorMessage = null,
                    isOnlineServerReachable = true
                )
                watchLobby(db, normalized, asHost = false, opId = opId)
                startHeartbeat(db, normalized, asHost = false, opId = opId)
            } catch (e: CancellationException) {
                if (e is TimeoutCancellationException && operationSeq.get() == opId) {
                    val msg = friendlyError(e)
                    _sessionState.value = _sessionState.value.copy(
                        role = NetworkRole.ONLINE_GUEST,
                        status = LanConnectionStatus.ERROR,
                        roomCode = normalized,
                        errorMessage = msg,
                        onlineErrorMessage = msg
                    )
                } else {
                    throw e
                }
            } catch (e: Exception) {
                if (operationSeq.get() != opId) return@launch
                val msg = friendlyError(e)
                _sessionState.value = _sessionState.value.copy(
                    role = NetworkRole.ONLINE_GUEST,
                    status = LanConnectionStatus.ERROR,
                    roomCode = normalized,
                    errorMessage = msg,
                    onlineErrorMessage = msg
                )
            }
        }
    }

    override fun startWatchingLobbies() {
        if (watching) return
        watching = true
        beginLobbyWatch()
    }

    override fun refreshLobbies() {
        watching = true
        beginLobbyWatch()
    }

    private fun beginLobbyWatch() {
        watchLobbiesJob?.cancel()
        listListener?.remove()
        listListener = null
        _sessionState.value = _sessionState.value.copy(
            isListingLobbies = true,
            onlineErrorMessage = null
        )

        watchLobbiesJob = scope.launch {
            try {
                val playerId = resolvePlayerId()
                val db = firestore()
                ensureNetworkEnabled(db)

                val query = db.collection(LOBBIES)
                    .whereEqualTo("status", "waiting")
                    .limit(50)

                // Verify live server reachability with a bounded timeout so the UI never spins forever.
                val initialSnap = withTimeout(LOBBY_PROBE_TIMEOUT_MS) {
                    query.get(Source.SERVER).await()
                }
                if (!watching) return@launch

                val initialLobbies = parseOpenLobbies(initialSnap.documents, playerId)
                _sessionState.value = _sessionState.value.copy(
                    openLobbies = initialLobbies,
                    isListingLobbies = false,
                    isOnlineServerReachable = true,
                    onlineErrorMessage = if (_sessionState.value.status == LanConnectionStatus.ERROR && _sessionState.value.role.isOnline) {
                        _sessionState.value.onlineErrorMessage
                    } else {
                        null
                    }
                )

                listListener?.remove()
                listListener = query.addSnapshotListener { snap, error ->
                    if (!watching) return@addSnapshotListener
                    if (error != null) {
                        val msg = friendlyError(error)
                        _sessionState.value = _sessionState.value.copy(
                            isListingLobbies = false,
                            isOnlineServerReachable = false,
                            onlineErrorMessage = msg
                        )
                        return@addSnapshotListener
                    }
                    val lobbies = parseOpenLobbies(snap?.documents.orEmpty(), playerId)
                    val fromServer = snap != null && !snap.metadata.isFromCache
                    _sessionState.value = _sessionState.value.copy(
                        openLobbies = lobbies,
                        isListingLobbies = false,
                        isOnlineServerReachable = if (fromServer) true else _sessionState.value.isOnlineServerReachable,
                        onlineErrorMessage = if (_sessionState.value.status == LanConnectionStatus.ERROR && _sessionState.value.role.isOnline) {
                            _sessionState.value.onlineErrorMessage
                        } else {
                            null
                        }
                    )
                }
            } catch (e: CancellationException) {
                if (e is TimeoutCancellationException && watching) {
                    _sessionState.value = _sessionState.value.copy(
                        isListingLobbies = false,
                        isOnlineServerReachable = false,
                        onlineErrorMessage = friendlyError(e)
                    )
                } else {
                    throw e
                }
            } catch (e: Exception) {
                if (!watching) return@launch
                _sessionState.value = _sessionState.value.copy(
                    isListingLobbies = false,
                    isOnlineServerReachable = false,
                    onlineErrorMessage = friendlyError(e)
                )
            }
        }
    }

    override fun stopWatchingLobbies() {
        watching = false
        watchLobbiesJob?.cancel()
        watchLobbiesJob = null
        listListener?.remove()
        listListener = null
        _sessionState.value = _sessionState.value.copy(isListingLobbies = false)
    }

    override fun sendLine(line: String) {
        val code = lobbyCode
        val playerId = uid
        if (code.isEmpty() || playerId.isEmpty()) return
        val role = _sessionState.value.role
        val opId = operationSeq.get()
        scope.launch {
            sendMutex.withLock {
                if (operationSeq.get() != opId || lobbyCode != code) return@withLock
                try {
                    val ref = firestore().collection(LOBBIES).document(code)
                    if (role.isHost) {
                        stateSeq += 1
                        val seq = stateSeq
                        withTimeout(SERVER_TIMEOUT_MS) {
                            ref.update(
                                mapOf(
                                    "state" to line,
                                    "stateSeq" to seq,
                                    "hostSeenAt" to System.currentTimeMillis()
                                )
                            ).await()
                        }
                    } else if (role.isGuest) {
                        withTimeout(SERVER_TIMEOUT_MS) {
                            ref.collection(ACTIONS).add(
                                mapOf(
                                    "fromId" to playerId,
                                    "line" to line,
                                    "createdAt" to System.currentTimeMillis()
                                )
                            ).await()
                        }
                    }
                } catch (e: CancellationException) {
                    if (e is TimeoutCancellationException && _sessionState.value.status == LanConnectionStatus.CONNECTED) {
                        val msg = friendlyError(e)
                        _sessionState.value = _sessionState.value.copy(
                            status = LanConnectionStatus.ERROR,
                            errorMessage = msg,
                            onlineErrorMessage = msg
                        )
                    } else {
                        throw e
                    }
                } catch (e: Exception) {
                    if (_sessionState.value.status == LanConnectionStatus.CONNECTED && operationSeq.get() == opId) {
                        val msg = friendlyError(e)
                        _sessionState.value = _sessionState.value.copy(
                            status = LanConnectionStatus.ERROR,
                            errorMessage = msg,
                            onlineErrorMessage = msg
                        )
                    }
                }
            }
        }
    }

    override fun leave() {
        operationSeq.incrementAndGet()
        pendingLobbyJob?.cancel()
        pendingLobbyJob = null
        val code = lobbyCode
        val role = _sessionState.value.role
        val signedIn = uid
        stopListeners()
        lobbyCode = ""
        _sessionState.value = LanSessionState(
            openLobbies = _sessionState.value.openLobbies,
            isListingLobbies = watching && _sessionState.value.isListingLobbies,
            isOnlineServerReachable = _sessionState.value.isOnlineServerReachable
        )
        if (code.isNotEmpty() && signedIn.isNotEmpty()) {
            scope.launch {
                runCatching {
                    val ref = firestore().collection(LOBBIES).document(code)
                    withTimeout(6_000L) {
                        if (role == NetworkRole.ONLINE_HOST) {
                            ref.update("status", "closed").await()
                        } else if (role == NetworkRole.ONLINE_GUEST) {
                            val snap = ref.get().await()
                            when (snap.getString("status")) {
                                "waiting" -> ref.update(
                                    mapOf(
                                        "guestId" to FieldValue.delete(),
                                        "guestName" to FieldValue.delete(),
                                        "guestSeenAt" to FieldValue.delete()
                                    )
                                ).await()
                                "playing" -> ref.update("status", "closed").await()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun shutdown() {
        stopWatchingLobbies()
        leave()
        scope.cancel()
    }

    private fun watchLobby(db: FirebaseFirestore, code: String, asHost: Boolean, opId: Long) {
        lobbyListener?.remove()
        lobbyListener = db.collection(LOBBIES).document(code)
            .addSnapshotListener { snap, error ->
                if (operationSeq.get() != opId) return@addSnapshotListener
                if (error != null) {
                    val msg = friendlyError(error)
                    _sessionState.value = _sessionState.value.copy(
                        status = LanConnectionStatus.ERROR,
                        errorMessage = msg,
                        onlineErrorMessage = msg
                    )
                    return@addSnapshotListener
                }
                if (snap == null || !snap.exists()) {
                    if (_sessionState.value.role.isOnline) {
                        val msg = "Lobby closed."
                        _sessionState.value = _sessionState.value.copy(
                            status = LanConnectionStatus.ERROR,
                            errorMessage = msg,
                            onlineErrorMessage = msg
                        )
                    }
                    return@addSnapshotListener
                }
                val status = snap.getString("status")
                if (status == "closed" && _sessionState.value.status != LanConnectionStatus.OFFLINE) {
                    val msg = "Opponent disconnected."
                    _sessionState.value = _sessionState.value.copy(
                        status = LanConnectionStatus.ERROR,
                        errorMessage = msg,
                        onlineErrorMessage = msg
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
                            errorMessage = null,
                            onlineErrorMessage = null
                        )
                        watchActions(db, code, opId)
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

    private fun watchActions(db: FirebaseFirestore, code: String, opId: Long) {
        actionListener?.remove()
        actionListener = db.collection(LOBBIES).document(code).collection(ACTIONS)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, error ->
                if (error != null || operationSeq.get() != opId) return@addSnapshotListener
                val changes = snap?.documentChanges.orEmpty()
                scope.launch {
                    changes.forEach { change ->
                        if (change.type != DocumentChange.Type.ADDED) return@forEach
                        val isNew = synchronized(seenActionIds) { seenActionIds.add(change.document.id) }
                        if (!isNew) return@forEach
                        val line = change.document.getString("line") ?: return@forEach
                        onMessageReceived(line)
                    }
                }
            }
    }

    private fun startHeartbeat(db: FirebaseFirestore, code: String, asHost: Boolean, opId: Long) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            var lastObservedPeerSeen = -1L
            var lastPeerActivityNanos = System.nanoTime()
            while (isActive && operationSeq.get() == opId) {
                delay(HEARTBEAT_MS)
                if (operationSeq.get() != opId) break
                val field = if (asHost) "hostSeenAt" else "guestSeenAt"
                runCatching {
                    val ref = db.collection(LOBBIES).document(code)
                    withTimeout(8_000L) {
                        ref.update(field, System.currentTimeMillis()).await()
                    }
                    if (_sessionState.value.status == LanConnectionStatus.CONNECTED) {
                        val snap = withTimeout(8_000L) { ref.get(Source.SERVER).await() }
                        val peerField = if (asHost) "guestSeenAt" else "hostSeenAt"
                        val peerSeen = snap.getLong(peerField) ?: 0L
                        val nowNanos = System.nanoTime()
                        if (peerSeen > 0L) {
                            if (lastObservedPeerSeen < 0L || peerSeen != lastObservedPeerSeen) {
                                lastObservedPeerSeen = peerSeen
                                lastPeerActivityNanos = nowNanos
                            } else {
                                val elapsedMs = (nowNanos - lastPeerActivityNanos) / 1_000_000L
                                if (elapsedMs > PEER_TIMEOUT_MS) {
                                    val msg = "Opponent timed out."
                                    _sessionState.value = _sessionState.value.copy(
                                        status = LanConnectionStatus.ERROR,
                                        errorMessage = msg,
                                        onlineErrorMessage = msg
                                    )
                                }
                            }
                        }
                    } else {
                        lastPeerActivityNanos = System.nanoTime()
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
        synchronized(seenActionIds) { seenActionIds.clear() }
    }

    private fun parseOpenLobbies(
        documents: List<com.google.firebase.firestore.DocumentSnapshot>,
        currentPlayerId: String
    ): List<OnlineLobbySummary> {
        val now = System.currentTimeMillis()
        return documents.mapNotNull { doc ->
            val hostSeen = doc.getLong("hostSeenAt") ?: 0L
            // Tolerate minor clock skew across phones while pruning abandoned lobbies.
            if (hostSeen > 0L && now - hostSeen > LOBBY_STALE_MS) return@mapNotNull null
            if (doc.getString("hostId") == currentPlayerId) return@mapNotNull null
            val code = doc.getString("code") ?: doc.id
            val hostName = doc.getString("hostName").orEmpty().ifBlank { "Host" }
            OnlineLobbySummary(code, hostName, doc.getLong("createdAt") ?: 0L)
        }.sortedByDescending { it.createdAtMs }
    }

    private suspend fun ensureNetworkEnabled(db: FirebaseFirestore) {
        runCatching {
            withTimeout(3_000L) {
                db.enableNetwork().await()
            }
        }
    }

    private fun resolveContext(): Context {
        return contextProvider?.invoke()
            ?: runCatching { FirebaseApp.getInstance().applicationContext }.getOrNull()
            ?: error("Online play is not configured in this build.")
    }

    private fun resolvePlayerId(): String {
        if (uid.isNotEmpty()) return uid
        val context = resolveContext()
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

    private fun firestore(): FirebaseFirestore {
        val context = resolveContext()
        val app = FirebaseApp.getApps(context).firstOrNull()
            ?: FirebaseApp.initializeApp(context)
            ?: error("Online play is not configured in this APK build (missing google-services.json).")

        val dbIdRes = context.resources.getIdentifier(
            "firestore_database_id",
            "string",
            context.packageName
        )
        val databaseId = if (dbIdRes != 0) context.getString(dbIdRes).trim() else ""
        return if (databaseId.isNotEmpty() && databaseId != "(default)") {
            FirebaseFirestore.getInstance(app, databaseId)
        } else {
            FirebaseFirestore.getInstance(app)
        }
    }

    internal fun friendlyError(error: Throwable): String {
        val cause = (error.cause as? FirebaseFirestoreException) ?: (error as? FirebaseFirestoreException)
        if (error is TimeoutCancellationException) {
            return "Online server didn't respond in time. Check your connection and verify Cloud Firestore is active."
        }
        if (cause != null) {
            return when (cause.code) {
                FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                    "Server rejected access (PERMISSION_DENIED). Make sure firestore.rules is deployed to Firebase."
                FirebaseFirestoreException.Code.NOT_FOUND ->
                    "Firestore database not found (NOT_FOUND). Create the Cloud Firestore database in Firebase Console."
                FirebaseFirestoreException.Code.UNAVAILABLE,
                FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
                    "Can't reach the online server (${cause.code.name}). Check your internet connection or try again."
                FirebaseFirestoreException.Code.FAILED_PRECONDITION ->
                    "Firestore isn't ready (${cause.code.name}): ${cause.message?.substringBefore('\n').orEmpty().take(120)}"
                else ->
                    "Server error (${cause.code.name}): ${cause.message?.substringBefore('\n').orEmpty().take(120)}"
            }
        }
        val message = error.message.orEmpty()
        return when {
            message.contains("Default FirebaseApp is not initialized", ignoreCase = true) ||
                message.contains("missing google-services.json", ignoreCase = true) ->
                "Online play isn't configured in this APK build (missing Firebase config)."
            message.contains("not configured", ignoreCase = true) ->
                "Online play needs Firebase configured on this build."
            message.contains("CONFIGURATION_NOT_FOUND", ignoreCase = true) ->
                "Online play is not configured on the server yet."
            message.contains("PERMISSION_DENIED", ignoreCase = true) ->
                "Server rejected access (PERMISSION_DENIED). Deploy firestore.rules to Firebase."
            message.contains("client is offline", ignoreCase = true) ->
                "Can't reach the online server (offline). Check your internet connection and retry."
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
        const val MAX_CODE_ATTEMPTS = 5
        const val SERVER_TIMEOUT_MS = 12_000L
        const val LOBBY_PROBE_TIMEOUT_MS = 10_000L
        const val HEARTBEAT_MS = 20_000L
        const val PEER_TIMEOUT_MS = 120_000L
        const val LOBBY_STALE_MS = 600_000L
        const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val ROOM_CODE = Regex("^[A-Z0-9]{4,8}$")
    }
}
