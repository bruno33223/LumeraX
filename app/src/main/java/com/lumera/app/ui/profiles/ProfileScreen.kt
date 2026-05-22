package com.lumera.app.ui.profiles

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.lumera.app.ui.settings.ThemeEditorScreen
import com.lumera.app.R
import com.lumera.app.data.model.ProfileEntity
import com.lumera.app.data.model.ThemeEntity
import com.lumera.app.ui.addons.VoidButton
import com.lumera.app.ui.addons.VoidInput
import com.lumera.app.ui.components.CenterCarouselRow
import com.lumera.app.ui.components.dialogs.ParentalPinDialog
import com.lumera.app.ui.home.DpadRepeatGate
import com.lumera.app.ui.theme.DefaultThemes
import com.lumera.app.ui.theme.LumeraTheme
import com.lumera.app.ui.theme.ThemeManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.ui.res.stringResource
import java.util.Locale

private const val PROFILE_HORIZONTAL_REPEAT_INTERVAL_MS = 150L

@Composable
fun ProfileScreen(
    profiles: List<ProfileEntity>,
    onProfileSelected: (ProfileEntity) -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    var initialLanguageSelected by remember { mutableStateOf(false) }
    var currentLanguage by remember { mutableStateOf("en") }
    val context = LocalContext.current

    var pinTargetProfile by remember { mutableStateOf<ProfileEntity?>(null) }
    var pinError by remember { mutableStateOf<String?>(null) }

    val wizardStep by viewModel.wizardStep.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {

        // FIX: If loading, just show black background (or nothing)
        // This prevents the "WelcomeView" from flashing briefly.
        if (!isLoading) {
            OnboardingLocaleWrapper(language = currentLanguage) {
                AnimatedContent(
                    targetState = wizardStep,
                    transitionSpec = { fadeIn(tween(500)) togetherWith fadeOut(tween(300)) },
                    label = "ProfileFlow"
                ) { step ->
                    when (step) {
                        0 -> {
                            // ZERO STATE or SELECTOR
                            if (profiles.isEmpty()) {
                                if (!initialLanguageSelected) {
                                    OnboardingLanguageSelector(
                                        onLanguageSelected = { lang ->
                                            viewModel.tempAppLanguage = lang
                                            currentLanguage = lang
                                            initialLanguageSelected = true
                                        }
                                    )
                                } else {
                                    WelcomeView(onStart = { viewModel.startWizard() })
                                }
                            } else {
                                ProfileSelectorView(
                                    profiles = profiles,
                                    onSelect = { profile ->
                                        if (profile.parentalPin.isNotEmpty()) {
                                            pinTargetProfile = profile
                                            pinError = null
                                        } else {
                                            onProfileSelected(profile)
                                        }
                                    },
                                    onAdd = { viewModel.startWizard() },
                                    onEdit = { viewModel.startEditWizard(it) },
                                    onDelete = { viewModel.deleteProfile(it.id) },
                                    viewModel = viewModel
                                )
                            }
                        }
                        1 -> WizardNameStep(
                            initialName = viewModel.tempName,
                            onNext = { viewModel.setWizardName(it) },
                            onCancel = { viewModel.cancelWizard() }
                        )
                        2 -> WizardAvatarStep(
                            onNext = { viewModel.setWizardAvatar(it) },
                            onBack = { viewModel.goBackStep() }
                        )
                        3 -> WizardThemeStep(
                            onFinish = { viewModel.setWizardTheme(it) },
                            onBack = { viewModel.goBackStep() }
                        )
                    }
                }
            }
        }

        pinTargetProfile?.let { targetProfile ->
            ParentalPinDialog(
                title = stringResource(R.string.parental_locked_profile),
                subtitle = stringResource(R.string.parental_enter_pin_desc),
                errorMessage = pinError,
                onPinSubmitted = { enteredPin ->
                    if (enteredPin == targetProfile.parentalPin) {
                        val p = pinTargetProfile
                        pinTargetProfile = null
                        pinError = null
                        if (p != null) {
                            onProfileSelected(p)
                        }
                    } else {
                        pinError = context.getString(R.string.parental_pin_incorrect)
                    }
                },
                onDismiss = {
                    pinTargetProfile = null
                    pinError = null
                }
            )
        }
    }
}


