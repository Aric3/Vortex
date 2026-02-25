package com.kimiha.vortexcore.engine;

import com.kimiha.vortexcore.model.OrderEntity;
import java.util.*;

public class OrderBook {
    private final String securityId;

    // 交易所标准盘口 买单：价格从高到低排序 卖单：价格从低到高排序
    private final TreeMap<Double, LinkedList<OrderEntity>> bids = new TreeMap<>(Collections.reverseOrder());
    private final TreeMap<Double, LinkedList<OrderEntity>> asks = new TreeMap<>();

    // 股东价格索引 shareholderId -> {price -> order count}
    private final Map<String, TreeMap<Double, Integer>> shareholderBids = new HashMap<>();
    private final Map<String, TreeMap<Double, Integer>> shareholderAsks = new HashMap<>();

    // 当前在簿挂单的 clOrderId 集合，用于 O(1) 唯一性检测
    private final Set<String> existingClOrderIds = new HashSet<>();

    public OrderBook(String securityId) {
        this.securityId = securityId;
    }

    /**
     * 当前订单簿挂单笔数（用于监控与测试断言）
     */
    public int getRestingOrderCount() {
        int count = 0;
        for (LinkedList<OrderEntity> queue : bids.values()) count += queue.size();
        for (LinkedList<OrderEntity> queue : asks.values()) count += queue.size();
        return count;
    }

    /**
     * 订单簿快照：按价格档位聚合，买盘从高到低、卖盘从低到高，各取前 depth 档。
     */
    public OrderBookSnapshot getSnapshot(int depth) {
        List<OrderBookLevel> bidLevels = new ArrayList<>();
        int b = 0;
        for (Map.Entry<Double, LinkedList<OrderEntity>> e : bids.entrySet()) {
            if (b >= depth) break;
            long qty = 0;
            for (OrderEntity o : e.getValue()) qty += o.getQty();
            bidLevels.add(new OrderBookLevel(e.getKey(), qty, e.getValue().size()));
            b++;
        }
        List<OrderBookLevel> askLevels = new ArrayList<>();
        int a = 0;
        for (Map.Entry<Double, LinkedList<OrderEntity>> e : asks.entrySet()) {
            if (a >= depth) break;
            long qty = 0;
            for (OrderEntity o : e.getValue()) qty += o.getQty();
            askLevels.add(new OrderBookLevel(e.getKey(), qty, e.getValue().size()));
            a++;
        }
        return new OrderBookSnapshot(securityId, bidLevels, askLevels, System.currentTimeMillis());
    }

    /**
     * 检测 clOrderId 是否在当前订单簿中已存在（不合法）。
     * 若 order 或 clOrderId 为空，返回 false，由上层校验。
     */
    public boolean illegalClOrderId(OrderEntity order) {
        if (order == null || order.getClOrderId() == null) {
            return false;
        }
        return existingClOrderIds.contains(order.getClOrderId());
    }

    /**
     * 券商前置风控 检测是否存在对敲风险
     */
    public boolean isWashTrading(OrderEntity newOrder) {
        String sid = newOrder.getShareholderId();
        double price = newOrder.getPrice();

        if ("B".equals(newOrder.getSide())) {
            // 买单：检查该股东是否有 价格 <= 当前买价 的卖单挂着
            TreeMap<Double, Integer> myAsks = shareholderAsks.get(sid);
            if (myAsks != null) {
                Double lowestAsk = myAsks.firstKey(); // 拿到该股东最低的卖挂单价
                return lowestAsk != null && lowestAsk <= price;
            }
        } else {
            // 卖单：检查该股东是否有 价格 >= 当前卖价 的买单挂着
            TreeMap<Double, Integer> myBids = shareholderBids.get(sid);
            if (myBids != null) {
                Double highestBid = myBids.firstKey(); // 拿到该股东最高的买挂单价
                return highestBid != null && highestBid >= price;
            }
        }
        return false;
    }

