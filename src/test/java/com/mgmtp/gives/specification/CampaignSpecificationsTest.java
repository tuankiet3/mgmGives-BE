package com.mgmtp.gives.specification;

import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.entity.CampaignFollower;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.UserRole;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.jpa.domain.Specification;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CampaignSpecificationsTest {

    @Test
    void isNotFollowedBy_returnsPredicateForAdminUser() {
        User admin = userWithRole(UserRole.ADMIN);
        CriteriaMocks criteria = CriteriaMocks.create();

        Specification<Campaign> specification = CampaignSpecifications.isNotFollowedBy(admin);

        Predicate predicate = specification.toPredicate(criteria.root, criteria.query, criteria.cb);

        assertSame(criteria.notExistsPredicate, predicate);
    }

    @Test
    void isNotFollowedBy_returnsPredicateForRegularUser() {
        User user = userWithRole(UserRole.USER);
        CriteriaMocks criteria = CriteriaMocks.create();

        Specification<Campaign> specification = CampaignSpecifications.isNotFollowedBy(user);

        Predicate predicate = specification.toPredicate(criteria.root, criteria.query, criteria.cb);

        assertSame(criteria.notExistsPredicate, predicate);
    }

    @Test
    void isNotFollowedBy_returnsNullWithoutCurrentUser() {
        CriteriaMocks criteria = CriteriaMocks.create();

        Specification<Campaign> specification = CampaignSpecifications.isNotFollowedBy(null);

        Predicate predicate = specification.toPredicate(criteria.root, criteria.query, criteria.cb);

        assertNull(predicate);
    }

    private static User userWithRole(UserRole role) {
        User user = new User();
        user.setId(42L);
        user.setRole(role);
        return user;
    }

    private static final class CriteriaMocks {
        private final Root<Campaign> root;
        private final CriteriaQuery<?> query;
        private final CriteriaBuilder cb;
        private final Predicate notExistsPredicate;

        private CriteriaMocks(
                Root<Campaign> root,
                CriteriaQuery<?> query,
                CriteriaBuilder cb,
                Predicate notExistsPredicate) {
            this.root = root;
            this.query = query;
            this.cb = cb;
            this.notExistsPredicate = notExistsPredicate;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private static CriteriaMocks create() {
            Root<Campaign> root = mock(Root.class);
            CriteriaQuery<?> query = mock(CriteriaQuery.class);
            CriteriaBuilder cb = mock(CriteriaBuilder.class);
            Subquery<Long> subquery = mock(Subquery.class);
            Root<CampaignFollower> subRoot = mock(Root.class);
            Path<Object> campaignPath = mock(Path.class);
            Path<Object> campaignIdPath = mock(Path.class);
            Path<Object> userPath = mock(Path.class);
            Path<Object> userIdPath = mock(Path.class);
            Path<Object> rootIdPath = mock(Path.class);
            Predicate campaignPredicate = mock(Predicate.class);
            Predicate userPredicate = mock(Predicate.class);
            Predicate combinedPredicate = mock(Predicate.class);
            Predicate existsPredicate = mock(Predicate.class);
            Predicate notExistsPredicate = mock(Predicate.class);

            lenient().when(query.subquery(Long.class)).thenReturn(subquery);
            lenient().when(subquery.from(CampaignFollower.class)).thenReturn(subRoot);
            lenient().when(subquery.select(any(Expression.class))).thenReturn(subquery);
            lenient().when(subquery.where(any(Predicate.class))).thenReturn(subquery);
            lenient().when(subRoot.get("campaign")).thenReturn((Path) campaignPath);
            lenient().when(campaignPath.get("id")).thenReturn((Path) campaignIdPath);
            lenient().when(subRoot.get("user")).thenReturn((Path) userPath);
            lenient().when(userPath.get("id")).thenReturn((Path) userIdPath);
            lenient().when(root.get("id")).thenReturn((Path) rootIdPath);
            lenient().when(cb.equal(campaignIdPath, rootIdPath)).thenReturn(campaignPredicate);
            lenient().when(cb.equal(userIdPath, 42L)).thenReturn(userPredicate);
            lenient().when(cb.and(eq(campaignPredicate), eq(userPredicate))).thenReturn(combinedPredicate);
            lenient().when(cb.exists(subquery)).thenReturn(existsPredicate);
            lenient().when(cb.not(existsPredicate)).thenReturn(notExistsPredicate);

            return new CriteriaMocks(root, query, cb, notExistsPredicate);
        }
    }
}