// --- 1. WELCOME VIEW ---
@Composable
fun WelcomeView(onStart: () -> Unit) {
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(100); requester.requestFocus() }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            stringResource(R.string.profile_welcome_title),
            style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 4.sp),
            color = Color.White
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.profile_welcome_subtitle),
            style = MaterialTheme.typography.titleMedium,
            color = Color.Gray
        )
        Spacer(Modifier.height(48.dp))

        VoidButton(
            text = stringResource(R.string.profile_create_first),
            onClick = onStart,
            isPrimary = true,
            modifier = Modifier.width(250.dp),
            focusRequester = requester
        )
    }
}

// --- 2. SELECTOR VIEW ---
@Composable
fun ProfileSelectorView(
    profiles: List<ProfileEntity>,
    onSelect: (ProfileEntity) -> Unit,
    onAdd: () -> Unit,
    onEdit: (ProfileEntity) -> Unit,
    onDelete: (ProfileEntity) -> Unit,
    viewModel: ProfileViewModel
) {
    var activeProfileForOptions by remember { mutableStateOf<ProfileEntity?>(null) }
    var setupTargetProfile by remember { mutableStateOf<ProfileEntity?>(null) }
    var showSetupChoiceDialog by remember { mutableStateOf(false) }
    var showCopyFromDialog by remember { mutableStateOf(false) }
    var showScratchConfirmDialog by remember { mutableStateOf(false) }
    val isInitializingProfile by viewModel.isInitializingProfile.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            stringResource(R.string.profile_who_is_watching),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = Color.White
        )

        Spacer(Modifier.height(50.dp))

        // Static centered row (no scrolling)
        val focusRequesters = remember(profiles.size) { 
            List(profiles.size) { FocusRequester() } 
        }
        
        // Request focus on first profile
        LaunchedEffect(profiles.size, activeProfileForOptions) {
            if (profiles.isNotEmpty() && activeProfileForOptions == null) {
                delay(100)
                focusRequesters[0].requestFocus()
            }
        }
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            profiles.forEachIndexed { index, profile ->
                ProfileCard(
                    profile = profile,
                    onClick = {
                        if (isInitializingProfile) return@ProfileCard
                        if (viewModel.needsInitialSetup(profile.id)) {
                            val hasCopySource = profiles.any { it.id != profile.id }
                            if (!hasCopySource) {
                                viewModel.initializeProfileFromScratch(profile.id) {
                                    onSelect(profile)
                                }
                            } else {
                                setupTargetProfile = profile
                                showSetupChoiceDialog = true
                            }
                        } else {
                            onSelect(profile)
                        }
                    },
                    onEdit = { activeProfileForOptions = profile },
                    focusRequester = focusRequesters[index]
                )
            }

            // Only show Add button if under 6 profiles
            if (profiles.size < 6) {
                AddProfileCard(onClick = onAdd)
            }
        }
    }

    if (activeProfileForOptions != null) {
        ProfileOptionsDialog(
            profile = activeProfileForOptions!!,
            onDismiss = { activeProfileForOptions = null },
            onEdit = {
                onEdit(activeProfileForOptions!!)
                activeProfileForOptions = null
            },
            onDelete = {
                onDelete(activeProfileForOptions!!)
                activeProfileForOptions = null
            }
        )
    }

    val setupTarget = setupTargetProfile
    if (showSetupChoiceDialog && setupTarget != null) {
        val sourceProfiles = profiles.filter { it.id != setupTarget.id }
        ProfileInitialSetupDialog(
            profileName = setupTarget.name,
            canCopy = sourceProfiles.isNotEmpty(),
            isLoading = isInitializingProfile,
            onCopy = {
                showSetupChoiceDialog = false
                showCopyFromDialog = true
            },
            onStartScratch = {
                showSetupChoiceDialog = false
                showScratchConfirmDialog = true
            },
            onDismiss = {
                showSetupChoiceDialog = false
                setupTargetProfile = null
            }
        )
    }

    if (showCopyFromDialog && setupTarget != null) {
        val sourceProfiles = profiles.filter { it.id != setupTarget.id }
        CopyProfileSelectionDialog(
            targetProfileName = setupTarget.name,
            sourceProfiles = sourceProfiles,
            isLoading = isInitializingProfile,
            onSelectProfile = { sourceProfile ->
                viewModel.initializeProfileByCopy(setupTarget.id, sourceProfile.id) {
                    showCopyFromDialog = false
                    setupTargetProfile = null
                    onSelect(setupTarget)
                }
            },
            onBack = {
                showCopyFromDialog = false
                showSetupChoiceDialog = true
            },
            onDismiss = {
                showCopyFromDialog = false
                setupTargetProfile = null
            }
        )
    }

    if (showScratchConfirmDialog && setupTarget != null) {
        ScratchConfirmDialog(
            profileName = setupTarget.name,
            isLoading = isInitializingProfile,
            onConfirm = {
                viewModel.initializeProfileFromScratch(setupTarget.id) {
                    showScratchConfirmDialog = false
                    setupTargetProfile = null
                    onSelect(setupTarget)
                }
            },
            onBack = {
                showScratchConfirmDialog = false
                showSetupChoiceDialog = true
            },
            onDismiss = {
                showScratchConfirmDialog = false
                setupTargetProfile = null
            }
        )
    }
}

