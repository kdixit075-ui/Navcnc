package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import com.example.data.CncDatabase
import com.example.data.CncRecipe
import com.example.data.CncRecipeRepository
import com.example.ui.theme.MyApplicationTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    
    val database = CncDatabase.getDatabase(applicationContext)
    val repository = CncRecipeRepository(database.cncRecipeDao())
    val factory = CncViewModelFactory(repository)

    setContent {
      MyApplicationTheme {
        Scaffold(
          modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
          Surface(
            modifier = Modifier
              .fillMaxSize()
              .padding(innerPadding),
            color = MaterialTheme.colorScheme.background
          ) {
            val viewModel: CncViewModel = viewModel(factory = factory)
            CncCalculatorDashboard(viewModel)
          }
        }
      }
    }
  }
}

@Composable
fun CncCalculatorDashboard(viewModel: CncViewModel) {
    val isMetric by viewModel.isMetric.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    
    // Inputs
    val vcInput by viewModel.cuttingSpeedStr.collectAsStateWithLifecycle()
    val diameterInput by viewModel.diameterStr.collectAsStateWithLifecycle()
    val flutesInput by viewModel.flutesStr.collectAsStateWithLifecycle()
    val fzInput by viewModel.feedPerToothStr.collectAsStateWithLifecycle()
    
    val customRpmInput by viewModel.customRpmStr.collectAsStateWithLifecycle()
    val docInput by viewModel.depthOfCutStr.collectAsStateWithLifecycle()
    val wocInput by viewModel.widthOfCutStr.collectAsStateWithLifecycle()
    val customFeedInput by viewModel.customFeedStr.collectAsStateWithLifecycle()
    
    val selectedMaterial by viewModel.selectedMaterialPresetName.collectAsStateWithLifecycle()
    
    // Calculated Outputs
    val calculatedRpm by viewModel.calculatedRpm.collectAsStateWithLifecycle()
    val finalRpmUsed by viewModel.currentRpmToUse.collectAsStateWithLifecycle()
    val calculatedFeed by viewModel.calculatedFeedRate.collectAsStateWithLifecycle()
    val finalFeedUsed by viewModel.currentFeedToUse.collectAsStateWithLifecycle()
    val calculatedMrr by viewModel.calculatedMrr.collectAsStateWithLifecycle()
    
    val savedRecipes by viewModel.savedRecipes.collectAsStateWithLifecycle()
    
    var showSaveDialog by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("dashboard_scrollable_container"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Controller Header Block (Retro machine layout)
        item {
            MachineControlPanelHeader(
                isMetric = isMetric,
                onUnitToggle = { viewModel.toggleUnits() }
            )
        }

        // 2. Mode Tabs
        item {
            MachineModeSelectTabs(
                selectedTabIndex = selectedTab,
                onTabSelected = { viewModel.setSelectedTab(it) }
            )
        }

        // 3. Digital Read-Out (DRO) Panel
        item {
            CncDigitalReadOutPanel(
                selectedTab = selectedTab,
                isMetric = isMetric,
                rpm = finalRpmUsed,
                feedRate = finalFeedUsed,
                mrr = calculatedMrr,
                vc = vcInput.toDoubleOrNull() ?: 0.0,
                diameter = diameterInput.toDoubleOrNull() ?: 0.0,
                materialName = selectedMaterial,
                onSaveClick = { showSaveDialog = true }
            )
        }

        // 4. Input Controls Card
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("calculator_inputs_card")
                    .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "🛠️ MACHINING CONFIGURATION",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = FontFamily.Monospace
                    )

                    // Helper Presets
                    MaterialQuickPresetsRow(
                        presets = viewModel.materialPresets,
                        selectedPresetName = selectedMaterial,
                        onPresetSelected = { viewModel.applyMaterialPreset(it) }
                    )

                    // Display respective fields depending on Active Tab
                    when (selectedTab) {
                        0 -> {
                            // RPM focus fields: Cutting Speed (Vc) & Diameter (D)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    CncTextField(
                                        value = vcInput,
                                        onValueChange = { viewModel.cuttingSpeedStr.value = it },
                                        label = if (isMetric) "Speed Vc (m/min)" else "Speed Vc (SFM)",
                                        keyboardType = KeyboardType.Number,
                                        modifier = Modifier.testTag("cuttingSpeedInput")
                                    )
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    CncTextField(
                                        value = diameterInput,
                                        onValueChange = { viewModel.diameterStr.value = it },
                                        label = if (isMetric) "Diameter D (mm)" else "Diameter D (in)",
                                        keyboardType = KeyboardType.Decimal,
                                        modifier = Modifier.testTag("diameterInput")
                                    )
                                }
                            }

                            // Tool Diameter Quick Presets
                            ToolSizeQuickPresets(
                                isMetric = isMetric,
                                onPresetSelected = { inches, mm -> viewModel.applyDiameterPreset(inches, mm) }
                            )
                        }
                        1 -> {
                            // Feed Rate focus fields: Spindle RPM, Flutes, Feed / Tooth (fz)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(modifier = Modifier.weight(1.3f)) {
                                    Column {
                                        CncTextField(
                                            value = if (customRpmInput.isEmpty()) calculatedRpm.toString() else customRpmInput,
                                            onValueChange = { viewModel.customRpmStr.value = it },
                                            label = "Spindle Speed (RPM)",
                                            keyboardType = KeyboardType.Number,
                                            modifier = Modifier.testTag("feed_rpm_input")
                                        )
                                        Text(
                                            text = if (customRpmInput.isEmpty()) "Using calculated RPM" else "Custom override active",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (customRpmInput.isEmpty()) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(start = 4.dp, top = 2.0.dp)
                                        )
                                    }
                                }
                                Box(modifier = Modifier.weight(0.7f)) {
                                    CncTextField(
                                        value = flutesInput,
                                        onValueChange = { viewModel.flutesStr.value = it },
                                        label = "Teeth (z)",
                                        keyboardType = KeyboardType.Number,
                                        modifier = Modifier.testTag("feed_flutes_input")
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            CncTextField(
                                value = fzInput,
                                onValueChange = { viewModel.feedPerToothStr.value = it },
                                label = if (isMetric) "Feed per Tooth fz (mm/tooth)" else "Feed per Tooth fz (IPT)",
                                keyboardType = KeyboardType.Decimal,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("feed_fz_input")
                            )
                            
                            // Visual prompt to guide back
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.setSelectedTab(0) }
                                    .background(
                                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f),
                                        RoundedCornerShape(6.dp)
                                    )
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = "info",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Recalculate parent RPM on Spindle Speed tab if needed.",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                        2 -> {
                            // MRR focus fields: Axial Depth (ap), Radial Width (ae), Feed Rate F
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    CncTextField(
                                        value = docInput,
                                        onValueChange = { viewModel.depthOfCutStr.value = it },
                                        label = if (isMetric) "Depth ap (mm)" else "Depth ap (in)",
                                        keyboardType = KeyboardType.Decimal,
                                        modifier = Modifier.testTag("mrr_ap_input")
                                    )
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    CncTextField(
                                        value = wocInput,
                                        onValueChange = { viewModel.widthOfCutStr.value = it },
                                        label = if (isMetric) "Width ae (mm)" else "Width ae (in)",
                                        keyboardType = KeyboardType.Decimal,
                                        modifier = Modifier.testTag("mrr_ae_input")
                                    )
                                }
                            }

                            CncTextField(
                                value = if (customFeedInput.isEmpty()) String.format(Locale.US, "%.1f", calculatedFeed) else customFeedInput,
                                onValueChange = { viewModel.customFeedStr.value = it },
                                label = if (isMetric) "Feed Rate F (mm/min)" else "Feed Rate F (IPM)",
                                keyboardType = KeyboardType.Decimal,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("mrr_feed_input")
                            )

                            Text(
                                text = if (customFeedInput.isEmpty()) "Linked to calculated Feed Rate" else "Custom Feed Rate override in use",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (customFeedInput.isEmpty()) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                            )
                        }
                    }

                    // Bottom Calculator Actions button
                    Button(
                        onClick = {
                            keyboardController?.hide()
                            Toast.makeText(context, "Calculated in Real-Time!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("calculateBtn"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "recalculate")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "RECALCULATE PROFILE",
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // 5. Saved Toolpaths & Recipes Log
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📂 SAVED SHOP RECIPES (${savedRecipes.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }

        if (savedRecipes.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "empty",
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                        Text(
                            text = "No saved profiles yet",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = "Fill calculations and tap [+ SAVE TO PANEL] to capture shop-floor configurations.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(savedRecipes, key = { it.id }) { recipe ->
                CncRecipeListItem(
                    recipe = recipe,
                    onDelete = { viewModel.deleteRecipe(recipe) }
                )
            }
        }
    }

    // Modal dialogue to save configuration
    if (showSaveDialog) {
        var setupTitle by remember { mutableStateOf("") }
        val autoSuggested = if (selectedMaterial.isNotEmpty()) {
            "$selectedMaterial Toolpath"
        } else {
            "Custom Path (${diameterInput}mm)"
        }

        Dialog(
            onDismissRequest = { showSaveDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = true)
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                    .testTag("save_recipe_dialog")
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "💾 SAVE TOOLPATH FILE",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = FontFamily.Monospace
                    )

                    Text(
                        text = "Store current spindle speeds, feeds-per-tooth, and metal parameters into local persistent database.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )

                    OutlinedTextField(
                        value = setupTitle,
                        onValueChange = { setupTitle = it },
                        placeholder = { Text("e.g. Aluminum Rough Hole") },
                        label = { Text("Profile Name / Description") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("recipe_title_input"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showSaveDialog = false },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("CANCEL")
                        }

                        Button(
                            onClick = {
                                val finalTitle = setupTitle.ifEmpty { autoSuggested }
                                viewModel.saveCurrentConfiguration(finalTitle)
                                showSaveDialog = false
                                Toast.makeText(context, "Recipe Saved Successful!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .weight(1.2f)
                                .testTag("save_recipe_confirm_btn"),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("SAVE TO LIBRARY")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MachineControlPanelHeader(
    isMetric: Boolean,
    onUnitToggle: () -> Unit
) {
    // Elegant pulsing warning animation LED
    val infiniteTransition = rememberInfiniteTransition(label = "pulsing_led")
    val ledAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "led_pulse"
    )

    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF161719) // Custom Heavy Charcoal
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, Color(0xFF37474F), RoundedCornerShape(12.dp))
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Retro Top Grid strip design
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Console title with simulated indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Beating green active machine panel indicator
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .alpha(ledAlpha)
                            .background(Color(0xFF00E676), RoundedCornerShape(5.dp))
                    )
                    Text(
                        text = "CNC SYSTEM CONSOLE v1.0",
                        color = Color(0xFFECEFF1),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.5.sp
                    )
                }

                Text(
                    text = "ONLINE",
                    color = Color(0xFF00E676),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .border(1.dp, Color(0xFF00E676), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            Divider(color = Color(0xFF37474F), thickness = 1.dp)

            Spacer(modifier = Modifier.height(12.dp))

            // Main Console Action: Title and System Unit Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "navcnc RPM/Feed Calculator",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Spindle speed, radial feeds & MRR formulas",
                        color = Color(0xFF90A4AE),
                        fontSize = 11.sp
                    )
                }

                // Metric / Imperial system switcher
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF263238))
                        .clickable { onUnitToggle() }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (isMetric) "⚡ METRIC (mm)" else "🛠️ IMPERIAL (in)",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "swap",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun MachineModeSelectTabs(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit
) {
    val tabs: List<Pair<String, ImageVector>> = listOf(
        Pair("SPEED (RPM)", Icons.Default.PlayArrow),
        Pair("FEED RATE", Icons.Default.ArrowForward),
        Pair("MRR (VOLUME)", Icons.Default.Info)
    )

    TabRow(
        selectedTabIndex = selectedTabIndex,
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.primary,
        indicator = { tabPositions ->
            TabRowDefaults.SecondaryIndicator(
                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                color = MaterialTheme.colorScheme.primary,
                height = 3.dp
            )
        },
        divider = {}
    ) {
        tabs.forEachIndexed { index, item ->
            val isSelected = selectedTabIndex == index
            val colorSelected by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                label = "textColor"
            )

            Tab(
                selected = isSelected,
                onClick = { onTabSelected(index) },
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .testTag("calculator_tab_$index"),
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = item.second,
                            contentDescription = item.first,
                            modifier = Modifier.size(16.dp),
                            tint = colorSelected
                        )
                        Text(
                            text = item.first,
                            color = colorSelected,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontFamily = FontFamily.Monospace,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1
                        )
                    }
                }
            )
        }
    }
}

