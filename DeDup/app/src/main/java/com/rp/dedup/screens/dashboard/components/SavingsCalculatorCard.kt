package com.rp.dedup.screens.dashboard.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rp.dedup.R
import com.rp.dedup.UIConstants
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

@Composable
fun SavingsCalculatorCard(reclaimableBytes: Long, overrideCurrencyCode: String = "") {
    val locale = Locale.getDefault()
    val currency = try {
        if (overrideCurrencyCode.isNotEmpty()) Currency.getInstance(overrideCurrencyCode)
        else Currency.getInstance(locale)
    } catch (_: Exception) {
        Currency.getInstance("USD")
    }

    val costPerGb = when (currency.currencyCode) {
        "USD" -> 0.03;  "CAD" -> 0.04;  "MXN" -> 0.49;  "BRL" -> 0.04;  "ARS" -> 3.00
        "CLP" -> 30.0;  "COP" -> 130.0; "PEN" -> 0.11
        "EUR" -> 0.02;  "GBP" -> 0.016; "CHF" -> 0.011; "SEK" -> 0.29;  "NOK" -> 0.29
        "DKK" -> 0.15;  "PLN" -> 0.05;  "CZK" -> 0.49;  "HUF" -> 9.90;  "RON" -> 0.05
        "BGN" -> 0.04;  "TRY" -> 0.29;  "RUB" -> 0.69;  "UAH" -> 0.29
        "INR" -> 1.30;  "JPY" -> 2.50;  "CNY" -> 0.20;  "KRW" -> 39.0;  "AUD" -> 0.045
        "NZD" -> 0.05;  "SGD" -> 0.04;  "HKD" -> 0.23;  "TWD" -> 0.90;  "MYR" -> 0.13
        "THB" -> 0.35;  "IDR" -> 490.0; "PHP" -> 1.60;  "VND" -> 750.0; "PKR" -> 8.49
        "BDT" -> 3.49;  "SAR" -> 0.11;  "AED" -> 0.11;  "ILS" -> 0.11;  "EGP" -> 0.49
        "NGN" -> 19.0;  "KES" -> 1.10;  "ZAR" -> 0.45
        else  -> 0.03
    }

    val reclaimableGb = reclaimableBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
    val moneySaved = reclaimableGb * costPerGb
    val currencyFormatter = NumberFormat.getCurrencyInstance(locale).apply { this.currency = currency }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
        )
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = UIConstants.ColorSavingsGreen.copy(alpha = 0.15f),
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Payments,
                        contentDescription = null,
                        tint = UIConstants.ColorSavingsGreen,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    stringResource(R.string.potential_savings),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
                Text(
                    "Money saved: ${currencyFormatter.format(moneySaved)}",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                )
                Text(
                    "Based on storage cost of ${currencyFormatter.format(costPerGb)} / GB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f)
                )
            }
        }
    }
}
