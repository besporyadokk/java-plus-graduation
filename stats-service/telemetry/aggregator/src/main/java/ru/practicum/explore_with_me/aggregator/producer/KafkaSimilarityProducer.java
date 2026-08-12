package ru.practicum.explore_with_me.aggregator.producer;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.stereotype.Component;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;
import ru.practicum.explore_with_me.aggregator.producer.config.KafkaProducerConfig;

@Component
public class KafkaSimilarityProducer implements AutoCloseable {
    private final KafkaProducer<String, EventSimilarityAvro> kafkaProducer;
    private final String topic;

    public KafkaSimilarityProducer(KafkaProducerConfig producerConfig) {
        this.kafkaProducer = new KafkaProducer<>(producerConfig.getProperties());
        this.topic = producerConfig.getTopic();
    }

    public void send(EventSimilarityAvro event, long timestamp) {
        ProducerRecord<String, EventSimilarityAvro> producerRecord =
                new ProducerRecord<>(topic, null, timestamp, null, event);
        kafkaProducer.send(producerRecord);
    }

    public void flush() {
        kafkaProducer.flush();
    }

    @Override
    public void close() {
        kafkaProducer.close();
    }
}