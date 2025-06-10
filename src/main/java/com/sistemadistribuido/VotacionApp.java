package com.sistemadistribuido;

import com.google.gson.Gson;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.eclipse.jetty.websocket.api.Session;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static spark.Spark.*;



public class VotacionApp {



    // ¡NUEVA LÍNEA! La dirección de nuestro broker de Kafka.
    // Reemplaza esto con el DNS público de tu instancia EC2 de Kafka.
    private static final String KAFKA_BROKER_DNS = "";

    // --- ESTADO Y CONFIGURACIÓN ---
    private static final Map<Session, Session> sessions = new ConcurrentHashMap<>();
    private static final Map<String, Integer> votes = new ConcurrentHashMap<>();
    private static final Gson gson = new Gson();


    private static final String KAFKA_TOPIC = "topic-votos";

    // --- PRODUCTOR DE KAFKA ---
    private static Producer<String, String> kafkaProducer;

    public static void main(String[] args) {
        // Inicializamos el mapa de votos
        votes.put("Opcion A", 0);
        votes.put("Opcion B", 0);

        // --- INICIALIZACIÓN DE COMPONENTES ---
        // 1. Inicializar el Productor de Kafka
        Properties producerProps = new Properties();
        producerProps.put("bootstrap.servers", KAFKA_BROKER_DNS);
        producerProps.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        producerProps.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        kafkaProducer = new KafkaProducer<>(producerProps);

        // 2. Iniciar el Consumidor de Kafka en un hilo separado para no bloquear el servidor web
        startKafkaConsumer();

        // 3. Configurar el servidor web SparkJava
        port(4567);
        staticFiles.location("/public");
        webSocket("/votacion", VotacionWebSocketHandler.class);

        // --- ENDPOINT HTTP (AHORA SOLO PUBLICA EN KAFKA) ---
        post("/vote", (request, response) -> {
            String option = request.queryParams("option");
            if (option != null && votes.containsKey(option)) {
                // En lugar de actualizar el estado local, publicamos un evento en Kafka
                ProducerRecord<String, String> record = new ProducerRecord<>(KAFKA_TOPIC, option);
                kafkaProducer.send(record);

                System.out.println("Evento de voto para '" + option + "' publicado en Kafka.");
                response.status(200);
                return "Voto para '" + option + "' enviado al sistema.";
            }
            response.status(400);
            return "Opción inválida.";
        });

        init();
        System.out.println("Servidor de votación iniciado. Conectado a Kafka en " + KAFKA_BROKER_DNS);
    }

    // --- LÓGICA DEL CONSUMIDOR DE KAFKA ---
    private static void startKafkaConsumer() {
        Properties consumerProps = new Properties();
        consumerProps.put("bootstrap.servers", KAFKA_BROKER_DNS);
        // ¡TRUCO IMPORTANTE! Usamos un ID de grupo único para cada instancia.
        // Esto convierte al grupo de consumidores en un modelo Pub/Sub donde CADA instancia recibe CADA mensaje.
        consumerProps.put("group.id", "votacion-app-" + UUID.randomUUID().toString());
        consumerProps.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        consumerProps.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        consumerProps.put("auto.offset.reset", "latest"); // Empezar a leer desde los mensajes más nuevos

        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps);
        consumer.subscribe(Collections.singletonList(KAFKA_TOPIC));

        // Creamos y arrancamos un nuevo hilo para el consumidor
        Thread consumerThread = new Thread(() -> {
            System.out.println("Hilo del consumidor de Kafka iniciado.");
            while (true) {
                // El consumidor sondea Kafka en busca de nuevos mensajes
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
                for (ConsumerRecord<String, String> record : records) {
                    System.out.println("Mensaje recibido de Kafka: " + record.value());
                    // Al recibir un mensaje, actualizamos el estado local y notificamos a los clientes
                    votes.compute(record.value(), (key, val) -> (val == null) ? 1 : val + 1);
                    broadcastVotes();
                }
            }
        });
        consumerThread.start();
    }

    // --- MÉTODOS DE WEBSOCKET (SIN CAMBIOS) ---
    public static void addSession(Session session) { sessions.put(session, session); }
    public static void removeSession(Session session) { sessions.remove(session); }
    public static void sendCurrentVotesToUser(Session session) {
        try {
            session.getRemote().sendString(gson.toJson(votes));
        } catch (IOException e) { /* ... */ }
    }
    private static void broadcastVotes() {
        String votesJson = gson.toJson(votes);
        for (Session session : sessions.keySet()) {
            if (session.isOpen()) {
                try {
                    session.getRemote().sendString(votesJson);
                } catch (IOException e) { /* ... */ }
            }
        }
    }
}