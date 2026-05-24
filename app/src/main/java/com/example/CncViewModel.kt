package com.example

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.CncRecipe
import com.example.data.CncRecipeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.roundToInt

// Data Model to represent predefined Material Guidelines
data class MaterialPreset(
    val name: String,
    val metricVc: Double,   // m/min
    val imperialVc: Double, // SFM
    val metricFz: Double,   // mm/tooth
    val imperialFz: Double, // Inches per tooth (IPT)
    val chipsMaterialEmoji: String
)

class CncViewModel(private val repository: CncRecipeRepository) : ViewModel() {

    // Metric (true) vs Imperial (false)
    private val _isMetric = MutableStateFlow(true)
    val isMetric = _isMetric.asStateFlow()

    // Tab Choice: 0 = RPM, 1 = Feed, 2 = MRR
    private val _selectedTab = MutableStateFlow(0)
    val selectedTab = _selectedTab.asStateFlow()

    // Common Inputs
    val cuttingSpeedStr = MutableStateFlow("150") // Vc (m/min or SFM)
    val diameterStr = MutableStateFlow("10") // D (mm or inches)
    val flutesStr = MutableStateFlow("3") // Number of teeth (flutes)
    val feedPerToothStr = MutableStateFlow("0.1") // fz (mm/tooth or IPT)

    // Sub-Calculators Specific (so they can be overriden manually)
    val customRpmStr = MutableStateFlow("") // Overrides calculated RPM in Feed Tab
    val depthOfCutStr = MutableStateFlow("2.0") // ap (mm or inches)
    val widthOfCutStr = MutableStateFlow("5.0") // ae (mm or inches)
    val customFeedStr = MutableStateFlow("") // Overrides calculated Feed Rate in MRR Tab

    // Material List Setup
    val materialPresets = listOf(
        MaterialPreset("Aluminum (6061)", 300.0, 1000.0, 0.12, 0.005, "🪵"),
        MaterialPreset("Mild Steel (A36)", 100.0, 330.0, 0.08, 0.003, "⚙️"),
        MaterialPreset("Stainless (304)", 70.0, 230.0, 0.05, 0.002, "💎"),
        MaterialPreset("Brass (C360)", 150.0, 500.0, 0.10, 0.004, "🌕"),
        MaterialPreset("Titanium (Gr5)", 50.0, 160.0, 0.06, 0.0025, "🚀"),
        MaterialPreset("Delrin Plastic", 400.0, 1300.0, 0.15, 0.006, "⚪")
    )
    
    private val _selectedMaterialPresetName = MutableStateFlow("")
    val selectedMaterialPresetName = _selectedMaterialPresetName.asStateFlow()

    // Retrieve Saved Reciepes
    val savedRecipes: StateFlow<List<CncRecipe>> = repository.allRecipes
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun toggleUnits() {
        val oldMetric = _isMetric.value
        val newMetric = !oldMetric
        _isMetric.value = newMetric

        // Recalculate or convert values nicely to prevent huge numbers
        val d = diameterStr.value.toDoubleOrNull() ?: 10.0
        val vc = cuttingSpeedStr.value.toDoubleOrNull() ?: 150.0
        val fz = feedPerToothStr.value.toDoubleOrNull() ?: 0.1
        val doc = depthOfCutStr.value.toDoubleOrNull() ?: 2.0
        val woc = widthOfCutStr.value.toDoubleOrNull() ?: 5.0

        if (newMetric) {
            // Conversions: Imperial -> Metric
            diameterStr.value = formatVal(d * 25.4)
            cuttingSpeedStr.value = formatVal(vc * 0.3048)
            feedPerToothStr.value = formatVal(fz * 25.4)
            depthOfCutStr.value = formatVal(doc * 25.4)
            widthOfCutStr.value = formatVal(woc * 25.4)
        } else {
            // Conversions: Metric -> Imperial
            diameterStr.value = formatVal(d / 25.4)
            cuttingSpeedStr.value = formatVal(vc / 0.3048)
            feedPerToothStr.value = formatVal(fz / 25.4)
            depthOfCutStr.value = formatVal(doc / 25.4)
            widthOfCutStr.value = formatVal(woc / 25.4)
        }
        
        // Clear manual overrides when switching units
        customRpmStr.value = ""
        customFeedStr.value = ""
        _selectedMaterialPresetName.value = ""
    }

    fun setSelectedTab(tabIndex: Int) {
        _selectedTab.value = tabIndex
    }

