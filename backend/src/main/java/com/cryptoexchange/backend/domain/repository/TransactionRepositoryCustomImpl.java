package com.cryptoexchange.backend.domain.repository;

import com.cryptoexchange.backend.domain.model.OrderSide;
import com.cryptoexchange.backend.domain.model.Transaction;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Custom implementation that builds JPQL dynamically, adding filter clauses
 * only when parameters are non-null. This avoids PostgreSQL's
 * "could not determine data type of parameter" error with nullable JPQL params.
 */
public class TransactionRepositoryCustomImpl implements TransactionRepositoryCustom {

    private final EntityManager em;

    public TransactionRepositoryCustomImpl(EntityManager em) {
        this.em = em;
    }

    @Override
    public Page<Transaction> findFiltered(UUID userId, String symbol, OrderSide side,
                                          OffsetDateTime from, OffsetDateTime to,
                                          Pageable pageable) {

        StringBuilder jpql = new StringBuilder("SELECT t FROM Transaction t WHERE t.user.id = :userId");
        StringBuilder countJpql = new StringBuilder("SELECT COUNT(t) FROM Transaction t WHERE t.user.id = :userId");

        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);

        if (symbol != null) {
            jpql.append(" AND t.assetSymbol = :symbol");
            countJpql.append(" AND t.assetSymbol = :symbol");
            params.put("symbol", symbol);
        }
        if (side != null) {
            jpql.append(" AND t.side = :side");
            countJpql.append(" AND t.side = :side");
            params.put("side", side);
        }
        if (from != null) {
            jpql.append(" AND t.createdAt >= :from");
            countJpql.append(" AND t.createdAt >= :from");
            params.put("from", from);
        }
        if (to != null) {
            jpql.append(" AND t.createdAt <= :to");
            countJpql.append(" AND t.createdAt <= :to");
            params.put("to", to);
        }

        // Apply sorting from Pageable
        Sort sort = pageable.getSort();
        if (sort.isSorted()) {
            jpql.append(" ORDER BY ");
            List<String> orders = sort.stream()
                    .map(o -> "t." + o.getProperty() + " " + (o.isAscending() ? "ASC" : "DESC"))
                    .toList();
            jpql.append(String.join(", ", orders));
        }

        // Count query
        TypedQuery<Long> countQuery = em.createQuery(countJpql.toString(), Long.class);
        params.forEach(countQuery::setParameter);
        long total = countQuery.getSingleResult();

        // Data query
        TypedQuery<Transaction> dataQuery = em.createQuery(jpql.toString(), Transaction.class);
        params.forEach(dataQuery::setParameter);
        dataQuery.setFirstResult((int) pageable.getOffset());
        dataQuery.setMaxResults(pageable.getPageSize());

        List<Transaction> content = dataQuery.getResultList();

        return new PageImpl<>(content, pageable, total);
    }
}
