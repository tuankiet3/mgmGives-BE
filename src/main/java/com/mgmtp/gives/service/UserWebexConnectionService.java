package com.mgmtp.gives.service;

import com.mgmtp.gives.dto.webex.WebexAuthorizeUrlResponse;
import com.mgmtp.gives.dto.webex.WebexConnectionStatusResponse;
import com.mgmtp.gives.entity.User;

public interface UserWebexConnectionService {
    WebexAuthorizeUrlResponse createAuthorizeUrl(User currentUser, String returnTo);

    String handleCallback(String code, String state);

    WebexConnectionStatusResponse getStatus(User currentUser);

    void disconnect(User currentUser);

    String getValidAccessToken(User user);
}