@Composable
fun CncDigitalReadOutPanel(
    selectedTab: Int,
    isMetric: Boolean,
    rpm: Int,
    feedRate: Double,
    mrr: Double,
    vc: Double,
    diameter: Double,
    materialName: String,
    onSaveClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0F111A) // Real black screen look
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, Color(0xFF263238), RoundedCornerShape(12.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Screen title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📟 DIGITAL READ-OUT (DRO)",
                    color = Color(0xFFFF9100), // Amber phosphor
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )

                Button(
                    onClick = onSaveClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = Color.Black
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .height(28.dp)
                        .testTag("save_config_button")
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "save",
                        modifier = Modifier.size(14.dp),
                        tint = Color.Black
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "SAVE RECIPE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Display major results dynamically based on Tab Choice with Neon typography
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF07090F), RoundedCornerShape(8.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1.3f)) {
                    val unitSpeed = if (isMetric) "m/min" else "SFM"
                    val labelText = when (selectedTab) {
                        0 -> "SPINDLE ROTATIONAL SPEED"
                        1 -> "LINEAR AXIAL FEED"
                        else -> "MATERIAL REMOVAL RATE"
                    }
                    Text(
                        text = labelText,
                        color = Color(0xFFECEFF1).copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    val (glowingNumber, metricText) = when (selectedTab) {
                        0 -> Pair("$rpm", "RPM")
                        1 -> Pair(String.format(Locale.US, "%.1f", feedRate), if (isMetric) "mm/min" else "IPM")
                        else -> Pair(String.format(Locale.US, "%.3f", mrr), if (isMetric) "cm³/min" else "in³/min")
                    }

                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = glowingNumber,
                            color = Color(0xFFFFCC00), // Neon Yellow-Amber
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.testTag("resultText")
                        )
                        Text(
                            text = metricText,
                            color = Color(0xFF00E5FF), // Cyan coolant
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }

                // Circle tool rotation simulator drawing canvas
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .weight(0.7f),
                    contentAlignment = Alignment.Center
                ) {
                    ToolRotationCanvas(rpmSpeed = rpm)
                }
            }

            // Real-time dynamic technical recommendations feedback
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF37474F), RoundedCornerShape(6.dp))
                    .background(Color(0xFF13151D), RoundedCornerShape(6.dp))
                    .padding(8.dp)
            ) {
                Text(
                    text = "📋 MACHINING TELEMETRY & ALERTS",
                    fontSize = 10.sp,
                    color = Color(0xFFB0BEC5),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                val (metricVelocity, velocityUnit) = if (isMetric) {
                    Pair(String.format(Locale.US, "%.1f", vc), "m/min")
                } else {
                    Pair(String.format(Locale.US, "%.1f", vc), "SFM")
                }

                val prompt = when {
                    diameter <= 0.0 -> {
                        "⚠️ ERROR: Tool Diameter cannot be zero. Enter tool diameter to initiate speed calculator."
                    }
                    rpm <= 0 -> {
                        "⚠️ ERROR: Cutting speed set to zero. Spindle speed cannot be calculated."
                    }
                    rpm > 18000 -> {
                        "⚠️ HIGH SPEED ALERT ($rpm RPM): Requires dynamic tool balance. Recommended for small engraving, wood or composite tooling."
                    }
                    rpm in 1..400 -> {
                        "⚠️ SLOW RANGE ALERT ($rpm RPM): Common for heavy mill steel indexing face mills. Ensure system is in low gear range (high-torque)."
                    }
                    materialName.contains("Aluminum", ignoreCase = true) && vc < 150.0 -> {
                        "💡 Aluminum suggests higher surface speeds (>150 m/min / 500 SFM) for optimum surface finish and chip evacuation."
                    }
                    materialName.contains("Stainless", ignoreCase = true) && vc > 100.0 -> {
                        "⚠️ HIGH TEMPERATURE TOOL RISK: Carbide/HSS will heat quickly in Stainless Steel at VC > 100m/min. Recommended low coolant flood."
                    }
                    else -> {
                        "✅ Speed balanced successfully. Active tool boundary velocity: $metricVelocity $velocityUnit on $materialName."
                    }
                }

                Text(
                    text = prompt,
                    fontSize = 11.sp,
                    color = when {
                        prompt.startsWith("⚠️") -> Color(0xFFFF5252) // Warning red
                        prompt.startsWith("💡") -> Color(0xFFFFC107) // Amber notice
                        else -> Color(0xFF81C784) // Safe green
                    },
                    lineHeight = 15.sp
                )
            }
        }
    }
}

