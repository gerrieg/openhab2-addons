package org.openhab.binding.gardena.internal.model.api;

import com.google.gson.annotations.SerializedName;

public class PostOAuth2Response {
    @SerializedName("access_token")
    public String accessToken;

    // The scope of the token (what you are allowed to do)
    public String scope;

    // The expire time in seconds for the access token
    @SerializedName("expires_in")
    public Integer expiresIn;

    @SerializedName("refresh_token")
    public String refreshToken;

    public String provider;

    @SerializedName("user_id")
    public String userId;

    @SerializedName("token_type")
    public String tokenType;
}

