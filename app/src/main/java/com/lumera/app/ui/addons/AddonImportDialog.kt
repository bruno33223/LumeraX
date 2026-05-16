package com.lumera.app.ui.addons

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.IndeterminateCheckBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.lumera.app.data.model.StremioAddonItem
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay

/**
 * Dialog for selecting which Stremio addons to import.
 * Supports D-pad navigation for Android TV.
 */
@Composable
fun AddonImportDialog(
    addons: List<StremioAddonItem>,
    onDismissRequest: () -> Unit,
    onConfirmImport: (List<StremioAddonItem>) -> Unit
) {
    // Local mutable state for selections
    var addonItems by remember { mutableStateOf(addons) }
    val selectableFocusRequester = remember { FocusRequester() }
    val confirmFocusRequester = remember { FocusRequester() }
    
    // Count stats
    val newAddons = addonItems.filter { !it.isAlreadyInstalled }
    val selectedCount = newAddons.count { it.isSelected }
    val selectableCount = newAddons.size
    
    // Initial focus on first selectable item or confirm button
    LaunchedEffect(Unit) {
        delay(150)
        if (selectableCount > 0) {
            selectableFocusRequester.requestFocus()
        } else {
            confirmFocusRequester.requestFocus()
        }
    }
    
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .width(420.dp)
                .heightIn(max = 480.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.background)
                .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(16.dp))
                .padding(24.dp)
        ) {
            Column {
                // Header
                Text(
                    stringResource(id = com.lumera.app.R.string.addons_import_title),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                
                Text(
                    stringResource(id = com.lumera.app.R.string.addons_import_stats, addonItems.size, selectedCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 8.dp)
                )
                
                Spacer(Modifier.height(16.dp))
                
                // Select All / Deselect All buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SmallActionButton(
                        text = stringResource(id = com.lumera.app.R.string.addons_select_all),
                        onClick = {
                            addonItems = addonItems.map { 
                                if (!it.isAlreadyInstalled) it.copy(isSelected = true) else it 
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = selectableCount > 0
                    )
                    
                    SmallActionButton(
                        text = stringResource(id = com.lumera.app.R.string.addons_deselect_all),
                        onClick = {
                            addonItems = addonItems.map { 
                                if (!it.isAlreadyInstalled) it.copy(isSelected = false) else it 
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = selectableCount > 0
                    )
                }
                
                Spacer(Modifier.height(16.dp))
                
                // Addon List
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    itemsIndexed(addonItems) { index, addon ->
                        val isFirstSelectable = index == addonItems.indexOfFirst { !it.isAlreadyInstalled }
                        val isLastSelectable = index == addonItems.indexOfLast { !it.isAlreadyInstalled }

                        AddonImportRow(
                            addon = addon,
                            onToggle = {
                                if (!addon.isAlreadyInstalled) {
                                    addonItems = addonItems.toMutableList().apply {
                                        set(index, addon.copy(isSelected = !addon.isSelected))
                                    }
                                }
                            },
                            focusRequester = if (isFirstSelectable) selectableFocusRequester else null,
                            downKeyFocusTarget = if (isLastSelectable) confirmFocusRequester else null
                        )
                    }
                }
                
                Spacer(Modifier.height(20.dp))
                
                // Action Buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    VoidButton(
                        text = stringResource(id = com.lumera.app.R.string.dialog_cancel),
                        onClick = onDismissRequest,
                        modifier = Modifier.weight(1f)
                    )
                    
                    VoidButton(
                        text = stringResource(id = com.lumera.app.R.string.addons_import_action, selectedCount),
                        onClick = {
                            val selectedAddons = addonItems.filter { it.isSelected && !it.isAlreadyInstalled }
                            onConfirmImport(selectedAddons)
                        },
                        isPrimary = true,
                        enabled = selectedCount > 0,
                        modifier = Modifier.weight(1f),
                        focusRequester = confirmFocusRequester
                    )
                }
            }
        }
    }
}

@Composable
private fun AddonImportRow(
    addon: StremioAddonItem,
    onToggle: () -> Unit,
    focusRequester: FocusRequester? = null,
    downKeyFocusTarget: FocusRequester? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val isDisabled = addon.isAlreadyInstalled
    val alpha = if (isDisabled) 0.5f else 1f
    
    val bgColor by animateColorAsState(
        when {
            isDisabled -> Color.White.copy(0.02f)
            isFocused -> Color.White.copy(0.1f)
            else -> Color.White.copy(0.05f)
        }
    )
    
    val borderColor by animateColorAsState(
        when {
            isFocused && !isDisabled -> MaterialTheme.colorScheme.primary
            else -> Color.Transparent
        }
    )
    
    val scale by animateFloatAsState(if (isFocused && !isDisabled) 1.02f else 1f)
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(if (isFocused && !isDisabled) 2.dp else 0.dp, borderColor, RoundedCornerShape(8.dp))
            .then(
                if (!isDisabled) {
                    Modifier
                        .clickable(interactionSource = interactionSource, indication = null) { onToggle() }
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown &&
                                (event.key == Key.Enter || event.key == Key.DirectionCenter)) {
                                onToggle()
                                true
                            } else if (downKeyFocusTarget != null && event.key == Key.DirectionDown && event.type == KeyEventType.KeyDown) {
                                downKeyFocusTarget.requestFocus()
                                true
                            } else false
                        }
                        .focusable(interactionSource = interactionSource)
                } else Modifier
            )
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .padding(12.dp)
            .alpha(alpha)
    ) {
        // Checkbox
        val checkboxIcon = when {
            isDisabled -> Icons.Default.IndeterminateCheckBox
            addon.isSelected -> Icons.Default.CheckBox
            else -> Icons.Default.CheckBoxOutlineBlank
        }
        
        val checkboxColor = when {
            isDisabled -> Color.Gray
            addon.isSelected -> MaterialTheme.colorScheme.primary
            else -> Color.Gray
        }
        
        Icon(
            imageVector = checkboxIcon,
            contentDescription = if (addon.isSelected) stringResource(id = com.lumera.app.R.string.addons_selected_desc) else stringResource(id = com.lumera.app.R.string.addons_not_selected_desc),
            tint = checkboxColor,
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(Modifier.width(12.dp))
        
        // Addon Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = addon.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = if (isFocused && !isDisabled) Color.White else Color.White.copy(0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            addon.description?.let { desc ->
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        
        // Status Badge
        if (isDisabled) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Gray.copy(0.3f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    stringResource(id = com.lumera.app.R.string.addons_installed_badge),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
private fun SmallActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val bgColor = when {
        !enabled -> Color.White.copy(0.03f)
        isFocused -> Color.White.copy(0.15f)
        else -> Color.White.copy(0.08f)
    }
    
    val textColor = when {
        !enabled -> Color.White.copy(0.3f)
        isFocused -> Color.White
        else -> Color.White.copy(0.7f)
    }
    
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .then(if (enabled) Modifier.clickable(interactionSource = interactionSource, indication = null) { onClick() } else Modifier)
            .then(if (enabled) Modifier.focusable(interactionSource = interactionSource) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = textColor
        )
    }
}