@Composable
private fun ProfileInitialSetupDialog(
    profileName: String,
    canCopy: Boolean,
    isLoading: Boolean,
    onCopy: () -> Unit,
    onStartScratch: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(520.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black)
                .border(2.dp, Color(0xFF333333), RoundedCornerShape(24.dp))
                .padding(32.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.profile_setup_title, profileName),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.profile_setup_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
                Spacer(Modifier.height(24.dp))

                VoidButton(
                    text = if (canCopy) stringResource(R.string.profile_copy_another) else stringResource(R.string.profile_no_copy_available),
                    onClick = onCopy,
                    enabled = canCopy && !isLoading,
                    isPrimary = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                VoidButton(
                    text = stringResource(R.string.profile_start_scratch),
                    onClick = onStartScratch,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                VoidButton(
                    text = stringResource(R.string.common_cancel),
                    onClick = onDismiss,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun CopyProfileSelectionDialog(
    targetProfileName: String,
    sourceProfiles: List<ProfileEntity>,
    isLoading: Boolean,
    onSelectProfile: (ProfileEntity) -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(560.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black)
                .border(2.dp, Color(0xFF333333), RoundedCornerShape(24.dp))
                .padding(32.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.profile_copy_config_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.profile_copy_config_subtitle, targetProfileName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
                Spacer(Modifier.height(20.dp))

                if (sourceProfiles.isEmpty()) {
                    Text(
                        text = stringResource(R.string.profile_no_profiles_to_copy),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                } else {
                    sourceProfiles.forEach { sourceProfile ->
                        VoidButton(
                            text = sourceProfile.name,
                            onClick = { onSelectProfile(sourceProfile) },
                            enabled = !isLoading,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }

                VoidButton(
                    text = stringResource(R.string.common_back),
                    onClick = onBack,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ScratchConfirmDialog(
    profileName: String,
    isLoading: Boolean,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(520.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black)
                .border(2.dp, Color(0xFF333333), RoundedCornerShape(24.dp))
                .padding(32.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.profile_start_scratch_confirm_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.profile_start_scratch_confirm_subtitle, profileName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
                Spacer(Modifier.height(24.dp))
                VoidButton(
                    text = stringResource(R.string.profile_yes_start_fresh),
                    onClick = onConfirm,
                    enabled = !isLoading,
                    isPrimary = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                VoidButton(
                    text = stringResource(R.string.common_back),
                    onClick = onBack,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun ProfileOptionsDialog(
    profile: ProfileEntity,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val editRequester = remember { FocusRequester() }

    // SAFETY LOCK: Delay input to prevent accidental clicks
    var areButtonsReady by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(100)
        editRequester.requestFocus()
        delay(400)
        areButtonsReady = true
    }

    if (!showDeleteConfirmation) {
        Dialog(onDismissRequest = onDismiss) {
            Box(
                modifier = Modifier
                    .width(400.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black)
                    .border(2.dp, Color(0xFF333333), RoundedCornerShape(24.dp))
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // 1. RESOLVE AVATAR STRING TO INT ID
                    val avatarSource = ProfileAssets.getAvatarSource(profile.avatarRef)

                    // 2. DISPLAY IMAGE
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(avatarSource)
                            .size(300, 300)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    )

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = profile.name.uppercase(),
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White
                    )
                    Spacer(Modifier.height(32.dp))

                    // Action Buttons
                    VoidButton(
                        text = stringResource(R.string.profile_edit),
                        onClick = {
                            if (areButtonsReady) onEdit()
                        },
                        isPrimary = false,
                        modifier = Modifier.fillMaxWidth(),
                        focusRequester = editRequester
                    )
                    Spacer(Modifier.height(16.dp))
                    VoidButton(
                        text = stringResource(R.string.profile_delete),
                        onClick = {
                            if (areButtonsReady) showDeleteConfirmation = true
                        },
                        isPrimary = false,
                        isDestructive = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmation) {
        DeleteConfirmationDialog(
            profileName = profile.name,
            onConfirm = {
                showDeleteConfirmation = false
                onDelete()
            },
            onDismiss = { showDeleteConfirmation = false }
        )
    }
}

@Composable
fun DeleteConfirmationDialog(
    profileName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val noRequester = remember { FocusRequester() }
    var isReady by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(50)
        noRequester.requestFocus()
        isReady = true
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(400.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black)
                .border(2.dp, Color(0xFF333333), RoundedCornerShape(24.dp))
                .padding(32.dp)
                .alpha(if (isReady) 1f else 0f),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.profile_delete_confirm_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.profile_delete_confirm_subtitle, profileName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
                Spacer(Modifier.height(32.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    VoidButton(
                        text = stringResource(R.string.common_yes),
                        onClick = onConfirm,
                        isPrimary = false,
                        isDestructive = true,
                        modifier = Modifier.weight(1f)
                    )
                    VoidButton(
                        text = stringResource(R.string.common_no),
                        onClick = onDismiss,
                        isPrimary = false,
                        modifier = Modifier.weight(1f),
                        focusRequester = noRequester
                    )
                }
            }
        }
    }
}

// --- 3. WIZARD STEPS ---

@Composable
fun WizardNameStep(initialName: String, onNext: (String) -> Unit, onCancel: () -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { delay(100); focusRequester.requestFocus() }
    BackHandler { onCancel() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .offset(y = (-80).dp), // Move up to avoid virtual keyboard cropping
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            if(initialName.isEmpty()) stringResource(R.string.profile_wizard_name_title_new) else stringResource(R.string.profile_wizard_name_title_edit),
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White
        )
        Spacer(Modifier.height(32.dp))

        Box(modifier = Modifier.width(400.dp)) {
            VoidInput(
                value = name,
                onValueChange = { name = it },
                placeholder = stringResource(R.string.profile_wizard_name_placeholder),
                modifier = Modifier.focusRequester(focusRequester),
                onDone = { if(name.isNotEmpty()) onNext(name) }
            )
        }
    }
}

@Composable
fun WizardAvatarStep(onNext: (String) -> Unit, onBack: () -> Unit) {
    val avatars = ProfileAssets.AVATAR_MAP.toList()
    val initialIndex = avatars.size / 2
    val listState = rememberLazyListState()
    val horizontalRepeatGate = remember {
        DpadRepeatGate(horizontalRepeatIntervalMs = PROFILE_HORIZONTAL_REPEAT_INTERVAL_MS)
    }
    val focusRequesters = remember(avatars.size) { List(avatars.size) { FocusRequester() } }
    val uploadButtonRequester = remember { FocusRequester() }
    
    var showUploadDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        listState.scrollToItem(initialIndex)
        delay(100)
        focusRequesters[initialIndex].requestFocus()
    }
    BackHandler { onBack() }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.profile_wizard_avatar_title), style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.profile_wizard_avatar_subtitle), color = Color.Gray)

        Spacer(Modifier.height(40.dp))

        CenterCarouselRow(
            itemWidth = 120.dp,
            itemSpacing = 24.dp,
            state = listState,
            modifier = Modifier.onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionLeft || event.key == Key.DirectionRight)
                ) {
                    horizontalRepeatGate.shouldConsume(event)
                } else {
                    false
                }
            }
        ) {
            items(avatars.size) { index ->
                val (key, resId) = avatars[index]
                AvatarGridItem(
                    resId = resId,
                    onClick = { onNext(key) },
                    focusRequester = focusRequesters[index],
                    modifier = Modifier.size(120.dp)
                )
            }
        }
        
        Spacer(Modifier.height(32.dp))
        
        Text(
            stringResource(R.string.common_or),
            style = MaterialTheme.typography.bodyLarge,
            color = Color.Gray
        )
        
        Spacer(Modifier.height(16.dp))
        
        UploadAvatarButton(
            onClick = { showUploadDialog = true },
            focusRequester = uploadButtonRequester
        )
    }
    
    if (showUploadDialog) {
        AvatarUploadDialog(
            onDismissRequest = { showUploadDialog = false },
            onAvatarReceived = { avatarPath ->
                showUploadDialog = false
                onNext(avatarPath)
            }
        )
    }
}

@Composable
private fun UploadAvatarButton(
    onClick: () -> Unit,
    focusRequester: FocusRequester
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (isFocused) 1.05f else 1f)
    
    Box(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isFocused) MaterialTheme.colorScheme.primary
                else Color.White.copy(alpha = 0.1f)
            )
            .border(
                width = 2.dp,
                color = if (isFocused) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
            )
            .focusRequester(focusRequester)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            stringResource(R.string.option_upload_own),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = if (isFocused) Color.Black else Color.White.copy(alpha = 0.8f)
        )
    }
}

