package com.hospital.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * order=0 让事务切面位于最外层，AuditLogAspect(@Order(100)) 在其内层执行，
 * 从而保证审计写入落在业务事务内部（业务回滚则审计一并回滚）。
 */
@Configuration
@EnableTransactionManagement(order = 0)
public class TransactionOrderConfig {
}
