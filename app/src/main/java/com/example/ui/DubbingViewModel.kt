package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.database.DubbingProject
import com.example.data.database.SubtitleCue
import com.example.data.repository.DubbingRepository
import com.example.data.api.GeminiTranslator
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface TranslationState {
    object Idle : TranslationState
    object Translating : TranslationState
    data class Success(val project: DubbingProject) : TranslationState
    data class Error(val message: String) : TranslationState
}

data class VideoPreset(
    val id: String,
    val title: String,
    val videoUrl: String,
    val sourceLanguage: String,
    val description: String,
    val defaultCues: List<Pair<Long, Long>> // Pair of (startMs, endMs) paired with default texts below
) {
    val defaultTexts = when (id) {
        "nature" -> listOf(
            "Welcome to the beautiful valley of American mountains.",
            "Here, peaceful creatures live in perfect peace and harmony.",
            "But today, an epic story of adventure is about to begin!"
        )
        "tech" -> listOf(
            "Within the machine, infinite dreams are generated every second.",
            "We are exploring the boundaries of virtual realities and AI.",
            "Can we truly distinguish what is human from what is manufactured?"
        )
        else -> listOf(
            "Bienvenidos al increíble espectáculo del fuego controlado.",
            "Las flamas bailan al ritmo de la física molecular.",
            "Observe la energía pura que se libera en cada segundo."
        )
    }
}

class DubbingViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: DubbingRepository
    val allProjects: StateFlow<List<DubbingProject>>

    // Preset options
    val presets = listOf(
        VideoPreset(
            id = "nature",
            title = "Aventuras na Natureza",
            videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
            sourceLanguage = "Inglês",
            description = "Uma jornada cinematográfica pela floresta verdejante e fauna americana.",
            defaultCues = listOf(
                Pair(1000L, 5000L),
                Pair(6000L, 11000L),
                Pair(12000L, 18000L)
            )
        ),
        VideoPreset(
            id = "tech",
            title = "Tecnologia & IA",
            videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
            sourceLanguage = "Inglês",
            description = "Uma imersão futurista sobre sonhos virtuais e robôs inteligentes.",
            defaultCues = listOf(
                Pair(1000L, 5000L),
                Pair(6000L, 11000L),
                Pair(12000L, 18000L)
            )
        ),
        VideoPreset(
            id = "fire",
            title = "Dinâmica de Elementos",
            videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
            sourceLanguage = "Espanhol",
            description = "Demonstração poética sobre flamas e física molecular química.",
            defaultCues = listOf(
                Pair(1000L, 4000L),
                Pair(5000L, 8000L),
                Pair(9000L, 14000L)
            )
        )
    )

    // Translation setup state
    private val _selectedPreset = MutableStateFlow<VideoPreset>(presets[0])
    val selectedPreset: StateFlow<VideoPreset> = _selectedPreset.asStateFlow()

    private val _targetLanguage = MutableStateFlow("Português")
    val targetLanguage: StateFlow<String> = _targetLanguage.asStateFlow()

    private val _selectedTone = MutableStateFlow("Épico")
    val selectedTone: StateFlow<String> = _selectedTone.asStateFlow()

    private val _customPrompt = MutableStateFlow("")
    val customPrompt: StateFlow<String> = _customPrompt.asStateFlow()

    private val _customVideoUrl = MutableStateFlow("")
    val customVideoUrl: StateFlow<String> = _customVideoUrl.asStateFlow()

    private val _isCustomVideo = MutableStateFlow(false)
    val isCustomVideo: StateFlow<Boolean> = _isCustomVideo.asStateFlow()

    // Active project state inside studio
    private val _activeProject = MutableStateFlow<DubbingProject?>(null)
    val activeProject: StateFlow<DubbingProject?> = _activeProject.asStateFlow()

    private val _activeCues = MutableStateFlow<List<SubtitleCue>>(emptyList())
    val activeCues: StateFlow<List<SubtitleCue>> = _activeCues.asStateFlow()

    private val _currentCue = MutableStateFlow<SubtitleCue?>(null)
    val currentCue: StateFlow<SubtitleCue?> = _currentCue.asStateFlow()

    // Studio controller states
    private val _isDubbedMode = MutableStateFlow(true)
    val isDubbedMode: StateFlow<Boolean> = _isDubbedMode.asStateFlow()

    private val _currentTimeMs = MutableStateFlow(0L)
    val currentTimeMs: StateFlow<Long> = _currentTimeMs.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _speechCueToSpeak = MutableSharedFlow<String>(extraBufferCapacity = 5)
    val speechCueToSpeak: SharedFlow<String> = _speechCueToSpeak.asSharedFlow()

    private val _translationState = MutableStateFlow<TranslationState>(TranslationState.Idle)
    val translationState: StateFlow<TranslationState> = _translationState.asStateFlow()

    private var lastSpokenCueIndex = -1

    init {
        val database = AppDatabase.getDatabase(application)
        repository = DubbingRepository(database.dubbingProjectDao())
        allProjects = repository.allProjects.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    fun selectPreset(preset: VideoPreset) {
        _selectedPreset.value = preset
        _isCustomVideo.value = false
    }

    fun setTargetLanguage(language: String) {
        _targetLanguage.value = language
    }

    fun setSelectedTone(tone: String) {
        _selectedTone.value = tone
    }

    fun setCustomPrompt(prompt: String) {
        _customPrompt.value = prompt
    }

    fun setCustomVideoUrl(url: String) {
        _customVideoUrl.value = url
        _isCustomVideo.value = url.isNotBlank()
    }

    fun toggleDubbedMode(enabled: Boolean) {
        _isDubbedMode.value = enabled
        if (enabled) {
            // Force replay of current cue if playing
            val current = _currentCue.value
            if (current != null) {
                viewModelScope.launch {
                    _speechCueToSpeak.emit(current.translatedText)
                }
            }
        }
    }

    fun updatePlaybackStatus(playing: Boolean) {
        _isPlaying.value = playing
        if (!playing) {
            lastSpokenCueIndex = -1
        }
    }

    fun updatePlaybackTime(timeMs: Long) {
        _currentTimeMs.value = timeMs
        syncActiveSubtitle(timeMs)
    }

    private fun syncActiveSubtitle(timeMs: Long) {
        val cues = _activeCues.value
        val active = cues.firstOrNull { timeMs >= it.startMs && timeMs <= it.endMs }
        
        if (active != _currentCue.value) {
            _currentCue.value = active
            if (active != null && _isDubbedMode.value && active.index != lastSpokenCueIndex) {
                lastSpokenCueIndex = active.index
                viewModelScope.launch {
                    _speechCueToSpeak.emit(active.translatedText)
                }
            }
        }
    }

    fun loadProject(id: Int) {
        viewModelScope.launch {
            val project = repository.getProjectById(id)
            if (project != null) {
                _activeProject.value = project
                val cuesList = repository.deserializeCues(project.cuesJson)
                _activeCues.value = cuesList
                _currentTimeMs.value = 0L
                _currentCue.value = null
                lastSpokenCueIndex = -1
            }
        }
    }

    fun deleteProject(project: DubbingProject) {
        viewModelScope.launch {
            repository.deleteProject(project)
            if (_activeProject.value?.id == project.id) {
                _activeProject.value = null
                _activeCues.value = emptyList()
                _currentCue.value = null
            }
        }
    }

    fun startNewTranslation() {
        _translationState.value = TranslationState.Translating
        viewModelScope.launch {
            try {
                val preset = _selectedPreset.value
                val isCustom = _isCustomVideo.value
                
                val title = if (isCustom) "Vídeo Personalizado" else preset.title
                val videoUrl = if (isCustom) _customVideoUrl.value else preset.videoUrl
                val sourceLanguage = if (isCustom) "Inglês" else preset.sourceLanguage
                val targetLang = _targetLanguage.value
                val tone = _selectedTone.value
                val usrPrompt = _customPrompt.value

                // Format inputs for Gemini API
                val originalTexts = if (isCustom) {
                    listOf(
                        "Hello everyone, welcome to this video.",
                        "Today we are showing off an exciting new technology segment.",
                        "Let me know in the comments what you think about AI development!"
                    )
                } else {
                    preset.defaultTexts
                }

                val originalSubCues = if (isCustom) {
                    listOf(
                        Pair(1000L, 5000L),
                        Pair(6000L, 11000L),
                        Pair(12000L, 18000L)
                    )
                } else {
                    preset.defaultCues
                }

                val cuesToTranslate = originalTexts.mapIndexed { idx, text -> Pair(idx, text) }

                // Call the Gemini api
                val translations = GeminiTranslator.translateSubtitles(
                    originalCues = cuesToTranslate,
                    sourceLang = sourceLanguage,
                    targetLang = targetLang,
                    tone = tone,
                    customPrompt = usrPrompt
                )

                // Re-associate translations with times
                val finalSubCues = originalSubCues.mapIndexed { idx, timePair ->
                    val transText = translations.getOrNull(idx) ?: "[$tone DUB] ${originalTexts[idx]}"
                    SubtitleCue(
                        index = idx,
                        startMs = timePair.first,
                        endMs = timePair.second,
                        originalText = originalTexts[idx],
                        translatedText = transText
                    )
                }

                val originalConcatenated = originalTexts.joinToString(" | ")
                val targetConcatenated = translations.joinToString(" | ")
                val cuesJsonStr = repository.serializeCues(finalSubCues)

                val newProject = DubbingProject(
                    title = title,
                    videoUrl = videoUrl,
                    originalText = originalConcatenated,
                    targetText = targetConcatenated,
                    sourceLanguage = sourceLanguage,
                    targetLanguage = targetLang,
                    tone = tone,
                    customPrompt = usrPrompt,
                    status = "Dublado",
                    cuesJson = cuesJsonStr
                )

                val generatedId = repository.insertProject(newProject)
                val projectWithId = newProject.copy(id = generatedId.toInt())

                _activeProject.value = projectWithId
                _activeCues.value = finalSubCues
                _currentTimeMs.value = 0L
                _currentCue.value = null
                lastSpokenCueIndex = -1

                _translationState.value = TranslationState.Success(projectWithId)
            } catch (e: Exception) {
                _translationState.value = TranslationState.Error(e.message ?: "Ocorreu um erro na IA")
            }
        }
    }

    fun resetTranslationState() {
        _translationState.value = TranslationState.Idle
    }
}
