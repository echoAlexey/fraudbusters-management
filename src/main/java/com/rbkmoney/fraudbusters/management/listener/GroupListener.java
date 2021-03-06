package com.rbkmoney.fraudbusters.management.listener;

import com.rbkmoney.damsel.fraudbusters.Command;
import com.rbkmoney.fraudbusters.management.converter.CommandToGroupModelConverter;
import com.rbkmoney.fraudbusters.management.dao.group.GroupDao;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class GroupListener extends CommandListener {

    private final GroupDao groupDao;
    private final CommandToGroupModelConverter converter;

    @KafkaListener(topics = "${kafka.topic.fraudbusters.group.list}", containerFactory = "kafkaGroupListenerContainerFactory")
    public void listen(Command command) {
        log.info("GroupListener event: {}", command);
        handle(command, converter, groupDao::insert, groupDao::remove);
    }

}
