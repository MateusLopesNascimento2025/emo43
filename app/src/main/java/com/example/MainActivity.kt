package com.example

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.VideoView
import android.net.Uri
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.*
import com.example.data.database.DubbingProject
import com.example.data.database.SubtitleCue
import com.example.ui.DubbingViewModel
import com.example.ui.TranslationState
import com.example.ui.VideoPreset
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.sin

// Reusable Extension Modifier for Frosted Glass appearance on components
fun Modifier.glassCard(
    backgroundColor: Color = Color(0xCCFFFFFF), // Translucent white surface
    borderColor: Color = Color(0x2B386620),     // Tiny tint of Forest Green border
    cornerRadius: Int = 24
): Modifier = this
    .clip(RoundedCornerShape(cornerRadius.dp))
    .background(backgroundColor)
    .border(1.dp, borderColor, RoundedCornerShape(cornerRadius.dp))

class VideoViewRef(var value: VideoView? = null)

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private val viewModel: DubbingViewModel by viewModels()
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    // Configuration states for Pitch and SpeechRate
    private var ttsPitch = 1.0f
    private var ttsSpeechRate = 1.0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Init TextToSpeech safely
        try {
            tts = TextToSpeech(this, this)
        } catch (e: Exception) {
            Log.e("DublIA", "Error initializing TextToSpeech constructor: ${e.message}", e)
            ttsReady = false
        }

        // Observe the TTS signals from ViewModel and narrate
        lifecycleScope.launch {
            viewModel.speechCueToSpeak.collect { cueText ->
                if (ttsReady && tts != null) {
                    try {
                        val activeProj = viewModel.activeProject.value
                        if (activeProj != null) {
                            setTtsLanguageFor(activeProj.targetLanguage)
                        }
                        tts?.setPitch(ttsPitch)
                        tts?.setSpeechRate(ttsSpeechRate)
                        
                        tts?.speak(
                            cueText,
                            TextToSpeech.QUEUE_FLUSH,
                            null,
                            "dubbing_cue"
                        )
                        Log.d("DublIA", "TTS speaking: $cueText (lang: ${activeProj?.targetLanguage})")
                    } catch (e: Exception) {
                        Log.e("DublIA", "Error during tts.speak matching: ${e.message}", e)
                    }
                }
            }
        }

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true
            tts?.setLanguage(Locale("pt", "BR"))
            Log.d("DublIA", "TextToSpeech initialized successfully.")
        } else {
            Log.e("DublIA", "Failed to initialize TextToSpeech Engine.")
        }
    }

    private fun setTtsLanguageFor(targetLanguage: String) {
        val locale = when (targetLanguage) {
            "Português" -> Locale("pt", "BR")
            "Inglês" -> Locale.US
            "Espanhol" -> Locale("es", "ES")
            "Francês" -> Locale.FRANCE
            "Alemão" -> Locale.GERMANY
            "Japonês" -> Locale.JAPAN
            "Italiano" -> Locale.ITALY
            else -> Locale("pt", "BR")
        }
        try {
            tts?.setLanguage(locale)
        } catch (e: Exception) {
            Log.e("DublIA", "Error setting language $targetLanguage on TTS: ${e.message}", e)
        }
    }

    fun updateTtsSettings(pitch: Float, rate: Float) {
        this.ttsPitch = pitch
        this.ttsSpeechRate = rate
    }

    override fun onDestroy() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.e("DublIA", "Error shutting down TTS: ${e.message}", e)
        }
        super.onDestroy()
    }

    @Composable
    fun AppNavigation() {
        val navController = rememberNavController()

        NavHost(
            navController = navController,
            startDestination = "dashboard"
        ) {
            composable("dashboard") {
                DashboardScreen(
                    onNavigateToCreate = { navController.navigate("config") },
                    onNavigateToStudio = { projId ->
                        viewModel.loadProject(projId)
                        navController.navigate("studio")
                    }
                )
            }

            composable("config") {
                ConfigScreen(
                    onBack = { navController.popBackStack() },
                    onTranslationSuccess = {
                        navController.navigate("studio") {
                            popUpTo("config") { inclusive = true }
                        }
                    }
                )
            }

            composable("studio") {
                StudioScreen(
                    onBack = { navController.navigate("dashboard") {
                        popUpTo("dashboard") { inclusive = true }
                    } },
                    onUpdateTts = { pitch, rate -> updateTtsSettings(pitch, rate) }
                )
            }
        }
    }
}

