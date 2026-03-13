package com.fraud.backend.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fraud.backend.service.EventStreamService;

@RestController
@RequestMapping("/events")
public class EventController {

    private final EventStreamService eventStreamService;

    public EventController(EventStreamService eventStreamService) {
        this.eventStreamService = eventStreamService;
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamEvents() {
        return eventStreamService.subscribe();
    }
}