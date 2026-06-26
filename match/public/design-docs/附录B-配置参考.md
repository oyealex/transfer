# 附录B 配置参考

## 1. application.yml 示例

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:h2:file:./data/shophub;MODE=MySQL;DATABASE_TO_UPPER=false
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: update
    open-in-view: false
  cache:
    type: caffeine

security:
  jwt:
    issuer: shophub
    secret: local-development-secret-change-me
    expire-minutes: 120

order:
  expire-minutes: 60
  max-items: 30
  packaging-fee: 2.00
  free-shipping-threshold: 199.00

payment:
  retry-times: 5
  refund-fee-rate: 0.02
  callback-timeout-seconds: 5

invoice:
  tax-rate: 0.06
  max-title-length: 100

cart:
  ttl-days: 7
  max-items: 100

loyalty:
  points-per-yuan: 1
  redeem-rate: 100
  max-redeem-points-per-order: 10000
  max-redeem-ratio: 0.5
  expire-months: 12
  review-reward-points: 20

promotion:
  stack-order:
    - FULL_REDUCTION
    - COUPON
    - MEMBER_DISCOUNT

logistics:
  default-carrier: LOCAL_EXPRESS
  free-shipping-threshold: 199.00

test:
  reset-enabled: false
```

## 2. 配置默认值

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `order.expire-minutes` | 60 | 未支付订单自动取消超时时间 |
| `order.max-items` | 30 | 单笔订单最大商品种类 |
| `payment.retry-times` | 5 | 支付失败最大重试次数 |
| `payment.refund-fee-rate` | 0.02 | 退款手续费率 |
| `invoice.tax-rate` | 0.06 | 发票默认税率 |
| `cart.ttl-days` | 7 | 购物车本地缓存 TTL |
| `loyalty.max-redeem-points-per-order` | 10000 | 单笔订单最多抵扣积分 |
| `loyalty.max-redeem-ratio` | 0.5 | 积分最多抵扣订单金额比例 |
