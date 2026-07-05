package com.gongpx.androidacpclient.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gongpx.androidacpclient.data.bridge.BridgeClient
import com.gongpx.androidacpclient.data.model.ConnectionState
import com.gongpx.androidacpclient.data.model.Machine
import com.gongpx.androidacpclient.data.pairing.PairingLinkParser
import com.gongpx.androidacpclient.data.store.MachineStore
import kotlinx.coroutines.launch

private enum class AppTab(val label: String) {
    Chats("Chats"),
    Approvals("Approvals"),
    Machines("Machines"),
    Settings("Settings"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AndroidAcpClientApp(incomingPairingLink: MutableState<String?>) {
    val context = LocalContext.current
    val store = remember { MachineStore(context.applicationContext) }
    val bridgeClient = remember { BridgeClient() }
    val parser = remember { PairingLinkParser() }
    val machines = remember { mutableStateListOf<Machine>() }
    var selectedTab by remember { mutableStateOf(AppTab.Machines) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun upsertMachine(machine: Machine) {
        val index = machines.indexOfFirst { it.id == machine.id }
        if (index >= 0) {
            machines[index] = machine
        } else {
            machines.add(machine)
        }
        store.upsert(machine)
    }

    fun pairFromLink(link: String) {
        val payload = parser.parse(link).getOrElse {
            statusMessage = it.message ?: "Invalid pairing link."
            return
        }
        statusMessage = "Waiting for bridge approval on ${payload.machineName}..."
        scope.launch {
            bridgeClient.redeemPairing(payload)
                .onSuccess { machine ->
                    upsertMachine(machine)
                    statusMessage = "Paired ${machine.displayName}. Testing connection..."
                    bridgeClient.fetchMachineDetails(machine)
                        .onSuccess {
                            upsertMachine(it)
                            statusMessage = "${it.displayName} is online."
                        }
                        .onFailure {
                            statusMessage = "Paired, but health check failed: ${it.message}"
                        }
                }
                .onFailure {
                    statusMessage = "Pairing failed: ${it.message}"
                }
        }
    }

    LaunchedEffect(Unit) {
        machines.clear()
        machines.addAll(store.load())
    }

    LaunchedEffect(incomingPairingLink.value) {
        incomingPairingLink.value?.let { link ->
            selectedTab = AppTab.Machines
            pairFromLink(link)
            incomingPairingLink.value = null
        }
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = { TopAppBar(title = { Text("Android ACP") }) },
                bottomBar = {
                    NavigationBar {
                        AppTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = selectedTab == tab,
                                onClick = { selectedTab = tab },
                                label = { Text(tab.label) },
                                icon = {},
                            )
                        }
                    }
                },
            ) { padding ->
                when (selectedTab) {
                    AppTab.Chats -> PlaceholderScreen(padding, "Chats", "Chat creation starts after a machine and workspace are paired.")
                    AppTab.Approvals -> PlaceholderScreen(padding, "Approvals", "Pending agent approvals will appear here.")
                    AppTab.Machines -> MachinesScreen(
                        padding = padding,
                        machines = machines,
                        statusMessage = statusMessage,
                        onPairLink = ::pairFromLink,
                        onRefreshMachine = { machine ->
                            scope.launch {
                                bridgeClient.fetchMachineDetails(machine)
                                    .onSuccess {
                                        upsertMachine(it)
                                        statusMessage = "${it.displayName} is online."
                                    }
                                    .onFailure {
                                        val offline = machine.copy(connectionState = ConnectionState.Offline)
                                        upsertMachine(offline)
                                        statusMessage = "Connection failed: ${it.message}"
                                    }
                            }
                        },
                    )
                    AppTab.Settings -> PlaceholderScreen(padding, "Settings", "Settings will manage security, notifications, and bridge defaults.")
                }
            }
        }
    }
}

@Composable
private fun MachinesScreen(
    padding: PaddingValues,
    machines: List<Machine>,
    statusMessage: String?,
    onPairLink: (String) -> Unit,
    onRefreshMachine: (Machine) -> Unit,
) {
    var pairingLink by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(16.dp),
    ) {
        item {
            Text("Add Machine", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text("Scan the bridge QR code with Android camera, or paste the acpclient://pair link below.")
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = pairingLink,
                onValueChange = { pairingLink = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Pairing link") },
                minLines = 3,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                enabled = pairingLink.isNotBlank(),
                onClick = { onPairLink(pairingLink) },
            ) {
                Text("Pair Machine")
            }
            statusMessage?.let {
                Spacer(Modifier.height(8.dp))
                Text(it)
            }
            Spacer(Modifier.height(24.dp))
            Text("Machines", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
        }

        if (machines.isEmpty()) {
            item {
                Text("No machines paired yet.")
            }
        } else {
            items(machines, key = { it.id }) { machine ->
                MachineCard(machine = machine, onRefresh = { onRefreshMachine(machine) })
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun MachineCard(machine: Machine, onRefresh: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(machine.displayName, style = MaterialTheme.typography.titleMedium)
            Text(machine.endpoint)
            Text("State: ${machine.connectionState}")
            machine.bridgeVersion?.let { Text("Bridge: $it") }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onRefresh) {
                Text("Test Connection")
            }
            if (machine.workspaces.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Workspaces", style = MaterialTheme.typography.titleSmall)
                machine.workspaces.forEach { workspace ->
                    Text("${workspace.displayName}: ${workspace.absolutePath}")
                }
            }
            if (machine.agents.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Agents", style = MaterialTheme.typography.titleSmall)
                machine.agents.forEach { agent ->
                    Text("${agent.displayName}: ${agent.status}")
                }
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(padding: PaddingValues, title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(body)
    }
}

