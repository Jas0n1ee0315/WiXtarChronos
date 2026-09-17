package com.jas0n1ee.wixtar.chronos.mobile.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun MultiDatePickerDialog(
    initialSelectedDatesCsv: String,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    var selectedDates by remember(initialSelectedDatesCsv) {
        mutableStateOf(
            initialSelectedDatesCsv.split(",")
                .mapNotNull { s -> s.trim().takeIf { it.isNotBlank() } }
                .toSet()
        )
    }

    var currentYearMonth by remember { mutableStateOf(YearMonth.now()) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = {
                    val sortedCsv = selectedDates.sorted().joinToString(",")
                    onConfirm(sortedCsv)
                }
            ) {
                Text("確定", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (selectedDates.isNotEmpty()) {
                    TextButton(onClick = { selectedDates = emptySet() }) {
                        Text("清空", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismissRequest) {
                    Text("取消")
                }
            }
        },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "選擇自動打卡暫停日",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "已選 ${selectedDates.size} 天",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Year & Month Header Navigation
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { currentYearMonth = currentYearMonth.minusMonths(1) }) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "上個月")
                    }

                    Text(
                        text = "${currentYearMonth.year}年 ${currentYearMonth.monthValue}月",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    IconButton(onClick = { currentYearMonth = currentYearMonth.plusMonths(1) }) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "下個月")
                    }
                }

                // Days of Week Header (日、一、二、三、四、五、六)
                val daysOfWeek = listOf("日", "一", "二", "三", "四", "五", "六")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    daysOfWeek.forEach { dayLabel ->
                        Text(
                            text = dayLabel,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Month Calendar Days Grid
                val firstDayOfWeek = currentYearMonth.atDay(1).dayOfWeek.value % 7 // 0 for Sunday
                val daysInMonth = currentYearMonth.lengthOfMonth()
                val totalCells = firstDayOfWeek + daysInMonth

                LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(totalCells) { index ->
                        if (index < firstDayOfWeek) {
                            Spacer(modifier = Modifier.size(36.dp))
                        } else {
                            val dayOfMonth = index - firstDayOfWeek + 1
                            val date = LocalDate.of(currentYearMonth.year, currentYearMonth.monthValue, dayOfMonth)
                            val dateStr = date.format(formatter)
                            val isSelected = selectedDates.contains(dateStr)
                            val isToday = date == LocalDate.now()

                            Surface(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable {
                                        selectedDates = if (isSelected) {
                                            selectedDates - dateStr
                                        } else {
                                            selectedDates + dateStr
                                        }
                                    },
                                shape = CircleShape,
                                color = when {
                                    isSelected -> MaterialTheme.colorScheme.primaryContainer
                                    isToday -> MaterialTheme.colorScheme.surfaceContainerHigh
                                    else -> MaterialTheme.colorScheme.surface
                                },
                                contentColor = when {
                                    isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                                    isToday -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Text(
                                        text = "$dayOfMonth",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }

                if (selectedDates.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        val displayFormatter = DateTimeFormatter.ofPattern("MM/dd(E)", Locale.TAIWAN)
                        val summaryText = selectedDates.sorted().mapNotNull { dStr ->
                            try {
                                LocalDate.parse(dStr, formatter).format(displayFormatter)
                            } catch (_: Exception) {
                                null
                            }
                        }.joinToString("、")

                        Text(
                            text = "暫停日：$summaryText",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        }
    )
}

fun formatPauseDaysForDisplay(daysCsv: String): String {
    if (daysCsv.isBlank()) return "點擊設定暫停打卡日期"
    val days = daysCsv.split(",").mapNotNull { s -> s.trim().takeIf { it.isNotBlank() } }
    if (days.isEmpty()) return "點擊設定暫停打卡日期"

    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val displayFormatter = DateTimeFormatter.ofPattern("MM/dd(E)", Locale.TAIWAN)

    val formattedList = days.sorted().mapNotNull { dayStr ->
        try {
            val date = LocalDate.parse(dayStr, formatter)
            date.format(displayFormatter)
        } catch (_: Exception) {
            null
        }
    }

    return if (formattedList.isEmpty()) "點擊設定暫停打卡日期" else formattedList.joinToString("、")
}
