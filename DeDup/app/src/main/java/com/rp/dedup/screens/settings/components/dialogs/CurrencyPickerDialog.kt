package com.rp.dedup.screens.settings.components.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rp.dedup.R
import java.util.Currency
import java.util.Locale

private data class CurrencyInfo(val code: String, val name: String, val flag: String)

private val ALL_CURRENCIES = listOf(
    CurrencyInfo("USD", "US Dollar", "🇺🇸"),          CurrencyInfo("CAD", "Canadian Dollar", "🇨🇦"),
    CurrencyInfo("MXN", "Mexican Peso", "🇲🇽"),        CurrencyInfo("BRL", "Brazilian Real", "🇧🇷"),
    CurrencyInfo("ARS", "Argentine Peso", "🇦🇷"),      CurrencyInfo("CLP", "Chilean Peso", "🇨🇱"),
    CurrencyInfo("COP", "Colombian Peso", "🇨🇴"),      CurrencyInfo("PEN", "Peruvian Sol", "🇵🇪"),
    CurrencyInfo("EUR", "Euro", "🇪🇺"),                CurrencyInfo("GBP", "British Pound", "🇬🇧"),
    CurrencyInfo("CHF", "Swiss Franc", "🇨🇭"),         CurrencyInfo("SEK", "Swedish Krona", "🇸🇪"),
    CurrencyInfo("NOK", "Norwegian Krone", "🇳🇴"),     CurrencyInfo("DKK", "Danish Krone", "🇩🇰"),
    CurrencyInfo("PLN", "Polish Złoty", "🇵🇱"),        CurrencyInfo("CZK", "Czech Koruna", "🇨🇿"),
    CurrencyInfo("HUF", "Hungarian Forint", "🇭🇺"),    CurrencyInfo("RON", "Romanian Leu", "🇷🇴"),
    CurrencyInfo("BGN", "Bulgarian Lev", "🇧🇬"),       CurrencyInfo("TRY", "Turkish Lira", "🇹🇷"),
    CurrencyInfo("RUB", "Russian Ruble", "🇷🇺"),       CurrencyInfo("UAH", "Ukrainian Hryvnia", "🇺🇦"),
    CurrencyInfo("INR", "Indian Rupee", "🇮🇳"),        CurrencyInfo("JPY", "Japanese Yen", "🇯🇵"),
    CurrencyInfo("CNY", "Chinese Yuan", "🇨🇳"),        CurrencyInfo("KRW", "South Korean Won", "🇰🇷"),
    CurrencyInfo("AUD", "Australian Dollar", "🇦🇺"),   CurrencyInfo("NZD", "New Zealand Dollar", "🇳🇿"),
    CurrencyInfo("SGD", "Singapore Dollar", "🇸🇬"),    CurrencyInfo("HKD", "Hong Kong Dollar", "🇭🇰"),
    CurrencyInfo("TWD", "Taiwan Dollar", "🇹🇼"),       CurrencyInfo("MYR", "Malaysian Ringgit", "🇲🇾"),
    CurrencyInfo("THB", "Thai Baht", "🇹🇭"),           CurrencyInfo("IDR", "Indonesian Rupiah", "🇮🇩"),
    CurrencyInfo("PHP", "Philippine Peso", "🇵🇭"),     CurrencyInfo("VND", "Vietnamese Dong", "🇻🇳"),
    CurrencyInfo("PKR", "Pakistani Rupee", "🇵🇰"),     CurrencyInfo("BDT", "Bangladeshi Taka", "🇧🇩"),
    CurrencyInfo("SAR", "Saudi Riyal", "🇸🇦"),         CurrencyInfo("AED", "UAE Dirham", "🇦🇪"),
    CurrencyInfo("ILS", "Israeli Shekel", "🇮🇱"),      CurrencyInfo("EGP", "Egyptian Pound", "🇪🇬"),
    CurrencyInfo("NGN", "Nigerian Naira", "🇳🇬"),      CurrencyInfo("KES", "Kenyan Shilling", "🇰🇪"),
    CurrencyInfo("ZAR", "South African Rand", "🇿🇦"),
)

@Composable
fun CurrencyPickerDialog(currentCode: String, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(query) {
        if (query.isBlank()) ALL_CURRENCIES
        else ALL_CURRENCIES.filter {
            it.code.contains(query, ignoreCase = true) || it.name.contains(query, ignoreCase = true)
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f).wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Storage Cost Currency", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                Text("Sets the currency used in the savings calculator on the dashboard.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text("Search currency…") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Column(
                    modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val useLocale = currentCode.isEmpty()
                    CurrencyRow(
                        flag = "🌐",
                        name = "Auto (device locale)",
                        subtitle = try { Currency.getInstance(Locale.getDefault()).currencyCode } catch (_: Exception) { "USD" },
                        selected = useLocale,
                        onClick = { onSelect("") }
                    )
                    filtered.forEach { info ->
                        val symbol = try { Currency.getInstance(info.code).symbol } catch (_: Exception) { info.code }
                        CurrencyRow(
                            flag = info.flag,
                            name = info.name,
                            subtitle = "${info.code} · $symbol",
                            selected = currentCode == info.code,
                            onClick = { onSelect(info.code) }
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    }
}

@Composable
private fun CurrencyRow(flag: String, name: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
        border = BorderStroke(1.dp,
            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(flag, fontSize = 20.sp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) {
                Icon(Icons.Default.CheckCircle, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
        }
    }
}
