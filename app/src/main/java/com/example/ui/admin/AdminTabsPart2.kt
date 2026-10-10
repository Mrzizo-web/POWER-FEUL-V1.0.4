package com.example.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.*
import com.example.domain.model.UserRole
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AdminInventoryTab(
    rawMaterials: List<RawMaterialEntity>,
    onAddStock: (materialId: String, amount: Double) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("إدارة المخزون والمواد الخام (${rawMaterials.size})", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(modifier = Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(rawMaterials) { m ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(m.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("الرصيد الحالي: ${m.currentStock} ${m.baseUnit} • حد التنبيه: ${m.minStock}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminPurchasesTab(
    purchases: List<PurchaseEntity>,
    suppliers: List<SupplierEntity>
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("المشتريات والتوريد (${purchases.size})", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(modifier = Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(purchases) { p ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("فاتورة شراء: ${p.invoiceNumber} • المورد: ${p.supplierName}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("الإجمالي: ${p.totalAmount} ريال", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
fun AdminDebtsTab(
    customers: List<CustomerEntity>
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("العملاء والديون (${customers.size})", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(modifier = Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(customers) { c ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(c.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("هاتف: ${c.phone} • دين: ${c.currentDebt} ريال • سقف: ${c.creditLimit} ريال", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminExpensesTab(
    expenses: List<ExpenseEntity>
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("سجل المصروفات (${expenses.size})", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(modifier = Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(expenses) { e ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(e.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("المبلغ: ${e.amount} ريال • المدفوع لـ: ${e.paidTo}", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun AdminShiftsTab(
    shifts: List<ShiftEntity>
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("سجل الورديات (${shifts.size})", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(modifier = Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shifts) { s ->
                val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH).format(Date(s.startTime))
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("شفت #${s.shiftNumber} - ${s.userName} (${s.status.titleAr})", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("البداية: $dateStr • افتتاحي: ${s.openingCash} ريال • نقدي: ${s.totalCashSales} ريال", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun AdminEmployeesTab(
    users: List<UserEntity>,
    onAddEmployee: (name: String, username: String, pin: String, role: UserRole, phone: String) -> Unit,
    onToggleActive: (userId: String, active: Boolean) -> Unit,
    onRemoveUser: (userId: String) -> Unit = {},
    canManageUsers: Boolean = true
) {
    var showDialog by remember { mutableStateOf(false) }
    var userPendingRemoval by remember { mutableStateOf<UserEntity?>(null) }
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf(UserRole.CASHIER) }
    var showRoleMenu by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("الموظفون والمستخدمون (${users.size})", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            if (canManageUsers) {
                Button(onClick = { showDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("إضافة موظف")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(users) { u ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(u.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("اسم المستخدم: @${u.username} • الدور: ${u.role.titleAr} • PIN: محمي ومشفّر", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = u.isActive,
                                enabled = canManageUsers && u.role != UserRole.OWNER,
                                onCheckedChange = { onToggleActive(u.id, it) }
                            )
                            if (canManageUsers && u.role != UserRole.OWNER) {
                                IconButton(onClick = { userPendingRemoval = u }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "إزالة المستخدم",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val pendingRemoval = userPendingRemoval
    if (pendingRemoval != null) {
        AlertDialog(
            onDismissRequest = { userPendingRemoval = null },
            title = { Text("تأكيد إزالة المستخدم") },
            text = { Text("هل تريد إزالة المستخدم ${pendingRemoval.name}؟ لا يمكن التراجع عن هذا الإجراء.") },
            confirmButton = {
                Button(
                    onClick = {
                        onRemoveUser(pendingRemoval.id)
                        userPendingRemoval = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("إزالة المستخدم") }
            },
            dismissButton = {
                TextButton(onClick = { userPendingRemoval = null }) { Text("إلغاء") }
            }
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("إضافة موظف جديد") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("الاسم") }, singleLine = true)
                    OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("اسم المستخدم") }, singleLine = true)
                    OutlinedTextField(value = pin, onValueChange = { pin = it }, label = { Text("رمز PIN") }, singleLine = true)
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("رقم الهاتف") }, singleLine = true)
                    Box {
                        TextButton(onClick = { showRoleMenu = true }) {
                            Text("الصلاحية: ${selectedRole.titleAr}")
                        }
                        DropdownMenu(
                            expanded = showRoleMenu,
                            onDismissRequest = { showRoleMenu = false }
                        ) {
                            UserRole.values().filter { it != UserRole.OWNER }.forEach { role ->
                                DropdownMenuItem(
                                    text = { Text(role.titleAr) },
                                    onClick = {
                                        selectedRole = role
                                        showRoleMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddEmployee(name, username, pin, selectedRole, phone)
                        showDialog = false
                    }
                ) {
                    Text("حفظ الموظف")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("إلغاء") }
            }
        )
    }
}

@Composable
fun AdminReportsTab(
    sales: List<SaleEntity>,
    expenses: List<ExpenseEntity>
) {
    val totalSales = sales.sumOf { it.netAmount }
    val totalExpenses = expenses.sumOf { it.amount }
    val netProfit = totalSales - totalExpenses

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("التقارير المالية والأرباح", fontWeight = FontWeight.Bold, fontSize = 18.sp)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("إجمالي المبيعات", fontSize = 12.sp)
                    Text("$totalSales ريال", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF2E7D32))
                }
            }
            Card(modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("إجمالي المصروفات", fontSize = 12.sp)
                    Text("$totalExpenses ريال", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.error)
                }
            }
            Card(modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("صافي الربح", fontSize = 12.sp)
                    Text("$netProfit ريال", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun AdminAuditLogTab(
    logs: List<AuditLogEntity>
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("سجل التدقيق والأمان (${logs.size})", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(modifier = Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(logs) { log ->
                val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).format(Date(log.timestamp))
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${log.userName} (${log.userRole}): ${log.action}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(dateStr, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (log.notes.isNotEmpty()) {
                            Text(log.notes, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminSettingsTab(
    users: List<UserEntity>,
    gatewayTokenValue: String,
    gatewayDeviceIdValue: String,
    gatewayPortValue: Int,
    autoStartGatewayValue: Boolean,
    onSaveSetting: (key: String, value: String) -> Unit = { _, _ -> },
    onSaveGatewaySettings: (token: String, deviceId: String, port: String, autoStart: Boolean) -> Unit = { _, _, _, _ -> }
) {
    var gymName by remember { mutableStateOf("نادي Power Home Gym") }
    var gatewayPort by remember(gatewayPortValue) { mutableStateOf(gatewayPortValue.toString()) }
    var gatewayToken by remember(gatewayTokenValue) { mutableStateOf(gatewayTokenValue) }
    var gatewayDeviceId by remember(gatewayDeviceIdValue) { mutableStateOf(gatewayDeviceIdValue) }
    var autoStartGateway by remember(autoStartGatewayValue) { mutableStateOf(autoStartGatewayValue) }
    var configError by remember { mutableStateOf<String?>(null) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("إعدادات النظام وبوابة الرسائل والأمان", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("بيانات المنشأة وبوابة نقاط البيع:", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    OutlinedTextField(
                        value = gymName,
                        onValueChange = { gymName = it },
                        label = { Text("اسم المنشأة / النادي") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = gatewayPort,
                        onValueChange = { gatewayPort = it.filter(Char::isDigit).take(5); configError = null },
                        label = { Text("منفذ بوابة Wi-Fi المحلية (Port)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = gatewayToken,
                        onValueChange = { gatewayToken = it.trim(); configError = null },
                        label = { Text("رمز Gateway (32 حرفًا على الأقل)") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = gatewayDeviceId,
                        onValueChange = { gatewayDeviceId = it.trim(); configError = null },
                        label = { Text("معرّف جهاز SMS Gateway") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Text(
                        "يجب إدخال الرمز ومعرّف الجهاز نفسيهما في تطبيق SMS Gateway. اترك الحقلين فارغين إذا لم تجهّز الربط بعد.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = "5",
                        onValueChange = {},
                        label = { Text("القفل التلقائي عند عدم النشاط (دقائق)") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = false,
                        singleLine = true
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("تشغيل بوابة الرسائل تلقائيًا بعد إعادة التشغيل", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(
                                "قد يمنع Android 15 وما بعده تشغيل خادم الشبكة تلقائيًا عند الإقلاع؛ في هذه الحالة شغّله من التطبيق.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = autoStartGateway, onCheckedChange = { autoStartGateway = it })
                    }
                    if (configError != null) {
                        Text(configError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                    Button(
                        onClick = {
                            val cleanToken = gatewayToken.trim()
                            val cleanDeviceId = gatewayDeviceId.trim()
                            val parsedPort = gatewayPort.toIntOrNull()
                            configError = when {
                                parsedPort == null || parsedPort !in 1..65535 ->
                                    "أدخل منفذًا صحيحًا بين 1 و65535."
                                cleanToken.isNotEmpty() && cleanToken.length < 32 ->
                                    "رمز Gateway يجب أن يتكون من 32 حرفًا على الأقل."
                                cleanToken.isNotEmpty() != cleanDeviceId.isNotEmpty() ->
                                    "أدخل الرمز ومعرّف الجهاز معًا، أو اترك الحقلين فارغين."
                                else -> null
                            }
                            if (configError == null) {
                                onSaveSetting("gym_name", gymName)
                                onSaveGatewaySettings(cleanToken, cleanDeviceId, gatewayPort, autoStartGateway)
                            }
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("حفظ التغييرات")
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("معلومات النظام والأمان:", fontWeight = FontWeight.Bold)
                    Text("نظام التشغيل: POWER FEUL POS", fontSize = 13.sp)
                    Text("قاعدة البيانات: SQLite / Room (محلي دون مزامنة سحابية)", fontSize = 13.sp)
                    Text("المحافظ المدعومة: جيب (JEEB) • فلوسك (FLOOSAK) • جوالي (JAWALI)", fontSize = 13.sp)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("المستخدمون المصرح لهم بالدخول (${users.size}):", fontWeight = FontWeight.Bold)
                    users.forEach { u ->
                        Text("• ${u.name} — الدور: ${u.role.titleAr} (${if (u.isActive) "نشط" else "معطل"})", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
