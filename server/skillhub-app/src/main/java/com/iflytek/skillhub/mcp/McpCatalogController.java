package com.iflytek.skillhub.mcp;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.ratelimit.RateLimit;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({"/api/v1/mcp", "/api/web/mcp"})
public class McpCatalogController extends BaseApiController {
    private final McpCatalogService catalogService;

    public McpCatalogController(McpCatalogService catalogService, ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.catalogService = catalogService;
    }

    @GetMapping("/servers")
    @RateLimit(category = "search", authenticated = 60, anonymous = 20)
    public ApiResponse<McpCatalogResponse> servers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(name = "auth_type", required = false) String authType,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) List<String> tags,
            @Parameter(schema = @Schema(type = "integer", defaultValue = "0", minimum = "0"))
            @RequestParam(required = false) String page,
            @Parameter(schema = @Schema(type = "integer", defaultValue = "24", minimum = "1"))
            @RequestParam(required = false) String size) {
        return ok("response.success.read", catalogService.search(
                search,
                category,
                authType,
                provider,
                tags,
                page,
                size
        ));
    }

    @GetMapping("/internal-servers")
    @RateLimit(category = "search", authenticated = 60, anonymous = 20)
    public ApiResponse<McpInternalServerResponse> internalServers(
            @RequestParam(required = false) String search,
            @Parameter(schema = @Schema(type = "integer", defaultValue = "0", minimum = "0"))
            @RequestParam(required = false) String page,
            @Parameter(schema = @Schema(type = "integer", defaultValue = "24", minimum = "1"))
            @RequestParam(required = false) String size) {
        return ok("response.success.read", catalogService.internalServers(search, page, size));
    }
}
