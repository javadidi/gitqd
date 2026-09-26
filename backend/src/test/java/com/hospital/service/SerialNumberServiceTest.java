package com.hospital.service;

import com.hospital.enums.SerialType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class SerialNumberServiceTest {

    @Test
    void concurrentFormat_noDuplicates() throws InterruptedException {
        AtomicLong counter = new AtomicLong(0);
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        int threadCount = 10;
        Set<String> results = Collections.newSetFromMap(new ConcurrentHashMap<>());
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    startLatch.await();
                    long seq = counter.incrementAndGet();
                    String serial = SerialNumberService.format(SerialType.YY, dateStr, seq);
                    results.add(serial);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }

        startLatch.countDown();
        doneLatch.await();

        assertEquals(threadCount, results.size(), "10线程并发取号不应有重复");
        for (String serial : results) {
            assertTrue(serial.startsWith("YY"), "前缀应为 YY");
            assertTrue(serial.matches("YY\\d{8}-\\d{4}"), "格式应为 YY{yyyyMMdd}-{4位序号}");
        }
    }

    @Test
    void format_correctOutput() {
        String dateStr = "20260924";
        String serial = SerialNumberService.format(SerialType.CF, dateStr, 1);
        assertEquals("CF20260924-0001", serial);

        String serial2 = SerialNumberService.format(SerialType.YY, dateStr, 42);
        assertEquals("YY20260924-0042", serial2);
    }

    @Test
    void format_allPrefixes() {
        String dateStr = "20260924";
        for (SerialType type : SerialType.values()) {
            String serial = SerialNumberService.format(type, dateStr, 1);
            assertTrue(serial.startsWith(type.getPrefix()),
                    type.name() + " 前缀应为 " + type.getPrefix());
            assertTrue(serial.matches(type.getPrefix() + "\\d{8}-\\d{4}"),
                    type.name() + " 格式不正确: " + serial);
        }
    }
}
