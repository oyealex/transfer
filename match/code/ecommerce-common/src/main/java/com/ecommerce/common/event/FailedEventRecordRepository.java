package com.ecommerce.common.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for managing failed event records.
 */
@Repository
public interface FailedEventRecordRepository extends JpaRepository<FailedEventRecord, Long> {
}
