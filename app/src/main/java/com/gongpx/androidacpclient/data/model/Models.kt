package com.gongpx.androidacpclient.data.model

data class PairingPayload(
    val version: Int,
    val type: String,
    val machineName: String,
    val endpoint: String,
    val pairingId: String,
    val pairingToken: String,
    val expiresAt: String,
    val bridgeFingerprint: String,
)

data class Machine(
    val id: String,
    val displayName: String,
    val endpoint: String,
    val deviceToken: String,
    val bridgeFingerprint: String,
    val bridgeVersion: String? = null,
    val connectionState: ConnectionState = ConnectionState.Unknown,
    val workspaces: List<Workspace> = emptyList(),
    val agents: List<Agent> = emptyList(),
)

data class Workspace(
    val id: String,
    val displayName: String,
    val absolutePath: String,
)

data class Agent(
    val id: String,
    val displayName: String,
    val status: String,
)

enum class ConnectionState {
    Unknown,
    Online,
    Offline,
}

