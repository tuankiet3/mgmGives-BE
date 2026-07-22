package com.mgmtp.gives.service;

import com.mgmtp.gives.dto.campaign_follower.CampaignFollowerFilterCriteria;
import com.mgmtp.gives.entity.CampaignFollower;
import com.mgmtp.gives.mapper.CampaignFollowerMapper;
import com.mgmtp.gives.repository.CampaignFollowerRepository;
import com.mgmtp.gives.repository.CampaignMemberRepository;
import com.mgmtp.gives.repository.CampaignRepository;
import com.mgmtp.gives.repository.DonationRepository;
import com.mgmtp.gives.service.impl.CampaignFollowerServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CampaignFollowerServiceImplTest {

    @Mock
    private CampaignFollowerRepository campaignFollowerRepo;

    @Mock
    private CampaignFollowerMapper mapper;

    @Mock
    private CampaignRepository campaignRepo;

    @Mock
    private DonationRepository donationRepository;

    @Mock
    private CampaignMemberRepository campaignMemberRepository;

    @InjectMocks
    private CampaignFollowerServiceImpl service;

    @Test
    @SuppressWarnings("unchecked")
    void getFollowedCampaigns_deduplicatesCategoryIdsBeforeFiltering() {
        CampaignFollowerFilterCriteria criteria = new CampaignFollowerFilterCriteria();
        criteria.setCategoryIds(List.of(1L, 1L, 2L));
        Pageable pageable = PageRequest.of(0, 10);
        when(campaignFollowerRepo.findAllByUserIdWithFilters(
                eq(7L),
                anyString(),
                isNull(),
                isNull(),
                anyBoolean(),
                anyList(),
                anyLong(),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.<CampaignFollower>of(), pageable, 0));

        service.getFollowedCampaigns(7L, criteria, pageable);

        ArgumentCaptor<List<Long>> categoryIdsCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Long> categoryCountCaptor = ArgumentCaptor.forClass(Long.class);
        verify(campaignFollowerRepo).findAllByUserIdWithFilters(
                eq(7L),
                eq(""),
                isNull(),
                isNull(),
                eq(true),
                categoryIdsCaptor.capture(),
                categoryCountCaptor.capture(),
                eq(pageable)
        );
        assertThat(categoryIdsCaptor.getValue()).containsExactly(1L, 2L);
        assertThat(categoryCountCaptor.getValue()).isEqualTo(2L);
    }
}
