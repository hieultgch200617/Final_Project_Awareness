package com.awareness.app.network;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface SafeBrowsingApiService {

    /**
     * Checks if a URL appears in Google's threat database.
     *
     * @param apiKey  Your Google Cloud API key (from ApiConstants)
     * @param request The request body containing the URL to check
     * @return        A Call object — call .enqueue() to execute asynchronously
     */
    @POST("v4/threatMatches:find")
    Call<SafeBrowsingResponse> checkUrl(
            @Query("key") String apiKey,
            @Body SafeBrowsingRequest request
    );
}
