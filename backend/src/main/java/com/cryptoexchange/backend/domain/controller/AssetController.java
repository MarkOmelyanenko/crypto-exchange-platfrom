package com.cryptoexchange.backend.domain.controller;

import com.cryptoexchange.backend.domain.model.Asset;
import com.cryptoexchange.backend.domain.model.Balance;
import com.cryptoexchange.backend.domain.repository.BalanceRepository;
import com.cryptoexchange.backend.domain.service.AssetService;
import com.cryptoexchange.backend.domain.service.WhiteBitService;
import com.cryptoexchange.backend.domain.service.WhiteBitService.WhiteBitTicker24h;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Asset management endpoints.
 * Combines asset metadata from DB with live prices from WhiteBit.
 */
@RestController
@RequestMapping("/api/assets")
@Tag(name = "Assets", description = "Asset management endpoints")
@Validated
public class AssetController {

    private static final Logger log = LoggerFactory.getLogger(AssetController.class);

    private final AssetService assetService;
    private final WhiteBitService whiteBitService;
    private final BalanceRepository balanceRepository;

    public AssetController(AssetService assetService,
                           WhiteBitService whiteBitService,
                           BalanceRepository balanceRepository) {
        this.assetService = assetService;
        this.whiteBitService = whiteBitService;
        this.balanceRepository = balanceRepository;
    }

    @GetMapping
    @Operation(summary = "List assets with prices",
               description = "Returns paginated list of assets with live WhiteBit prices. " +
                             "Supports search, sorting, and pagination.")
    public ResponseEntity<PagedResponse<AssetListDto>> listAssets(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "symbol") String sort,
            @RequestParam(defaultValue = "asc") String dir,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        // 1. Get assets from DB (with optional search)
        List<Asset> allAssets;
        if (q != null && !q.isBlank()) {
            allAssets = assetService.searchAssets(q.trim());
        } else {
            allAssets = assetService.listAssets();
        }

        // 2. Fetch WhiteBit 24h tickers in batch for all tradeable assets
        List<String> whiteBitSymbols = allAssets.stream()
                .filter(a -> !"USDT".equalsIgnoreCase(a.getSymbol()))
                .map(a -> whiteBitService.toWhiteBitSymbol(a.getSymbol()))
                .collect(Collectors.toList());

        Map<String, WhiteBitTicker24h> tickers = Collections.emptyMap();
        try {
            tickers = whiteBitService.getBatchTicker24h(whiteBitSymbols);
        } catch (Exception e) {
            log.error("Failed to fetch batch tickers from WhiteBit", e);
        }

        // 3. Build DTOs with price data
        final Map<String, WhiteBitTicker24h> tickersFinal = tickers;
        List<AssetListDto> dtos = allAssets.stream()
                .map(asset -> buildAssetListDto(asset, tickersFinal))
                .collect(Collectors.toList());

        // 4. Sort
        Comparator<AssetListDto> comparator = getComparator(sort);
        if ("desc".equalsIgnoreCase(dir)) {
            comparator = comparator.reversed();
        }
        dtos.sort(comparator);

        // 5. Paginate
        int total = dtos.size();
        int fromIndex = Math.min(page * size, total);
        int toIndex = Math.min(fromIndex + size, total);
        List<AssetListDto> pageItems = dtos.subList(fromIndex, toIndex);
        int totalPages = (int) Math.ceil((double) total / size);

