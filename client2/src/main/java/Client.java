import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class Client {
    //    private final int SKI_DAY_LENGTH = 420;
    private static final String SEASON_ID = "2021";
    private static final String DAY_ID = "7";

    public static void main(String[] args) throws InterruptedException, IOException {
        CommandLineParser parser = new CommandLineParser();
        if (!parser.parseCommandLine(args)) {
            return;
        }

        int numThreads, numSkiers, numLifts, numRuns, serverAddress;
        numThreads = parser.getNumThreads();
        numSkiers = parser.getNumSkiers();
        numLifts = parser.getNumLifts();
        numRuns = parser.getNumRuns();
//        serverAddress = Integer.parseInt(parser.getServerAddress());

        // count number of successful and failed requests
        AtomicInteger totalSuccessfulPosts = new AtomicInteger();
        AtomicInteger totalFailedPosts = new AtomicInteger();

        BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter("output.csv"));
        bufferedWriter.write("start_time, request_type, latency, response_code\n");

        List<Double> responseTimes = new ArrayList<>();

        int totalThreads = numThreads / 4 + numThreads + numThreads / 10;
        CountDownLatch sharedLatch = new CountDownLatch(totalThreads);
        long start = System.currentTimeMillis();

        //phase 1
        int startTime = 1;
        int endTime = 90;
        int numPostRequests = (int) Math.ceil(numRuns * 0.2 * numSkiers/(numThreads / 4));
        int numWaitedThreads = (int)Math.ceil(numThreads / 4 * 0.2);

        CountDownLatch privateLatch1 = new CountDownLatch(numWaitedThreads);
        for (int i = 0; i < numThreads / 4; i++) {
            int startID = i * numSkiers / (numThreads / 4) + 1;
            int endID = (i + 1) * numSkiers /(numThreads / 4);

            SkierThread skierThread = new SkierThread(startID, endID, startTime, endTime, numRuns,
                    numSkiers, numThreads / 4, numLifts, 5, SEASON_ID, DAY_ID,
                    numPostRequests, numWaitedThreads, privateLatch1, sharedLatch, responseTimes,
                    totalSuccessfulPosts, totalFailedPosts, bufferedWriter);
            Thread thread = new Thread(skierThread);
            thread.start();
        }

        privateLatch1.await();
        // phase 2
        startTime = 91;
        endTime = 360;
        numPostRequests = (int) Math.ceil(numRuns * 0.6 * numSkiers/numThreads);
        numWaitedThreads = (int)Math.ceil(numThreads * 0.2);
        CountDownLatch privateLatch2 = new CountDownLatch(numWaitedThreads);
        for (int i = 0; i < numThreads; i++) {
            int startID = i * numSkiers / numThreads + 1;
            int endID = (i + 1) * numSkiers /numThreads;

            SkierThread skierThread = new SkierThread(startID, endID, startTime, endTime, numRuns,
                    numSkiers, numThreads, numLifts, 5, SEASON_ID, DAY_ID, numPostRequests,
                    numWaitedThreads, privateLatch2, sharedLatch, responseTimes, totalSuccessfulPosts,
                    totalFailedPosts, bufferedWriter);
            Thread thread = new Thread(skierThread);
            thread.start();
        }

        privateLatch2.await();
        // phase 3 - starting 10% of numThreads, with each thread sending (0.1 x numRuns) POST requests,
        startTime = 361;
        endTime = 420;
        numPostRequests = (int) Math.ceil(numRuns * 0.1);
        numWaitedThreads = numThreads / 4;
        CountDownLatch privateLatch3 = new CountDownLatch(numWaitedThreads);
        for (int i = 0; i < numThreads / 10; i++) {
            int startID = i * numSkiers / (numThreads / 10) + 1;
            int endID = (i + 1) * numSkiers /(numThreads / 10);

            SkierThread skierThread = new SkierThread(startID, endID, startTime, endTime, numRuns,
                    numSkiers, numThreads / 10, numLifts, 5, SEASON_ID, DAY_ID,
                    numPostRequests, numWaitedThreads, privateLatch3, sharedLatch, responseTimes,
                    totalSuccessfulPosts, totalFailedPosts, bufferedWriter);
            Thread thread = new Thread(skierThread);
            thread.start();
        }

        sharedLatch.await();
        long end = System.currentTimeMillis();
        long runTime = end - start;
//        double throughput = (totalSuccessfulPosts.get() + totalFailedPosts.get()) / runTime;
        System.out.println("Number of successful requests sent: " + totalSuccessfulPosts);
        System.out.println("Number of unsuccessful requests sent: " + totalFailedPosts);
        System.out.println("Total run time (wall time): " + runTime);
//        System.out.println("Total throughput in requests per second: " + throughput);
        bufferedWriter.close();


        // calculate mean
        double totalResponseTime = 0;
        for (Double latency: responseTimes) {
            totalResponseTime += latency;
        }
        double averageResponseTime = totalResponseTime / responseTimes.size();
        System.out.println("Mean response time: " + averageResponseTime);

        // calculate median
        Collections.sort(responseTimes);
        double medianResponseTime;
        int num = responseTimes.size();
        if (num % 2 == 0) {
            medianResponseTime =
                    (responseTimes.get(num / 2) + responseTimes.get(num / 2 - 1)) / 2;
        } else {
            medianResponseTime = responseTimes.get(num / 2);
        }
        System.out.println("Median response time: " + medianResponseTime);

        // calculate throughput: total number of requests/wall time (requests/second)
        System.out.println("Total throughput in requests per second: " + 1000L * totalSuccessfulPosts.intValue() / runTime);

        // 99th percentile
        double percentile99 = responseTimes.get((int) Math.ceil(responseTimes.size() * 0.99) - 1);
        System.out.println("99th percentile: " + percentile99);

        // min and max response time
        double minResponseTime = responseTimes.get(0);
        double maxResponseTime = responseTimes.get(responseTimes.size() - 1);
        System.out.println("Min response time: " + minResponseTime);
        System.out.println("Max response time: " + maxResponseTime);
    }
}