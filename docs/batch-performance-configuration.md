# Batch Performance Optimization - Configuration Guide

## 🔧 Required Configuration Changes

### application.properties

Add the following Hibernate batch processing settings to your `src/main/resources/application.properties`:

```properties
# ===== 🚀 배치 처리 성능 최적화 =====
# Hibernate Batch Insert/Update (N개를 한번에 처리)
spring.jpa.properties.hibernate.jdbc.batch_size=50
spring.jpa.properties.hibernate.order_inserts=true
spring.jpa.properties.hibernate.order_updates=true
spring.jpa.properties.hibernate.jdbc.batch_versioned_data=true

# Batch Fetch (N+1 문제 해결)
spring.jpa.properties.hibernate.default_batch_fetch_size=50

# Statement Caching (PreparedStatement 재사용)
spring.jpa.properties.hibernate.jdbc.use_streams_for_binary=true
spring.jpa.properties.hibernate.query.plan_cache_max_size=2048
spring.jpa.properties.hibernate.query.plan_parameter_metadata_max_size=128
```

### Why These Settings?

1. **hibernate.jdbc.batch_size=50**
   - Groups 50 INSERT/UPDATE statements into a single batch
   - Reduces database round-trips by 50x

2. **hibernate.order_inserts=true & hibernate.order_updates=true**
   - Orders SQL statements for better batching efficiency
   - Required for batch_size to work properly

3. **hibernate.default_batch_fetch_size=50**
   - Solves N+1 query problem
   - Fetches related entities in batches

4. **Statement Caching**
   - Reuses prepared statements
   - Reduces SQL parsing overhead

## 📝 Application Instructions

After adding these properties:

1. Restart your application
2. Test with the new optimized endpoints:
   - `/api/batch/process-optimized` - Single batch processing
   - `/api/batch/process-parallel` - Parallel batch processing

3. Monitor performance improvements in logs

## ⚠️ Important Notes

- These settings are already included in the main documentation
- You need to manually add them to your local `application.properties`
- The file is excluded from git due to sensitive information
