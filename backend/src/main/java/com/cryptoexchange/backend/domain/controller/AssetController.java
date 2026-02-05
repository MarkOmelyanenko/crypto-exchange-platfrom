package com.cryptoexchange.backend.domain.controller;

import com.cryptoexchange.backend.domain.model.Asset;
import com.cryptoexchange.backend.domain.service.AssetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Asset management endpoints.
 */
@RestController
@RequestMapping("/api/assets")
@Tag(name = "Assets", description = "Asset management endpoints")
@Validated
public class AssetController {

    private final AssetService assetService;

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    @GetMapping
    @Operation(summary = "List all assets", description = "Returns a list of all available assets")
    public ResponseEntity<List<AssetDto>> listAssets() {
        List<Asset> assets = assetService.listAssets();
        List<AssetDto> response = assets.stream()
            .map(AssetDto::from)
            .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get asset by ID", description = "Returns an asset by its ID")
    public ResponseEntity<AssetDto> getAsset(@PathVariable UUID id) {
        Asset asset = assetService.getAsset(id);
        return ResponseEntity.ok(AssetDto.from(asset));
    }

    // DTO
    public static class AssetDto {
        public String id;
        public String symbol;
        public String name;
        public Integer scale;
        public String createdAt;

        public static AssetDto from(Asset asset) {
            AssetDto dto = new AssetDto();
            dto.id = asset.getId().toString();
            dto.symbol = asset.getSymbol();
            dto.name = asset.getName();
            dto.scale = asset.getScale();
            dto.createdAt = asset.getCreatedAt() != null ? asset.getCreatedAt().toString() : null;
            return dto;
        }
    }
}
