package com.kimiha.vortexcore.engine;

import com.kimiha.vortexcore.model.entity.CancellationEntity;
import com.kimiha.vortexcore.model.OrderStateMachine;
import com.kimiha.vortexcore.model.OrderStatus;
import com.kimiha.vortexcore.model.domain.Order;

import java.time.LocalDateTime;
import java.util.*;

public class OrderBook {
    private final String securityId;

    // 交易所标准盘口 买单：价格从高到低排序 卖单：价格从低到高排序
    private final TreeMap<Double, LinkedList<Order>> bids = new TreeMap<>(Collections.reverseOrder());
    private final TreeMap<Double, LinkedList<Order>> asks = new TreeMap<>();

    // 股东价格索引 shareholderId -> {price -> order count} 用于检测对敲风险
    private final Map<String, TreeMap<Double, Integer>> shareholderBids = new HashMap<>();
    private final Map<String, TreeMap<Double, Integer>> shareholderAsks = new HashMap<>();

    // clOrderId -> price 索引：用于 O(1) 唯一性检测，以及撤单时 O(k) 定位（k 为同价档订单数）
    private final Map<String, Double> clOrderIdToPrice = new HashMap<>();

    public OrderBook(String securityId) {
        this.securityId = securityId;
    }

    /**
     * 当前订单簿挂单笔数（用于监控与测试断言）
     */
    public int getRestingOrderCount() {
        int count = 0;
        for (LinkedList<Order> queue : bids.values()) count += queue.size();
        for (LinkedList<Order> queue : asks.values()) count += queue.size();
        return count;
    }

    /**
     * 订单簿快照：按价格档位聚合，买盘从高到低、卖盘从低到高，各取前 depth 档。
     */
    public OrderBookSnapshot getSnapshot(int depth) {
        List<OrderBookLevel> bidLevels = new ArrayList<>();
        int b = 0;
        for (Map.Entry<Double, LinkedList<Order>> e : bids.entrySet()) {
            if (b >= depth) break;
            long qty = 0;
            for (Order o : e.getValue()) qty += o.getQty();
            bidLevels.add(new OrderBookLevel(e.getKey(), qty, e.getValue().size()));
            b++;
        }
        List<OrderBookLevel> askLevels = new ArrayList<>();
        int a = 0;
        for (Map.Entry<Double, LinkedList<Order>> e : asks.entrySet()) {
            if (a >= depth) break;
            long qty = 0;
            for (Order o : e.getValue()) qty += o.getQty();
            askLevels.add(new OrderBookLevel(e.getKey(), qty, e.getValue().size()));
            a++;
        }
        return new OrderBookSnapshot(securityId, bidLevels, askLevels, System.currentTimeMillis());
    }

    /**
     * 检测 clOrderId 是否在当前订单簿中已存在（不合法）。
     * 若 order 或 clOrderId 为空，返回 false，由上层校验。
     */
    public boolean illegalClOrderId(Order order) {
        if (order == null || order.getClOrderId() == null) {
            return false;
        }
        return clOrderIdToPrice.containsKey(order.getClOrderId());
    }

