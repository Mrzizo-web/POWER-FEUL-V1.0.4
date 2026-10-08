package com.example.ui.admin

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.*

@Composable
fun AdminProductsTab(
    categories: List<CategoryEntity>,
    products: List<ProductEntity>,
    onAddProduct: (name: String, catId: String, price: Double, cost: Double) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }
    var costText by remember { mutableStateOf("") }
    var selectedCatId by remember { mutableStateOf(categories.firstOrNull()?.id ?: "") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("إدارة المنتجات (${products.size})", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Button(onClick = { showDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("إضافة منتج")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(products) { p ->
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
                            Text(p.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("السعر: ${p.price} ريال • التكلفة: ${p.costPrice} ريال", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("إضافة منتج جديد") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("اسم المنتج") }, singleLine = true)
                    OutlinedTextField(value = priceText, onValueChange = { priceText = it }, label = { Text("سعر البيع (ريال)") }, singleLine = true)
                    OutlinedTextField(value = costText, onValueChange = { costText = it }, label = { Text("سعر التكلفة (ريال)") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pr = priceText.toDoubleOrNull() ?: 0.0
                        val cs = costText.toDoubleOrNull() ?: 0.0
                        onAddProduct(name, selectedCatId, pr, cs)
                        showDialog = false
                    }
                ) {
                    Text("حفظ")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("إلغاء") }
            }
        )
    }
}

@Composable
fun AdminRecipesTab(
    recipes: List<RecipeEntity>,
    rawMaterials: List<RawMaterialEntity>
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("الوصفات والخلطات (${recipes.size})", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(modifier = Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(recipes) { r ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(r.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("التكلفة المحسوبة: ${r.calculatedCost} ريال", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
fun AdminSalesTab(
    sales: List<SaleEntity>,
    onVoidSale: (saleId: String, reason: String) -> Unit
) {
    var voidingSaleId by remember { mutableStateOf<String?>(null) }
    var voidReason by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("سجل المبيعات والفواتير (${sales.size})", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(sales) { s ->
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
                            Text("فاتورة: ${s.invoiceNumber}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("المبلغ: ${s.netAmount} ريال • ${s.paymentMethod.titleAr} • المحاسب: ${s.userName}", fontSize = 12.sp)
                            if (s.status == "VOIDED") {
                                Text("ملغاة: ${s.voidReason}", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                            }
                        }
                        if (s.status != "VOIDED") {
                            OutlinedButton(onClick = { voidingSaleId = s.id }) {
                                Text("إلغاء الفاتورة", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    if (voidingSaleId != null) {
        AlertDialog(
            onDismissRequest = { voidingSaleId = null },
            title = { Text("إلغاء واسترجاع الفاتورة") },
            text = {
                OutlinedTextField(
                    value = voidReason,
                    onValueChange = { voidReason = it },
                    label = { Text("سبب الإلغاء") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onVoidSale(voidingSaleId!!, voidReason)
                        voidingSaleId = null
                        voidReason = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("تأكيد الإلغاء واسترجاع المخزون")
                }
            },
            dismissButton = {
                TextButton(onClick = { voidingSaleId = null }) { Text("إلغاء") }
            }
        )
    }
}
