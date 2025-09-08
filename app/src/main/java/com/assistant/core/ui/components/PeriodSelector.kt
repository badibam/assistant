package com.assistant.core.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.assistant.core.ui.*
import com.assistant.core.services.AppConfigService
import com.assistant.core.utils.DateUtils
import kotlinx.coroutines.launch
import java.util.*

/**
 * Enum pour les types de périodes supportés
 */
enum class PeriodType {
    HOUR, DAY, WEEK, MONTH, YEAR
}

/**
 * Data class représentant une période
 */
data class Period(
    val timestamp: Long,
    val type: PeriodType
) {
    companion object {
        fun now(type: PeriodType): Period = Period(System.currentTimeMillis(), type)
    }
}

/**
 * Composant générique de sélection de période avec navigation intelligente
 * Affiche des labels relatifs (Aujourd'hui, Cette semaine, etc.) avec flèches de navigation
 * et possibilité de cliquer pour ouvrir un sélecteur de date
 */
@Composable
fun PeriodSelector(
    period: Period,
    onPeriodChange: (Period) -> Unit,
    modifier: Modifier = Modifier,
    showDatePicker: Boolean = true
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // État pour le sélecteur de date
    var showPicker by remember { mutableStateOf(false) }
    
    // Service de configuration
    val appConfigService = remember { AppConfigService(context) }
    
    // Génération du label intelligent
    val label = remember(period) {
        generatePeriodLabel(period, appConfigService)
    }
    
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Bouton précédent
        UI.ActionButton(
            action = ButtonAction.BACK,
            display = ButtonDisplay.ICON,
            size = Size.M,
            onClick = {
                val previousPeriod = getPreviousPeriod(period, appConfigService)
                onPeriodChange(previousPeriod)
            }
        )
        
        // Label cliquable au centre
        Box(modifier = Modifier.weight(1f)) {
            if (showDatePicker) {
                UI.Button(
                    type = ButtonType.DEFAULT,
                    size = Size.M,
                    onClick = { showPicker = true }
                ) {
                    UI.Text(
                        text = label,
                        type = TextType.BODY,
                        fillMaxWidth = true,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                UI.Text(
                    text = label,
                    type = TextType.BODY,
                    fillMaxWidth = true,
                    textAlign = TextAlign.Center
                )
            }
        }
        
        // Bouton suivant
        UI.ActionButton(
            action = ButtonAction.UP, // Réutilise UP pour "suivant"
            display = ButtonDisplay.ICON,
            size = Size.M,
            onClick = {
                val nextPeriod = getNextPeriod(period, appConfigService)
                onPeriodChange(nextPeriod)
            }
        )
    }
    
    // Sélecteur de date si activé
    if (showPicker && showDatePicker) {
        when (period.type) {
            PeriodType.HOUR -> {
                // Pour HOUR : DatePicker garde l'heure existante
                UI.DatePicker(
                    selectedDate = DateUtils.formatDateForDisplay(period.timestamp),
                    onDateSelected = { selectedDate ->
                        // Combiner nouvelle date + heure existante
                        val existingHour = Calendar.getInstance().apply { 
                            timeInMillis = period.timestamp 
                        }.get(Calendar.HOUR_OF_DAY)
                        val existingMinute = Calendar.getInstance().apply { 
                            timeInMillis = period.timestamp 
                        }.get(Calendar.MINUTE)
                        
                        val newDate = DateUtils.parseDateForFilter(selectedDate)
                        val combinedTimestamp = Calendar.getInstance().apply {
                            timeInMillis = newDate
                            set(Calendar.HOUR_OF_DAY, existingHour)
                            set(Calendar.MINUTE, existingMinute)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                        
                        onPeriodChange(Period(combinedTimestamp, period.type))
                    },
                    onDismiss = { showPicker = false }
                )
            }
            else -> {
                // Pour les autres types : DatePicker classique
                UI.DatePicker(
                    selectedDate = DateUtils.formatDateForDisplay(period.timestamp),
                    onDateSelected = { selectedDate ->
                        val newTimestamp = DateUtils.parseDateForFilter(selectedDate)
                        onPeriodChange(Period(newTimestamp, period.type))
                    },
                    onDismiss = { showPicker = false }
                )
            }
        }
    }
}

/**
 * Génère le label intelligent pour une période donnée
 */
private fun generatePeriodLabel(period: Period, appConfigService: AppConfigService): String {
    val now = System.currentTimeMillis()
    
    return when (period.type) {
        PeriodType.HOUR -> generateHourLabel(period.timestamp, now)
        PeriodType.DAY -> generateDayLabel(period.timestamp, now)
        PeriodType.WEEK -> generateWeekLabel(period.timestamp, now, appConfigService)
        PeriodType.MONTH -> generateMonthLabel(period.timestamp, now)
        PeriodType.YEAR -> generateYearLabel(period.timestamp, now)
    }
}

/**
 * Labels pour les heures
 */
private fun generateHourLabel(timestamp: Long, now: Long): String {
    val diffHours = ((now - timestamp) / (60 * 60 * 1000)).toInt()
    
    return when {
        diffHours == 0 -> "Maintenant"
        diffHours > 0 && diffHours <= 12 -> "Il y a $diffHours heure${if (diffHours > 1) "s" else ""}"
        diffHours < 0 && diffHours >= -12 -> "Dans ${-diffHours} heure${if (-diffHours > 1) "s" else ""}"
        else -> {
            val date = DateUtils.formatDateForDisplay(timestamp)
            val hour = Calendar.getInstance().apply { timeInMillis = timestamp }.get(Calendar.HOUR_OF_DAY)
            "$date ${hour}h"
        }
    }
}

/**
 * Labels pour les jours
 */
private fun generateDayLabel(timestamp: Long, now: Long): String {
    when {
        DateUtils.isToday(timestamp, now) -> return "Aujourd'hui"
        DateUtils.isYesterday(timestamp, now) -> return "Hier"
    }
    
    val diffDays = ((now - timestamp) / (24 * 60 * 60 * 1000)).toInt()
    
    return when {
        diffDays == -1 -> "Demain"
        diffDays > 0 && diffDays <= 7 -> "Il y a $diffDays jour${if (diffDays > 1) "s" else ""}"
        diffDays < 0 && diffDays >= -7 -> "Dans ${-diffDays} jour${if (-diffDays > 1) "s" else ""}"
        else -> DateUtils.formatDateForDisplay(timestamp)
    }
}

/**
 * Labels pour les semaines
 */
private fun generateWeekLabel(timestamp: Long, now: Long, appConfigService: AppConfigService): String {
    // TODO: Implémenter avec getWeekStartDay() d'AppConfigService
    val diffWeeks = ((now - timestamp) / (7 * 24 * 60 * 60 * 1000)).toInt()
    
    return when {
        diffWeeks == 0 -> "Cette semaine"
        diffWeeks == 1 -> "Semaine dernière"
        diffWeeks == -1 -> "Semaine prochaine"
        diffWeeks > 0 && diffWeeks <= 4 -> "Il y a $diffWeeks semaine${if (diffWeeks > 1) "s" else ""}"
        diffWeeks < 0 && diffWeeks >= -4 -> "Dans ${-diffWeeks} semaine${if (-diffWeeks > 1) "s" else ""}"
        else -> {
            val weekStart = getWeekStart(timestamp, appConfigService)
            "Semaine du ${DateUtils.formatDateForDisplay(weekStart)}"
        }
    }
}

/**
 * Labels pour les mois
 */
private fun generateMonthLabel(timestamp: Long, now: Long): String {
    val cal = Calendar.getInstance()
    val nowCal = Calendar.getInstance().apply { timeInMillis = now }
    val tsCal = Calendar.getInstance().apply { timeInMillis = timestamp }
    
    val diffMonths = (nowCal.get(Calendar.YEAR) - tsCal.get(Calendar.YEAR)) * 12 + 
                    (nowCal.get(Calendar.MONTH) - tsCal.get(Calendar.MONTH))
    
    return when {
        diffMonths == 0 -> "Ce mois"
        diffMonths == 1 -> "Mois dernier"
        diffMonths == -1 -> "Mois prochain"
        diffMonths > 0 && diffMonths <= 6 -> "Il y a $diffMonths mois"
        diffMonths < 0 && diffMonths >= -6 -> "Dans ${-diffMonths} mois"
        else -> {
            val monthNames = arrayOf(
                "Janvier", "Février", "Mars", "Avril", "Mai", "Juin",
                "Juillet", "Août", "Septembre", "Octobre", "Novembre", "Décembre"
            )
            val month = monthNames[tsCal.get(Calendar.MONTH)]
            val year = tsCal.get(Calendar.YEAR)
            "$month $year"
        }
    }
}

/**
 * Labels pour les années
 */
private fun generateYearLabel(timestamp: Long, now: Long): String {
    val nowYear = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.YEAR)
    val tsYear = Calendar.getInstance().apply { timeInMillis = timestamp }.get(Calendar.YEAR)
    val diffYears = nowYear - tsYear
    
    return when {
        diffYears == 0 -> "Cette année"
        diffYears == 1 -> "Année dernière"
        diffYears == -1 -> "Année prochaine"
        diffYears > 0 && diffYears <= 3 -> "Il y a $diffYears année${if (diffYears > 1) "s" else ""}"
        diffYears < 0 && diffYears >= -3 -> "Dans ${-diffYears} année${if (-diffYears > 1) "s" else ""}"
        else -> tsYear.toString()
    }
}

/**
 * Calcule la période précédente
 */
private fun getPreviousPeriod(period: Period, appConfigService: AppConfigService): Period {
    val cal = Calendar.getInstance().apply { timeInMillis = period.timestamp }
    
    when (period.type) {
        PeriodType.HOUR -> cal.add(Calendar.HOUR_OF_DAY, -1)
        PeriodType.DAY -> cal.add(Calendar.DAY_OF_MONTH, -1)
        PeriodType.WEEK -> cal.add(Calendar.WEEK_OF_YEAR, -1)
        PeriodType.MONTH -> cal.add(Calendar.MONTH, -1)
        PeriodType.YEAR -> cal.add(Calendar.YEAR, -1)
    }
    
    return Period(cal.timeInMillis, period.type)
}

/**
 * Calcule la période suivante
 */
private fun getNextPeriod(period: Period, appConfigService: AppConfigService): Period {
    val cal = Calendar.getInstance().apply { timeInMillis = period.timestamp }
    
    when (period.type) {
        PeriodType.HOUR -> cal.add(Calendar.HOUR_OF_DAY, 1)
        PeriodType.DAY -> cal.add(Calendar.DAY_OF_MONTH, 1)
        PeriodType.WEEK -> cal.add(Calendar.WEEK_OF_YEAR, 1)
        PeriodType.MONTH -> cal.add(Calendar.MONTH, 1)
        PeriodType.YEAR -> cal.add(Calendar.YEAR, 1)
    }
    
    return Period(cal.timeInMillis, period.type)
}

/**
 * Calcule le début de la semaine selon la configuration
 */
private fun getWeekStart(timestamp: Long, appConfigService: AppConfigService): Long {
    // TODO: Implémenter avec appConfigService.getWeekStartDay()
    // Pour l'instant, utilise lundi par défaut
    val cal = Calendar.getInstance().apply { 
        timeInMillis = timestamp
        firstDayOfWeek = Calendar.MONDAY
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
    }
    return cal.timeInMillis
}