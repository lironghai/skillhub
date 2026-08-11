package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.SkillBundleDetailResponse;
import com.iflytek.skillhub.dto.SkillBundleDraftRequest;
import com.iflytek.skillhub.dto.SkillBundleSummaryResponse;
import com.iflytek.skillhub.service.SkillBundleAppService;
import com.iflytek.skillhub.service.SkillBundleDownloadService;
import com.iflytek.skillhub.ratelimit.RateLimit;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/v1/skill-bundles", "/api/web/skill-bundles"})
public class SkillBundleController extends BaseApiController {

    private static final Pattern NON_NEGATIVE_INTEGER = Pattern.compile("\\d+");
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    private final SkillBundleAppService skillBundleAppService;
    private final SkillBundleDownloadService skillBundleDownloadService;

    public SkillBundleController(SkillBundleAppService skillBundleAppService,
                                 SkillBundleDownloadService skillBundleDownloadService,
                                 ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.skillBundleAppService = skillBundleAppService;
        this.skillBundleDownloadService = skillBundleDownloadService;
    }

    @GetMapping
    public ApiResponse<PageResponse<SkillBundleSummaryResponse>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String namespace,
            @RequestParam(name = "label", required = false) java.util.List<String> labels,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size,
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {
        return ok("response.success.read", skillBundleAppService.search(
                q,
                namespace,
                sort,
                parseNonNegativeInt(page, DEFAULT_PAGE),
                parsePositiveInt(size, DEFAULT_SIZE),
                labels,
                userId,
                userNsRoles
        ));
    }

    @GetMapping("/{namespace}/{slug}")
    public ApiResponse<SkillBundleDetailResponse> detail(
            @PathVariable String namespace,
            @PathVariable String slug,
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {
        return ok("response.success.read", skillBundleAppService.detail(namespace, slug, userId, userNsRoles));
    }

    @GetMapping("/{namespace}/{slug}/download")
    @RateLimit(category = "download", authenticated = 120, anonymous = 30)
    public ResponseEntity<InputStreamResource> downloadLatest(
            @PathVariable String namespace,
            @PathVariable String slug,
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {
        SkillBundleDownloadService.DownloadResult result = skillBundleDownloadService.downloadLatest(
                namespace,
                slug,
                userId,
                userNsRoles != null ? userNsRoles : Map.of()
        );
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + result.filename() + "\"")
                .contentType(MediaType.parseMediaType(result.contentType()))
                .contentLength(result.contentLength())
                .body(new InputStreamResource(result.openContent()));
    }

    @PostMapping
    public ApiResponse<SkillBundleDetailResponse> create(
            @Valid @RequestBody SkillBundleDraftRequest request,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {
        return ok("response.success.created", skillBundleAppService.create(request, userId, userNsRoles));
    }

    @PutMapping("/{namespace}/{slug}")
    public ApiResponse<SkillBundleDetailResponse> update(
            @PathVariable String namespace,
            @PathVariable String slug,
            @Valid @RequestBody SkillBundleDraftRequest request,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {
        return ok("response.success.updated", skillBundleAppService.update(namespace, slug, request, userId, userNsRoles));
    }

    private int parseNonNegativeInt(String rawValue, int defaultValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return defaultValue;
        }
        String normalized = rawValue.trim();
        if (!NON_NEGATIVE_INTEGER.matcher(normalized).matches()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private int parsePositiveInt(String rawValue, int defaultValue) {
        int parsed = parseNonNegativeInt(rawValue, defaultValue);
        return parsed > 0 ? parsed : defaultValue;
    }
}