    /**
     * 交易所撮合引擎 执行撮合逻辑
     */
    public List<TradeResult> executeMatch(OrderEntity newOrder) {
        List<TradeResult> tradeResults = new ArrayList<>();
        // 对手盘
        TreeMap<Double, LinkedList<OrderEntity>> counterParties = "B".equals(newOrder.getSide()) ? asks : bids;

        // 1. 尝试撮合：对手盘不为空且新订单还有剩余量
        while (!counterParties.isEmpty() && newOrder.getQty() > 0) {
            Double bestPrice = counterParties.firstKey();
            // 买单：新订单价格 < 对手盘最低价
            if ("B".equals(newOrder.getSide()) && newOrder.getPrice() < bestPrice)
                break;
            // 卖单：新订单价格 > 对手盘最高价
            if ("S".equals(newOrder.getSide()) && newOrder.getPrice() > bestPrice)
                break;

            LinkedList<OrderEntity> queue = counterParties.get(bestPrice);
            Iterator<OrderEntity> iterator = queue.iterator();

            // 吃单：新订单还有剩余量且对手盘还有剩余量
            while (iterator.hasNext() && newOrder.getQty() > 0) {
                OrderEntity maker = iterator.next();
                // 成交数量：新订单剩余量和对手盘剩余量中的较小值
                int tradeQty = Math.min(newOrder.getQty(), maker.getQty());
                int makerOriginalQty = maker.getQty();

                tradeResults.add(new TradeResult(
                        newOrder.getClOrderId(), maker.getClOrderId(), bestPrice, tradeQty, securityId,
                        maker.getSide(), maker.getShareholderId(), makerOriginalQty, maker.getPrice()));

                newOrder.setQty(newOrder.getQty() - tradeQty);
                maker.setQty(maker.getQty() - tradeQty);

                if (maker.getQty() == 0) {
                    iterator.remove();
                    existingClOrderIds.remove(maker.getClOrderId());
                    updateShareholderIndex(maker, false); // 从索引中移除已成交完的maker订单
                }
            }
            if (queue.isEmpty())
                counterParties.remove(bestPrice);
        }

        // 2. 剩余部分进入挂单
        if (newOrder.getQty() > 0) {
            TreeMap<Double, LinkedList<OrderEntity>> mySide = "B".equals(newOrder.getSide()) ? bids : asks;
            mySide.computeIfAbsent(newOrder.getPrice(), k -> new LinkedList<>()).add(newOrder);
            if (newOrder.getClOrderId() != null) {
                existingClOrderIds.add(newOrder.getClOrderId());
            }
            updateShareholderIndex(newOrder, true); // 添加到股东价格索引
        }
        return tradeResults;
    }

    /**
     * 更新股东价格索引
     * 
     * @param order 需要处理的订单实体
     * @param isAdd true 代表订单进入挂单（新增索引），false 代表订单成交或撤单（移除索引）
     */
    private void updateShareholderIndex(OrderEntity order, boolean isAdd) {
        String sid = order.getShareholderId();
        Double price = order.getPrice();
        boolean isBuy = "B".equals(order.getSide());

        // 定位到对应的 Side Map (买方索引或卖方索引)
        Map<String, TreeMap<Double, Integer>> sideMap = isBuy ? shareholderBids : shareholderAsks;

        if (isAdd) {
            // --- 处理新增挂单逻辑 ---
            TreeMap<Double, Integer> priceMap = sideMap.get(sid);
            if (priceMap == null) {
                priceMap = new TreeMap<>(isBuy ? Collections.reverseOrder() : null);
                sideMap.put(sid, priceMap);
            }

            // 记录该价格下的订单笔数 (可能有多个订单挂在同一个价格)
            priceMap.put(price, priceMap.getOrDefault(price, 0) + 1);

        } else {
            // --- 处理成交或撤单逻辑 ---
            TreeMap<Double, Integer> priceMap = sideMap.get(sid);
            if (priceMap != null) {
                Integer count = priceMap.get(price);
                if (count != null) {
                    if (count <= 1) {
                        // 该价格下的最后一笔订单消耗完，直接移除该价格档位
                        priceMap.remove(price);
                    } else {
                        // 该价格下还有其他挂单，计数减一
                        priceMap.put(price, count - 1);
                    }
                }

                if (priceMap.isEmpty()) {
                    sideMap.remove(sid);
                }
            }
        }
    }
}