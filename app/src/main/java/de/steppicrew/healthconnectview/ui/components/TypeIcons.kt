package de.steppicrew.healthconnectview.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Accessible
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Sports
import androidx.compose.material.icons.filled.Stairs
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.ui.graphics.vector.ImageVector
import de.steppicrew.healthconnectview.registry.Category
import de.steppicrew.healthconnectview.registry.RecordTypeSpec

/**
 * An icon for a type, where a row of figures has no room for names: the overview tile's 2x1
 * grid. Never the only cue -- the icon carries the type's name for a screen reader, and a 2x2
 * tile names what is unusual in words. Types without a mark of their own take their category's.
 */
fun iconFor(spec: RecordTypeSpec<*>): ImageVector = when (spec.type.simpleName) {
    "StepsRecord" -> Icons.AutoMirrored.Filled.DirectionsWalk
    "FloorsClimbedRecord" -> Icons.Default.Stairs
    "ElevationGainedRecord" -> Icons.Default.Terrain
    "DistanceRecord" -> Icons.Default.Route
    "ActiveCaloriesBurnedRecord", "TotalCaloriesBurnedRecord", "BasalMetabolicRateRecord" -> Icons.Default.LocalFireDepartment
    "SpeedRecord", "StepsCadenceRecord" -> Icons.Default.Speed
    "CyclingPedalingCadenceRecord" -> Icons.AutoMirrored.Filled.DirectionsBike
    "PowerRecord" -> Icons.Default.Bolt
    "WheelchairPushesRecord" -> Icons.Default.Accessible
    "ExerciseSessionRecord" -> Icons.Default.FitnessCenter
    "PlannedExerciseSessionRecord" -> Icons.Default.Event
    "HeightRecord" -> Icons.Default.Height
    "BodyWaterMassRecord" -> Icons.Default.WaterDrop
    "HeartRateRecord" -> Icons.Default.Favorite
    "RestingHeartRateRecord" -> Icons.Default.FavoriteBorder
    "RespiratoryRateRecord" -> Icons.Default.Air
    "OxygenSaturationRecord" -> Icons.Default.Opacity
    "BloodGlucoseRecord" -> Icons.Default.Bloodtype
    "BodyTemperatureRecord", "SkinTemperatureRecord", "BasalBodyTemperatureRecord" -> Icons.Default.Thermostat
    "HydrationRecord" -> Icons.Default.LocalDrink
    else -> when (spec.category) {
        Category.ACTIVITY -> Icons.Default.Sports
        Category.BODY -> Icons.Default.MonitorWeight
        Category.VITALS -> Icons.Default.MonitorHeart
        Category.NUTRITION -> Icons.Default.Restaurant
        Category.SLEEP -> Icons.Default.Bedtime
        Category.MINDFULNESS -> Icons.Default.SelfImprovement
        Category.CYCLE -> Icons.Default.CalendarMonth
    }
}
