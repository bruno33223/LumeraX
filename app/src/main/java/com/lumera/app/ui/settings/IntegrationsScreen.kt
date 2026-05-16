package com.lumera.app.ui.settings

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import com.lumera.app.data.auth.StremioConnectionState
import com.lumera.app.data.trakt.DeviceAuthState
import com.lumera.app.remote_input.ServerInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.google.android.gms.auth.api.signin.GoogleSignIn
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.lumera.app.data.backup.DriveBackupManager
import com.lumera.app.ui.addons.VoidButton
import com.lumera.app.ui.addons.VoidDialog
import androidx.compose.material.icons.filled.Storage

@Composable
fun IntegrationsScreen(
    onBack: () -> Unit,
    viewModel: IntegrationsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    // Dialog state
    var showConnectDialog by remember { mutableStateOf(false) }
    var showManagementDialog by remember { mutableStateOf(false) }
    var showDisconnectConfirm by remember { mutableStateOf(false) }
    var showDriveDialog by remember { mutableStateOf(false) }

    val driveSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            showDriveDialog = true
        }
    }

    // Handle events
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is IntegrationsEvent.LoginSuccess -> {
                    Toast.makeText(context, "Connected to Stremio!", Toast.LENGTH_SHORT).show()
                    showConnectDialog = false
                }
                is IntegrationsEvent.LoginError -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                }
                is IntegrationsEvent.SyncComplete -> {
                    Toast.makeText(context, "Imported ${event.count} addon(s)", Toast.LENGTH_SHORT).show()
                }
                is IntegrationsEvent.Disconnected -> {
                    Toast.makeText(context, "Disconnected from Stremio", Toast.LENGTH_SHORT).show()
                }
                is IntegrationsEvent.BackupSuccess -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
                is IntegrationsEvent.BackupError -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val goBackModifier = Modifier.onPreviewKeyEvent {
        if (it.key == Key.DirectionLeft && it.type == KeyEventType.KeyDown) {
            onBack()
            true
        } else false
    }
    
    // Block Up navigation when top nav is active
    val upBlockModifier = Modifier.onPreviewKeyEvent {
        if (it.key == Key.DirectionUp && it.type == KeyEventType.KeyDown) {
            true // Consume the event to block focus escape
        } else false
    }

    // Extract connection state for use in dialogs
    val stremioConnected = state.connectionState is StremioConnectionState.Connected
    val stremioEmail = (state.connectionState as? StremioConnectionState.Connected)?.email

    var showTmdbSettings by remember { mutableStateOf(false) }
    var showTraktDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Top
    ) {
        // Header
        Text(
            "Integrations",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
            color = Color.White
        )
        Text(
            "Connect external services to enhance your experience.",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            color = Color.White.copy(0.6f),
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(Modifier.height(32.dp))

        // Stremio Integration Item
        IntegrationItem(
            title = "Stremio",
            subtitle = if (stremioConnected) stremioEmail ?: "Connected" else "Not Connected",
            isConnected = stremioConnected,
            onClick = {
                if (stremioConnected) {
                    showManagementDialog = true
                } else {
                    showConnectDialog = true
                }
            },
            modifier = goBackModifier.then(upBlockModifier)
        )

        Spacer(Modifier.height(12.dp))

        // TMDB Integration Item
        IntegrationItem(
            title = "TMDB",
            subtitle = if (state.tmdbEnabled) {
                val langName = TMDB_LANGUAGE_OPTIONS
                    .firstOrNull { it.second == state.tmdbLanguage }?.first
                    ?: "Device Language"
                "Enabled · $langName"
            } else "Disabled",
            isConnected = state.tmdbEnabled,
            onClick = { showTmdbSettings = true },
            modifier = goBackModifier
        )

        Spacer(Modifier.height(12.dp))

        // Trakt Integration Item
        IntegrationItem(
            title = "Trakt",
            subtitle = if (state.traktConnected) "Connected" else "Not Connected",
            isConnected = state.traktConnected,
            onClick = { showTraktDialog = true },
            modifier = goBackModifier
        )

        Spacer(Modifier.height(12.dp))

        // Google Drive Integration Item
        val driveAccount = GoogleSignIn.getLastSignedInAccount(context)
        IntegrationItem(
            title = "Google Drive",
            subtitle = driveAccount?.email ?: "Not Connected",
            isConnected = driveAccount != null,
            onClick = {
                if (driveAccount != null) {
                    showDriveDialog = true
                } else {
                    val gso = DriveBackupManager.getGoogleSignInOptions()
                    val signInClient = GoogleSignIn.getClient(context, gso)
                    driveSignInLauncher.launch(signInClient.signInIntent)
                }
            },
            modifier = goBackModifier
        )
    }

    // Connect Dialog
    if (showConnectDialog) {
        ConnectStremioDialog(
            isLoading = state.isLoading,
            onDismiss = { showConnectDialog = false },
            onLogin = { email, password ->
                viewModel.login(email, password)
            }
        )
    }

    // Management Dialog (when connected)
    if (showManagementDialog) {
        StremioManagementDialog(
            email = stremioEmail ?: "",
            onDismiss = { showManagementDialog = false },
            onSyncAddons = {
                showManagementDialog = false
                viewModel.syncAddons()
            },
            onDisconnect = {
                showManagementDialog = false
                showDisconnectConfirm = true
            }
        )
    }

    // Disconnect Confirmation
    if (showDisconnectConfirm) {
        DisconnectConfirmDialog(
            onDismiss = { showDisconnectConfirm = false },
            onConfirm = {
                showDisconnectConfirm = false
                viewModel.disconnect()
            }
        )
    }

    // Addon Import Dialog
    state.pendingAddons?.let { addons ->
        com.lumera.app.ui.addons.AddonImportDialog(
            addons = addons,
            onDismissRequest = { viewModel.dismissImportDialog() },
            onConfirmImport = { selectedAddons ->
                viewModel.importAddons(selectedAddons)
            }
        )
    }

    // TMDB Settings Dialog
    if (showTmdbSettings) {
        TmdbSettingsDialog(
            enabled = state.tmdbEnabled,
            language = state.tmdbLanguage,
            onEnabledChange = { viewModel.updateTmdbEnabled(it) },
            onLanguageChange = { viewModel.updateTmdbLanguage(it) },
            onDismiss = { showTmdbSettings = false }
        )
    }

    // Trakt Auth Dialog
    if (showTraktDialog) {
        TraktAuthDialog(
            isConnected = state.traktConnected,
            authState = state.traktAuthState,
            onConnect = { viewModel.startTraktAuth() },
            onDisconnect = { viewModel.disconnectTrakt() },
            onDismiss = {
                showTraktDialog = false
                viewModel.resetTraktAuthState()
            }
        )
    }

    // Drive Backup Dialog
    if (showDriveDialog) {
        val driveAccount = GoogleSignIn.getLastSignedInAccount(context)
        VoidDialog(
            onDismissRequest = { showDriveDialog = false },
            title = "Google Drive Sync"
        ) {
            Text(
                "Keep your profiles, themes and addons synced across devices using your Google Drive AppData storage.",
                color = Color.Gray,
                style = MaterialTheme.typography.bodyMedium
            )
            
            if (driveAccount != null) {
                Text(
                    driveAccount.email ?: "",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(Modifier.height(24.dp))

            VoidButton(
                text = "Backup para Google Drive",
                onClick = {
                    viewModel.exportBackup(context)
                    showDriveDialog = false
                },
                modifier = Modifier.fillMaxWidth(),
                isPrimary = true
            )

            Spacer(Modifier.height(12.dp))

            VoidButton(
                text = "Restaurar Backup",
                onClick = {
                    viewModel.restoreBackup(context)
                    showDriveDialog = false
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            var backupStatusMessage by remember { mutableStateOf("") }
            val statusScope = rememberCoroutineScope()

            VoidButton(
                text = "Verificar Status do Backup",
                onClick = {
                    statusScope.launch {
                        backupStatusMessage = "Consultando Google Drive..."
                        backupStatusMessage = DriveBackupManager.getInstance().checkBackupStatus(context)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            if (backupStatusMessage.isNotEmpty()) {
                Text(
                    text = backupStatusMessage,
                    color = Color.Yellow,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(Modifier.height(24.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                VoidButton(
                    text = "Logout", 
                    onClick = {
                        val gso = DriveBackupManager.getGoogleSignInOptions()
                        GoogleSignIn.getClient(context, gso).signOut().addOnCompleteListener {
                            showDriveDialog = false
                        }
                    }, 
                    modifier = Modifier.width(120.dp),
                    isDestructive = true
                )
                Spacer(Modifier.width(12.dp))
                VoidButton(text = "Close", onClick = { showDriveDialog = false }, modifier = Modifier.width(120.dp))
            }
        }
    }
}

@Composable
private fun IntegrationItem(
    title: String,
    subtitle: String,
    isConnected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val scale by animateFloatAsState(if (isFocused) 1.02f else 1f)
    val borderColor by animateColorAsState(
        if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent
    )
    val bgColor = Color.White.copy(0.05f)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(if (isFocused) 2.dp else 0.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .focusable(interactionSource = interactionSource)
            .padding(16.dp)
    ) {
        // Icon
        Icon(
            imageVector = if (isConnected) Icons.Default.Cloud else Icons.Default.CloudOff,
            contentDescription = null,
            tint = if (isConnected) MaterialTheme.colorScheme.primary else Color.Gray,
            modifier = Modifier.size(32.dp)
        )

        Spacer(Modifier.width(16.dp))

        // Text
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isConnected) MaterialTheme.colorScheme.primary else Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Status indicator
        if (isFocused) {
            Text(
                if (isConnected) "Manage" else "Connect",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

// =============================================================================
// CONNECT STREMIO DIALOG (Split Layout: Manual + QR Code)
// =============================================================================

@Composable
private fun ConnectStremioDialog(
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onLogin: (email: String, password: String) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var serverInfo by remember { mutableStateOf<ServerInfo?>(null) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    val emailFocusRequester = remember { FocusRequester() }
    val serverManager = remember { com.lumera.app.remote_input.IntegrationServerManager() }

    // Start server for QR code login
    LaunchedEffect(Unit) {
        delay(100)
        emailFocusRequester.requestFocus()
        
        val info = serverManager.startServer { receivedEmail, receivedPassword ->
            // Login with credentials received from phone
            onLogin(receivedEmail, receivedPassword)
        }
        
        if (info != null) {
            serverInfo = info
            qrBitmap = generateQrCode(info.url)
        }
    }

    // Stop server when dialog closes
    DisposableEffect(Unit) {
        onDispose {
            serverManager.stopServer()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 65.dp)
                    .width(900.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.background)
                    .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(16.dp))
                    .imePadding()
                    .padding(horizontal = 32.dp, vertical = 24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(40.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // LEFT: Header + Manual Input
                    Column(
                        modifier = Modifier.weight(1.3f)
                    ) {
                        // Header
                        Text(
                            "Connect your Stremio account",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Visible,
                            softWrap = false
                        )
                        Text(
                            "Import your existing addons from Stremio",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Visible,
                            softWrap = false
                        )

                        // Email & Password in a column for vertical layout
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            IntegrationTextField(
                                value = email,
                                onValueChange = { email = it },
                                placeholder = "Email",
                                keyboardType = KeyboardType.Email,
                                focusRequester = emailFocusRequester,
                                modifier = Modifier.fillMaxWidth()
                            )

                            IntegrationTextField(
                                value = password,
                                onValueChange = { password = it },
                                placeholder = "Password",
                                isPassword = true,
                                keyboardType = KeyboardType.Password,
                                modifier = Modifier.fillMaxWidth(),
                                onDone = {
                                    if (email.isNotBlank() && password.isNotBlank() && !isLoading) {
                                        onLogin(email, password)
                                    }
                                }
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        // Buttons row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            IntegrationButton(
                                text = if (isLoading) "Connecting..." else "Connect",
                                onClick = { onLogin(email, password) },
                                enabled = email.isNotBlank() && password.isNotBlank() && !isLoading,
                                isPrimary = true,
                                modifier = Modifier.width(140.dp)
                            )
                        }
                    }

                    // Vertical Divider
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(160.dp)
                            .background(Color.White.copy(0.1f))
                    )

                    // RIGHT: QR Code (compact)
                    Column(
                        modifier = Modifier.weight(0.7f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "Or Scan with Phone",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Visible,
                            softWrap = false,
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.height(16.dp))

                        if (qrBitmap != null && serverInfo != null) {
                            Box(
                                modifier = Modifier
                                    .size(150.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.White)
                                    .padding(4.dp)
                            ) {
                                Image(
                                    bitmap = qrBitmap!!.asImageBitmap(),
                                    contentDescription = "QR Code",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Spacer(Modifier.height(12.dp))

                            Text(
                                serverInfo!!.url,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = Color.Gray,
                                maxLines = 1,
                                overflow = TextOverflow.Visible,
                                softWrap = false,
                                textAlign = TextAlign.Center
                            )
                        } else {
                            CircularProgressIndicator(
                                modifier = Modifier.size(40.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 3.dp
                            )
                        }
                    }
                }

                // Loading overlay
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

// =============================================================================
// STREMIO MANAGEMENT DIALOG
// =============================================================================

@Composable
private fun StremioManagementDialog(
    email: String,
    onDismiss: () -> Unit,
    onSyncAddons: () -> Unit,
    onDisconnect: () -> Unit
) {
    val syncFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(100)
        syncFocusRequester.requestFocus()
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(400.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.background)
                .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(16.dp))
                .padding(24.dp)
        ) {
            Column {
                Text(
                    "Stremio Account",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )

                Text(
                    email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(Modifier.height(24.dp))

                // Sync Addons
                ManagementMenuItem(
                    icon = Icons.Default.Sync,
                    title = "Add New Addons",
                    subtitle = "Import addons from your Stremio account",
                    onClick = onSyncAddons,
                    focusRequester = syncFocusRequester
                )

                Spacer(Modifier.height(12.dp))

                // Disconnect
                ManagementMenuItem(
                    icon = Icons.Default.Logout,
                    title = "Disconnect Account",
                    subtitle = "Remove Stremio connection",
                    onClick = onDisconnect,
                    isDestructive = true
                )

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IntegrationButton(
                        text = "Close",
                        onClick = onDismiss,
                        modifier = Modifier.width(100.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ManagementMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    isDestructive: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(if (isFocused) 1.02f else 1f)
    val bgColor by animateColorAsState(if (isFocused) Color.White.copy(0.1f) else Color.White.copy(0.05f))
    val iconColor = if (isDestructive) Color.Red else if (isFocused) MaterialTheme.colorScheme.primary else Color.Gray

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .focusable(interactionSource = interactionSource)
            .padding(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = if (isDestructive) Color.Red else Color.White
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
    }
}

// =============================================================================
// DISCONNECT CONFIRMATION DIALOG
// =============================================================================

@Composable
private fun DisconnectConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val confirmFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(100)
        confirmFocusRequester.requestFocus()
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(350.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.background)
                .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(16.dp))
                .padding(24.dp)
        ) {
            Column {
                Text(
                    "Disconnect Stremio?",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    "Your installed addons will remain, but you won't be able to sync new addons until you reconnect.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IntegrationButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )

                    IntegrationButton(
                        text = "Disconnect",
                        onClick = onConfirm,
                        isDestructive = true,
                        modifier = Modifier.weight(1f),
                        focusRequester = confirmFocusRequester
                    )
                }
            }
        }
    }
}

// =============================================================================
// TMDB SETTINGS DIALOG
// =============================================================================

private val TMDB_LANGUAGE_OPTIONS: List<Pair<String, String>> = listOf(
    "Device Language" to "",
    "English" to "en",
    "Spanish" to "es",
    "Spanish (Latin America)" to "es-419",
    "French" to "fr",
    "German" to "de",
    "Italian" to "it",
    "Portuguese" to "pt",
    "Portuguese (Brazil)" to "pt-BR",
    "Russian" to "ru",
    "Japanese" to "ja",
    "Korean" to "ko",
    "Chinese" to "zh",
    "Chinese (Simplified)" to "zh-CN",
    "Chinese (Traditional)" to "zh-TW",
    "Arabic" to "ar",
    "Hindi" to "hi",
    "Turkish" to "tr",
    "Polish" to "pl",
    "Dutch" to "nl",
    "Swedish" to "sv",
    "Norwegian" to "no",
    "Danish" to "da",
    "Finnish" to "fi",
    "Czech" to "cs",
    "Hungarian" to "hu",
    "Romanian" to "ro",
    "Thai" to "th",
    "Vietnamese" to "vi",
    "Indonesian" to "id",
    "Ukrainian" to "uk",
    "Greek" to "el",
    "Hebrew" to "he",
    "Malay" to "ms",
    "Croatian" to "hr",
    "Bulgarian" to "bg",
    "Slovak" to "sk",
    "Serbian" to "sr",
    "Filipino" to "tl",
    "Persian" to "fa",
    "Bengali" to "bn",
    "Tamil" to "ta",
    "Telugu" to "te",
    "Afrikaans" to "af",
    "Albanian" to "sq",
    "Armenian" to "hy",
    "Azerbaijani" to "az",
    "Basque" to "eu",
    "Belarusian" to "be",
    "Bosnian" to "bs",
    "Catalan" to "ca",
    "Estonian" to "et",
    "Georgian" to "ka",
    "Icelandic" to "is",
    "Irish" to "ga",
    "Kannada" to "kn",
    "Kazakh" to "kk",
    "Latvian" to "lv",
    "Lithuanian" to "lt",
    "Macedonian" to "mk",
    "Malayalam" to "ml",
    "Mongolian" to "mn",
    "Slovenian" to "sl",
    "Swahili" to "sw",
    "Urdu" to "ur"
)

@Composable
private fun TmdbSettingsDialog(
    enabled: Boolean,
    language: String,
    onEnabledChange: (Boolean) -> Unit,
    onLanguageChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val toggleFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(100)
        toggleFocusRequester.requestFocus()
    }

    val selectedIndex = TMDB_LANGUAGE_OPTIONS.indexOfFirst { it.second == language }.coerceAtLeast(0)
    val listState = rememberLazyListState()
    val accentColor = MaterialTheme.colorScheme.primary

    LaunchedEffect(selectedIndex) {
        if (selectedIndex > 0) runCatching { listState.scrollToItem(selectedIndex) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 65.dp)
                    .width(460.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.background)
                    .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(16.dp))
                    .padding(24.dp)
            ) {
                Column {
                    Text(
                        "TMDB Settings",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )

                    Text(
                        "Enrich metadata with localized info, cast, ratings, and more.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Spacer(Modifier.height(24.dp))

                    // Enable/Disable toggle
                    TmdbToggleItem(
                        title = "Enable TMDB",
                        subtitle = "Fetch enhanced metadata from The Movie Database",
                        isEnabled = enabled,
                        onToggle = { onEnabledChange(!enabled) },
                        focusRequester = toggleFocusRequester
                    )

                    Spacer(Modifier.height(20.dp))

                    // Language section header
                    Text(
                        "Metadata Language",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = if (enabled) Color.White else Color.White.copy(0.4f)
                    )
                    Text(
                        "Titles, descriptions, and logos will use this language when available.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (enabled) Color.Gray else Color.Gray.copy(0.5f),
                        modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                    )

                    // Scrollable language list
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                    ) {
                        itemsIndexed(TMDB_LANGUAGE_OPTIONS) { _, (displayName, code) ->
                            val isSelected = code == language
                            val interactionSource = remember { MutableInteractionSource() }
                            val isFocused by interactionSource.collectIsFocusedAsState()

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        when {
                                            isFocused -> Color.White.copy(0.1f)
                                            isSelected -> accentColor.copy(0.1f)
                                            else -> Color.Transparent
                                        }
                                    )
                                    .border(
                                        width = if (isFocused) 1.dp else 0.dp,
                                        color = if (isFocused) accentColor else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .then(
                                        if (enabled) Modifier
                                            .clickable(interactionSource = interactionSource, indication = null) {
                                                onLanguageChange(code)
                                            }
                                            .focusable(interactionSource = interactionSource)
                                        else Modifier
                                    )
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = if (enabled) accentColor else Color.Gray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                }
                                Text(
                                    text = displayName,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                    ),
                                    color = when {
                                        !enabled -> Color.White.copy(0.3f)
                                        isSelected -> accentColor
                                        isFocused -> Color.White
                                        else -> Color.White.copy(0.7f)
                                    }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IntegrationButton(
                            text = "Close",
                            onClick = onDismiss,
                            modifier = Modifier.width(100.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TmdbToggleItem(
    title: String,
    subtitle: String,
    isEnabled: Boolean,
    onToggle: () -> Unit,
    focusRequester: FocusRequester? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(if (isFocused) 1.02f else 1f)
    val bgColor by animateColorAsState(if (isFocused) Color.White.copy(0.1f) else Color.White.copy(0.05f))

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .clickable(interactionSource = interactionSource, indication = null) { onToggle() }
            .focusable(interactionSource = interactionSource)
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = Color.White
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }

        Spacer(Modifier.width(12.dp))

        // Toggle indicator
        Box(
            modifier = Modifier
                .size(width = 40.dp, height = 22.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(
                    if (isEnabled) MaterialTheme.colorScheme.primary.copy(0.8f)
                    else Color.White.copy(0.15f)
                ),
            contentAlignment = if (isEnabled) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .padding(2.dp)
                    .size(18.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color.White)
            )
        }
    }
}

// =============================================================================
// SHARED UI COMPONENTS
// =============================================================================

@Composable
private fun IntegrationTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    focusRequester: FocusRequester? = null,
    onDone: (() -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }

    val borderBrush = if (isFocused) {
        Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary))
    } else {
        SolidColor(Color.White.copy(0.1f))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(0.5f))
            .border(if (isFocused) 2.dp else 1.dp, borderBrush, RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isEmpty()) {
            Text(placeholder, color = Color.Gray)
        }

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            singleLine = true,
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = if (onDone != null) ImeAction.Done else ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onDone = { onDone?.invoke() }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                .onFocusChanged { isFocused = it.isFocused }
        )
    }
}

@Composable
private fun IntegrationButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
    isDestructive: Boolean = false,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(if (isFocused && enabled) 1.05f else 1f)

    val activeColor = when {
        isDestructive -> Color.Red
        else -> MaterialTheme.colorScheme.primary
    }

    val bgColor = if (!enabled) Color.White.copy(0.05f) else Color.White.copy(0.08f)

    val textColor = when {
        !enabled -> Color.White.copy(0.3f)
        isFocused -> activeColor
        isDestructive || isPrimary -> activeColor.copy(alpha = 0.95f)
        else -> Color.White
    }

    val borderColor = when {
        !enabled -> Color.White.copy(0.1f)
        isFocused -> activeColor
        isDestructive || isPrimary -> activeColor.copy(alpha = 0.75f)
        else -> Color.White.copy(0.2f)
    }

    Box(
        modifier = modifier
            .height(50.dp)
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .then(if (enabled) Modifier.clickable(interactionSource = interactionSource, indication = null) { onClick() } else Modifier)
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .then(if (enabled) Modifier.focusable(interactionSource = interactionSource) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = textColor
        )
    }
}

@Composable
private fun TraktAuthDialog(
    isConnected: Boolean,
    authState: DeviceAuthState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(authState) {
        delay(200)
        runCatching { focusRequester.requestFocus() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .width(460.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.background)
                    .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(16.dp))
                    .padding(24.dp)
            ) {
                Text(
                    "Trakt",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Text(
                    "Track what you watch automatically across all your apps.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(Modifier.height(24.dp))

                if (isConnected) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Connected to Trakt", color = Color.White, style = MaterialTheme.typography.bodyLarge)
                    }

                    Spacer(Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                    ) {
                        IntegrationButton(
                            text = "Disconnect",
                            onClick = onDisconnect,
                            isDestructive = true,
                            modifier = Modifier.width(130.dp),
                            focusRequester = focusRequester
                        )
                        IntegrationButton(
                            text = "Close",
                            onClick = onDismiss,
                            modifier = Modifier.width(100.dp)
                        )
                    }
                } else {
                    when (authState) {
                        is DeviceAuthState.Idle -> {
                            Text(
                                "Connect your Trakt account to sync your watchlist, watch history, and scrobble playback.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(0.7f)
                            )

                            Spacer(Modifier.height(24.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                            ) {
                                IntegrationButton(
                                    text = "Connect",
                                    onClick = onConnect,
                                    isPrimary = true,
                                    modifier = Modifier.width(130.dp),
                                    focusRequester = focusRequester
                                )
                                IntegrationButton(
                                    text = "Close",
                                    onClick = onDismiss,
                                    modifier = Modifier.width(100.dp)
                                )
                            }
                        }

                        is DeviceAuthState.WaitingForUser -> {
                            Text(
                                "Go to the URL below and enter the code:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(0.7f)
                            )

                            Spacer(Modifier.height(16.dp))

                            Text(
                                authState.verificationUrl,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary
                            )

                            Spacer(Modifier.height(16.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White.copy(0.08f), RoundedCornerShape(12.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(0.3f), RoundedCornerShape(12.dp))
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    authState.userCode,
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 8.sp
                                    ),
                                    color = Color.White
                                )
                            }

                            Spacer(Modifier.height(12.dp))

                            val qrBitmap = remember(authState.verificationUrl) {
                                generateQrCode(authState.verificationUrl, 200)
                            }
                            if (qrBitmap != null) {
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        bitmap = qrBitmap.asImageBitmap(),
                                        contentDescription = "QR Code",
                                        modifier = Modifier.size(120.dp)
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                            }

                            Text(
                                "Waiting for authorization...",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(0.5f)
                            )

                            Spacer(Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                IntegrationButton(
                                    text = "Cancel",
                                    onClick = onDismiss,
                                    modifier = Modifier.width(100.dp),
                                    focusRequester = focusRequester
                                )
                            }
                        }

                        is DeviceAuthState.Success -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Successfully connected!", color = Color.White, style = MaterialTheme.typography.bodyLarge)
                            }

                            Spacer(Modifier.height(24.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                IntegrationButton(
                                    text = "Done",
                                    onClick = onDismiss,
                                    isPrimary = true,
                                    modifier = Modifier.width(100.dp),
                                    focusRequester = focusRequester
                                )
                            }
                        }

                        is DeviceAuthState.Error -> {
                            Text(authState.message, style = MaterialTheme.typography.bodyMedium, color = Color.Red.copy(0.8f))

                            Spacer(Modifier.height(24.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                            ) {
                                IntegrationButton(text = "Retry", onClick = onConnect, isPrimary = true, modifier = Modifier.width(100.dp), focusRequester = focusRequester)
                                IntegrationButton(text = "Close", onClick = onDismiss, modifier = Modifier.width(100.dp))
                            }
                        }

                        is DeviceAuthState.Expired -> {
                            Text("The authorization code has expired. Please try again.", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(0.7f))

                            Spacer(Modifier.height(24.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                            ) {
                                IntegrationButton(text = "Retry", onClick = onConnect, isPrimary = true, modifier = Modifier.width(100.dp), focusRequester = focusRequester)
                                IntegrationButton(text = "Close", onClick = onDismiss, modifier = Modifier.width(100.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun generateQrCode(url: String, size: Int = 512): Bitmap? {
    return try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(url, BarcodeFormat.QR_CODE, size, size)

        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)

        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
            }
        }

        bitmap
    } catch (e: Exception) {
        if (com.lumera.app.BuildConfig.DEBUG) android.util.Log.w("IntegrationsScreen", "QR generation error", e)
        null
    }
}
