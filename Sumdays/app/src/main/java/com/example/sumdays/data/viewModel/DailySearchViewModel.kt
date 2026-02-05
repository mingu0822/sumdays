import androidx.lifecycle.switchMap
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.LiveData
import com.example.sumdays.data.DailyEntry
import com.example.sumdays.data.repository.DailyEntryRepository

class DailySearchViewModel(
    private val repo: DailyEntryRepository
) : ViewModel() {

    private val queryLiveData = MutableLiveData("")

    val results: LiveData<List<DailyEntry>> =
        queryLiveData.switchMap { q ->
            val trimmed = q.trim()
            if (trimmed.isBlank()) {
                MutableLiveData(emptyList())
            } else {
                repo.search(trimmed)
            }
        }

    fun setQuery(q: String) {
        queryLiveData.value = q
    }
}
