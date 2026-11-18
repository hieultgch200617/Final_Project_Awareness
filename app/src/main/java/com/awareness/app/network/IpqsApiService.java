package com.awareness.app.network;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * IPQualityScore Phone Number Validation API.
 *
 *   GET https://ipqualityscore.com/api/json/phone/{API_KEY}/{PHONE_NUMBER}?strictness=1
 *
 * {PHONE_NUMBER} is digits only, including the country code (e.g. 84912345678).
 * The key travels in the URL path, so this client must NEVER log full URLs
 * (see GuardianRepository: the IPQS client has no logging interceptor).
 */
public interface IpqsApiService {

    @GET("api/json/phone/{key}/{phone}")
    Call<PhoneCheckResponse> checkPhone(
            @Path("key") String apiKey,
            @Path("phone") String phoneDigits,
            @Query("strictness") int strictness);
}
