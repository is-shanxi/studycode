package com.certpractice.catalog;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Catalog navigation endpoints shared by the user and admin applications.
 */
@RestController
@RequestMapping("/api/v1")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(final CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/catalog/tree")
    public CatalogDtos.CatalogTreeResponse tree() {
        return catalogService.tree();
    }

    @GetMapping("/tags")
    public List<CatalogDtos.TagResponse> tags() {
        return catalogService.tags();
    }
}
