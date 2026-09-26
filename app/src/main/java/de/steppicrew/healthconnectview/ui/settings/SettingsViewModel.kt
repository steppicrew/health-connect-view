package de.steppicrew.healthconnectview.ui.settings

import android.net.Uri
import de.steppicrew.healthconnectview.dashboard.DashboardStore
import de.steppicrew.healthconnectview.settings.BackupError
import de.steppicrew.healthconnectview.settings.SettingsBackup
import de.steppicrew.healthconnectview.settings.SettingsBackupCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.steppicrew.healthconnectview.health.HealthRepository
import de.steppicrew.healthconnectview.settings.Settings
import de.steppicrew.healthconnectview.dashboard.SourceStore
import de.steppicrew.healthconnectview.health.TimeRange
import de.steppicrew.healthconnectview.registry.RecordRegistry
import de.steppicrew.healthconnectview.settings.SettingsStore
import de.steppicrew.healthconnectview.settings.ThemeChoice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/** How a backup action went, for a one-line message. */
enum class BackupEvent { Exported, Restored, NotABackup, NewerFormat, Failed }

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val store = SettingsStore(application)
    private val repository = HealthRepository(application)

    val settings: StateFlow<Settings> = store.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), Settings())

    private val _grantedCount = MutableStateFlow(0)
    val grantedCount: StateFlow<Int> = _grantedCount.asStateFlow()

    private val sourceStore = SourceStore(application)

    /** The app preferred where a tile has no per-type choice, or null for all sources. */
    val preferredSource: StateFlow<String?> = sourceStore.preferred
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), null)

    private val _writers = MutableStateFlow<List<String>>(emptyList())

    /**
     * Apps that have written any granted type recently, as the choices for the preference.
     *
     * Discovered from the data rather than from installed packages: an app that writes health
     * data is not distinguishable by manifest, and listing every installed app would be both
     * useless and a needless breadth of query. A month is enough to catch anything writing
     * regularly without making this screen slow.
     */
    val writers: StateFlow<List<String>> = _writers.asStateFlow()

    init {
        refresh()
    }

    /** Granted permissions are re-read rather than cached; they change outside this app. */
    fun refresh() {
        viewModelScope.launch {
            val granted = runCatching { repository.grantedPermissions() }.getOrDefault(emptySet())
            _grantedCount.value = granted.size
            discoverWriters(granted)
        }
    }

    fun preferSource(packageName: String?) {
        viewModelScope.launch { sourceStore.preferSource(packageName) }
    }

    /**
     * Reads a capped page of each granted type over the last month and collects the writers.
     *
     * Capped deliberately: the newest few records name the apps writing now, and reading a
     * type in full to answer "who writes this" would cost far more than the answer is worth.
     */
    private suspend fun discoverWriters(granted: Set<String>) {
        val range = TimeRange.MONTH
        // Guarded: the collectors below resume on whichever thread their read finished on,
        // so the accumulating set is touched from several of them.
        val lock = Mutex()
        val found = sortedSetOf<String>()

        // Concurrent, and capped for the same reason the dashboard caps its tiles: a granted
        // registry is upwards of thirty types, and firing that many reads at Health Connect
        // at once is worse than pacing them. Sequentially it took about twenty seconds on the
        // phone, which had the row appearing long after the screen had settled.
        val gate = Semaphore(MAX_CONCURRENT_READS)
        coroutineScope {
            RecordRegistry.all
                .filter { it.permission in granted }
                .map { spec ->
                    async {
                        val writers = gate.withPermit {
                            runCatching {
                                repository
                                    .read(spec.type, range.filter(), maxRecords = WRITER_SAMPLE)
                                    .map { spec.originOf(it) }
                            }.getOrDefault(emptyList())
                        }
                        // Published as they arrive rather than at the end, so the row appears
                        // as soon as a second writer is known instead of waiting for types
                        // that may have nothing to say.
                        lock.withLock {
                            if (found.addAll(writers)) {
                                _writers.value = found.toList()
                            }
                        }
                    }
                }
                .awaitAll()
        }
    }

    fun setTheme(theme: ThemeChoice) {
        viewModelScope.launch { store.setTheme(theme) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { store.setDynamicColor(enabled) }
    }

    /**
     * Withdraws every granted permission.
     *
     * Nothing is deleted: this app stores no health data, so revoking only removes its ability
     * to read. The wording in the UI says exactly that, because "withdraw access" invites the
     * fear that it also erases the underlying records.
     */
    fun revokeAll(onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching { repository.revokeAll() }
            refresh()
            onDone()
        }
    }

    private val dashboardStore = DashboardStore(application)

    /** A backup read from a file and waiting for the user to confirm replacing everything. */
    private val _pendingRestore = MutableStateFlow<SettingsBackup?>(null)
    val pendingRestore: StateFlow<SettingsBackup?> = _pendingRestore.asStateFlow()

    private val _backupEvents = MutableSharedFlow<BackupEvent>(extraBufferCapacity = 1)
    val backupEvents: SharedFlow<BackupEvent> = _backupEvents.asSharedFlow()

    /** Writes the dashboard, source choices and display settings to [uri]. No health data. */
    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            val resolver = getApplication<Application>().contentResolver
            val result = runCatching {
                val backup = SettingsBackup(
                    dashboard = dashboardStore.config.first(),
                    sourceSelections = sourceStore.selections.first(),
                    preferredSource = sourceStore.preferred.first(),
                    settings = store.settings.first(),
                )
                val text = SettingsBackupCodec.encode(backup, LocalDate.now())
                withContext(Dispatchers.IO) {
                    requireNotNull(resolver.openOutputStream(uri)) { "cannot open $uri" }
                        .use { it.write(text.toByteArray(Charsets.UTF_8)) }
                }
            }
            _backupEvents.tryEmit(if (result.isSuccess) BackupEvent.Exported else BackupEvent.Failed)
        }
    }

    /**
     * Reads a backup from [uri] and holds it for confirmation; nothing changes yet. Replacing a
     * dashboard someone arranged by hand is not undoable, so it is asked, not assumed.
     */
    fun readBackup(uri: Uri) {
        viewModelScope.launch {
            val resolver = getApplication<Application>().contentResolver
            val result = runCatching {
                val text = withContext(Dispatchers.IO) {
                    requireNotNull(resolver.openInputStream(uri)) { "cannot open $uri" }.use { input ->
                        // A settings backup is a few kilobytes; anything far larger is not one,
                        // and reading it whole would only waste memory before refusing it.
                        val bytes = input.readAtMost(MAX_BACKUP_BYTES + 1)
                        if (bytes.size > MAX_BACKUP_BYTES) throw BackupError.NotABackup()
                        bytes.toString(Charsets.UTF_8)
                    }
                }
                SettingsBackupCodec.decode(text)
            }
            result.fold(
                onSuccess = { _pendingRestore.value = it },
                onFailure = { error ->
                    _backupEvents.tryEmit(
                        when (error) {
                            is BackupError.NotABackup -> BackupEvent.NotABackup
                            is BackupError.NewerFormat -> BackupEvent.NewerFormat
                            else -> BackupEvent.Failed
                        },
                    )
                },
            )
        }
    }

    fun confirmRestore() {
        val backup = _pendingRestore.value ?: return
        _pendingRestore.value = null
        viewModelScope.launch {
            val result = runCatching {
                dashboardStore.save(backup.dashboard)
                sourceStore.restore(backup.sourceSelections, backup.preferredSource)
                store.restore(backup.settings)
            }
            _backupEvents.tryEmit(if (result.isSuccess) BackupEvent.Restored else BackupEvent.Failed)
        }
    }

    fun cancelRestore() {
        _pendingRestore.value = null
    }

    private companion object {
        const val STOP_TIMEOUT = 5_000L

        /** Far above any real backup (a few KB), far below anything worth reading whole. */
        const val MAX_BACKUP_BYTES = 256 * 1024

        /** Newest records per type when discovering writers; enough to name who writes now. */
        const val WRITER_SAMPLE = 50

        /** Parallel reads while discovering writers; matches the dashboard's tile cap. */
        const val MAX_CONCURRENT_READS = 4
    }
}

/** Up to [limit] bytes; InputStream.readNBytes would do this but needs API 33, minSdk is 26. */
private fun java.io.InputStream.readAtMost(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    while (out.size() < limit) {
        val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
        if (read < 0) break
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}
