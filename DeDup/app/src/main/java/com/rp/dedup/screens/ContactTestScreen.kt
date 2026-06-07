package com.rp.dedup.screens

import android.content.ContentProviderOperation
import android.content.Context
import android.provider.ContactsContract
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.rp.dedup.UIConstants
import com.rp.dedup.core.ui.DeDupTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TEST_NOTE = "DEDUP_TEST_CONTACT"

// ── Data model ────────────────────────────────────────────────────────────────

private data class TestContactSpec(
    val name: String,
    val phone: String,
    val email: String? = null
)

private fun buildTestContactSpecs(): List<TestContactSpec> {
    val specs = mutableListOf<TestContactSpec>()

    // 25 exact duplicate pairs — same name, same phone.
    // These should be caught by both the name-match and phone-match detectors.
    for (i in 1..25) {
        val name = "Test Exact %02d".format(i)
        val phone = "+1555%07d".format(i)
        repeat(2) { specs.add(TestContactSpec(name, phone)) }
    }

    // 25 name-only duplicate pairs — same name, different phones.
    // After merge the primary should end up with both numbers.
    for (i in 26..50) {
        val name = "Test NameDup %02d".format(i)
        specs.add(TestContactSpec(name, "+1555%07d".format(i),    "a$i@example.com"))
        specs.add(TestContactSpec(name, "+1556%07d".format(i),    "b$i@example.com"))
    }

    return specs
}

// ── Contact insertion / deletion helpers ──────────────────────────────────────

private suspend fun insertTestContacts(
    context: Context,
    onProgress: (String) -> Unit
): Int = withContext(Dispatchers.IO) {
    val specs = buildTestContactSpecs()
    var inserted = 0
    val batchSize = 20          // keep batch memory-friendly

    specs.chunked(batchSize).forEachIndexed { batchIndex, chunk ->
        val ops = ArrayList<ContentProviderOperation>()

        chunk.forEach { spec ->
            val backRef = ops.size          // index of the newInsert(RawContacts) op

            // 1. Raw contact row.
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null as String?)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null as String?)
                    .build()
            )

            // 2. Display name.
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, backRef)
                    .withValue(ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, spec.name)
                    .build()
            )

            // 3. Phone number.
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, backRef)
                    .withValue(ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, spec.phone)
                    .withValue(ContactsContract.CommonDataKinds.Phone.TYPE,
                        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                    .build()
            )

            // 4. Email (if provided).
            if (spec.email != null) {
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, backRef)
                        .withValue(ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, spec.email)
                        .withValue(ContactsContract.CommonDataKinds.Email.TYPE,
                            ContactsContract.CommonDataKinds.Email.TYPE_WORK)
                        .build()
                )
            }

            // 5. Note used as a tag so we can find and delete test contacts later.
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, backRef)
                    .withValue(ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Note.NOTE, TEST_NOTE)
                    .build()
            )
        }

        context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
        inserted += chunk.size

        withContext(Dispatchers.Main) {
            onProgress("Inserted $inserted / ${specs.size} contacts…")
        }
    }

    inserted
}

private suspend fun deleteTestContacts(
    context: Context,
    onProgress: (String) -> Unit
): Int = withContext(Dispatchers.IO) {

    // Find all raw contact IDs that have our test note.
    val rawIds = mutableListOf<String>()
    context.contentResolver.query(
        ContactsContract.Data.CONTENT_URI,
        arrayOf(ContactsContract.Data.RAW_CONTACT_ID),
        "${ContactsContract.Data.MIMETYPE} = ? AND ${ContactsContract.CommonDataKinds.Note.NOTE} = ?",
        arrayOf(ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE, TEST_NOTE),
        null
    )?.use { cursor ->
        while (cursor.moveToNext()) rawIds.add(cursor.getString(0))
    }

    if (rawIds.isEmpty()) {
        withContext(Dispatchers.Main) { onProgress("No test contacts found to delete.") }
        return@withContext 0
    }

    var deleted = 0
    rawIds.chunked(50).forEach { chunk ->
        val ops = chunk.map { rawId ->
            ContentProviderOperation.newDelete(ContactsContract.RawContacts.CONTENT_URI)
                .withSelection("${ContactsContract.RawContacts._ID} = ?", arrayOf(rawId))
                .build()
        }
        context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ArrayList(ops))
        deleted += chunk.size
        withContext(Dispatchers.Main) { onProgress("Deleted $deleted / ${rawIds.size} contacts…") }
    }

    deleted
}

private suspend fun countTestContacts(context: Context): Int = withContext(Dispatchers.IO) {
    context.contentResolver.query(
        ContactsContract.Data.CONTENT_URI,
        arrayOf(ContactsContract.Data.RAW_CONTACT_ID),
        "${ContactsContract.Data.MIMETYPE} = ? AND ${ContactsContract.CommonDataKinds.Note.NOTE} = ?",
        arrayOf(ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE, TEST_NOTE),
        null
    )?.use { it.count } ?: 0
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactTestScreen(navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isRunning by remember { mutableStateOf(false) }
    var log by remember { mutableStateOf("Ready.\n") }
    var testContactCount by remember { mutableStateOf<Int?>(null) }

    fun appendLog(line: String) { log = "$log$line\n" }

    LaunchedEffect(Unit) {
        testContactCount = countTestContacts(context)
        appendLog("Found ${testContactCount} test contacts already on device.")
    }

    Scaffold(
        topBar = {
            DeDupTopBar(
                title = "Contact Merge Test Lab",
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── Status card ───────────────────────────────────────────────────
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Test contacts on device",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(
                            testContactCount?.toString() ?: "…",
                            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    if (isRunning) CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
            }

            // ── What gets generated ───────────────────────────────────────────
            Card(shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("What will be generated",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold)
                    Text("• 25 exact pairs — same name + same phone (50 contacts)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("• 25 name pairs — same name, different phones + emails (50 contacts)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("All contacts are tagged so they can be deleted cleanly.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // ── Action buttons ────────────────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            isRunning = true
                            appendLog("\n--- Inserting 100 test contacts ---")
                            val n = insertTestContacts(context) { appendLog(it) }
                            appendLog("Done. $n contacts inserted.")
                            testContactCount = countTestContacts(context)
                            isRunning = false
                        }
                    },
                    enabled = !isRunning,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Generate 100")
                }

                OutlinedButton(
                    onClick = {
                        scope.launch {
                            isRunning = true
                            appendLog("\n--- Deleting test contacts ---")
                            val n = deleteTestContacts(context) { appendLog(it) }
                            appendLog("Done. $n contacts deleted.")
                            testContactCount = countTestContacts(context)
                            isRunning = false
                        }
                    },
                    enabled = !isRunning,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Clean Up")
                }
            }

            // ── Go test ───────────────────────────────────────────────────────
            FilledTonalButton(
                onClick = { navController.navigate(UIConstants.ROUTE_CONTACT_DEDUP) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Open Contact Dedup Screen →")
            }

            // ── Log output ────────────────────────────────────────────────────
            Text("Log", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF0D1117),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = log,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color(0xFF58D68D)
                    )
                )
            }
        }
    }
}
