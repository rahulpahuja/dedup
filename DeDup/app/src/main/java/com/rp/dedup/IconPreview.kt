package com.rp.dedup

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rp.dedup.ui.theme.DeDupTheme

@Composable
@Preview(showBackground = false)
fun AppIconPreview() {
    DeDupTheme {
        Icon(
            painter = painterResource(id = R.drawable.ic_app_icon),
            contentDescription = null,
            modifier = Modifier.size(512.dp),
            tint = androidx.compose.ui.graphics.Color.Unspecified
        )
    }
}
