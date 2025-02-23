package com.example.theshoppinnglist

import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.apollographql.apollo3.ApolloClient
import com.example.theshoppinglist.ProductsQuery
import kotlin.random.Random

// --- Data classes for product details and shopping item ---
data class ProductData(
    val price: Double,
    val deal: String,
    val inStock: Boolean
)

data class ShoppingItem(
    val id: Int,
    var name: String,
    var quantity: Int,
    var purchased: Boolean = false,
    var isEditing: Boolean = false,
    val productData: ProductData? = null
)

val apolloClient: ApolloClient = ApolloClient.Builder()
    .serverUrl("http://192.168.1.18:4000/")
    .build()

// --- Updated suspend function using GraphQL via Apollo ---
// This function queries your GraphQL endpoint and searches for a product whose title
// contains the provided query string. It then generates a random discount value
// since discount isn’t available in the schema.
suspend fun fetchProductData(query: String): ProductData {
    return withContext(Dispatchers.IO) {
        try {
            val response = apolloClient.query(ProductsQuery()).execute()
            if (!response.hasErrors()) {
                val matchedProduct = response.data?.products?.find { it?.title?.contains(query, ignoreCase = true) == true }
                if (matchedProduct != null) {
                    // Use price (ensure it's non-null; if it's Float then convert to Double)
                    val price = matchedProduct.price ?: 0.0
                    // Generate a random discount percentage between 0 and 30
                    val discount = Random.nextInt(0, 31).toDouble()
                    val deal = if (discount > 0) "Save ${discount}%" else "No deal"
                    // Use rating.count as a proxy for stock availability
                    val stock = matchedProduct.rating?.count ?: 0
                    val inStock = stock > 0
                    return@withContext ProductData(
                        price = price,
                        deal = deal,
                        inStock = inStock
                    )
                } else {
                    return@withContext ProductData(price = 0.0, deal = "No match", inStock = false)
                }
            } else {
                return@withContext ProductData(price = 0.0, deal = "Error", inStock = false)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext ProductData(price = 0.0, deal = "Exception occurred", inStock = false)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListApp() {
    // App state
    var items by remember { mutableStateOf(listOf<ShoppingItem>()) }
    var itemName by remember { mutableStateOf("") }
    var itemQuantity by remember { mutableStateOf("1") }
    var showDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var nextId by remember { mutableStateOf(1) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shopping List") },
                actions = {
                    IconButton(onClick = {
                        val shareText = if (items.isEmpty()) {
                            "My shopping list is empty."
                        } else {
                            items.joinToString(separator = "\n") { item ->
                                val info = item.productData?.let { pd ->
                                    "Price: \$${pd.price.toDouble() * item.quantity}, Deal: ${pd.deal}, In Stock: ${pd.inStock}"
                                } ?: ""
                                "${item.name} (Qty: ${item.quantity}) $info"
                            }
                        }
                        val intent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, shareText)
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(intent, "Share shopping list"))
                    }) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = "Share List")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showDialog = true }) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Item")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(items, key = { _, item -> item.id }) { _, item ->
                    if (item.isEditing) {
                        ShoppingItemEditor(
                            item = item,
                            onEditComplete = { newName, newQuantity, productData ->
                                items = items.map {
                                    if (it.id == item.id) it.copy(name = newName, quantity = newQuantity, productData = productData, isEditing = false)
                                    else it
                                }
                            }
                        ) {
                            items = items.map {
                                if (it.id == item.id) it.copy(isEditing = false)
                                else it
                            }
                        }
                    } else {
                        ShoppingListItem(
                            item = item,
                            onEditClick = { items = items.map { it.copy(isEditing = it.id == item.id) } },
                            onTogglePurchased = {
                                items = items.map {
                                    if (it.id == item.id) it.copy(purchased = !it.purchased)
                                    else it
                                }
                            },
                            onDeleteClick = {
                                val removedItem = item
                                items = items.filter { it.id != removedItem.id }
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "${removedItem.name} deleted",
                                        actionLabel = "Undo",
                                        duration = androidx.compose.material3.SnackbarDuration.Short
                                    )
                                    if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                                        items = items + removedItem
                                        items = items.sortedBy { it.id }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showDialog) {
        itemQuantity = "1"
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Add Shopping Item") },
            text = {
                Column {
                    OutlinedTextField(
                        value = itemName,
                        onValueChange = { itemName = it },
                        label = { Text("Item Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    )
                    OutlinedTextField(
                        value = itemQuantity,
                        onValueChange = { itemQuantity = it },
                        label = { Text("Quantity") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    )
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = {
                            if (itemName.isNotBlank()) {
                                scope.launch {
                                    // Query GraphQL for product data using the entered itemName.
                                    val productData = fetchProductData(itemName)
                                    val newItem = ShoppingItem(
                                        id = nextId,
                                        name = itemName,
                                        quantity = itemQuantity.toIntOrNull() ?: 1,
                                        productData = productData
                                    )
                                    nextId++
                                    items = items + newItem
                                    showDialog = false
                                    itemName = ""
                                }
                            }
                        }
                    ) {
                        Text("Add")
                    }
                    Button(onClick = { showDialog = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
}

@Composable
fun ShoppingListItem(
    item: ShoppingItem,
    onEditClick: () -> Unit,
    onTogglePurchased: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xff281c17)),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { onTogglePurchased() },
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (item.purchased) TextDecoration.LineThrough else null
                )
                Text(
                    text = "Qty: ${item.quantity}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.LightGray
                )
                item.productData?.let { pd ->
                    Text(
                        text = "Price: \$${pd.price.toDouble() * item.quantity} | ${pd.deal} | In Stock: ${pd.inStock}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
            Row {
                IconButton(onClick = onEditClick) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit")
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingItemEditor(
    item: ShoppingItem,
    onEditComplete: (String, Int, ProductData?) -> Unit,
    onCancel: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var editedName by remember { mutableStateOf(item.name) }
    var editedQuantity by remember { mutableStateOf(item.quantity.toString()) }
    Surface(
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFF018786)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        tonalElevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            OutlinedTextField(
                value = editedName,
                onValueChange = { editedName = it },
                label = { Text("Item Name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
            OutlinedTextField(
                value = editedQuantity,
                onValueChange = { editedQuantity = it },
                label = { Text("Quantity") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = {

                    if (editedName.isNotBlank()) {
                        scope.launch {
                            val productData = fetchProductData(editedName)
                            onEditComplete(editedName, editedQuantity.toIntOrNull() ?: 1, productData)
                        }
                    }



                }) {
                    Text("Save")
                }
                Button(onClick = onCancel) {
                    Text("Cancel")
                }
            }
        }
    }
}