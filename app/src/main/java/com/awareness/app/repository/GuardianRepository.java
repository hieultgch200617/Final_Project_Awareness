package com.awareness.app.repository;

import android.util.Log;

import androidx.annotation.NonNull;

import com.awareness.app.BuildConfig;
import com.awareness.app.network.ApiConstants;
import com.awareness.app.network.IpqsApiService;
import com.awareness.app.network.PhoneCheckResponse;
import com.awareness.app.network.SafeBrowsingApiService;
import com.awareness.app.network.SafeBrowsingRequest;
import com.awareness.app.network.SafeBrowsingResponse;
import com.awareness.app.utils.Constants;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.gson.JsonParseException;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Guardian tools:
 *   - URL scanner   -> Google Safe Browsing
 *   - Phone checker -> IPQualityScore (IPQS) Phone Number Validation API
 *
 * The phone checker calls IPQS straight from the app with the key from local.properties
 * (IPQS_API_KEY). Results are cached per number while the app is running, so re-checking the
 * same number doesn't use up another lookup from your plan.
 */
public class GuardianRepository {

    private static final String TAG = "GuardianRepository";

    public interface UrlScanCallback {
        void onResult(String rating, String threatType);  // SAFE / SUSPICIOUS / MALICIOUS
        void onError(String message);
    }

    public interface PhoneCheckCallback {
        void onResult(PhoneCheckResponse response);
        void onError(String message);
    }

    /** Result of a health probe against Safe Browsing (used by Admin -> API Health). */
    public interface PingCallback {
        void onResult(boolean ok, int httpCode, long latencyMs, String error);
    }

    private static GuardianRepository instance;

    private final SafeBrowsingApiService safeBrowsingService;
    private final IpqsApiService         ipqsService;
    private final FirebaseFirestore      db;

    /** number (digits) -> last result. Only touched from the main thread (Retrofit callbacks). */
    private final Map<String, PhoneCheckResponse> phoneCache = new HashMap<>();

    private GuardianRepository() {
        safeBrowsingService = buildSafeBrowsingClient();
        ipqsService         = buildIpqsClient();
        db                  = FirebaseFirestore.getInstance();
    }

    public static synchronized GuardianRepository getInstance() {
        if (instance == null) instance = new GuardianRepository();
        return instance;
    }

    // ── URL Scanner (Google Safe Browsing) ───────────────────────

    public void scanUrl(String rawUrl, UrlScanCallback callback) {
        String url = normalizeUrl(rawUrl);
        SafeBrowsingRequest request = new SafeBrowsingRequest(url);

        safeBrowsingService.checkUrl(ApiConstants.SAFE_BROWSING_API_KEY, request)
                .enqueue(new Callback<SafeBrowsingResponse>() {

                    @Override
                    public void onResponse(@NonNull Call<SafeBrowsingResponse> call,
                                           @NonNull Response<SafeBrowsingResponse> response) {
                        if (!response.isSuccessful()) {
                            callback.onError("Scan failed (error " + response.code()
                                    + "). Please try again later.");
                            return;
                        }

                        SafeBrowsingResponse body = response.body();
                        String rating;
                        String threatType = null;

                        if (body == null || body.isSafe()) {
                            rating = Constants.RATING_SAFE;
                        } else {
                            threatType = body.getPrimaryThreatType();
                            rating = "MALWARE".equals(threatType) || "PHISHING".equals(threatType)
                                    ? Constants.RATING_MALICIOUS
                                    : Constants.RATING_SUSPICIOUS;
                        }

                        logScanToFirestore("url", url, rating);
                        callback.onResult(rating, threatType);
                    }

                    @Override
                    public void onFailure(@NonNull Call<SafeBrowsingResponse> call,
                                          @NonNull Throwable t) {
                        callback.onError("Network error. Check your connection.");
                    }
                });
    }

