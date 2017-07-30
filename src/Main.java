import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Created by Mayank Bhura on 30/07/17.
 */
public class Main {
    public static void main(String[] args) throws Exception {
        String username;
        if (args.length != 0) {
            username = args[0];
        } else {
            BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
            System.out.println("Enter username:");
            username = stdin.readLine();
        }

        long startTime = System.nanoTime();
        CodeforcesCrawler crawler = new CodeforcesCrawler(username);
        crawler.crawl();
        long endTime = System.nanoTime();

        // Execution time calculation.
        printHoursMinutesAndSeconds(endTime - startTime);
    }

    /**
     * Converts time given as nanoseconds to hh:mm:ss:ms format and prints it.
     *
     * @param time Time in nanoseconds.
     */
    private static void printHoursMinutesAndSeconds(long time) {
        time = time / 1000000L;
        long hours = time / 3600000;
        time = time % 3600000;
        long minutes = time / 60000;
        time = time % 60000;
        long seconds = time / 1000;
        time = time % 1000;
        System.out.println("\nCompleted fetching all successful submissions in "
                + hours + " hours, "
                + minutes + " minutes, "
                + seconds + " seconds, "
                + time + " milliseconds.");
    }
}
