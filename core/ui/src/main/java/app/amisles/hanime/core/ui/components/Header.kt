package app.amisles.hanime.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.amisles.hanime.core.ui.R

private fun brandParts(siteUrl: String?): Triple<String, String, String> {
    val host = runCatching { android.net.Uri.parse(siteUrl).host?.lowercase() }.getOrNull()
        ?.removePrefix("www.")
    if (host.isNullOrBlank() || !host.contains('.')) return Triple("H", "anime1", ".me")
    val name = host.substringBeforeLast('.')
    val tld = host.substringAfterLast('.')
    if (name.isEmpty() || tld.isEmpty()) return Triple("H", "anime1", ".me")
    return Triple(name.first().uppercaseChar().toString(), name.drop(1), ".$tld")
}

@Composable
fun Header(
    title: String? = null,
    siteUrl: String? = null,
    onSearchNavigate: () -> Unit = {},
    onProfileClick: (() -> Unit)? = null,
    onComicClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 15.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onProfileClick != null) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = stringResource(R.string.profile_open),
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .size(38.dp)
                    .clickable { onProfileClick() }
                    .padding(end = 10.dp)
            )
        }
        if (title != null) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
        } else {
            val (initial, name, tld) = brandParts(siteUrl)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = initial,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = name,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = tld,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        if (onComicClick != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                contentDescription = stringResource(R.string.comic_entry),
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .size(26.dp)
                    .clickable { onComicClick() }
                    .padding(end = 14.dp)
            )
        }

        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = stringResource(R.string.common_search),
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .size(30.dp)
                .clickable { onSearchNavigate() }
        )
    }
}