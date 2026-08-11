package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.label.LabelDefinitionService;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skillbundle.SkillBundleService;
import com.iflytek.skillhub.dto.SkillBundleDraftRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class SkillBundleAppServiceTest {

    private final SkillBundleAppService service = new SkillBundleAppService(
            mock(SkillBundleService.class),
            mock(LabelDefinitionService.class),
            mock(LabelLocalizationService.class)
    );

    @Test
    void createShouldReturnBadRequestForUnknownVisibilityValue() {
        SkillBundleDraftRequest request = new SkillBundleDraftRequest(
                "global",
                "Invalid Bundle",
                "invalid-bundle",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "VISIBLE_TO_EVERYONE",
                "DRAFT",
                List.of(),
                List.of()
        );

        DomainBadRequestException exception = assertThrows(DomainBadRequestException.class,
                () -> service.create(request, "user-1", Map.of()));

        assertEquals("error.skillBundle.visibility.invalid", exception.messageCode());
    }

    @Test
    void createShouldReturnBadRequestForUnknownStatusValue() {
        SkillBundleDraftRequest request = new SkillBundleDraftRequest(
                "global",
                "Invalid Bundle",
                "invalid-bundle",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "PUBLIC",
                "READY",
                List.of(),
                List.of()
        );

        DomainBadRequestException exception = assertThrows(DomainBadRequestException.class,
                () -> service.create(request, "user-1", Map.of()));

        assertEquals("error.skillBundle.status.invalid", exception.messageCode());
    }
}
