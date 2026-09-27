package com.vinz.appmanager

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppInfo(
    val label: String,
    val packageName: String,
    val icon: android.graphics.drawable.Drawable?,
    val isSystemApp: Boolean,
    val isFrozen: Boolean
)

class AppListViewModel(app: Application) : AndroidViewModel(app) {

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    fun loadApps() {
        viewModelScope.launch {
            _loading.value = true
            val pm = getApplication<Application>().packageManager
            val result = withContext(Dispatchers.IO) {
                pm.getInstalledApplications(PackageManager.GET_META_DATA)
                    .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                    .map {
                        AppInfo(
                            label = pm.getApplicationLabel(it).toString(),
                            packageName = it.packageName,
                            icon = pm.getApplicationIcon(it),
                            isSystemApp = (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                            isFrozen = !it.enabled
                        )
                    }
                    .sortedBy { it.label.lowercase() }
            }
            _apps.value = result
            _loading.value = false
        }
    }

    fun refreshAfterAction(packageName: String, frozen: Boolean) {
        _apps.value = _apps.value.map {
            if (it.packageName == packageName) it.copy(isFrozen = frozen) else it
        }
    }

    fun removeApp(packageName: String) {
        _apps.value = _apps.value.filterNot { it.packageName == packageName }
    }
}
