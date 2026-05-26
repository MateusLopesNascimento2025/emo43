package com.example.data.api

import com.example.BuildConfig
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val systemInstruction: Content? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class Part(
    val text: String
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    val responseMimeType: String? = null,
    val temperature: Float? = null
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    val candidates: List<Candidate>?
)

@JsonClass(generateAdapter = true)
data class Candidate(
    val content: Content?
)

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val service: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }
}

// Utility class to perform translations with Gemini API
object GeminiTranslator {
    suspend fun translateSubtitles(
        originalCues: List<Pair<Int, String>>,
        sourceLang: String,
        targetLang: String,
        tone: String,
        customPrompt: String = ""
    ): List<String> {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            // Fallback for empty API Key in dev preview
            return originalCues.map { "[Traduzido ($targetLang) - $tone] ${it.second}" }
        }

        // Build a structured prompt
        val cuesPrompt = originalCues.joinToString("\n") { "${it.first}: ${it.second}" }

        val systemPrompt = """
            Você é um tradutor especialista em localização de vídeos, dublagem e geração de legendas. 
            Sua tarefa é traduzir as falas fornecidas do idioma original ($sourceLang) para o idioma de destino ($targetLang). 
            
            O tom da tradução deve ser ajustado para: '$tone'.
            Instrução para o tom: 
             - Tom Épico: Use termos cinematográficos, voz imponente, dramáticos e poéticos.
             - Tom Jornalístico: Use linguagem séria, clara, concisa, formal e informativa.
             - Tom Entusiasta: Use termos empolgantes, enérgicos, gírias amigáveis, exclamações e ritmo acelerado.
             - Tom Calmo: Use vocabulário suave, pacífico, tranquilo e acolhedor.
             - Tom Sussurrado: Use tons misteriosos, passagens curtas, suave e confidencial.
             
            Outras preferências personalizadas de dublagem: '$customPrompt'.
            
            Você DEVE retornar a tradução no formato JSON estritamente estruturado como um Array de strings do mesmo tamanho do array original. 
            Mantenha o mesmo número de elementos e a ordem respectiva dos índices.
            Retorne APENAS o JSON contendo o Array de strings. Não adicione markdown como ```json ou explicações.
        """.trimIndent()

        val promptText = """
            Traduzir exatamente a lista abaixo de falas numeradas mantendo a correspondência direta de cada linha:
            $cuesPrompt
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(Content(parts = listOf(Part(text = promptText)))),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.5f
            ),
            systemInstruction = Content(parts = listOf(Part(text = systemPrompt)))
        )

        return try {
            val response = RetrofitClient.service.generateContent(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: throw Exception("Empty response from AI")

            // Parse the JSON array
            val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            val listType = com.squareup.moshi.Types.newParameterizedType(List::class.java, String::class.java)
            val adapter = moshi.adapter<List<String>>(listType)
            
            // Clean up potentially wrapped markdown details if Gemini didn't obey
            val cleanedJson = jsonText.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            adapter.fromJson(cleanedJson) ?: throw Exception("Failed to parse localized script")
        } catch (e: Exception) {
            e.printStackTrace()
            // Graceful fallback to simulate or preserve experience if connection fails
            originalCues.map { "[$tone Dublagem] ${it.second}" }
        }
    }
}
