package com.lumera.app.ui.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumera.app.data.local.AddonDao
import com.lumera.app.data.model.ProfileEntity
import com.lumera.app.data.profile.ProfileConfigurationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val dao: AddonDao,
    private val profileConfigurationManager: ProfileConfigurationManager
) : ViewModel() {

    private val _profiles = MutableStateFlow<List<ProfileEntity>>(emptyList())
    val profiles: StateFlow<List<ProfileEntity>> = _profiles

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _wizardStep = MutableStateFlow(0)
    val wizardStep: StateFlow<Int> = _wizardStep

    private val _isInitializingProfile = MutableStateFlow(false)
    val isInitializingProfile: StateFlow<Boolean> = _isInitializingProfile

    // WIZARD DATA
    var tempName = ""
    var tempAvatarRef = "avatar_1"
    var tempThemeId = "void"
    var tempAppLanguage = "en"  // Changed from tempColor

    private var editingProfileId: Int? = null

    init {
        loadProfiles()
    }

    private fun loadProfiles() {
        viewModelScope.launch {
            dao.getProfiles().collect { list ->
                _profiles.value = list
                _isLoading.value = false
            }
        }
    }

    // --- WIZARD ACTIONS ---

    fun startWizard() {
        editingProfileId = null
        tempName = ""
        tempAvatarRef = "avatar_1"
        tempThemeId = "void"
        _wizardStep.value = 1
    }

    fun startEditWizard(profile: ProfileEntity) {
        editingProfileId = profile.id
        tempName = profile.name
        tempAvatarRef = profile.avatarRef
        tempThemeId = profile.themeId
        _wizardStep.value = 1
    }

    fun cancelWizard() {
        _wizardStep.value = 0
        editingProfileId = null
    }

    fun setWizardName(name: String) {
        tempName = name
        _wizardStep.value = 2
    }

    fun setWizardAvatar(avatarKey: String) {
        tempAvatarRef = avatarKey
        _wizardStep.value = 3
    }

    fun setWizardTheme(themeId: String) {
        tempThemeId = themeId
        finishWizard()
    }

    private fun finishWizard() {
        viewModelScope.launch(Dispatchers.IO + NonCancellable) {
            if (editingProfileId != null) {
                val updatedProfile = _profiles.value.find { it.id == editingProfileId }?.copy(
                    name = tempName,
                    avatarRef = tempAvatarRef,
                    themeId = tempThemeId
                )
                if (updatedProfile != null) dao.updateProfile(updatedProfile)
            } else {
                val profileId = dao.insertProfile(
                    ProfileEntity(
                        name = tempName,
                        avatarRef = tempAvatarRef,
                        themeId = tempThemeId,
                        appLanguage = tempAppLanguage,
                        navPosition = "left",
                        homeTabLayout = "cinematic",
                        roundCorners = true
                    )
                ).toInt()
                if (profileId > 0) {
                    profileConfigurationManager.markPendingSetup(profileId)
                }
            }
            _wizardStep.value = 0
            editingProfileId = null
        }
    }

    fun needsInitialSetup(profileId: Int): Boolean {
        return profileConfigurationManager.needsInitialSetup(profileId)
    }

    fun initializeProfileFromScratch(profileId: Int, onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO + NonCancellable) {
            _isInitializingProfile.value = true
            try {
                profileConfigurationManager.initializeFromScratch(profileId)
                onComplete()
            } finally {
                _isInitializingProfile.value = false
            }
        }
    }

    fun initializeProfileByCopy(targetProfileId: Int, sourceProfileId: Int, onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO + NonCancellable) {
            _isInitializingProfile.value = true
            try {
                profileConfigurationManager.initializeByCopying(targetProfileId, sourceProfileId)
                onComplete()
            } finally {
                _isInitializingProfile.value = false
            }
        }
    }

    fun deleteProfile(id: Int) {
        viewModelScope.launch(Dispatchers.IO + NonCancellable) {
            dao.deleteProfile(id)
            profileConfigurationManager.deleteProfileState(id)
        }
    }

    fun goBackStep() {
        if (_wizardStep.value > 0) _wizardStep.value -= 1
    }
}