    /**
     * Health probe: scans a harmless URL and reports HTTP status + latency.
     * Deliberately NOT logged to Firestore, so it doesn't pollute scan analytics.
     */
    public void pingSafeBrowsing(PingCallback callback) {
        final long start = System.currentTimeMillis();
        safeBrowsingService.checkUrl(ApiConstants.SAFE_BROWSING_API_KEY,
                        new SafeBrowsingRequest("https://www.google.com/"))
                .enqueue(new Callback<SafeBrowsingResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<SafeBrowsingResponse> call,
                                           @NonNull Response<SafeBrowsingResponse> response) {
                        callback.onResult(response.isSuccessful(), response.code(),
                                System.currentTimeMillis() - start, null);
                    }

                    @Override
                    public void onFailure(@NonNull Call<SafeBrowsingResponse> call,
                                          @NonNull Throwable t) {
                        callback.onResult(false, 0, System.currentTimeMillis() - start,
                                "No response (offline or blocked network)");
                    }
                });
    }

    // ── Phone Checker (IPQualityScore) ───────────────────────────

    public void checkPhone(String rawNumber, PhoneCheckCallback callback) {
        String key = ApiConstants.IPQS_API_KEY;
        if (key == null || key.trim().isEmpty()) {
            callback.onError("The phone checker isn't set up yet (IPQS_API_KEY is missing).");
            return;
        }

        String e164   = formatToE164(rawNumber);        // "+84912345678"
        String digits = e164.substring(1);              // IPQS wants digits incl. country code

        PhoneCheckResponse cached = phoneCache.get(digits);
        if (cached != null) {                           // free: doesn't use a lookup credit
            callback.onResult(cached);
            return;
        }

        ipqsService.checkPhone(key.trim(), digits, ApiConstants.IPQS_STRICTNESS)
                .enqueue(new Callback<PhoneCheckResponse>() {

                    @Override
                    public void onResponse(@NonNull Call<PhoneCheckResponse> call,
                                           @NonNull Response<PhoneCheckResponse> response) {
                        if (!response.isSuccessful()) {
                            callback.onError(httpErrorMessage(response.code()));
                            return;
                        }
                        PhoneCheckResponse body = response.body();
                        if (body == null) {
                            callback.onError("Empty response from the phone-check service.");
                            return;
                        }
                        if (!body.isSuccess()) {
                            // IPQS answers HTTP 200 with success=false for bad keys, exhausted
                            // credits, invalid numbers, etc. The reason is in "message".
                            if (BuildConfig.DEBUG) Log.w(TAG, "IPQS error: " + body.getMessage());
                            callback.onError(friendlyIpqsError(body.getMessage()));
                            return;
                        }

                        phoneCache.put(digits, body);
                        logScanToFirestore("phone", e164, body.getAppRating());
                        callback.onResult(body);
                    }

                    @Override
                    public void onFailure(@NonNull Call<PhoneCheckResponse> call,
                                          @NonNull Throwable t) {
                        if (t instanceof JsonParseException) {
                            callback.onError("Unexpected response from the phone-check service.");
                        } else {
                            callback.onError("Network error. Check your connection.");
                        }
                    }
                });
    }

    private String httpErrorMessage(int code) {
        if (code == 401 || code == 403) return "The phone-check API key was rejected.";
        if (code == 429)                return "Too many phone checks right now. Try again in a minute.";
        return "Phone check failed (error " + code + "). Please try again later.";
    }

    /** Turns IPQS's technical "message" into something a user can act on. */
    private String friendlyIpqsError(String message) {
        String m = message == null ? "" : message.toLowerCase(Locale.US);
        if (m.contains("credit") || m.contains("quota") || m.contains("exceed") || m.contains("insufficient")) {
            return "The phone-check quota is used up for now. Please try again later.";
        }
        if (m.contains("key")) {
            return "The phone-check API key was rejected.";
        }
        if (m.contains("phone") || m.contains("number") || m.contains("invalid")) {
            return "That doesn't look like a valid phone number.";
        }
        return "The phone-check service returned an error. Please try again later.";
    }

    // ── Firestore logging (feeds the admin dashboard) ────────────

    private void logScanToFirestore(String type, String query, String rating) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;   // not signed in -> rules would reject it anyway

        // Keys must match firestore.rules (scan_logs create rule)
        Map<String, Object> log = new HashMap<>();
        log.put("type",      type);
        log.put("query",     query);
        log.put("rating",    rating);
        log.put("userId",    user.getUid());
        log.put("scannedAt", FieldValue.serverTimestamp());

        db.collection(Constants.COLLECTION_SCANS).add(log);
    }

    // ── Retrofit clients ─────────────────────────────────────────

    private SafeBrowsingApiService buildSafeBrowsingClient() {
        // Never log request bodies/URLs (they contain the API key) in release builds
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(BuildConfig.DEBUG
                ? HttpLoggingInterceptor.Level.BODY
                : HttpLoggingInterceptor.Level.NONE);

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(logging)
                .build();

        return new Retrofit.Builder()
                .baseUrl(ApiConstants.SAFE_BROWSING_BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(SafeBrowsingApiService.class);
    }

    private IpqsApiService buildIpqsClient() {
        // Deliberately NO logging interceptor: the IPQS key is part of the URL path,
        // so logging requests would print the key to Logcat.
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build();

        return new Retrofit.Builder()
                .baseUrl(ApiConstants.IPQS_BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(IpqsApiService.class);
    }

    // ── Input helpers ────────────────────────────────────────────

    private String normalizeUrl(String url) {
        url = url.trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return "https://" + url;
        }
        return url;
    }

    /** "0912345678" / "84912345678" / "+84912345678"  ->  "+84912345678" */
    private String formatToE164(String number) {
        number = number.trim().replaceAll("[\\s\\-().]", "");
        if (number.startsWith("+"))  return number;
        if (number.startsWith("84")) return "+" + number;
        if (number.startsWith("0"))  return "+84" + number.substring(1);
        return "+84" + number;   // fallback: assume Vietnam
    }
}
