package com.mgmtp.gives.service;

import com.mgmtp.gives.dto.integration.PayOSConnectionRequest;
import com.mgmtp.gives.dto.integration.PayOSConnectionStatusResponse;
import com.mgmtp.gives.entity.User;

public interface PayOSIntegrationService {
    PayOSConnectionStatusResponse getStatus(User user);
    void connect(User user, PayOSConnectionRequest request);
    void disconnect(User user);
}
