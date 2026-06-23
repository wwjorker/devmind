package com.devmind.module.search.controller;

import com.devmind.common.api.Result;
import com.devmind.common.security.AuthenticatedUser;
import com.devmind.module.search.service.ChunkSearchService;
import com.devmind.module.search.vo.ChunkSearchResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/search")
public class ChunkSearchController {

    private final ChunkSearchService searchService;

    public ChunkSearchController(ChunkSearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/chunks")
    public Result<List<ChunkSearchResponse>> searchChunks(@AuthenticationPrincipal AuthenticatedUser user,
                                                          @RequestParam String keyword,
                                                          @RequestParam(required = false) Integer limit) {
        return Result.success(searchService.searchChunks(user.userId(), keyword, limit));
    }
}
