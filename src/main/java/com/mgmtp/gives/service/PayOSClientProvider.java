package com.mgmtp.gives.service;

import com.mgmtp.gives.entity.Campaign;
import vn.payos.PayOS;

public interface PayOSClientProvider {
    PayOS getClientForCampaign(Campaign campaign);
}
