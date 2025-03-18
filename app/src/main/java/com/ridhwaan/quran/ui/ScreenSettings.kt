package com.ridhwaan.quran.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ridhwaan.quran.model.UserPreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
        onNavigateBack: () -> Unit,
        userPreferences: UserPreferences,
        onUpdatePreferences: (UserPreferences) -> Unit
) {
    val context = LocalContext.current

    Scaffold(
            topBar = {
                TopAppBar(
                        title = { Text("Settings") },
                        navigationIcon = {
                            IconButton(onClick = onNavigateBack) {
                                Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Navigate Back"
                                )
                            }
                        }
                )
            }
    ) { paddingValues ->
        Column(
                modifier =
                        Modifier.padding(paddingValues)
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // App Interface Settings Section
            SettingsSectionHeader(title = "App Interface")

            // Navigation Bar Setting
            SettingsSwitchItem(
                    title = "Keep Navigation Bar Visible",
                    description = "Always show the navigation bar instead of auto-hiding it",
                    icon = Icons.Filled.Visibility,
                    checked = userPreferences.keepNavigationBarVisible,
                    onCheckedChange = { isChecked ->
                        onUpdatePreferences(
                                userPreferences.copy(keepNavigationBarVisible = isChecked)
                        )
                    }
            )

            // Landscape Display Mode Setting
            SettingsSwitchItem(
                    title = "Dual Page in Landscape",
                    description =
                            "Display two pages side by side in landscape mode (toggle off to use full width for single page)",
                    icon = Icons.Filled.AutoStories,
                    checked = userPreferences.useDualPageInLandscape,
                    onCheckedChange = { isChecked ->
                        onUpdatePreferences(
                                userPreferences.copy(useDualPageInLandscape = isChecked)
                        )
                    }
            )

            HorizontalDivider()

            // About and Legal Section
            SettingsSectionHeader(title = "About & Legal")

            // Privacy Policy
            SettingsOptionItem(
                    title = "Privacy Policy",
                    description = "View our privacy policy",
                    icon = Icons.Filled.PrivacyTip,
                    onClick = { openWebPage(context, "https://www.ridhwaan.xyz/quran/privacy") }
            )

            // Terms of Service
            SettingsOptionItem(
                    title = "Terms of Service",
                    description = "View our terms of service",
                    icon = Icons.Filled.Description,
                    onClick = {
                        openWebPage(context, "https://www.ridhwaan.xyz/quran/terms-of-service")
                    }
            )

            // App Information
            SettingsAppInfo()
        }
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
fun SettingsSwitchItem(
        title: String,
        description: String,
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit
) {
    Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 16.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SettingsOptionItem(
        title: String,
        description: String,
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        onClick: () -> Unit
) {
    Row(
            modifier =
                    Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 16.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = "Open",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SettingsAppInfo() {
    Column(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
                text = "Quran App v1.0.0",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun openWebPage(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    }
}
