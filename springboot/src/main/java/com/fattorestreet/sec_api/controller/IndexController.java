package com.fattorestreet.sec_api.controller;

import com.fattorestreet.sec_api.index.IndexMemberApiService;
import com.fattorestreet.sec_api.index.IndexMemberApiService.IndexMemberRow;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public index membership APIs (same URL style as {@link PublicController} root paths).
 */
@RestController
public class IndexController {

    private final IndexMemberApiService indexMemberApiService;

    public IndexController(IndexMemberApiService indexMemberApiService) {
        this.indexMemberApiService = indexMemberApiService;
    }

    @GetMapping("/index-members")
    public List<IndexMemberRow> listIndexMembers(@RequestParam(required = false) String code) {
        if (code != null && !code.isBlank()) {
            return indexMemberApiService.listByIndexCode(code.trim());
        }
        return indexMemberApiService.listAll();
    }
}
