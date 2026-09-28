package dev.spendtracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.spendtracker.data.SpendRepository
import dev.spendtracker.data.db.Category
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CategoriesViewModel(private val repo: SpendRepository) : ViewModel() {

    val categories: StateFlow<List<Category>> = repo.observeAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun consumeMessage() {
        _message.value = null
    }

    fun save(category: Category) {
        viewModelScope.launch { repo.saveCategory(category) }
    }

    fun delete(category: Category) {
        viewModelScope.launch {
            val deleted = repo.deleteCategory(category)
            if (!deleted) {
                repo.saveCategory(category.copy(isArchived = true))
                _message.value = "“${category.name}” is used by transactions, so it was hidden instead of deleted."
            }
        }
    }

    fun setArchived(category: Category, archived: Boolean) = save(category.copy(isArchived = archived))
}
