package com.starspace.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.starspace.app.data.ModelCatalog

/**
 * 只读的模型选择字段：点击后弹出模型列表，支持搜索与自定义模型名。
 */
@Composable
fun ModelPickerField(
    label: String,
    value: String,
    options: List<String>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    emptyLabel: String = "选择一个模型"
) {
    var showDialog by remember { mutableStateOf(false) }
    val theme = AppTheme.colors

    Column(modifier) {
        Text(
            label,
            fontSize = 12.sp,
            color = theme.muted,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        Surface(
            onClick = { showDialog = true },
            shape = RoundedCornerShape(14.dp),
            color = theme.inputFieldBg,
            border = BorderStroke(1.dp, theme.divider),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    value.ifBlank { emptyLabel },
                    color = if (value.isBlank()) theme.muted else theme.ink,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text("▾", color = theme.muted, fontSize = 13.sp)
            }
        }
    }

    if (showDialog) {
        ModelPickerDialog(
            title = "选择模型",
            options = options,
            selected = value,
            onDismiss = { showDialog = false },
            onPick = {
                onValueChange(it)
                showDialog = false
            }
        )
    }
}

@Composable
fun ModelPickerDialog(
    title: String,
    options: List<String>,
    selected: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    val theme = AppTheme.colors
    var query by remember { mutableStateOf("") }

    val extras = remember(options) {
        options.filter { it.isNotBlank() && it !in ModelCatalog.all }.distinct()
    }
    val grouped = remember(extras) {
        buildList {
            val base = ModelCatalog.groups
            if (extras.isNotEmpty()) add("已保存配置中的模型" to extras)
            addAll(base)
        }
    }
    val filteredGroups = remember(query, grouped) {
        val keyword = query.trim()
        grouped.map { (group, models) ->
            group to if (keyword.isBlank()) models
            else models.filter { it.contains(keyword, ignoreCase = true) }
        }.filter { it.second.isNotEmpty() }
    }
    val custom = query.trim()
    val canUseCustom = custom.isNotBlank() &&
        !ModelCatalog.all.any { it.equals(custom, true) } &&
        !extras.any { it.equals(custom, true) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = theme.cardBg,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(top = 18.dp, bottom = 8.dp)) {
                Row(
                    Modifier.padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        title,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.ink,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "当前：${selected.ifBlank { "未选择" }}",
                        fontSize = 11.sp,
                        color = theme.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("搜索模型，或输入自定义模型名") },
                    leadingIcon = {
                        Icon(Icons.Outlined.Search, contentDescription = null, tint = theme.muted)
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )

                if (canUseCustom) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(custom) }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "使用自定义模型",
                            color = theme.starBlue,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            custom,
                            color = theme.muted,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    HorizontalDivider(color = theme.divider, modifier = Modifier.padding(vertical = 4.dp))
                }

                LazyColumn(Modifier.heightIn(max = 340.dp)) {
                    if (filteredGroups.isEmpty()) {
                        item {
                            Text(
                                "没有匹配的模型，直接输入完整模型名即可使用",
                                color = theme.muted,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)
                            )
                        }
                    }
                    filteredGroups.forEach { (group, models) ->
                        item(key = "header-$group") {
                            Text(
                                group,
                                color = theme.muted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 20.dp, top = 10.dp, bottom = 4.dp)
                            )
                        }
                        items(models, key = { "$group-$it" }) { model ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(model) }
                                    .padding(horizontal = 20.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    model,
                                    color = if (model == selected) theme.starBlue else theme.ink,
                                    fontSize = 14.sp,
                                    fontWeight = if (model == selected) FontWeight.SemiBold
                                    else FontWeight.Normal,
                                    modifier = Modifier.weight(1f)
                                )
                                if (model == selected) {
                                    Icon(
                                        Icons.Outlined.Check,
                                        contentDescription = "已选择",
                                        tint = theme.starBlue,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                }
            }
        }
    }
}