// --- 4. ONBOARDING COMPONENTS ---

@Composable
fun OnboardingLanguageSelector(onLanguageSelected: (String) -> Unit) {
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(100); requester.requestFocus() }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            stringResource(R.string.onboarding_language_title),
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White
        )
        Spacer(Modifier.height(48.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            LanguageEmojiButton(
                emoji = "\uD83C\uDDFA\uD83C\uDDF8", // US Flag
                label = "English",
                onClick = { onLanguageSelected("en") },
                focusRequester = requester
            )
            LanguageEmojiButton(
                emoji = "\uD83C\uDDE7\uD83C\uDDF7", // Brazil Flag
                label = "Português",
                onClick = { onLanguageSelected("pt-BR") }
            )
            LanguageEmojiButton(
                emoji = "\uD83C\uDDEA\uD83C\uDDF8", // Spain Flag
                label = "Español",
                onClick = { onLanguageSelected("es") }
            )
        }
    }
}

@Composable
fun LanguageEmojiButton(
    emoji: String,
    label: String,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (isFocused) 1.1f else 1f, label = "EmojiScale")

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isFocused) Color.White.copy(0.2f) else Color.Transparent)
            .border(2.dp, if (isFocused) Color.White else Color.Transparent, RoundedCornerShape(16.dp))
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .focusable(interactionSource = interactionSource)
            .padding(24.dp)
    ) {
        Text(emoji, fontSize = 48.sp)
        Spacer(Modifier.height(8.dp))
        Text(label, color = Color.White, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun OnboardingLocaleWrapper(language: String, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    
    val locale = remember(language) {
        val parts = language.split("-")
        if (parts.size > 1) Locale(parts[0], parts[1].removePrefix("r"))
        else Locale(language)
    }

    val localizedConfiguration = remember(locale) {
        Configuration(configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
    }

    val localizedContext = remember(locale, context) {
        val configContext = context.createConfigurationContext(localizedConfiguration)
        object : android.content.ContextWrapper(context) {
            override fun getResources() = configContext.resources
        }
    }

    CompositionLocalProvider(
        LocalConfiguration provides localizedConfiguration,
        LocalContext provides localizedContext
    ) {
        content()
    }
}

@Composable
fun WizardThemeStep(onFinish: (String) -> Unit, onBack: () -> Unit) {
    val themeManager: ThemeManager = hiltViewModel()
    val themes = DefaultThemes.ALL
    val initialIndex = themes.size / 2
    var previewTheme by remember { mutableStateOf(themes[initialIndex]) }
    val listState = rememberLazyListState()
    val horizontalRepeatGate = remember {
        DpadRepeatGate(horizontalRepeatIntervalMs = PROFILE_HORIZONTAL_REPEAT_INTERVAL_MS)
    }
    val focusRequesters = remember(themes.size) { List(themes.size) { FocusRequester() } }
    val createButtonRequester = remember { FocusRequester() }
    
    var showThemeEditor by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        listState.scrollToItem(initialIndex)
        delay(100)
        focusRequesters[initialIndex].requestFocus()
    }
    BackHandler { onBack() }

    LumeraTheme(theme = previewTheme) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Offset for theme name labels below circles (not present in avatar selector)
            Spacer(Modifier.height(32.dp))
            
            Text("Choose a Theme", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Spacer(Modifier.height(16.dp))
            Text("Select a color scheme for your experience.", color = Color.Gray)

            Spacer(Modifier.height(40.dp))

            CenterCarouselRow(
                itemWidth = 120.dp,
                itemSpacing = 24.dp,
                state = listState,
                modifier = Modifier.onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown &&
                        (event.key == Key.DirectionLeft || event.key == Key.DirectionRight)
                    ) {
                        horizontalRepeatGate.shouldConsume(event)
                    } else {
                        false
                    }
                }
            ) {
                items(themes.size) { index ->
                    val theme = themes[index]
                    ThemePickItem(
                        theme = theme,
                        onClick = { onFinish(theme.id) },
                        focusRequester = focusRequesters[index],
                        onFocused = { previewTheme = theme }
                    )
                }
            }
            
            Spacer(Modifier.height(32.dp))
            
            Text(
                "Or",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Gray
            )
            
            Spacer(Modifier.height(16.dp))
            
            CreateThemeButton(
                onClick = { showThemeEditor = true },
                focusRequester = createButtonRequester
            )
        }
    }
    
    // Theme Editor Dialog
    if (showThemeEditor) {
        Dialog(
            onDismissRequest = { showThemeEditor = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .padding(32.dp)
            ) {
                ThemeEditorScreen(
                    editingTheme = null,
                    onSave = { name, primary, background ->
                        val newThemeId = themeManager.createCustomTheme(
                            name = name,
                            primaryColor = primary,
                            backgroundColor = background
                        )
                        showThemeEditor = false
                        onFinish(newThemeId)
                    },
                    onCancel = { showThemeEditor = false }
                )
            }
        }
    }
}