    fun applyMaterialPreset(preset: MaterialPreset) {
        _selectedMaterialPresetName.value = preset.name
        if (_isMetric.value) {
            cuttingSpeedStr.value = formatVal(preset.metricVc)
            feedPerToothStr.value = formatVal(preset.metricFz)
        } else {
            cuttingSpeedStr.value = formatVal(preset.imperialVc)
            feedPerToothStr.value = formatVal(preset.imperialFz)
        }
    }

    fun applyDiameterPreset(inches: Double, mm: Double) {
        if (_isMetric.value) {
            diameterStr.value = formatVal(mm)
        } else {
            diameterStr.value = formatVal(inches)
        }
    }

    // Calculations (Derived State Flow)
    val calculatedRpm: StateFlow<Int> = combine(
        _isMetric, cuttingSpeedStr, diameterStr
    ) { isMetricMode, vcInput, dInput ->
        val vc = vcInput.toDoubleOrNull() ?: 0.0
        val d = dInput.toDoubleOrNull() ?: 0.0
        if (d <= 0.0 || vc <= 0.0) return@combine 0

        if (isMetricMode) {
            // Metric: RPM = (1000 * Vc) / (pi * D)
            ((1000.0 * vc) / (PI * d)).roundToInt()
        } else {
            // Imperial: RPM = (12 * Vc) / (pi * D)
            ((12.0 * vc) / (PI * d)).roundToInt()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val currentRpmToUse: StateFlow<Int> = combine(
        calculatedRpm, customRpmStr
    ) { calc, override ->
        val ov = override.toIntOrNull()
        if (ov != null && ov > 0) ov else calc
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val calculatedFeedRate: StateFlow<Double> = combine(
        currentRpmToUse, flutesStr, feedPerToothStr
    ) { rpm, flutesInput, fzInput ->
        val flutes = flutesInput.toIntOrNull() ?: 0
        val fz = fzInput.toDoubleOrNull() ?: 0.0
        if (rpm <= 0 || flutes <= 0 || fz <= 0.0) return@combine 0.0

        // F = RPM * flutes * fz
        rpm.toDouble() * flutes * fz
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val currentFeedToUse: StateFlow<Double> = combine(
        calculatedFeedRate, customFeedStr
    ) { calc, override ->
        val ov = override.toDoubleOrNull()
        if (ov != null && ov > 0.0) ov else calc
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val calculatedMrr: StateFlow<Double> = combine(
        _isMetric, depthOfCutStr, widthOfCutStr, currentFeedToUse
    ) { isMetricMode, docInput, wocInput, feedInput ->
        val doc = docInput.toDoubleOrNull() ?: 0.0
        val woc = wocInput.toDoubleOrNull() ?: 0.0
        val feed = feedInput
        if (doc <= 0.0 || woc <= 0.0 || feed <= 0.0) return@combine 0.0

        if (isMetricMode) {
            // Metric: MRR = (ap * ae * F) / 1000  -> Unit is cm^3/min
            (doc * woc * feed) / 1000.0
        } else {
            // Imperial: MRR = ap * ae * F     -> Unit is in^3/min
            doc * woc * feed
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    fun saveCurrentConfiguration(recipeTitle: String) {
        viewModelScope.launch {
            val title = recipeTitle.ifEmpty { "CNC Recipe (${if (_isMetric.value) "Metric" else "Imperial"})" }
            val toolName = "${diameterStr.value}${if (_isMetric.value) "mm" else "in"} " +
                    "${flutesStr.value}-Flute Endmill"
            val material = _selectedMaterialPresetName.value.ifEmpty { "Custom Material" }
            
            val recipe = CncRecipe(
                title = title,
                toolName = toolName,
                material = material,
                isMetric = _isMetric.value,
                cuttingSpeed = cuttingSpeedStr.value.toDoubleOrNull() ?: 0.0,
                diameter = diameterStr.value.toDoubleOrNull() ?: 0.0,
                rpm = currentRpmToUse.value,
                feedPerTooth = feedPerToothStr.value.toDoubleOrNull() ?: 0.0,
                flutes = flutesStr.value.toIntOrNull() ?: 1,
                feedRate = currentFeedToUse.value
            )
            repository.insert(recipe)
        }
    }

    fun deleteRecipe(recipe: CncRecipe) {
        viewModelScope.launch {
            repository.delete(recipe)
        }
    }

    private fun formatVal(v: Double): String {
        return if (v % 1.0 == 0.0) {
            v.toInt().toString()
        } else {
            String.format("%.3f", v).trimEnd('0').trimEnd('.')
        }
    }
}

// Simple Factory for ViewModel instantiation
class CncViewModelFactory(private val repository: CncRecipeRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CncViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CncViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
