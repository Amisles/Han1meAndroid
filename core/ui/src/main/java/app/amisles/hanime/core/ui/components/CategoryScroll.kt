package app.amisles.hanime.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.model.categories

@Composable
fun CategoryScroll(
    onCategorySelected: (String) -> Unit = {}
) {
    val selectedCategory = remember { mutableStateOf(categories[0].label) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 15.dp, vertical = 12.dp)
            .horizontalScroll(rememberScrollState())
    ) {
        categories.forEach { category ->
            val isSelected = selectedCategory.value == category.label
            Text(
                text = stringResource(category.displayRes),
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .clickable {
                        selectedCategory.value = category.label
                        onCategorySelected(category.label)
                    }
            )
        }
    }
}