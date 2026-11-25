package com.awareness.app.repository;

import android.net.Uri;
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
import com.google.firebase.firestore.SetOptions;
import com.google.gson.JsonParseException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
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
                        saveSiteListEntry(url, rating, threatType);   // file it under black / gray / white
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
                        savePhoneListEntry(e164, body);     // file it under black / gray / white
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

    // ── "My lists": black / gray / white ─────────────────────────

    /**
     * Which list a rating belongs to, or null when there is no verdict to file.
     *   MALICIOUS  -> "black"      SUSPICIOUS -> "gray"      SAFE -> "white"
     * (UNKNOWN results, such as an invalid number or no risk score, are not filed anywhere.)
     */
    public static String listNameFor(String rating) {
        if (Constants.RATING_MALICIOUS.equals(rating))  return "black";
        if (Constants.RATING_SUSPICIOUS.equals(rating)) return "gray";
        if (Constants.RATING_SAFE.equals(rating))       return "white";
        return null;
    }

    /**
     * Saves the number and its details to users/{uid}/phone_lists/{digits}. The id is the number's
     * digits, so checking the same number again UPDATES the entry (moving it to another list if its
     * rating changed) and bumps its check counter. Private to the user: see firestore.rules.
     *
     * Fire-and-forget on purpose: Firestore keeps the write on the device and sends it when the
     * connection allows, so a slow network never delays the result screen.
     */
    private void savePhoneListEntry(String e164, PhoneCheckResponse r) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String rating = r.getAppRating();
        String list = listNameFor(rating);
        if (user == null || list == null) return;

        List<String> signals = r.getWarningSigns();

        // Keys must match firestore.rules (users/{uid}/phone_lists)
        Map<String, Object> entry = new HashMap<>();
        entry.put("number",      e164);
        entry.put("list",        list);
        entry.put("rating",      rating);
        entry.put("fraudScore",  r.hasRiskScore() ? r.getRiskScore() : null);
        entry.put("carrier",     r.getCarrierName());
        entry.put("lineType",    r.getPhoneTypeDescription());
        entry.put("countryCode", r.getCountryCode());
        entry.put("voip",        r.isVoip());
        entry.put("signals",     signals);
        entry.put("checkedAt",   FieldValue.serverTimestamp());
        entry.put("checkCount",  FieldValue.increment(1));

        db.collection(Constants.COLLECTION_USERS).document(user.getUid())
                .collection("phone_lists").document(e164.substring(1))
                .set(entry, SetOptions.merge())
                .addOnFailureListener(e -> {
                    if (BuildConfig.DEBUG) Log.w(TAG, "Couldn't save phone list entry", e);
                });

        // Also add it to the server's private list (admin-only), if it isn't there yet
        saveServerListEntry(e164, rating, list, r, signals);
    }

    /**
     * Adds the number to the SERVER's private list (collection "server_phone_lists", id = digits).
     * Only admins can read it; the app can only CREATE entries, never change or read them.
     *
     * "If it isn't there yet" is enforced by the Firestore rules: they allow create but not update,
     * so this plain set() succeeds for a new number and is refused for one that is already listed.
     * That refusal is expected and ignored. The entry keeps the FIRST result seen for the number;
     * it is never modified by later checks.
     *
     * No user id is stored with it, so admins can't tell who checked a number (users' own lists stay
     * private even from admins).
     */
    private void saveServerListEntry(String e164, String rating, String list,
                                     PhoneCheckResponse r, List<String> signals) {
        // Keys must match firestore.rules (server_phone_lists)
        Map<String, Object> entry = new HashMap<>();
        entry.put("number",      e164);
        entry.put("list",        list);
        entry.put("rating",      rating);
        entry.put("fraudScore",  r.hasRiskScore() ? r.getRiskScore() : null);
        entry.put("carrier",     r.getCarrierName());
        entry.put("lineType",    r.getPhoneTypeDescription());
        entry.put("countryCode", r.getCountryCode());
        entry.put("voip",        r.isVoip());
        entry.put("signals",     signals);
        entry.put("checkedAt",   FieldValue.serverTimestamp());   // here: when it was first added
        entry.put("checkCount",  1);

        db.collection("server_phone_lists").document(e164.substring(1))
                .set(entry)
                .addOnFailureListener(e -> {
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Not added to the server list (already listed, or rules refused): "
                                + e.getMessage());
                    }
                });
    }

    // ── Websites: black / gray / white ───────────────────────────

    /**
     * The address as it is stored in the lists, or null if it has no readable host:
     *   "HTTPS://Example.com/Login/?token=abc#x"  ->  "https://example.com/Login"
     * Lower-cased scheme and host, no trailing slash, and the query string (?...) and fragment (#...)
     * are DROPPED, because links often carry private tokens (password resets, session ids) that must
     * never be written to a database. The Firestore rules refuse any stored URL that contains ? or #.
     */
    public static String listUrlFor(String rawUrl) {
        try {
            String raw = rawUrl.trim();
            if (!hasHttpScheme(raw)) raw = "https://" + raw;
            Uri uri = Uri.parse(raw);
            String host = uri.getHost();
            if (host == null || host.isEmpty()) return null;

            String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.US) : "https";
            String path = uri.getEncodedPath() != null ? uri.getEncodedPath() : "";
            while (path.endsWith("/")) path = path.substring(0, path.length() - 1);
            int port = uri.getPort();

            String url = scheme + "://" + host.toLowerCase(Locale.US) + (port > 0 ? ":" + port : "") + path;
            return url.length() <= 500 ? url : url.substring(0, 500);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * True if the text already starts with http:// or https:// in ANY letter case. The old checks were
     * case-sensitive, so "HTTPS://Example.com" became "https://HTTPS://Example.com" and the wrong
     * address was scanned.
     */
    private static boolean hasHttpScheme(String s) {
        String lower = s.toLowerCase(Locale.US);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    /** The document id for a website: SHA-256 of its stored address (a URL can't be a document id). */
    public static String listKeyForUrl(String rawUrl) {
        String url = listUrlFor(rawUrl);
        return url == null ? null : sha256Hex(url);
    }

    private static String sha256Hex(String s) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte x : digest) sb.append(String.format(Locale.US, "%02x", x));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);   // every Android device has SHA-256
        }
    }

    /**
     * Files a scanned website into the user's private list AND the server's private list, exactly like
     * phone numbers (see savePhoneListEntry). The same rating -> list rule applies.
     *   users/{uid}/site_lists/{hash}  : updated on every check (list may change, counter goes up)
     *   server_site_lists/{hash}       : created once, never updated, admin-only (see firestore.rules)
     */
    private void saveSiteListEntry(String scannedUrl, String rating, String threatType) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String list = listNameFor(rating);
        String url = listUrlFor(scannedUrl);
        if (user == null || list == null || url == null) return;

        String key = sha256Hex(url);
        String host = Uri.parse(url).getHost();

        // Keys must match firestore.rules (site_lists / server_site_lists)
        Map<String, Object> entry = new HashMap<>();
        entry.put("url",        url);
        entry.put("host",       host != null ? host : "");
        entry.put("list",       list);
        entry.put("rating",     rating);
        entry.put("threatType", threatType);               // null when nothing was found
        entry.put("checkedAt",  FieldValue.serverTimestamp());
        entry.put("checkCount", FieldValue.increment(1));

        db.collection(Constants.COLLECTION_USERS).document(user.getUid())
                .collection("site_lists").document(key)
                .set(entry, SetOptions.merge())
                .addOnFailureListener(e -> {
                    if (BuildConfig.DEBUG) Log.w(TAG, "Couldn't save site list entry", e);
                });

        // Server list: created once, so a plain set(). A second write for the same address is refused
        // by the rules (no updates), which is expected and ignored.
        Map<String, Object> serverEntry = new HashMap<>(entry);
        serverEntry.put("checkCount", 1);
        db.collection("server_site_lists").document(key)
                .set(serverEntry)
                .addOnFailureListener(e -> {
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Site not added to the server list (already listed, or rules refused): "
                                + e.getMessage());
                    }
                });
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
        if (!hasHttpScheme(url)) {
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
