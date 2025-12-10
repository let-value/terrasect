package com.terrasect.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TerrasectTest {
    @Test
    void helloShouldMatch() {
        assertEquals("Hello from Terrasect", Terrasect.hello());
    }
}
