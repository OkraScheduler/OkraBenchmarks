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

import okra.model.DefaultOkraItem;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@RunWith(JUnit4.class)
public class SingleThreadBenchmarkTest extends OkraBaseBenchmarkContainerTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(SingleThreadBenchmarkTest.class);

    @Test
    public void benchmarkSingleThreadTest() throws InterruptedException {

        int totalItems = 3000;
        LOGGER.info("Scheduling {} items... (0 to 30 secs ahead creation)", totalItems);

        Random random = new Random();
        for (int i = 0; i < totalItems; i++) {
            DefaultOkraItem item = new DefaultOkraItem();
            item.setRunDate(LocalDateTime.now().plusSeconds(random.nextInt(31)));
            getOkra().schedule(item);
        }

        LOGGER.info("Items scheduled.");
        LOGGER.info("Polling for items...");

        int receivedItems = 0;

        List<Double> deviationList = new ArrayList<>();
        List<DefaultOkraItem> processedItems = new ArrayList<>();
        while (receivedItems < totalItems) {
            Optional<DefaultOkraItem> opt = getOkra().poll();
            if (opt.isPresent()) {
                DefaultOkraItem item = opt.get();
                LOGGER.debug("Scheduled item received...: {}", item);
                receivedItems++;
                LocalDateTime runDate = item.getRunDate();
                double millisDiff = Math.abs(LocalDateTime.now().until(runDate, ChronoUnit.MILLIS));
                deviationList.add(millisDiff);
                processedItems.add(item);
            } else {
                Thread.sleep(500);
            }
        }

        LOGGER.info("Avg diff: [{}]", calcAvgDiff(deviationList));

        LOGGER.info("Removing schedules...");
        processedItems.parallelStream().forEach(i -> getOkra().delete(i));
        LOGGER.info("Done!");

    }

    private double calcAvgDiff(List<Double> deviationList) {
        DoubleSummaryStatistics statistics = deviationList.parallelStream().mapToDouble(val -> val).summaryStatistics();
        return statistics.getAverage();
    }

}
