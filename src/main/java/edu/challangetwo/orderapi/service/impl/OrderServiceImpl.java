package edu.challangetwo.orderapi.service.impl;

import edu.challangetwo.orderapi.api.dto.*;
import edu.challangetwo.orderapi.exception.InvalidPriceOrQuantityException;
import edu.challangetwo.orderapi.exception.ResourceAlreadyExistsException;
import edu.challangetwo.orderapi.exception.ResourceNotFoundException;
import edu.challangetwo.orderapi.model.Item;
import edu.challangetwo.orderapi.model.Order;
import edu.challangetwo.orderapi.model.OrderStatus;
import edu.challangetwo.orderapi.repository.ItemRepository;
import edu.challangetwo.orderapi.repository.OrderRepository;
import edu.challangetwo.orderapi.service.interfaces.OrderService;
import edu.challangetwo.orderapi.service.util.CheckOrderStatus;
import edu.challangetwo.orderapi.service.util.OrderMapper;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final ItemRepository itemRepository;
    private final OrderMapper orderMapper;
    private final CheckOrderStatus checkOrderStatus;

    @Autowired
    public OrderServiceImpl(OrderRepository orderRepository, ItemRepository itemRepository, OrderMapper orderMapper, CheckOrderStatus checkOrderStatus) {
        this.orderRepository = orderRepository;
        this.itemRepository = itemRepository;
        this.orderMapper = orderMapper;
        this.checkOrderStatus = checkOrderStatus;
    }

    @Override
    @Transactional
    public OrderDTO createOrder(OrderDTO orderDTO) {
        String orderDTOId = orderDTO.getPedido();
        if(orderExists(orderDTOId))
            throw new ResourceAlreadyExistsException("Order with id " + orderDTOId + "already exists.");

        Order order = orderMapper.orderDTOToOrder(orderDTO);
        Order savedOrder = orderRepository.save(order);
        createItem(order);

        return orderMapper.orderToOrderDTO(savedOrder);  //fazer o teste direitinho
    }

    @Override
    @Transactional
    public OrderDTO updateOrder(String orderId, OrderUpdateDTO orderUpdateDTO) {
        Order existingOrder = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order with id " + orderId + "does not exists."));

        itemRepository.deleteByOrder(existingOrder);

        List<Item> newItems = new ArrayList<>();
        for (ItemDTO itemDTO : orderUpdateDTO.getItens()) {
            newItems.add(orderMapper.itemDTOtoItem(itemDTO));
        }

        existingOrder.setItems(newItems);
        createItem(existingOrder);
        Order updatedOrder = orderRepository.save(existingOrder);

        return orderMapper.orderToOrderDTO(updatedOrder);
    }

    @Override
    public List<OrderDTO> getAllOrders() {
        List<Order> orders = orderRepository.findAll();
        List<OrderDTO> ordersDTO = new ArrayList<>();
        for(Order order : orders) {
            ordersDTO.add(orderMapper.orderToOrderDTO(order));
        }
        return ordersDTO;
    }

    @Override
    public OrderDTO getOrderById(String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id " + orderId));

        return orderMapper.orderToOrderDTO(order);
    }

    @Override
    public void deleteOrderById(String orderId) {
        if(!orderExists(orderId))
            throw new ResourceNotFoundException("Order with id " + orderId + "does not exist.");

        getOrderById(orderId);
        orderRepository.deleteById(orderId);
    }

    @Override
    public StatusResponseDTO updateStatus(StatusRequestDTO statusRequestDTO) {
        String orderId = statusRequestDTO.getPedido();
        List<String> statusList = new ArrayList<>();
        if(!orderExists(orderId))
            statusList.add(OrderStatus.CODIGO_PEDIDO_INVALIDO.toString());
        else {
            OrderDTO orderDTO = getOrderById(orderId);
            statusList = checkOrderStatus.updateStatus(orderDTO, statusRequestDTO);
        }

        return new StatusResponseDTO(orderId, statusList);
    }

    private boolean orderExists(String orderId) {
        return orderRepository.findById(orderId).isPresent();
    }

    private void createItem(Order order) {
        order.getItems().forEach(item -> {
            item.setOrder(order); // Ensure bidirectional relationship
            validateItems(item);
        });
    }

    private void validateItems(Item item) {
        if(item.getUnitPrice().compareTo(BigDecimal.ZERO) <= 0 || item.getQuantity() <= 0)
            throw new InvalidPriceOrQuantityException("Invalid price or quantity for item: " + item.getDescription());
    }
}