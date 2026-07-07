# 附录D 本地事件契约

## 1. 事件通用字段

| 字段 | 说明 |
|------|------|
| eventId | 事件唯一 ID |
| eventType | 事件类型 |
| occurredAt | 发生时间 |
| aggregateId | 聚合根 ID |
| traceId | 链路 ID |

## 2. OrderPaidEvent

发布方：order-service

监听方：logistics-service、loyalty-service、common notification

载荷：

| 字段 | 说明 |
|------|------|
| orderId | 订单 ID |
| userId | 用户 ID |
| paidAmount | 实付金额 |
| items | 订单商品明细 |

监听器失败不得回滚支付状态。

## 3. PaymentSucceededEvent

发布方：payment-service

监听方：order-service、inventory-service、logistics-service、loyalty-service、common notification

载荷：

| 字段 | 说明 |
|------|------|
| paymentNo | 支付单号 |
| orderId | 订单 ID |
| paidAmount | 支付金额 |
| paidAt | 支付时间 |

## 4. ShipmentDeliveredEvent

发布方：logistics-service

监听方：order-service、loyalty-service

载荷：

| 字段 | 说明 |
|------|------|
| orderId | 订单 ID |
| shipmentId | 发货单 ID |
| deliveredAt | 签收时间 |

## 5. ReviewApprovedEvent

发布方：review-service

监听方：loyalty-service

载荷：

| 字段 | 说明 |
|------|------|
| reviewId | 评价 ID |
| userId | 用户 ID |
| orderId | 订单 ID |
| productId | 商品 ID |

