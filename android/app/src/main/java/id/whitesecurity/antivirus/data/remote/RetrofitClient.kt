package id.whitesecurity.antivirus.data.remote

import id.whitesecurity.antivirus.BuildConfig
import okhttp3.CertificatePinner
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private val apiKeyInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .addHeader("X-Api-Key", BuildConfig.REMOTE_API_KEY)
            .build()
        chain.proceed(request)
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BASIC
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    /**
     * Opt-in certificate pinning: only built when REMOTE_CERT_PINS is set,
     * so a missing/wrong pin can never hard-break connectivity for
     * deployments that haven't configured it yet. See server/README.md for
     * how to compute the pin(s).
     */
    private fun certificatePinnerOrNull(): CertificatePinner? {
        val pins = BuildConfig.REMOTE_CERT_PINS.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (pins.isEmpty()) return null
        val host = BuildConfig.REMOTE_BASE_URL.toHttpUrl().host
        val builder = CertificatePinner.Builder()
        pins.forEach { pin -> builder.add(host, pin) }
        return builder.build()
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(apiKeyInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .apply { certificatePinnerOrNull()?.let { certificatePinner(it) } }
        .build()

    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.REMOTE_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