    /**
     * 券商前置风控 检测是否存在对敲风险
     */
    public boolean isWashTrading(Order newOrder) {
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
     * 交易所撮合引擎 执行撮合逻辑；返回成交明细与状态/累计量发生变化的订单（taker + makers）。
     */
    public MatchResult executeMatch(Order newOrder) {
        List<TradeResult> tradeResults = new ArrayList<>();
        List<Order> ordersUpdated = new ArrayList<>();
        // 对手盘
        TreeMap<Double, LinkedList<Order>> counterParties = "B".equals(newOrder.getSide()) ? asks : bids;

        // 尝试撮合：对手盘不为空且新订单还有剩余量
        while (!counterParties.isEmpty() && newOrder.getQty() > 0) {
            Double bestPrice = counterParties.firstKey();
            // 买单：新订单价格 < 对手盘最低价
            if ("B".equals(newOrder.getSide()) && newOrder.getPrice() < bestPrice)
                break;
            // 卖单：新订单价格 > 对手盘最高价
            if ("S".equals(newOrder.getSide()) && newOrder.getPrice() > bestPrice)
                break;

            LinkedList<Order> queue = counterParties.get(bestPrice);
            Iterator<Order> iterator = queue.iterator();

            // 吃单：新订单还有剩余量且对手盘还有剩余量
            // TODO: 成交价最优原则，需要优化成交价生成算法
            while (iterator.hasNext() && newOrder.getQty() > 0) {
                Order maker = iterator.next();
                // 成交数量：新订单剩余量和对手盘剩余量中的较小值
                int tradeQty = Math.min(newOrder.getQty(), maker.getQty());
                int makerOriginalQty = maker.getQty();

                tradeResults.add(new TradeResult(
                        newOrder.getClOrderId(), maker.getClOrderId(), bestPrice, tradeQty, securityId,
                        maker.getSide(), maker.getShareholderId(), makerOriginalQty, maker.getPrice()));

                newOrder.setQty(newOrder.getQty() - tradeQty);
                newOrder.setCumQty(newOrder.getCumQty() + tradeQty);

                maker.setQty(maker.getQty() - tradeQty);
                maker.setCumQty(maker.getCumQty() + tradeQty);
                maker.setUpdatedTime(LocalDateTime.now());
                ordersUpdated.add(maker);

                if (maker.getQty() == 0) {
                    OrderStateMachine.transition(maker, OrderStatus.Filled);
                    iterator.remove();
                    clOrderIdToPrice.remove(maker.getClOrderId());
                    updateShareholderIndex(maker, false); // 从索引中移除已成交完的maker订单
                }
            }
            if (queue.isEmpty())
                counterParties.remove(bestPrice);
        }

        // 2. 剩余部分进入挂单；设置 taker 终态
        if (newOrder.getQty() > 0) {
            OrderStateMachine.transition(newOrder, OrderStatus.PartiallyFilled);
            TreeMap<Double, LinkedList<Order>> mySide = "B".equals(newOrder.getSide()) ? bids : asks;
            mySide.computeIfAbsent(newOrder.getPrice(), k -> new LinkedList<>()).add(newOrder);
            if (newOrder.getClOrderId() != null) {
                clOrderIdToPrice.put(newOrder.getClOrderId(), newOrder.getPrice());
            }
            updateShareholderIndex(newOrder, true); // 添加到股东价格索引
        } else {
            OrderStateMachine.transition(newOrder, OrderStatus.Filled);
        }
        ordersUpdated.add(newOrder);
        return new MatchResult(tradeResults, ordersUpdated);
    }

    /**
     * 按客户端订单号撤单：从订单簿中移除该挂单，并更新股东价格索引。
     *
     * @param cancellation 撤单请求
     * @return 被撤掉的订单（含 qty/price 等用于回报）；若未找到则返回 null
     */
    public Order cancelByClOrderId(CancellationEntity cancellation) {
        TreeMap<Double, LinkedList<Order>> sideMap = "B".equals(cancellation.getSide()) ? bids : asks;
        Order removed = removeFromSide(cancellation.getOrigClOrderId(), sideMap);
        if (removed != null) {
            OrderStateMachine.transition(removed, OrderStatus.Canceled);
            clOrderIdToPrice.remove(cancellation.getOrigClOrderId());
            updateShareholderIndex(removed, false);
        }
        return removed;
    }

    /**
     * 从指定方向的订单簿中移除指定客户端订单号对应的订单。
     * 通过 clOrderIdToPrice 索引直接定位价格档位，复杂度 O(k)，k 为同价档订单数。
     *
     * @param clOrderId 待移除的客户端订单号
     * @param side 订单簿方向（买单或卖单）
     * @return 被移除的订单实体；若未找到则返回 null
     */
    private Order removeFromSide(String clOrderId, TreeMap<Double, LinkedList<Order>> side) {
        Double price = clOrderIdToPrice.get(clOrderId);
        if (price == null) return null;

        LinkedList<Order> queue = side.get(price);
        if (queue == null) return null;

        Order removed = null;
        Iterator<Order> it = queue.iterator();
        while (it.hasNext()) {
            Order o = it.next();
            if (clOrderId.equals(o.getClOrderId())) {
                it.remove();
                removed = o;
                break;
            }
        }
        if (queue.isEmpty()) side.remove(price);
        return removed;
    }

    /**
     * 更新股东价格索引
     * 
     * @param order 需要处理的订单实体
     * @param isAdd true 代表订单进入挂单（新增索引），false 代表订单成交或撤单（移除索引）
     */
    private void updateShareholderIndex(Order order, boolean isAdd) {
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