        return ResponseEntity.ok(new PagedResponse<>(pageItems, total, page, size, totalPages));
    }

    @GetMapping("/{symbol}")
    @Operation(summary = "Get asset details by symbol",
               description = "Returns asset details with live price data. Symbol is case-insensitive.")
    public ResponseEntity<AssetDetailDto> getAssetBySymbol(@PathVariable String symbol) {
        Asset asset = assetService.findBySymbolIgnoreCase(symbol);

        String whiteBitSymbol = whiteBitService.toWhiteBitSymbol(asset.getSymbol());
        WhiteBitTicker24h ticker = null;
        boolean priceUnavailable = false;

        try {
            ticker = whiteBitService.getTicker24h(whiteBitSymbol);
        } catch (Exception e) {
            log.warn("Failed to fetch ticker for {}: {}", symbol, e.getMessage());
        }

        BigDecimal priceUsd = null;
        BigDecimal change24hPercent = null;
        BigDecimal highPrice24h = null;
        BigDecimal lowPrice24h = null;
        BigDecimal volume24h = null;

        if ("USDT".equalsIgnoreCase(asset.getSymbol())) {
            priceUsd = BigDecimal.ONE;
            change24hPercent = BigDecimal.ZERO;
        } else if (ticker != null) {
            priceUsd = parseBigDecimal(ticker.lastPrice);
            change24hPercent = parseBigDecimal(ticker.priceChangePercent);
            highPrice24h = parseBigDecimal(ticker.highPrice);
            lowPrice24h = parseBigDecimal(ticker.lowPrice);
            volume24h = parseBigDecimal(ticker.volume);
        } else {
            priceUnavailable = true;
        }

        AssetDetailDto dto = new AssetDetailDto();
        dto.id = asset.getId().toString();
        dto.symbol = asset.getSymbol();
        dto.name = asset.getName();
        dto.scale = asset.getScale();
        dto.priceUsd = priceUsd;
        dto.change24hPercent = change24hPercent;
        dto.highPrice24h = highPrice24h;
        dto.lowPrice24h = lowPrice24h;
        dto.volume24h = volume24h;
        dto.priceUnavailable = priceUnavailable;
        dto.updatedAt = OffsetDateTime.now();

        return ResponseEntity.ok(dto);
    }

    @GetMapping("/{symbol}/my-position")
    @Operation(summary = "Get user position for an asset",
               description = "Returns the authenticated user's position for the specified asset.")
    public ResponseEntity<PositionDto> getMyPosition(
            @PathVariable String symbol,
            Authentication authentication) {

        if (authentication == null || authentication.getPrincipal() == null) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).build();
        }

        UUID userId = UUID.fromString(authentication.getPrincipal().toString());
        Asset asset = assetService.findBySymbolIgnoreCase(symbol);

        // Find user balance for this asset
        Optional<Balance> balanceOpt = balanceRepository.findByUserIdAndAssetId(userId, asset.getId());

        PositionDto dto = new PositionDto();
        dto.symbol = asset.getSymbol();
        dto.name = asset.getName();

        if (balanceOpt.isPresent()) {
            Balance balance = balanceOpt.get();
            BigDecimal quantity = balance.getAvailable().add(balance.getLocked());
            dto.quantity = quantity;
            dto.availableQuantity = balance.getAvailable();
            dto.lockedQuantity = balance.getLocked();

            // Get current price for market value calculation
            if (!"USDT".equalsIgnoreCase(asset.getSymbol())) {
                String whiteBitSymbol = whiteBitService.toWhiteBitSymbol(asset.getSymbol());
                BigDecimal currentPrice = whiteBitService.getCurrentPrice(whiteBitSymbol);
                if (currentPrice != null && quantity.compareTo(BigDecimal.ZERO) > 0) {
                    dto.currentPriceUsd = currentPrice;
                    dto.marketValueUsd = quantity.multiply(currentPrice).setScale(2, RoundingMode.HALF_UP);
                }
            } else {
                dto.currentPriceUsd = BigDecimal.ONE;
                dto.marketValueUsd = dto.quantity.setScale(2, RoundingMode.HALF_UP);
            }
        } else {
            dto.quantity = BigDecimal.ZERO;
            dto.availableQuantity = BigDecimal.ZERO;
            dto.lockedQuantity = BigDecimal.ZERO;
            dto.marketValueUsd = BigDecimal.ZERO;
        }

        return ResponseEntity.ok(dto);
    }

    // ─── Helpers ───

    private AssetListDto buildAssetListDto(Asset asset, Map<String, WhiteBitTicker24h> tickers) {
        AssetListDto dto = new AssetListDto();
        dto.id = asset.getId().toString();
        dto.symbol = asset.getSymbol();
        dto.name = asset.getName();
        dto.scale = asset.getScale();
        dto.updatedAt = OffsetDateTime.now();

        if ("USDT".equalsIgnoreCase(asset.getSymbol())) {
            dto.priceUsd = BigDecimal.ONE;
            dto.change24hPercent = BigDecimal.ZERO;
            dto.priceUnavailable = false;
        } else {
            String whiteBitSymbol = whiteBitService.toWhiteBitSymbol(asset.getSymbol());
            WhiteBitTicker24h ticker = tickers.get(whiteBitSymbol);
            if (ticker != null) {
                dto.priceUsd = parseBigDecimal(ticker.lastPrice);
                dto.change24hPercent = parseBigDecimal(ticker.priceChangePercent);
                dto.priceUnavailable = false;
            } else {
                dto.priceUsd = null;
                dto.change24hPercent = null;
                dto.priceUnavailable = true;
            }
        }

        return dto;
    }

    private Comparator<AssetListDto> getComparator(String sort) {
        return switch (sort.toLowerCase()) {
            case "name" -> Comparator.comparing(d -> d.name, String.CASE_INSENSITIVE_ORDER);
            case "price" -> Comparator.comparing(d -> d.priceUsd != null ? d.priceUsd : BigDecimal.ZERO);
            case "change24h" -> Comparator.comparing(d -> d.change24hPercent != null ? d.change24hPercent : BigDecimal.ZERO);
            default -> Comparator.comparing(d -> d.symbol, String.CASE_INSENSITIVE_ORDER);
        };
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ─── DTOs ───

    public static class PagedResponse<T> {
        public final List<T> items;
        public final int total;
        public final int page;
        public final int size;
        public final int totalPages;

        public PagedResponse(List<T> items, int total, int page, int size, int totalPages) {
            this.items = items;
            this.total = total;
            this.page = page;
            this.size = size;
            this.totalPages = totalPages;
        }
    }

    public static class AssetListDto {
        public String id;
        public String symbol;
        public String name;
        public Integer scale;
        public BigDecimal priceUsd;
        public BigDecimal change24hPercent;
        public boolean priceUnavailable;
        public OffsetDateTime updatedAt;
    }

    public static class AssetDetailDto {
        public String id;
        public String symbol;
        public String name;
        public Integer scale;
        public BigDecimal priceUsd;
        public BigDecimal change24hPercent;
        public BigDecimal highPrice24h;
        public BigDecimal lowPrice24h;
        public BigDecimal volume24h;
        public boolean priceUnavailable;
        public OffsetDateTime updatedAt;
    }

    public static class PositionDto {
        public String symbol;
        public String name;
        public BigDecimal quantity;
        public BigDecimal availableQuantity;
        public BigDecimal lockedQuantity;
        public BigDecimal currentPriceUsd;
        public BigDecimal marketValueUsd;
    }
}
