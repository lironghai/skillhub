package com.iflytek.skillhub.auth.repository;

import com.iflytek.skillhub.auth.entity.IdentityBinding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

/**
 * JPA repository for links between platform users and external identity-provider subjects.
 */
@Repository
public interface IdentityBindingRepository extends JpaRepository<IdentityBinding, Long> {
    Optional<IdentityBinding> findByProviderCodeAndSubject(String providerCode, String subject);
    List<IdentityBinding> findByUserId(String userId);
    List<IdentityBinding> findByUserIdInAndProviderCode(List<String> userIds, String providerCode);
}
