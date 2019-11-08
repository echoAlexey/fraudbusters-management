package com.rbkmoney.fraudbusters.management;

import com.rbkmoney.damsel.wb_list.*;
import com.rbkmoney.fraudbusters.management.dao.p2p.wblist.P2PWbListDao;
import com.rbkmoney.fraudbusters.management.domain.ListRecord;
import com.rbkmoney.fraudbusters.management.domain.P2pListRecord;
import com.rbkmoney.fraudbusters.management.domain.PaymentListRecord;
import com.rbkmoney.fraudbusters.management.domain.tables.pojos.P2pWbListRecords;
import com.rbkmoney.fraudbusters.management.serializer.CommandChangeDeserializer;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jooq.JooqAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.mockito.ArgumentMatchers.any;

@Slf4j
@RunWith(SpringRunner.class)
@EnableAutoConfiguration(exclude = {FlywayAutoConfiguration.class, JooqAutoConfiguration.class})
@SpringBootTest(classes = FraudbustersManagementApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class P2pWbListApplicationTest extends AbstractKafkaIntegrationTest {

    private static final String VALUE = "value";
    private static final String LIST_NAME = "listName";
    public static final String IDENTITY_ID = "identityId";

    @LocalServerPort
    private int port;

    TestRestTemplate restTemplate = new TestRestTemplate();

    @Value("${kafka.topic.p2p.wblist.event.sink}")
    public String topicEventSink;
    @Value("${kafka.topic.p2p.wblist.command}")
    public String topicCommand;

    @MockBean
    public P2PWbListDao wbListDao;

    @Test
    public void listenCreated() throws ExecutionException, InterruptedException {
        Mockito.clearInvocations(wbListDao);

        Event event = new Event();
        Row row = createRow(ListType.black);
        event.setRow(row);
        event.setEventType(EventType.CREATED);
        ProducerRecord producerRecord = new ProducerRecord<>(topicEventSink, "test", event);
        try (Producer<String, Event> producer = createProducer()) {
            producer.send(producerRecord).get();
            producer.send(new ProducerRecord<>(topicEventSink, "test_1", event)).get();
            producer.send(new ProducerRecord<>(topicEventSink, "test_2", event)).get();
        }
        Thread.sleep(1000L);

        Mockito.verify(wbListDao, Mockito.times(3)).saveListRecord(any());
    }

    @Test
    public void listenCreatedGrey() throws ExecutionException, InterruptedException {
        Mockito.clearInvocations(wbListDao);

        Event event = new Event();
        Row row = createRow(ListType.grey);
        event.setEventType(EventType.CREATED);
        RowInfo rowInfo = RowInfo.count_info(new CountInfo()
                .setCount(5)
                .setTimeToLive("2019-08-22T13:14:17.443332Z")
                .setStartCountTime("2019-08-22T11:14:17.443332Z")
        );
        row.setRowInfo(rowInfo);

        event.setRow(row);

        try (Producer<String, Event> producer = createProducer()) {
            ProducerRecord<String, Event> producerRecord = new ProducerRecord<>(topicEventSink, "test", event);
            producer.send(producerRecord).get();
            producer.send(new ProducerRecord<>(topicEventSink, "test_1", event)).get();
            producer.send(new ProducerRecord<>(topicEventSink, "test_2", event)).get();
        }
        Thread.sleep(3000L);

        Mockito.verify(wbListDao, Mockito.times(3)).saveListRecord(any());
    }

    @Test
    public void listenDeleted() throws ExecutionException, InterruptedException {
        Event event = new Event();
        Row row = createRow(ListType.black);
        event.setRow(row);
        event.setEventType(EventType.DELETED);
        ProducerRecord producerRecord = new ProducerRecord<>(topicEventSink, "test", event);
        Producer<String, Event> producer = createProducer();

        producer.send(producerRecord).get();
        producer.close();
        Thread.sleep(3000L);

        Mockito.verify(wbListDao, Mockito.times(1)).removeRecord((P2pWbListRecords) any());
    }

    @NotNull
    private Row createRow(ListType listType) {
        Row row = new Row();
        row.setId(IdInfo.p2p_id(new P2pId()
                .setIdentityId(IDENTITY_ID))
        );
        row.setListName(LIST_NAME);
        row.setListType(listType);
        row.setValue(VALUE);
        return row;
    }

    @Test
    public void executeTest() throws IOException {
        Mockito.doNothing().when(wbListDao).saveListRecord(any());

        P2pListRecord record = new P2pListRecord();
        record.setListName(LIST_NAME);
        record.setIdentityId(IDENTITY_ID);
        record.setValue(VALUE);

        PaymentListRecord recordSecond = new PaymentListRecord();
        recordSecond.setListName(LIST_NAME);
        record.setIdentityId(IDENTITY_ID);
        recordSecond.setValue(VALUE + 2);

        insertToBlackList(record, recordSecond);

        Consumer<String, ChangeCommand> consumer = createConsumer(CommandChangeDeserializer.class);
        consumer.subscribe(Collections.singletonList(topicCommand));
        List<ChangeCommand> eventList = consumeCommand(consumer);

        Assert.assertEquals(2, eventList.size());
        Assert.assertEquals(eventList.get(0).command, Command.CREATE);
        Assert.assertEquals(eventList.get(0).getRow().getListType(), ListType.black);

        deleteFromWhiteList(record);

        consumer = createConsumer(CommandChangeDeserializer.class);
        consumer.subscribe(Collections.singletonList(topicCommand));
        eventList = consumeCommand(consumer);

        Assert.assertEquals(1, eventList.size());
        Assert.assertEquals(eventList.get(0).command, Command.DELETE);
        Assert.assertEquals(eventList.get(0).getRow().getListType(), ListType.white);
    }

    private void insertToBlackList(ListRecord... values) {
        HttpEntity<List<ListRecord>> entity = new HttpEntity<>(List.of(values), new org.springframework.http.HttpHeaders());
        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/fb-management/v1/p2p/blackList",
                HttpMethod.POST, entity, String.class);
        System.out.println(response);
    }

    private void deleteFromWhiteList(ListRecord value) {
        HttpEntity<ListRecord> entity = new HttpEntity<>(value, new org.springframework.http.HttpHeaders());
        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/fb-management/v1/p2p/whiteList",
                HttpMethod.DELETE, entity, String.class);
        System.out.println(response);
    }

    @NotNull
    private List<ChangeCommand> consumeCommand(Consumer<String, ChangeCommand> consumer) {
        List<ChangeCommand> eventList = new ArrayList<>();
        ConsumerRecords<String, ChangeCommand> consumerRecords =
                consumer.poll(Duration.ofSeconds(10));
        consumerRecords.forEach(command -> {
            log.info("poll command: {}", command.value());
            eventList.add(command.value());
        });
        consumer.close();
        return eventList;
    }
}