@Composable
private fun CreateThemeButton(
    onClick: () -> Unit,
    focusRequester: FocusRequester
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (isFocused) 1.05f else 1f)
    
    Box(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isFocused) MaterialTheme.colorScheme.primary
                else Color.White.copy(alpha = 0.1f)
            )
            .border(
                width = 2.dp,
                color = if (isFocused) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
            )
            .focusRequester(focusRequester)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "Create Your Own",
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = if (isFocused) Color.Black else Color.White.copy(alpha = 0.8f)
        )
    }
}

@Composable
fun ThemePickItem(
    theme: ThemeEntity,
    onClick: () -> Unit,
    focusRequester: FocusRequester?,
    onFocused: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (isFocused) 1.1f else 1f)

    LaunchedEffect(isFocused) {
        if (isFocused) onFocused?.invoke()
    }

    val focusModifier = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .then(focusModifier)
                .clickable(interactionSource = interactionSource, indication = null) { onClick() }
                .focusable(interactionSource = interactionSource),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(CircleShape)
                    .background(Color(theme.backgroundColor.toInt()))
                    .border(
                        3.dp,
                        if (isFocused) Color.White else Color.Transparent,
                        CircleShape
                    )
                    .padding(12.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(theme.primaryColor.toInt()))
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = theme.name,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = if (isFocused) Color.White else Color.Gray
        )
    }
}