@Composable
fun ToolRotationCanvas(rpmSpeed: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "tool_animation")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (rpmSpeed <= 0) 1000000 else (120000 / (rpmSpeed.coerceAtLeast(100)).coerceAtMost(3000)),
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation_angle"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val radius = size.width.coerceAtMost(size.height) / 2.3f

        // Draw machine spindle boundary
        drawCircle(
            color = Color(0xFF263238),
            radius = radius,
            center = Offset(centerX, centerY),
            style = Stroke(width = 1.5.dp.toPx())
        )

        // Draw coolant shield indices
        for (i in 0 until 12) {
            val angleRad = (i * 30) * PI / 180f
            val startX = centerX + (radius - 4.dp.toPx()) * kotlin.math.cos(angleRad).toFloat()
            val startY = centerY + (radius - 4.dp.toPx()) * kotlin.math.sin(angleRad).toFloat()
            val endX = centerX + radius * kotlin.math.cos(angleRad).toFloat()
            val endY = centerY + radius * kotlin.math.sin(angleRad).toFloat()
            
            drawLine(
                color = Color(0xFF37474F),
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = 1.dp.toPx()
            )
        }

        // Draw dynamic rotating tool core (2 flutes representer)
        if (rpmSpeed > 0) {
            val toolAngleRad1 = (angle) * PI / 180f
            val toolAngleRad2 = (angle + 180f) * PI / 180f

            val toolCoreRadius = radius * 0.75f
            
            // Flute 1 Line
            val flute1X = centerX + toolCoreRadius * kotlin.math.cos(toolAngleRad1).toFloat()
            val flute1Y = centerY + toolCoreRadius * kotlin.math.sin(toolAngleRad1).toFloat()
            drawLine(
                color = Color(0xFFFF9100),
                start = Offset(centerX, centerY),
                end = Offset(flute1X, flute1Y),
                strokeWidth = 3.dp.toPx()
            )
            // Flute cutting tip 1
            drawCircle(
                color = Color(0xFF00E5FF),
                radius = 3.dp.toPx(),
                center = Offset(flute1X, flute1Y)
            )

            // Flute 2 Line
            val flute2X = centerX + toolCoreRadius * kotlin.math.cos(toolAngleRad2).toFloat()
            val flute2Y = centerY + toolCoreRadius * kotlin.math.sin(toolAngleRad2).toFloat()
            drawLine(
                color = Color(0xFFFF9100),
                start = Offset(centerX, centerY),
                end = Offset(flute2X, flute2Y),
                strokeWidth = 3.dp.toPx()
            )
            // Flute cutting tip 2
            drawCircle(
                color = Color(0xFF00E5FF),
                radius = 3.dp.toPx(),
                center = Offset(flute2X, flute2Y)
            )
        }

        // Inside center screw represent
        drawCircle(
            color = Color(0xFF90A4AE),
            radius = 4.dp.toPx(),
            center = Offset(centerX, centerY)
        )
    }
}

