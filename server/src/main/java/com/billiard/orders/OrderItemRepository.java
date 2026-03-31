package com.billiard.orders;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findAllByOrder_Id(Long orderId);

    List<OrderItem> findAllByOrder_IdIn(Collection<Long> orderIds);

    void deleteAllByOrder_Id(Long orderId);
}
