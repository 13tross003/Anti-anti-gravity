package com.example.data

import android.content.Context
import com.example.R
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.example.model.AgentMessage
import com.example.model.AgentMode
import com.example.model.AgentProfile
import com.example.model.AgentSession
import com.example.model.ApprovalStatus
import com.example.model.Artifact
import com.example.model.ArtifactKind
import com.example.model.BuiltinTool
import com.example.model.GeminiModelOption
import com.example.model.MessageRole
import com.example.model.SessionStatus
import com.example.model.ToolStatus
import com.example.model.Workspace
import com.example.model.WorkspaceFile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class AntigravityRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    private fun sanitizeId(raw: String): String =
        raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(120).ifBlank { "id_${UUID.randomUUID().toString().take(8)}" }

    private fun <T> observeSafe(
        userId: String,
        path: String,
        source: Flow<T>,
    ): Flow<T> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        emitAll(
            source.catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val toLog = firestoreError ?: (error as? Exception)
                if (toLog != null && auth.currentUser?.uid == userId) {
                    handleFirestoreError(toLog, OperationType.LIST, path)
                }
                throw error
            }
        )
    }

    fun observeProfile(userId: String): Flow<AgentProfile?> = observeSafe(
        userId = userId,
        path = "users/$userId",
        source = db.collection("users").document(userId)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(AgentProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else null
            }
    )

    fun observeWorkspaces(userId: String): Flow<List<Workspace>> = observeSafe(
        userId = userId,
        path = "users/$userId/workspaces",
        source = db.collection("users").document(userId).collection("workspaces")
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { it.toObjects(Workspace::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
    )

    fun observeFiles(userId: String, workspaceId: String): Flow<List<WorkspaceFile>> = observeSafe(
        userId = userId,
        path = "users/$userId/workspaces/$workspaceId/files",
        source = db.collection("users").document(userId)
            .collection("workspaces").document(workspaceId)
            .collection("files")
            .snapshots()
            .map { snap ->
                snap.toObjects(WorkspaceFile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.path.lowercase() }
            }
    )

    fun observeSessions(userId: String): Flow<List<AgentSession>> = observeSafe(
        userId = userId,
        path = "users/$userId/sessions",
        source = db.collection("users").document(userId).collection("sessions")
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { it.toObjects(AgentSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
    )

    fun observeMessages(userId: String, sessionId: String): Flow<List<AgentMessage>> = observeSafe(
        userId = userId,
        path = "users/$userId/sessions/$sessionId/messages",
        source = db.collection("users").document(userId)
            .collection("sessions").document(sessionId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .snapshots()
            .map { it.toObjects(AgentMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
    )

    fun observeArtifacts(userId: String): Flow<List<Artifact>> = observeSafe(
        userId = userId,
        path = "users/$userId/artifacts",
        source = db.collection("users").document(userId).collection("artifacts")
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { it.toObjects(Artifact::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
    )

    suspend fun ensureSeededForUser(displayName: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Unauthenticated"))
        val userDoc = db.collection("users").document(uid)
        return try {
            val snap = userDoc.get().await()
            if (!snap.exists()) {
                val safeName = displayName.trim().ifBlank { "Antigravity Dev" }.take(80)
                val profilePayload = mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "preferredModel" to GeminiModelOption.GEMINI_3_8_FLASH.modelId,
                    "agentMode" to AgentMode.PLANNING.name,
                    "readOnlyMode" to false,
                    "enabledTools" to BuiltinTool.entries.map { it.name },
                    "blockDestructiveHooks" to true,
                    "darkTheme" to true,
                    "browserAllowlist" to listOf("antigravity.google", "localhost", "ai.google.dev"),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                userDoc.set(profilePayload).await()

                val wsRef = userDoc.collection("workspaces").document(AntigravitySeedData.DEFAULT_WORKSPACE_ID)
                wsRef.set(
                    mapOf(
                        "userId" to uid,
                        "name" to "antigravity-sdk-starter",
                        "description" to "Official Google Antigravity Python SDK workspace with pre_turn hooks & /browser preview",
                        "language" to "Python / Antigravity SDK",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()

                AntigravitySeedData.starterFiles.forEach { file ->
                    wsRef.collection("files").document(file.id).set(
                        mapOf(
                            "userId" to uid,
                            "workspaceId" to AntigravitySeedData.DEFAULT_WORKSPACE_ID,
                            "path" to file.path,
                            "language" to file.language,
                            "content" to file.content,
                            "previousContent" to file.previousContent,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp(),
                        )
                    ).await()
                }

                val sessRef = userDoc.collection("sessions").document(AntigravitySeedData.DEFAULT_SESSION_ID)
                sessRef.set(
                    mapOf(
                        "userId" to uid,
                        "workspaceId" to AntigravitySeedData.DEFAULT_WORKSPACE_ID,
                        "workspaceName" to "antigravity-sdk-starter",
                        "title" to "Configure @hooks.pre_turn safety policy",
                        "status" to SessionStatus.NEEDS_REVIEW.name,
                        "mode" to AgentMode.PLANNING.name,
                        "modelName" to GeminiModelOption.GEMINI_3_8_FLASH.modelId,
                        "summary" to "Added guard_destructive_commands hook & CapabilitiesConfig. Ready for review.",
                        "tasksTotal" to 4,
                        "tasksCompleted" to 2,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()

                sessRef.collection("messages").document("msg_seed_1").set(
                    mapOf(
                        "userId" to uid,
                        "sessionId" to AntigravitySeedData.DEFAULT_SESSION_ID,
                        "role" to MessageRole.USER.name,
                        "content" to "Set up LocalAgentConfig with @hooks.pre_turn to block destructive commands and restrict tools safely.",
                        "toolName" to "",
                        "toolStatus" to ToolStatus.NONE.name,
                        "artifactId" to "",
                        "createdAt" to FieldValue.serverTimestamp(),
                    )
                ).await()

                sessRef.collection("messages").document("msg_seed_2").set(
                    mapOf(
                        "userId" to uid,
                        "sessionId" to AntigravitySeedData.DEFAULT_SESSION_ID,
                        "role" to MessageRole.TOOL.name,
                        "content" to "Updated agent_quickstart.py (+18 / -3) with guard_destructive_commands and BuiltinTools allowlist.",
                        "toolName" to "WRITE_FILE",
                        "toolStatus" to ToolStatus.SUCCESS.name,
                        "artifactId" to AntigravitySeedData.DEFAULT_PLAN_ARTIFACT_ID,
                        "createdAt" to FieldValue.serverTimestamp(),
                    )
                ).await()

                sessRef.collection("messages").document("msg_seed_3").set(
                    mapOf(
                        "userId" to uid,
                        "sessionId" to AntigravitySeedData.DEFAULT_SESSION_ID,
                        "role" to MessageRole.AGENT.name,
                        "content" to "I configured `LocalAgentConfig` in `agent_quickstart.py` with `@hooks.pre_turn` and generated the Implementation Plan artifact for your review. Tap **Approve Plan** or continue chatting below.",
                        "toolName" to "",
                        "toolStatus" to ToolStatus.NONE.name,
                        "artifactId" to AntigravitySeedData.DEFAULT_PLAN_ARTIFACT_ID,
                        "createdAt" to FieldValue.serverTimestamp(),
                    )
                ).await()

                userDoc.collection("artifacts").document(AntigravitySeedData.DEFAULT_PLAN_ARTIFACT_ID).set(
                    mapOf(
                        "userId" to uid,
                        "workspaceId" to AntigravitySeedData.DEFAULT_WORKSPACE_ID,
                        "sessionId" to AntigravitySeedData.DEFAULT_SESSION_ID,
                        "title" to "Implementation Plan: SDK Safety Hooks",
                        "kind" to ArtifactKind.PLAN.name,
                        "content" to AntigravitySeedData.starterPlanMarkdown,
                        "approvalStatus" to ApprovalStatus.PENDING.name,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()

                val htmlFile = AntigravitySeedData.starterFiles.first { it.path == "preview.html" }
                userDoc.collection("artifacts").document(AntigravitySeedData.DEFAULT_HTML_ARTIFACT_ID).set(
                    mapOf(
                        "userId" to uid,
                        "workspaceId" to AntigravitySeedData.DEFAULT_WORKSPACE_ID,
                        "sessionId" to AntigravitySeedData.DEFAULT_SESSION_ID,
                        "title" to "SDK Status Card Preview",
                        "kind" to ArtifactKind.HTML_PREVIEW.name,
                        "content" to htmlFile.content,
                        "approvalStatus" to ApprovalStatus.APPROVED.name,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$uid")
            Result.failure(e)
        }
    }

    suspend fun updateProfile(
        preferredModel: String? = null,
        agentMode: AgentMode? = null,
        readOnlyMode: Boolean? = null,
        enabledTools: List<String>? = null,
        blockDestructiveHooks: Boolean? = null,
        darkTheme: Boolean? = null,
        browserAllowlist: List<String>? = null,
    ): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            path = "users/$uid"
            val updates = mutableMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
            preferredModel?.let { updates["preferredModel"] = it }
            agentMode?.let { updates["agentMode"] = it.name }
            readOnlyMode?.let { updates["readOnlyMode"] = it }
            enabledTools?.let { updates["enabledTools"] = it.take(10) }
            blockDestructiveHooks?.let { updates["blockDestructiveHooks"] = it }
            darkTheme?.let { updates["darkTheme"] = it }
            browserAllowlist?.let { updates["browserAllowlist"] = it.take(20) }
            db.collection("users").document(uid).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun createWorkspace(name: String, description: String, language: String): Result<String> {
        var path = "users"
        return try {
            val uid = requireUserId()
            val safeName = name.trim().take(80).ifBlank { "new-workspace" }
            val safeDesc = description.trim().take(300)
            val safeLang = language.trim().take(40).ifBlank { "Python" }
            val wsId = sanitizeId("ws_${UUID.randomUUID().toString().take(10)}")
            path = "users/$uid/workspaces/$wsId"
            val wsRef = db.collection("users").document(uid).collection("workspaces").document(wsId)
            wsRef.set(
                mapOf(
                    "userId" to uid,
                    "name" to safeName,
                    "description" to safeDesc,
                    "language" to safeLang,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()

            wsRef.collection("files").document(sanitizeId("f_readme")).set(
                mapOf(
                    "userId" to uid,
                    "workspaceId" to wsId,
                    "path" to "README.md",
                    "language" to "markdown",
                    "content" to "# $safeName\n\n$safeDesc\n\nConfigured for Google Antigravity ($safeLang).",
                    "previousContent" to "",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(wsId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun upsertFile(
        workspaceId: String,
        existingFileId: String?,
        filePath: String,
        language: String,
        newContent: String,
        previousContent: String,
    ): Result<String> {
        var docPath = "users"
        return try {
            val uid = requireUserId()
            val safeWsId = sanitizeId(workspaceId)
            val cleanPath = filePath.trim().take(200).ifBlank { "untitled.py" }
            val cleanLang = language.trim().take(30).ifBlank { "python" }
            val safeContent = newContent.take(99_000)
            val safePrev = previousContent.take(99_000)
            val fileId = existingFileId?.takeIf { it.isNotBlank() }?.let(::sanitizeId)
                ?: sanitizeId("file_${cleanPath.lowercase()}")
            val ref = db.collection("users").document(uid)
                .collection("workspaces").document(safeWsId)
                .collection("files").document(fileId)
            docPath = ref.path
            val exists = ref.get().await().exists()
            if (exists) {
                ref.update(
                    mapOf(
                        "path" to cleanPath,
                        "language" to cleanLang,
                        "content" to safeContent,
                        "previousContent" to safePrev,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            } else {
                ref.set(
                    mapOf(
                        "userId" to uid,
                        "workspaceId" to safeWsId,
                        "path" to cleanPath,
                        "language" to cleanLang,
                        "content" to safeContent,
                        "previousContent" to safePrev,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            }
            Result.success(fileId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteFile(workspaceId: String, fileId: String): Result<Unit> {
        var docPath = "users"
        return try {
            val uid = requireUserId()
            val ref = db.collection("users").document(uid)
                .collection("workspaces").document(sanitizeId(workspaceId))
                .collection("files").document(sanitizeId(fileId))
            docPath = ref.path
            ref.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun createSession(
        workspaceId: String,
        workspaceName: String,
        title: String,
        mode: AgentMode,
        modelName: String,
    ): Result<String> {
        var docPath = "users"
        return try {
            val uid = requireUserId()
            val sessionId = sanitizeId("sess_${UUID.randomUUID().toString().take(10)}")
            val ref = db.collection("users").document(uid).collection("sessions").document(sessionId)
            docPath = ref.path
            ref.set(
                mapOf(
                    "userId" to uid,
                    "workspaceId" to sanitizeId(workspaceId),
                    "workspaceName" to workspaceName.trim().take(80).ifBlank { "workspace" },
                    "title" to title.trim().take(120).ifBlank { "Agent Task" },
                    "status" to SessionStatus.RUNNING.name,
                    "mode" to mode.name,
                    "modelName" to modelName.take(60),
                    "summary" to "Dispatching task to Antigravity runtime...",
                    "tasksTotal" to 1,
                    "tasksCompleted" to 0,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(sessionId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateSession(
        sessionId: String,
        status: SessionStatus? = null,
        summary: String? = null,
        tasksTotal: Int? = null,
        tasksCompleted: Int? = null,
        mode: AgentMode? = null,
    ): Result<Unit> {
        var docPath = "users"
        return try {
            val uid = requireUserId()
            val ref = db.collection("users").document(uid)
                .collection("sessions").document(sanitizeId(sessionId))
            docPath = ref.path
            val updates = mutableMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
            status?.let { updates["status"] = it.name }
            summary?.let { updates["summary"] = it.take(500) }
            tasksTotal?.let { updates["tasksTotal"] = it.coerceIn(0, 100) }
            tasksCompleted?.let { updates["tasksCompleted"] = it.coerceIn(0, 100) }
            mode?.let { updates["mode"] = it.name }
            ref.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun addMessage(
        sessionId: String,
        role: MessageRole,
        content: String,
        toolName: String = "",
        toolStatus: ToolStatus = ToolStatus.NONE,
        artifactId: String = "",
    ): Result<String> {
        var docPath = "users"
        return try {
            val uid = requireUserId()
            val cleanSessId = sanitizeId(sessionId)
            val msgId = sanitizeId("msg_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(4)}")
            val ref = db.collection("users").document(uid)
                .collection("sessions").document(cleanSessId)
                .collection("messages").document(msgId)
            docPath = ref.path
            ref.set(
                mapOf(
                    "userId" to uid,
                    "sessionId" to cleanSessId,
                    "role" to role.name,
                    "content" to content.take(99_000).ifBlank { "..." },
                    "toolName" to toolName.take(60),
                    "toolStatus" to toolStatus.name,
                    "artifactId" to artifactId.take(128),
                    "createdAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(msgId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun createArtifact(
        workspaceId: String,
        sessionId: String,
        title: String,
        kind: ArtifactKind,
        content: String,
        approvalStatus: ApprovalStatus = ApprovalStatus.PENDING,
    ): Result<String> {
        var docPath = "users"
        return try {
            val uid = requireUserId()
            val artId = sanitizeId("art_${UUID.randomUUID().toString().take(10)}")
            val ref = db.collection("users").document(uid).collection("artifacts").document(artId)
            docPath = ref.path
            ref.set(
                mapOf(
                    "userId" to uid,
                    "workspaceId" to sanitizeId(workspaceId),
                    "sessionId" to sanitizeId(sessionId),
                    "title" to title.trim().take(120).ifBlank { kind.label },
                    "kind" to kind.name,
                    "content" to content.take(99_000),
                    "approvalStatus" to approvalStatus.name,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(artId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateArtifactApproval(artifactId: String, approvalStatus: ApprovalStatus): Result<Unit> {
        var docPath = "users"
        return try {
            val uid = requireUserId()
            val ref = db.collection("users").document(uid)
                .collection("artifacts").document(sanitizeId(artifactId))
            docPath = ref.path
            ref.update(
                mapOf(
                    "approvalStatus" to approvalStatus.name,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }
}
