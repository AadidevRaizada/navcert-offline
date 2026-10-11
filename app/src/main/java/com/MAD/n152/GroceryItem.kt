package com.MAD.n152

data class GroceryItem(
    val id: Int,
    val title: String,
    val price: Double,
    val imageUrl: String
)

val groceryItems = listOf(
    GroceryItem(1, "Carrot", 23.23, "https://picsum.photos/seed/carrot/100"),
    GroceryItem(2, "Tomato", 35.50, "https://picsum.photos/seed/tomato/100"),
    GroceryItem(3, "Potato", 28.75, "https://picsum.photos/seed/potato/100"),
    GroceryItem(4, "Spinach", 18.00, "https://picsum.photos/seed/spinach/100"),
    GroceryItem(5, "Capsicum", 42.00, "https://picsum.photos/seed/capsicum/100")
)
