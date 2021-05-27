package com.example.demo;

import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class HelloWorldPrinterImpl implements HelloWorldPrinter {
    @Override
    public void print(String name) {
        System.out.println(String.format("Hello %s! ", name) + Instant.now());
    }
}
