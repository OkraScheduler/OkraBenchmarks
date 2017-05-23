/*
 * Copyright (c) 2017 Okra Scheduler
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package okra;

import okra.base.Okra;
import okra.model.DefaultOkraItem;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

public class MultiThreadBenchmarkTest extends OkraBaseBenchmarkContainerTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(MultiThreadBenchmarkTest.class);

    private AtomicLong totalProcessedItems = new AtomicLong(0);

    private double calcAvgDiff(List<Double> deviationList) {
        DoubleSummaryStatistics statistics = deviationList.parallelStream().mapToDouble(val -> val).summaryStatistics();
        return statistics.getAverage();
    }

    @Test
    public void benchmarkWith30ThreadsTest() throws InterruptedException, ExecutionException {
        LOGGER.info("Starting benchmark tests using Mongo 3.4...");
        runBenchmarkTests(getOkra());
    }

    private void runBenchmarkTests(Okra<DefaultOkraItem> okra) throws InterruptedException, ExecutionException {
        int totalItems = 3000;
        LOGGER.info("Scheduling {} items... (0 to 30 secs ahead creation)", totalItems);

        Random random = new Random();
        for (int i = 0; i < totalItems; i++) {
            DefaultOkraItem item = new DefaultOkraItem();
            item.setRunDate(LocalDateTime.now().plusSeconds(random.nextInt(31)));
            okra.schedule(item);
        }

        LOGGER.info("Items scheduled.");
        LOGGER.info("Polling for items...");

        int threadCount = 30;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<?>> futures = new ArrayList<>();
        List<SchedulePoller> pollerList = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            SchedulePoller poller = new SchedulePoller(okra, totalItems);
            pollerList.add(poller);
            futures.add(executor.submit(poller));
        }

        List<DefaultOkraItem> processedItems = new ArrayList<>();
        List<Double> deviationList = new ArrayList<>();
        List<Double> pollerDeviationList;
        List<DefaultOkraItem> pollerProcessedItems;

        for (Future<?> future : futures) {
            future.get();
        }

        for (SchedulePoller schedulePoller : pollerList) {
            pollerProcessedItems = schedulePoller.getProcessedItems();
            pollerProcessedItems.forEach(processedItems::add);
            pollerDeviationList = schedulePoller.getDeviationList();
            pollerDeviationList.forEach(deviationList::add);
        }

        LOGGER.info("Avg diff: [{}]", calcAvgDiff(deviationList));

        LOGGER.info("Removing schedules...");
        processedItems.parallelStream().forEach(okra::delete);
        LOGGER.info("Done!");
    }

    public class SchedulePoller implements Runnable {

        private final Okra<DefaultOkraItem> scheduler;
        private final long totalItems;
        private List<Double> deviationList = new ArrayList<>();
        private List<DefaultOkraItem> processedItems = new ArrayList<>();

        public SchedulePoller(Okra<DefaultOkraItem> scheduler, long totalItems) {
            this.scheduler = scheduler;
            this.totalItems = totalItems;
        }

        @Override
        public void run() {

            while (totalProcessedItems.longValue() < totalItems) {
                Optional<DefaultOkraItem> opt = scheduler.poll();
                if (opt.isPresent()) {
                    DefaultOkraItem item = opt.get();
                    LOGGER.debug("Scheduled item received...: {}", item);
                    totalProcessedItems.incrementAndGet();
                    LocalDateTime runDate = item.getRunDate();
                    double millisDiff = Math.abs(LocalDateTime.now().until(runDate, ChronoUnit.MILLIS));
                    this.deviationList.add(millisDiff);
                    this.processedItems.add(item);
                } else {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        LOGGER.error("Error while sleeping");
                    }
                }
            }

            LOGGER.debug("Poller completed!");

        }

        public List<Double> getDeviationList() {
            return deviationList;
        }

        public List<DefaultOkraItem> getProcessedItems() {
            return processedItems;
        }
    }

}
