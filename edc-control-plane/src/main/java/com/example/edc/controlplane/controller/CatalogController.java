package com.example.edc.controlplane.controller;

import com.example.edc.common.model.CatalogEntry;
import com.example.edc.controlplane.dto.CatalogAssetCreateRequest;
import com.example.edc.controlplane.service.ControlPlaneService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/catalog")
public class CatalogController {
    private final ControlPlaneService controlPlaneService;

    public CatalogController(ControlPlaneService controlPlaneService) {
        this.controlPlaneService = controlPlaneService;
    }

    /**
     * 查询控制面目录清单。
     *
     * @return 目录条目列表，包含资产及 offer。
     */
    @GetMapping
    public List<CatalogEntry> listCatalog() {
        return controlPlaneService.listCatalog();
    }

    /**
     * 按资产 ID 查询目录条目详情。
     *
     * @param assetId 资产 ID。
     * @return 目录条目明细。
     */
    @GetMapping("/{assetId}")
    public CatalogEntry getCatalogEntry(@PathVariable("assetId") String assetId) {
        return controlPlaneService.getCatalogEntryByAssetId(assetId);
    }

    /**
     * 创建目录资产并发布 Offer。
     *
     * @param request 资产创建请求。
     * @return 新创建的目录条目。
     */
    @PostMapping("/assets")
    public CatalogEntry createCatalogAsset(@Valid @RequestBody CatalogAssetCreateRequest request) {
        return controlPlaneService.createCatalogAsset(request);
    }
}
