package com.flashsale.inventory;

import com.flashsale.common.events.PaymentCompletedEvent;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.time.Instant;
import java.util.Properties;

public class PoisonPillInjector {

    public static void main(String[] args) {
        // PASTE YOUR EXTRACTED UUIDs HERE
        String orderId = "60e4ce60-fa49-4b40-88b6-0c5547decded"; // Your Postgres ID
        String reservationId = "97eac882-0425-4db0-873c-ebf5241abcba";  // reservation id from postman response

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        // Use Spring's JsonSerializer to automatically attach the __TypeId__ headers
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class.getName());
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, "true");

        // Use the actual Java Record so the serializer maps the type correctly
        PaymentCompletedEvent event = new PaymentCompletedEvent(
                orderId,
                reservationId,
                "VIP-1",
                false,
                "DECLINED_INSUFFICIENT_FUNDS",
                Instant.now()
        );

        try (KafkaProducer<String, PaymentCompletedEvent> producer = new KafkaProducer<>(props)) {
            System.out.println("Firing valid payment failure event to Kafka...");
            producer.send(new ProducerRecord<>("payment-completed", "VIP-1", event));
            System.out.println("Poison pill injected successfully.");
        } catch (Exception e) {
            System.err.println("Failed to inject event: " + e.getMessage());
        }
    }
}