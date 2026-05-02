package com.cresensolutions.leaveservice.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares all exchanges, queues, and bindings for the Leave Service.
 *
 * Exchange layout:
 *   leave.events  (topic)  — all leave lifecycle events
 *   leave.dlx     (direct) — dead-letter exchange for failed messages
 *
 * Routing keys:
 *   leave.submitted              → q.leave.submitted
 *   leave.manager.approved       → q.leave.manager.approved
 *   leave.approved               → q.leave.approved
 *   leave.rejected               → q.leave.rejected
 *   leave.partial.decision       → q.leave.partial.decision
 *   leave.cancelled              → q.leave.cancelled
 *   leave.reminder               → q.leave.reminder
 *   leave.admin.notify           → q.leave.admin.notify
 *   leave.balance.deduct         → q.leave.balance.deduct
 */
@Configuration
public class RabbitMQConfig {

    // ── Exchange names ────────────────────────────────────────────────────────
    public static final String LEAVE_EVENTS_EXCHANGE     = "leave.events";
    public static final String LEAVE_DLX                 = "leave.dlx";

    // ── Routing keys ──────────────────────────────────────────────────────────
    public static final String RK_LEAVE_SUBMITTED        = "leave.submitted";
    public static final String RK_LEAVE_MANAGER_APPROVED = "leave.manager.approved";
    public static final String RK_LEAVE_APPROVED         = "leave.approved";
    public static final String RK_LEAVE_REJECTED         = "leave.rejected";
    public static final String RK_LEAVE_PARTIAL          = "leave.partial.decision";
    public static final String RK_LEAVE_CANCELLED        = "leave.cancelled";
    public static final String RK_LEAVE_REMINDER         = "leave.reminder";
    public static final String RK_LEAVE_ADMIN_NOTIFY     = "leave.admin.notify";
    public static final String RK_LEAVE_BALANCE_DEDUCT   = "leave.balance.deduct";

    // ── Queue names ───────────────────────────────────────────────────────────
    public static final String Q_LEAVE_SUBMITTED         = "q.leave.submitted";
    public static final String Q_LEAVE_MANAGER_APPROVED  = "q.leave.manager.approved";
    public static final String Q_LEAVE_APPROVED          = "q.leave.approved";
    public static final String Q_LEAVE_REJECTED          = "q.leave.rejected";
    public static final String Q_LEAVE_PARTIAL           = "q.leave.partial.decision";
    public static final String Q_LEAVE_CANCELLED         = "q.leave.cancelled";
    public static final String Q_LEAVE_REMINDER          = "q.leave.reminder";
    public static final String Q_LEAVE_ADMIN_NOTIFY      = "q.leave.admin.notify";
    public static final String Q_LEAVE_BALANCE_DEDUCT    = "q.leave.balance.deduct";

    // ── Dead-letter queue names ───────────────────────────────────────────────
    public static final String Q_LEAVE_BALANCE_DEDUCT_DLQ = "q.leave.balance.deduct.dlq";

    // ── Exchanges ─────────────────────────────────────────────────────────────

    @Bean TopicExchange leaveEventsExchange() {
        return ExchangeBuilder.topicExchange(LEAVE_EVENTS_EXCHANGE).durable(true).build();
    }

    @Bean DirectExchange leaveDlx() {
        return ExchangeBuilder.directExchange(LEAVE_DLX).durable(true).build();
    }

    // ── Dead-letter queues ────────────────────────────────────────────────────

    @Bean Queue leaveBalanceDeductDlq() {
        return QueueBuilder.durable(Q_LEAVE_BALANCE_DEDUCT_DLQ).build();
    }

    @Bean Binding leaveBalanceDeductDlqBinding() {
        return BindingBuilder.bind(leaveBalanceDeductDlq()).to(leaveDlx()).with(Q_LEAVE_BALANCE_DEDUCT);
    }

    // ── Main queues ───────────────────────────────────────────────────────────

