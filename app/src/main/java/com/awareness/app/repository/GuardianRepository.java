package com.awareness.app.repository;

import androidx.annotation.NonNull;

import com.awareness.app.BuildConfig;
import com.awareness.app.network.ApiConstants;
import com.awareness.app.network.SafeBrowsingApiService;
import com.awareness.app.network.SafeBrowsingRequest;
import com.awareness.app.network.SafeBrowsingResponse;
import com.awareness.app.network.TelesignResponse;
import com.awareness.app.utils.Constants;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.FirebaseFunctionsException;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.util.HashMap;
import java.util.Map;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * CHANGES vs. the original:
 *  1. Telesign is no longer called from the phone. checkPhone() calls the "checkPhone"
 *     Cloud Function, so the Telesign credentials never ship inside the APK.
 *  2. HTTP logging is only enabled in debug builds (BODY logging prints API keys and
 *     auth headers to Logcat).
 *  3. The user id is read when a scan is logged, not once when the singleton is created.
 *     Before, logging out and into another account kept writing the OLD user id (or ""
 *     if the singleton was first created before login).
 */
public class GuardianRepository {

    public interface UrlScanCallback {
        void onResult(String rating, String threatType);  // SAFE / SUSPICIOUS / MALICIOUS
        void onError(String message);
    }

    public interface PhoneCheckCallback {
        void onResult(TelesignResponse response);
        void onError(String message);
    }

    /** Result of a health probe against Safe Browsing (used by Admin -> API Health). */
    public interface PingCallback {
        void onResult(boolean ok, int httpCode, long latencyMs, String error);
    }

    public interface BackendHealthCallback {
        void onResult(Map<String, Object> data);
        void onError(String message);
    }

    private static GuardianRepository instance;

    private final SafeBrowsingApiService safeBrowsingService;
    private final FirebaseFunctions      functions;
    private final FirebaseFirestore      db;
    private final Gson                   gson = new Gson();

    private GuardianRepository() {
        safeBrowsingService = buildSafeBrowsingClient();
        functions           = FirebaseFunctions.getInstance(ApiConstants.FUNCTIONS_REGION);
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

    /** Staff-only: asks the "apiHealth" Cloud Function for backend status. */
    @SuppressWarnings("unchecked")
    public void fetchBackendHealth(BackendHealthCallback callback) {
        functions.getHttpsCallable("apiHealth")
                .call()
                .addOnSuccessListener(result -> {
                    Object data = result.getData();
                    if (data instanceof Map) {
                        callback.onResult((Map<String, Object>) data);
                    } else {
                        callback.onError("Unexpected response from the health check.");
                    }
                })
                .addOnFailureListener(e -> {
                    if (e instanceof FirebaseFunctionsException
                            && ((FirebaseFunctionsException) e).getCode()
                            == FirebaseFunctionsException.Code.NOT_FOUND) {
                        callback.onError("Cloud Function not deployed yet (firebase deploy --only functions).");
                    } else if (e instanceof FirebaseFunctionsException
                            && ((FirebaseFunctionsException) e).getCode()
                            == FirebaseFunctionsException.Code.PERMISSION_DENIED) {
                        callback.onError("Staff access required.");
                    } else {
                        callback.onError("Couldn't reach the Cloud Function.");
                    }
                });
    }

    // ── Phone Checker (via Cloud Function) ───────────────────────

    public void checkPhone(String rawNumber, PhoneCheckCallback callback) {
        String e164Number = formatToE164(rawNumber);

        Map<String, Object> data = new HashMap<>();
        data.put("phoneNumber", e164Number);

        functions.getHttpsCallable("checkPhone")
                .call(data)
                .addOnSuccessListener(result -> {
                    try {
                        // The function returns JSON in the same shape TelesignResponse expects
                        TelesignResponse body = gson.fromJson(
                                gson.toJson(result.getData()), TelesignResponse.class);
                        if (body == null) {
                            callback.onError("Empty response from the phone-check service.");
                            return;
                        }
                        logScanToFirestore("phone", e164Number, body.getAppRating());
                        callback.onResult(body);
                    } catch (JsonParseException e) {
                        callback.onError("Unexpected response from the phone-check service.");
                    }
                })
                .addOnFailureListener(e -> callback.onError(friendlyFunctionError(e)));
    }

    private String friendlyFunctionError(Exception e) {
        if (e instanceof FirebaseFunctionsException) {
            switch (((FirebaseFunctionsException) e).getCode()) {
                case UNAUTHENTICATED:    return "Please sign in again.";
                case INVALID_ARGUMENT:   return "That doesn't look like a valid phone number.";
                case RESOURCE_EXHAUSTED: return "Daily phone-check limit reached. Try again tomorrow.";
                case UNAVAILABLE:        return "The phone-check service is unavailable right now.";
                default: break;
            }
        }
        return "Network error. Check your connection.";
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

    // ── Retrofit client (Safe Browsing only) ─────────────────────

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