// --- DASHBOARD SCREEN ---
@Composable
fun DashboardScreen(
    onNavigateToCreate: () -> Unit,
    onNavigateToStudio: (Int) -> Unit
) {
    val viewModel: DubbingViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val projects by viewModel.allProjects.collectAsStateWithLifecycle()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToCreate,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier
                    .navigationBarsPadding()
                    .testTag("create_project_fab")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("🎙️", fontSize = 22.sp)
                    Text("Gerar Dublagem", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFF6F8F2),
                            Color(0xFFE5EADF)
                        )
                    )
                )
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
        ) {
            // Header Styled beautifully
            Spacer(modifier = Modifier.height(26.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(LightSageGreen)
                            .border(1.dp, Color(0x33386620), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🎙️", fontSize = 20.sp)
                    }
                    Column {
                        Text(
                            text = "DublIA",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = GlassTextPrimaryLight,
                            letterSpacing = (-0.5).sp
                        )
                        Text(
                            text = "Tradução de Vídeos por Inteligência Artificial",
                            fontSize = 11.sp,
                            color = Color(0xFF43493E),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(1.dp, GlassBorderLight, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("💡", fontSize = 18.sp)
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Stat counters with clean glass designs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Total Projects UI
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .glassCard(backgroundColor = Color(0xCCF0F3E8), cornerRadius = 24)
                        .padding(18.dp)
                ) {
                    Column {
                        Text(
                            text = "PROJETOS SALVOS",
                            fontSize = 10.sp,
                            color = Color(0xFF43493E),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${projects.size}",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            color = PrimaryForestGreen
                        )
                    }
                }

                // AI Engine profile stats
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .glassCard(backgroundColor = Color(0xCCF0F3E8), cornerRadius = 24)
                        .padding(18.dp)
                ) {
                    Column {
                        Text(
                            text = "PERFIS DE VOZ",
                            fontSize = 10.sp,
                            color = Color(0xFF43493E),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Pro Active",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryForestGreen
                            )
                            Text("⚡", fontSize = 14.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Section Header
            Text(
                text = "Meus Trabalhos de Dublagem",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = GlassTextPrimaryLight
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (projects.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .glassCard(backgroundColor = Color(0x80FFFFFF), cornerRadius = 28)
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "🎬",
                            fontSize = 54.sp,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        Text(
                            text = "Comece Agora!",
                            fontWeight = FontWeight.Bold,
                            color = GlassTextPrimaryLight,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Toque no botão 'Gerar Dublagem' abaixo para traduzir e localizer seu primeiro clipe de vídeo usando a IA do Gemini.",
                            color = Color(0xFF43493E),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 110.dp)
                ) {
                    items(projects) { project ->
                        ProjectItem(
                            project = project,
                            onPlay = { onNavigateToStudio(project.id) },
                            onDelete = { viewModel.deleteProject(project) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProjectItem(
    project: DubbingProject,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    // Beautiful glass list item card with soft reflection border
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(backgroundColor = Color(0xB3FFFFFF), borderColor = Color(0x2B386620), cornerRadius = 20)
            .clickable(onClick = onPlay)
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Rounded Icon representing tone
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(LightSageGreen)
                    .border(1.dp, Color(0x33386620), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (project.tone) {
                        "Épico" -> "🦸"
                        "Jornalístico" -> "📰"
                        "Entusiasta" -> "⚡"
                        "Calmo" -> "🧘"
                        else -> "🤫"
                    },
                    fontSize = 24.sp
                )
            }

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = project.title,
                    fontWeight = FontWeight.Bold,
                    color = GlassTextPrimaryLight,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "${project.sourceLanguage} ➜ ${project.targetLanguage}",
                        fontSize = 11.sp,
                        color = Color(0xFF43493E),
                        fontWeight = FontWeight.Medium
                    )
                    Text("•", fontSize = 11.sp, color = Color(0xFFC2C8BC))
                    Text(
                        text = "Tom ${project.tone}",
                        fontSize = 11.sp,
                        color = PrimaryForestGreen,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = onPlay,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = LightSageGreen
                    ),
                    modifier = Modifier.size(40.dp)
                ) {
                    Text("▶️", fontSize = 14.sp)
                }

                IconButton(
                    onClick = onDelete,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color(0xFFFFECEF)
                    ),
                    modifier = Modifier.size(40.dp)
                ) {
                    Text("🗑️", fontSize = 14.sp)
                }
            }
        }
    }
}

// --- CONFIG SETUP WIZARD SCREEN ---
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConfigScreen(
    onBack: () -> Unit,
    onTranslationSuccess: () -> Unit
) {
    val viewModel: DubbingViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val presets = viewModel.presets

    val selectedPreset by viewModel.selectedPreset.collectAsStateWithLifecycle()
    val targetLanguage by viewModel.targetLanguage.collectAsStateWithLifecycle()
    val selectedTone by viewModel.selectedTone.collectAsStateWithLifecycle()
    val customPrompt by viewModel.customPrompt.collectAsStateWithLifecycle()
    val customVideoUrl by viewModel.customVideoUrl.collectAsStateWithLifecycle()
    val isCustomVideo by viewModel.isCustomVideo.collectAsStateWithLifecycle()
    val translationState by viewModel.translationState.collectAsStateWithLifecycle()

    // Languages available
    val languages = listOf("Português", "Inglês", "Espanhol", "Francês", "Alemão", "Japonês", "Italiano")
    val tones = listOf(
        Pair("Épico", "🦸"),
        Pair("Jornalístico", "📰"),
        Pair("Entusiasta", "⚡"),
        Pair("Calmo", "🧘"),
        Pair("Sussurrado", "🤫")
    )

    LaunchedEffect(translationState) {
        if (translationState is TranslationState.Success) {
            viewModel.resetTranslationState()
            onTranslationSuccess()
        }
    }

    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFF6F8F2),
                            Color(0xFFE5EADF)
                        )
                    )
                )
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // Header back
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.White
                        ),
                        modifier = Modifier
                            .size(40.dp)
                            .border(1.dp, GlassBorderLight, CircleShape)
                    ) {
                        Text("←", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PrimaryForestGreen)
                    }
                    Text(
                        text = "Configurar Dublagem IA",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = GlassTextPrimaryLight,
                        modifier = Modifier.padding(start = 14.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Phase 1: Choose video from catalog
                Text("1. Selecione o Clipe de Vídeo", fontWeight = FontWeight.Bold, color = GlassTextPrimaryLight, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(12.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(presets) { preset ->
                        val isSelected = selectedPreset.id == preset.id && !isCustomVideo
                        Box(
                            modifier = Modifier
                                .width(240.dp)
                                .glassCard(
                                    backgroundColor = if (isSelected) LightSageGreen else Color(0x99FFFFFF),
                                    borderColor = if (isSelected) PrimaryForestGreen else Color(0x2B386620),
                                    cornerRadius = 24
                                )
                                .clickable { viewModel.selectPreset(preset) }
                                .padding(16.dp)
                        ) {
                            Column {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(90.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0xFF131510)), // Keep screen of video dark like real cinema
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = when (preset.id) {
                                                "nature" -> "🌲 NATUREZA"
                                                "tech" -> "💻 FUTURO"
                                                else -> "🔥 FÍSICA"
                                            },
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Visualizar", color = Color(0xAAFFFFFF), fontSize = 10.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = preset.title,
                                    fontWeight = FontWeight.Bold,
                                    color = GlassTextPrimaryLight,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = preset.description,
                                    color = Color(0xFF43493E),
                                    fontSize = 11.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                                Divider(color = Color(0x22386620), thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text("Origem:", fontSize = 10.sp, color = Color(0xFF43493E))
                                    Text(preset.sourceLanguage, color = PrimaryForestGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Custom Video URL Field
                Text(
                    text = "Ou insira um link MP4 personalizado:",
                    color = Color(0xFF43493E),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = customVideoUrl,
                    onValueChange = { viewModel.setCustomVideoUrl(it) },
                    placeholder = { Text("https://exemplo.com/meu-video.mp4", color = Color(0xFF8C9384)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_video_input"),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = GlassTextPrimaryLight,
                        unfocusedTextColor = GlassTextPrimaryLight,
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color(0x66FFFFFF),
                        focusedBorderColor = PrimaryForestGreen,
                        unfocusedBorderColor = Color(0xFFC2C8BC)
                    )
                )

                Spacer(modifier = Modifier.height(26.dp))

                // Phase 2: Dub destination language
                Text("2. Escolha o Idioma de Destino", fontWeight = FontWeight.Bold, color = GlassTextPrimaryLight, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    languages.forEach { lang ->
                        val isMatched = targetLanguage == lang
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isMatched) PrimaryForestGreen else Color(0x99FFFFFF))
                                .border(
                                    1.dp,
                                    if (isMatched) PrimaryForestGreen else Color(0xFFC2C8BC),
                                    RoundedCornerShape(20.dp)
                                )
                                .clickable { viewModel.setTargetLanguage(lang) }
                                .padding(horizontal = 18.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = lang,
                                color = if (isMatched) Color.White else Color(0xFF43493E),
                                fontSize = 13.sp,
                                fontWeight = if (isMatched) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(26.dp))

                // Phase 3: Choose AI Tone Accents
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("3. Tom Narrado & Emoção IA", fontWeight = FontWeight.Bold, color = GlassTextPrimaryLight, fontSize = 16.sp)
                    Text("Pro Active", color = PrimaryForestGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    tones.forEach { (tone, emoji) ->
                        val isMatched = selectedTone == tone
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isMatched) LightSageGreen else Color(0x99FFFFFF))
                                .border(
                                    1.dp,
                                    if (isMatched) PrimaryForestGreen else Color(0xFFC2C8BC),
                                    RoundedCornerShape(16.dp)
                                )
                                .clickable { viewModel.setSelectedTone(tone) }
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(emoji, fontSize = 16.sp)
                                Text(
                                    text = tone,
                                    color = if (isMatched) PrimaryForestGreen else Color(0xFF43493E),
                                    fontSize = 13.sp,
                                    fontWeight = if (isMatched) FontWeight.Bold else FontWeight.Medium
                                )
                                if (isMatched) {
                                    Text("✓", color = PrimaryForestGreen, fontSize = 11.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(26.dp))

                // Custom constraints instructions
                Text("4. Instruções de Personalização IA", fontWeight = FontWeight.Bold, color = GlassTextPrimaryLight, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = customPrompt,
                    onValueChange = { viewModel.setCustomPrompt(it) },
                    placeholder = { Text("Ex: Fale de forma muito animada, seja poético, mude gírias locais...", color = Color(0xFF8C9384)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_prompt_input"),
                    shape = RoundedCornerShape(16.dp),
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = GlassTextPrimaryLight,
                        unfocusedTextColor = GlassTextPrimaryLight,
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color(0x66FFFFFF),
                        focusedBorderColor = PrimaryForestGreen,
                        unfocusedBorderColor = Color(0xFFC2C8BC)
                    )
                )

                Spacer(modifier = Modifier.height(36.dp))

                // Action Call to translation
                Button(
                    onClick = { viewModel.startNewTranslation() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("generate_dub_button")
                        .shadow(6.dp, RoundedCornerShape(28.dp)),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryForestGreen
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("🪄", fontSize = 18.sp)
                        Text(
                            "Gerar Dublagem",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
                Spacer(modifier = Modifier.height(60.dp))
            }

            // Spinner Overlay with lovely Frosted glass blur effect
            if (translationState is TranslationState.Translating) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xE6F6F8F2)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .padding(24.dp)
                            .glassCard(backgroundColor = Color.White, cornerRadius = 28)
                            .padding(28.dp)
                    ) {
                        CircularProgressIndicator(
                            color = PrimaryForestGreen,
                            strokeWidth = 4.dp
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "Análise e Tradução Ativa",
                            fontWeight = FontWeight.Bold,
                            color = GlassTextPrimaryLight,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "A IA do Gemini está sincronizando o roteiro no tom '$selectedTone'...",
                            color = Color(0xFF43493E),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else if (translationState is TranslationState.Error) {
                AlertDialog(
                    onDismissRequest = { viewModel.resetTranslationState() },
                    title = { Text("Erro na Conexão", color = GlassTextPrimaryLight, fontWeight = FontWeight.Bold) },
                    text = { Text((translationState as TranslationState.Error).message, color = Color(0xFF43493E)) },
                    containerColor = Color.White,
                    confirmButton = {
                        TextButton(onClick = { viewModel.resetTranslationState() }) {
                            Text("Fechar", color = PrimaryForestGreen, fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }
        }
    }
}

// --- STUDIO PLAYBACK SCREEN ---
@Composable
fun StudioScreen(
    onBack: () -> Unit,
    onUpdateTts: (Float, Float) -> Unit
) {
    val viewModel: DubbingViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val activeProject by viewModel.activeProject.collectAsStateWithLifecycle()
    val activeCues by viewModel.activeCues.collectAsStateWithLifecycle()
    val currentCue by viewModel.currentCue.collectAsStateWithLifecycle()

    val currentTimeMs by viewModel.currentTimeMs.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val isDubbedMode by viewModel.isDubbedMode.collectAsStateWithLifecycle()

    var speedSetting by remember { mutableStateOf(1.0f) }
    var pitchSetting by remember { mutableStateOf(1.0f) }

    // Waveform phase animation state
    val waveTransition = rememberInfiniteTransition(label = "waveform")
    val wavePhase by waveTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    LaunchedEffect(speedSetting, pitchSetting) {
        onUpdateTts(pitchSetting, speedSetting)
    }

    if (activeProject == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(GlassBackdropLight),
            contentAlignment = Alignment.Center
        ) {
            Text("Nenhum projeto carregado.", color = GlassTextPrimaryLight)
        }
        return
    }

    val project = activeProject!!

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFF6F8F2),
                            Color(0xFFE5EADF)
                        )
                    )
                )
                .padding(innerPadding)
        ) {
            // Header with clean Back controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBack,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.White
                        ),
                        modifier = Modifier
                            .size(40.dp)
                            .border(1.dp, GlassBorderLight, CircleShape)
                    ) {
                        Text("←", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PrimaryForestGreen)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = project.title,
                            color = GlassTextPrimaryLight,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Idiomas: ${project.sourceLanguage} ➜ ${project.targetLanguage}",
                                color = Color(0xFF43493E),
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(LightSageGreen)
                        .border(1.dp, Color(0x33386620), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Tom", fontSize = 11.sp, color = Color(0xFF43493E))
                        Text(project.tone, color = PrimaryForestGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Video Container with a premium curved cinema aspect ratio
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .aspectRatio(16 / 9f)
                    .background(Color.Black)
            ) {
                val videoViewRef = remember { VideoViewRef() }

                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            try {
                                setVideoURI(Uri.parse(project.videoUrl))
                            } catch (e: Exception) {
                                Log.e("DublIA", "Error setting video URI: ${e.message}", e)
                            }
                            setOnPreparedListener { mp ->
                                try {
                                    mp.isLooping = true
                                    if (isDubbedMode) {
                                        mp.setVolume(0f, 0f)
                                    } else {
                                        mp.setVolume(1f, 1f)
                                    }
                                } catch (e: Exception) {
                                    Log.e("DublIA", "Error in prepared listener setup: ${e.message}", e)
                                }
                            }
                            setOnCompletionListener {
                                try {
                                    viewModel.updatePlaybackStatus(false)
                                    viewModel.updatePlaybackTime(0L)
                                    seekTo(0)
                                } catch (e: Exception) {
                                    Log.e("DublIA", "Error on completing playback safety seeking: ${e.message}", e)
                                }
                            }
                            setOnErrorListener { mp, what, extra ->
                                Log.e("DublIA", "VideoView Error intercepted: what=$what, extra=$extra")
                                true // Handles the error internally, preventing the standard dialog pop & crash
                            }
                            videoViewRef.value = this
                        }
                    },
                    update = { view ->
                        try {
                            if (isPlaying) {
                                if (!view.isPlaying) {
                                    view.start()
                                }
                            } else {
                                if (view.isPlaying) {
                                    view.pause()
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("DublIA", "Error with view state toggle start/pause: ${e.message}", e)
                        }

                        try {
                            view.setOnPreparedListener { mp ->
                                try {
                                    mp.isLooping = true
                                    if (isDubbedMode) {
                                        mp.setVolume(0f, 0f)
                                    } else {
                                        mp.setVolume(1f, 1f)
                                    }
                                } catch (ex: Exception) {
                                    Log.e("DublIA", "Error in updated prepared listener setVolume: ${ex.message}", ex)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("DublIA", "Error setting updated prepared listener: ${e.message}", e)
                        }
                        videoViewRef.value = view
                    }
                )

                // Timeline cursor poll coroutine
                LaunchedEffect(isPlaying) {
                    if (isPlaying) {
                        while (true) {
                            try {
                                videoViewRef.value?.let { view ->
                                    if (view.isPlaying) {
                                        val pos = view.currentPosition.toLong()
                                        viewModel.updatePlaybackTime(pos)
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("DublIA", "Error querying current position or playback state: ${e.message}")
                            }
                            delay(80)
                        }
                    }
                }

                // Frosted Glass control bar overlay on bottom of video! (Matches requested HTML mockup)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(12.dp)
                        .glassCard(backgroundColor = Color(0x33FFFFFF), borderColor = Color(0x33FFFFFF), cornerRadius = 16)
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "PROCESSANDO IA",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xCCFFFFFF),
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = if (isPlaying) "Sincronização ativada..." else "Vídeo pausado",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Volume speaker dynamic icon
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x22FFFFFF))
                                    .clickable { viewModel.toggleDubbedMode(!isDubbedMode) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(if (isDubbedMode) "🎙️" else "🔊", fontSize = 14.sp)
                            }

                            // Trigger Play/Pause
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .clickable { viewModel.updatePlaybackStatus(!isPlaying) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(if (isPlaying) "⏸️" else "▶️", fontSize = 14.sp)
                            }
                        }
                    }
                }

                // Play big indicator if completely paused
                if (!isPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0x33000000))
                            .clickable { viewModel.updatePlaybackStatus(true) },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color(0xE6FFFFFF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("▶️", fontSize = 22.sp, modifier = Modifier.padding(start = 2.dp))
                        }
                    }
                }
            }

            // Sync Timeline status bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Play Action Trigger
                Button(
                    onClick = { viewModel.updatePlaybackStatus(!isPlaying) },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPlaying) Color(0xFFC2C8BC) else PrimaryForestGreen
                    ),
                    modifier = Modifier.height(40.dp)
                ) {
                    Text(
                        text = if (isPlaying) "⏸ PAUSAR" else "▶ ASSISTIR",
                        fontWeight = FontWeight.Bold,
                        color = if (isPlaying) Color(0xFF43493E) else Color.White,
                        fontSize = 12.sp
                    )
                }

                // Clock timer counter
                val secondsText = String.format("%02d:%02d", currentTimeMs / 1000 / 60, (currentTimeMs / 1000) % 60)
                Box(
                    modifier = Modifier
                        .glassCard(backgroundColor = Color(0xCCF0F3E8), cornerRadius = 12)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Tempo: $secondsText",
                        color = Color(0xFF43493E),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            // Choose Dub Active Mode selectors
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .glassCard(
                            backgroundColor = if (!isDubbedMode) LightSageGreen else Color(0x66FFFFFF),
                            borderColor = if (!isDubbedMode) PrimaryForestGreen else Color(0x1B386620),
                            cornerRadius = 16
                        )
                        .clickable { viewModel.toggleDubbedMode(false) }
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "🔈 Áudio Original",
                            fontWeight = FontWeight.Bold,
                            color = if (!isDubbedMode) PrimaryForestGreen else Color(0xFF43493E),
                            fontSize = 13.sp
                        )
                        Text("Som do clipe original", color = Color(0xFF8C9384), fontSize = 10.sp)
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .glassCard(
                            backgroundColor = if (isDubbedMode) LightSageGreen else Color(0x66FFFFFF),
                            borderColor = if (isDubbedMode) PrimaryForestGreen else Color(0x1B386620),
                            cornerRadius = 16
                        )
                        .clickable { viewModel.toggleDubbedMode(true) }
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "🎙️ Dublagem IA",
                            fontWeight = FontWeight.Bold,
                            color = if (isDubbedMode) PrimaryForestGreen else Color(0xFF43493E),
                            fontSize = 13.sp
                        )
                        Text("Nova voz sobreposta", color = Color(0xFF8C9384), fontSize = 10.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Animated Visual Audio Waves
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .padding(horizontal = 20.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val wavePath = Path()
                    val width = size.width
                    val height = size.height
                    val centerY = height / 2f
                    val amplitude = if (isPlaying && isDubbedMode) height * 0.45f else if (isPlaying) height * 0.2f else height * 0.05f

                    wavePath.moveTo(0f, centerY)
                    for (x in 0..width.toInt() step 5) {
                        val relativeX = x.toFloat() / width
                        val sineValue = sin(relativeX * 4 * Math.PI + wavePhase).toFloat()
                        val y = centerY + sineValue * amplitude
                        wavePath.lineTo(x.toFloat(), y)
                    }

                    drawPath(
                        path = wavePath,
                        color = PrimaryForestGreen,
                        style = Stroke(width = 3.dp.toPx())
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Subtitle script tracker with highlighted synchronization
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .glassCard(backgroundColor = Color(0xB3FFFFFF), cornerRadius = 24)
                    .padding(16.dp)
            ) {
                Column {
                    Text(
                        text = "ROTEIRO LOCALIZADO E SINCRONIZADO",
                        color = Color(0xFF43493E),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(activeCues) { cue ->
                            val isActive = currentCue?.index == cue.index
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isActive) LightSageGreen else Color.Transparent)
                                    .border(
                                        1.dp,
                                        if (isActive) PrimaryForestGreen else Color.Transparent,
                                        RoundedCornerShape(14.dp)
                                    )
                                    .padding(12.dp)
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "FALA DUBLADA ${cue.index + 1}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isActive) PrimaryForestGreen else Color(0xFF43493E)
                                        )

                                        val startSec = cue.startMs / 1000
                                        val endSec = cue.endMs / 1000
                                        Text(
                                            text = "${startSec}s - ${endSec}s",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF43493E)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = cue.originalText,
                                        color = Color(0xFF43493E),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = cue.translatedText,
                                        color = if (isActive) PrimaryForestGreen else GlassTextPrimaryLight,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Sound properties sliders
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding(),
                colors = CardDefaults.cardColors(containerColor = Color(0xCCFFFFFF)),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color(0x1B386620))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Personalizar Sintetizador de Voz",
                        color = GlassTextPrimaryLight,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Tom (Pitch)", color = Color(0xFF43493E), fontSize = 11.sp, modifier = Modifier.width(74.dp))
                        Slider(
                            value = pitchSetting,
                            onValueChange = { pitchSetting = it },
                            valueRange = 0.5f..1.8f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = PrimaryForestGreen,
                                activeTrackColor = PrimaryForestGreen,
                                inactiveTrackColor = Color(0xFFC2C8BC)
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(String.format("%.1fx", pitchSetting), color = GlassTextPrimaryLight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Velocidade", color = Color(0xFF43493E), fontSize = 11.sp, modifier = Modifier.width(74.dp))
                        Slider(
                            value = speedSetting,
                            onValueChange = { speedSetting = it },
                            valueRange = 0.5f..1.8f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = PrimaryForestGreen,
                                activeTrackColor = PrimaryForestGreen,
                                inactiveTrackColor = Color(0xFFC2C8BC)
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(String.format("%.1fx", speedSetting), color = GlassTextPrimaryLight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}
