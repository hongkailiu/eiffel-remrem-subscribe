package com.ericsson.eiffel.remrem.subscribe.message;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.TimeoutException;

@Component("rabbitSender") @Slf4j
public class RabbitSender implements Sender {
    private static final int CHANNEL_COUNT = 1000;
    private static final Random random = new Random();
    @Value("${rabbitmq.host}") private String host;
    @Value("${rabbitmq.exchange.name}") private String exchangeName;
    private Connection rabbitConnection;

    @PostConstruct public void init() {
        //log.info("RMQHelper init ...");
        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(host);
            rabbitConnection = factory.newConnection();

        } catch (IOException | TimeoutException e) {
            log.error(e.getMessage(), e);
        }
    }

    @Override public void addEmitter(SseEmitter sseEmitter, String bindingKey)
        throws IOException {
        Channel ch = rabbitConnection.createChannel();
        String queueName = ch.queueDeclare().getQueue();
        ch.queueBind(queueName, exchangeName, bindingKey);
        System.out.println("Binding key:" + bindingKey);
        Consumer consumer = new DefaultConsumer(ch) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body)
                throws IOException {
                String message = new String(body, "UTF-8");
                System.out.println(" [x] Received '" + message + "'");
                sseEmitter.send(message);
            }
            @Override
            public void handleCancel(String consumerTag) throws IOException {
                sseEmitter.completeWithError(new Exception("Consumer Cancelled on RabbitMQ end"));
            }
        };
        ch.basicConsume(queueName, true, consumer);
    }

    @PreDestroy public void lastStep() throws IOException {
        //log.info("RMQHelper init ...");
        rabbitConnection.close();
    }

}