@Composable
fun MaterialQuickPresetsRow(
    presets: List<MaterialPreset>,
    selectedPresetName: String,
    onPresetSelected: (MaterialPreset) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "⚡ QUICK MATERIAL SELECT",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(presets) { preset ->
                val isSelected = preset.name == selectedPresetName
                val containerColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                    label = "preset_color"
                )
                val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent

                Row(
                    modifier = Modifier
                        .background(containerColor, RoundedCornerShape(8.dp))
                        .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                        .clickable { onPresetSelected(preset) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("material_preset_${preset.name.replace(" ", "_")}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(text = preset.chipsMaterialEmoji, fontSize = 14.sp)
                    Text(
                        text = preset.name,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun ToolSizeQuickPresets(
    isMetric: Boolean,
    onPresetSelected: (Double, Double) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "📐 QUICK TOOL SIZE SELECT",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val sizes = if (isMetric) {
                // mm options
                listOf(
                    Pair("3mm core", 3.0),
                    Pair("6mm slot", 6.0),
                    Pair("10mm mill", 10.0),
                    Pair("12mm rougher", 12.0)
                )
            } else {
                // Imperial equivalents
                listOf(
                    Pair("1/8\" drill", 0.125),
                    Pair("1/4\" endmill", 0.250),
                    Pair("3/8\" slot", 0.375),
                    Pair("1/2\" rougher", 0.500)
                )
            }

            sizes.forEach { (label, value) ->
                val conversionVal = if (isMetric) {
                    Pair(value / 25.4, value)
                } else {
                    Pair(value, value * 25.4)
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .clickable { onPresetSelected(conversionVal.first, conversionVal.second) }
                        .weight(1f)
                        .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
                        .testTag("tool_preset_${label.replace("\"", "")}"),
                ) {
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 5.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CncTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it) },
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = ImeAction.Done
        ),
        label = {
            Text(
                text = label,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "clear field",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    )
}

@Composable
fun CncRecipeListItem(
    recipe: CncRecipe,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
            .testTag("saved_recipe_item_${recipe.id}")
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = recipe.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1
                        )
                        Box(
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (recipe.isMetric) "METRIC" else "IMPERIAL",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "Tool Description: ${recipe.toolName} | ${recipe.material}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(24.dp)
                        .testTag("delete_recipe_btn_${recipe.id}")
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete prescription",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), thickness = 0.5.dp)

            Spacer(modifier = Modifier.height(8.dp))

            // Spec Parameters grid (like RPM & Feed side-by-side)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = "SPINDLE ROTATE",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "${recipe.rpm} RPM",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFCC00),
                        fontFamily = FontFamily.Monospace
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "LINEAR FEED RATE",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        fontFamily = FontFamily.Monospace
                    )
                    val feedUnit = if (recipe.isMetric) "mm/min" else "IPM"
                    Text(
                        text = "${String.format(Locale.US, "%.1f", recipe.feedRate)} $feedUnit",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00E5FF),
                        fontFamily = FontFamily.Monospace
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "BOUNDARY VC",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        fontFamily = FontFamily.Monospace
                    )
                    val speedUnit = if (recipe.isMetric) "m/min" else "SFM"
                    Text(
                        text = "${String.format(Locale.US, "%.1f", recipe.cuttingSpeed)} $speedUnit",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
