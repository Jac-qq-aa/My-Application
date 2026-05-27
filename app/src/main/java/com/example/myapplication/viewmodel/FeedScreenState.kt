package com.example.myapplication.viewmodel

sealed interface FeedScreenState {
    data object Loading : FeedScreenState
    data object Content : FeedScreenState
    data class Empty(val isTagFiltered: Boolean) : FeedScreenState
    data class Error(val message: String) : FeedScreenState
}