@Composable
fun ProfileCard(
    profile: ProfileEntity,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    focusRequester: FocusRequester? = null
) {
    val cardInteractionSource = remember { MutableInteractionSource() }
    val isCardFocused by cardInteractionSource.collectIsFocusedAsState()
    
    val editInteractionSource = remember { MutableInteractionSource() }
    val isEditFocused by editInteractionSource.collectIsFocusedAsState()
    
    // Pencil is visible when either card or pencil is focused
    val isPencilVisible = isCardFocused || isEditFocused
    
    val context = LocalContext.current

    val scale by animateFloatAsState(if (isCardFocused) 1.1f else 1f)
    val borderAlpha by animateFloatAsState(if (isCardFocused || isEditFocused) 1f else 0f)

    // CONVERT STRING ("avatar_5") -> Source (Int or File)
    val avatarSource = ProfileAssets.getAvatarSource(profile.avatarRef)

    val focusModifier = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.scale(scale)
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(Color(0xFF1A1A1A))
                .border(3.dp, MaterialTheme.colorScheme.primary.copy(alpha = borderAlpha), CircleShape)
                .then(focusModifier)
                .onPreviewKeyEvent { event ->
                    val isConfirm = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
                    if (isConfirm && event.type == KeyEventType.KeyUp) {
                        onClick()
                        true
                    } else {
                        false
                    }
                }
                .focusable(interactionSource = cardInteractionSource)
                .clickable(
                    interactionSource = cardInteractionSource,
                    indication = null,
                    onClick = onClick
                )
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(avatarSource)
                    .size(300, 300)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = profile.name.uppercase(),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
            color = if(isCardFocused || isEditFocused) Color.White else Color.Gray
        )
        
        Spacer(Modifier.height(8.dp))
        
        // Pencil Edit Icon - always in composition but visibility controlled by alpha
        val editScale by animateFloatAsState(if (isEditFocused) 1.2f else 1f)
        val pencilAlpha by animateFloatAsState(if (isPencilVisible) 1f else 0f)
        
        Box(
            modifier = Modifier
                .size(32.dp)
                .scale(editScale)
                .alpha(pencilAlpha)
                .clip(CircleShape)
                .background(if (isEditFocused) Color.White else Color.Transparent)
                .clickable(
                    interactionSource = editInteractionSource,
                    indication = null,
                    onClick = onEdit
                )
                .focusable(interactionSource = editInteractionSource),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = stringResource(R.string.profile_edit),
                tint = if (isEditFocused) Color.Black else Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun AddProfileCard(onClick: () -> Unit, focusRequester: FocusRequester? = null) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (isFocused) 1.1f else 1f)

    val focusModifier = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.scale(scale)
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(Color.White.copy(0.1f))
                .border(2.dp, if(isFocused) Color.White else Color.Transparent, CircleShape)
                .then(focusModifier)
                .clickable(interactionSource = interactionSource, indication = null) { onClick() }
                .focusable(interactionSource = interactionSource),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.profile_create_first),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
            color = if(isFocused) Color.White else Color.Gray
        )
        Spacer(Modifier.height(8.dp))
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
fun AvatarGridItem(
    resId: Int, 
    onClick: () -> Unit, 
    focusRequester: FocusRequester?,
    modifier: Modifier = Modifier,
    onFocused: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (isFocused) 1.15f else 1f)

    LaunchedEffect(isFocused) {
        if (isFocused) onFocused?.invoke()
    }

    val focusModifier = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
    val context = LocalContext.current

    Box(
        modifier = modifier
            .then(focusModifier)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .focusable(interactionSource = interactionSource),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .border(3.dp, if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
        ) {
            AsyncImage(
            model = ImageRequest.Builder(context)
                .data(resId)
                .size(300, 300)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}
}