    @Bean Queue qLeaveSubmitted()        { return durable(Q_LEAVE_SUBMITTED); }
    @Bean Queue qLeaveManagerApproved()  { return durable(Q_LEAVE_MANAGER_APPROVED); }
    @Bean Queue qLeaveApproved()         { return durable(Q_LEAVE_APPROVED); }
    @Bean Queue qLeaveRejected()         { return durable(Q_LEAVE_REJECTED); }
    @Bean Queue qLeavePartial()          { return durable(Q_LEAVE_PARTIAL); }
    @Bean Queue qLeaveCancelled()        { return durable(Q_LEAVE_CANCELLED); }
    @Bean Queue qLeaveReminder()         { return durable(Q_LEAVE_REMINDER); }
    @Bean Queue qLeaveAdminNotify()      { return durable(Q_LEAVE_ADMIN_NOTIFY); }

    @Bean Queue qLeaveBalanceDeduct() {
        return QueueBuilder.durable(Q_LEAVE_BALANCE_DEDUCT)
                .withArgument("x-dead-letter-exchange", LEAVE_DLX)
                .withArgument("x-dead-letter-routing-key", Q_LEAVE_BALANCE_DEDUCT)
                .build();
    }

    // ── Bindings ──────────────────────────────────────────────────────────────

    @Bean Binding bindLeaveSubmitted()       { return bind(qLeaveSubmitted(),       RK_LEAVE_SUBMITTED); }
    @Bean Binding bindLeaveManagerApproved() { return bind(qLeaveManagerApproved(), RK_LEAVE_MANAGER_APPROVED); }
    @Bean Binding bindLeaveApproved()        { return bind(qLeaveApproved(),        RK_LEAVE_APPROVED); }
    @Bean Binding bindLeaveRejected()        { return bind(qLeaveRejected(),        RK_LEAVE_REJECTED); }
    @Bean Binding bindLeavePartial()         { return bind(qLeavePartial(),         RK_LEAVE_PARTIAL); }
    @Bean Binding bindLeaveCancelled()       { return bind(qLeaveCancelled(),       RK_LEAVE_CANCELLED); }
    @Bean Binding bindLeaveReminder()        { return bind(qLeaveReminder(),        RK_LEAVE_REMINDER); }
    @Bean Binding bindLeaveAdminNotify()     { return bind(qLeaveAdminNotify(),     RK_LEAVE_ADMIN_NOTIFY); }
    @Bean Binding bindLeaveBalanceDeduct()   { return bind(qLeaveBalanceDeduct(),   RK_LEAVE_BALANCE_DEDUCT); }

    // ── Cross-service queues (consumed by Leave Service from user.events) ─────

    public static final String USER_EVENTS_EXCHANGE      = "user.events";
    public static final String RK_USER_DELETED           = "user.deleted";
    public static final String RK_ATTENDANCE_CHECKIN     = "attendance.checkin";

    public static final String Q_USER_DELETED_LEAVE      = "q.user.deleted.leave";      // Leave Service cleanup
    public static final String Q_ATTENDANCE_CHECKIN_LEAVE = "q.attendance.checkin.leave"; // Leave cross-check

    @Bean TopicExchange userEventsExchange() {
        return ExchangeBuilder.topicExchange(USER_EVENTS_EXCHANGE).durable(true).build();
    }

    @Bean Queue qUserDeletedLeave()       { return durable(Q_USER_DELETED_LEAVE); }
    @Bean Queue qAttendanceCheckinLeave() { return durable(Q_ATTENDANCE_CHECKIN_LEAVE); }

    @Bean Binding bindUserDeletedLeave() {
        return BindingBuilder.bind(qUserDeletedLeave()).to(userEventsExchange()).with(RK_USER_DELETED);
    }

    @Bean Binding bindAttendanceCheckinLeave() {
        return BindingBuilder.bind(qAttendanceCheckinLeave()).to(userEventsExchange()).with(RK_ATTENDANCE_CHECKIN);
    }

    // ── Serialization + template ──────────────────────────────────────────────

    @Bean
    public Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory cf) {
        RabbitTemplate tpl = new RabbitTemplate(cf);
        tpl.setMessageConverter(jsonMessageConverter());
        return tpl;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory cf) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(cf);
        factory.setMessageConverter(jsonMessageConverter());
        return factory;
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Queue durable(String name) {
        return QueueBuilder.durable(name).build();
    }

    private Binding bind(Queue queue, String routingKey) {
        return BindingBuilder.bind(queue).to(leaveEventsExchange()).with(routingKey);
    }
}
