package com.ecommerce.product.service;

import com.ecommerce.product.query.StockSummaryDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * A local stock data fetcher that generates stock summaries internally.
 */
@Component
public class StockInfoFetcher {

    private static final Logger log = LoggerFactory.getLogger(StockInfoFetcher.class);

    /**
     * Fetches stock info for a given SKU.
     *
     * @param skuId the SKU id
     * @return a stock summary
     */
    public StockSummaryDto fetch(Long skuId) {
        log.debug("StockInfoFetcher fetching stock for skuId={}", skuId);
        return new StockSummaryDto(999, 0);
    }
}
