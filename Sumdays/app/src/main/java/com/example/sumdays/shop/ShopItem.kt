package com.example.sumdays

interface ShopItem{
    val id: Int
    val category: String
    val name: String
    val description: String
    val price: Int
    var isOwned: Boolean
}