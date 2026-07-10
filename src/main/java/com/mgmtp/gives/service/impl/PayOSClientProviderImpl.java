package com.mgmtp.gives.service.impl;

import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.entity.UserPayOSConnection;
import com.mgmtp.gives.common.ErrorCode;
import com.mgmtp.gives.enums.DonationMethod;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.repository.UserPayOSConnectionRepository;
import com.mgmtp.gives.service.PayOSClientProvider;
import com.mgmtp.gives.service.TokenCryptoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.payos.PayOS;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayOSClientProviderImpl implements PayOSClientProvider {

    private final UserPayOSConnectionRepository userPayOSConnectionRepository;
    private final TokenCryptoService tokenCryptoService;
    private final PayOS defaultPayOS;

    @Override
    public PayOS getClientForCampaign(Campaign campaign) {
        if (campaign.getUser() != null) {
            Optional<UserPayOSConnection> connection =
                    userPayOSConnectionRepository.findByUserId(campaign.getUser().getId());
            if (connection.isPresent()) {
                UserPayOSConnection conn = connection.get();
                log.info("Using custom PayOS credentials for campaign owner: {}", campaign.getUser().getEmail());
                String clientId = tokenCryptoService.decrypt(conn.getClientId()).trim();
                String apiKey = tokenCryptoService.decrypt(conn.getApiKey()).trim();
                String checksumKey = tokenCryptoService.decrypt(conn.getChecksumKey()).trim();
                return new PayOS(clientId, apiKey, checksumKey);
            } else if (campaign.getDonationMethod() == DonationMethod.PAYOS || campaign.getDonationMethod() == DonationMethod.HYBRID) {
                log.warn("Campaign owner {} does not have custom PayOS credentials connected", campaign.getUser().getEmail());
                throw new AppException(ErrorCode.VALIDATION_ERROR,
                        "Campaign owner does not have a connected PayOS account.");
            }
        }
        log.info("Using global system-wide PayOS credentials");
        return this.defaultPayOS;
    }
}
