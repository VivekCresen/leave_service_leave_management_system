package com.cresensolutions.leaveservice.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;


@Slf4j
@RequiredArgsConstructor
public abstract class BaseEventPublisher {

    protected final RabbitTemplate rabbitTemplate;


    protected void publish(String exchange, String routingKey, Object payload) {
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, payload);
            log.debug("[{}] Published '{}': {}", getClass().getSimpleName(), routingKey, payload);
        } catch (Exception e) {
            log.error("[{}] Failed to publish '{}': {}", getClass().getSimpleName(), routingKey, e.getMessage());
        }
    }
}
