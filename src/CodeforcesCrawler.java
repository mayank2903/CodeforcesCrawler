import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

import com.google.common.util.concurrent.RateLimiter;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/**
 * Author: Mayank Bhura (pc.mayank@gmail.com)
 * Crawler class that provides utils to crawl over Codeforces website, to download successful solutions of a user.
 */

public class CodeforcesCrawler {
    private static final double CODEFORCES_QPS_LIMIT = 5.0;
    private static final String CODEFORCES_SOLUTION_URL_FORMAT = "http://codeforces.com/contest/%s/submission/%s";
    private static final String CODEFORCES_SUBMISSIONS_URL_FORMAT = "http://codeforces.com/api/user.status?handle=%s&from=1";
    private static final String FILE_SEPARATOR = System.getProperty("file.separator");
    private static final String CODEFORCES_SOLUTIONS_DIR = System.getProperty("user.home") + FILE_SEPARATOR + "CodeforcesSolutions";
    private static final String SOLUTION_FILE_PATH_FORMAT = CODEFORCES_SOLUTIONS_DIR + FILE_SEPARATOR + "%s" + FILE_SEPARATOR + "%s";
    private static final String VERDICT_OK = "OK";

    private final String userName;

    private RateLimiter throttler = RateLimiter.create(CODEFORCES_QPS_LIMIT);
    private Map<String, Boolean> alreadyFetchedSolutions = new HashMap<>();

    public CodeforcesCrawler(String userName) {
        this.userName = userName;
    }

    /**
     * The only public method of this class.
     * - Crawls over the codeforces website, and fetches successful submissions of user.
     * - Fetches only one successful submission per problem.
     */
    public void crawl() {
        List<Submission> submissions = getSubmissionsList();
        try {
            fetchSolutionsAndWriteToFile(submissions);
        } catch (IOException e) {
            System.out.printf("Failed to fetch and write solutions for %s. Reason:%s\n", userName, e);
            return;
        }
    }

    /**
     * Finds the unique successful public submissions of the user.
     * @return List of unique successful public submissions of the user.
     */
    private List<Submission> getSubmissionsList() {
        String userSubmissionsUrl = String.format(CODEFORCES_SUBMISSIONS_URL_FORMAT, userName);
        List<Submission> submissionsList = new ArrayList<>();

        String pageContent = null;
        try {
            pageContent = getPageContent(userSubmissionsUrl);
        } catch (Exception e) {
            System.out.printf("Failed to fetch list of submissions of %s. Reason: %s\n", userName, e);
            return submissionsList;
        }

        JSONObject jsonResponse = null;
        try {
            jsonResponse = (JSONObject) new JSONParser().parse(pageContent);
        } catch (Exception e) {
            System.out.println("Failed to parse JSON response for user's submissions. Reason:" + e);
            return submissionsList;
        }

        JSONArray submissions = (JSONArray) jsonResponse.get("result");
        ListIterator submissionsIterator = submissions.listIterator();
        while (submissionsIterator.hasNext()) {
            JSONObject submission = (JSONObject) submissionsIterator.next();
            if (submission.get("verdict").toString().equals(VERDICT_OK)) {
                int submissionId = Integer.parseInt(submission.get("id").toString());
                int contestId = Integer.parseInt(submission.get("contestId").toString());
                String problemName = Integer.toString(contestId) + ((JSONObject)submission.get("problem")).get("index").toString();
                String programmingLanguage = submission.get("programmingLanguage").toString();

                if (alreadyFetchedSolutions.containsKey(problemName)) {
                    continue;
                }
                submissionsList.add(new Submission(submissionId, contestId, problemName, programmingLanguage));
                alreadyFetchedSolutions.put(problemName, Boolean.TRUE);
            }
        }

        System.out.printf("User %s has a total of %d submissions.\n", userName, submissionsList.size());
        return submissionsList;
    }

    /**
     * Given the list of Submission objects for a user, downloads source codes for each.
     * @param submissions List of unique successful and public solutions of the user.
     * @throws IOException
     */
    private void fetchSolutionsAndWriteToFile(List<Submission> submissions) throws IOException {
        int count = 1;
        for (Submission submission : submissions) {
            System.out.printf("\n\n[%d/%d]: Fetching solution for problem: %s ...\n", count, submissions.size(), submission.getProblemName());
            String solutionUrl = String.format(CODEFORCES_SOLUTION_URL_FORMAT, submission.getContestId(), submission.getSubmissionId());
            String sourceCode = null;

            try {
                sourceCode = fetchSourceCode(solutionUrl);
                System.out.printf("Successfully fetched source code for problem: %s\n", submission.getProblemName());
            } catch (Exception e) {
                System.out.printf("Could not fetch source code for problem: %s, as it is not public.\n", submission.getProblemName());
                System.out.println("Continuing with other solutions...");
                count++;
                continue;
            }

            // Write the source code to file.
            String fileName = submission.getProblemName() + getFileExtension(submission.getProgrammingLanguage());
            String filePath = String.format(SOLUTION_FILE_PATH_FORMAT, userName, fileName);
            System.out.printf("Writing file: %s\n", filePath);
            File file = new File(filePath);
            File parentDirectory = file.getParentFile();
            if (parentDirectory != null) {
                parentDirectory.mkdirs();
            }

            FileWriter fileWriter = new FileWriter(file);
            StringTokenizer tokenizer = new StringTokenizer(sourceCode, "\n");
            while (tokenizer.hasMoreTokens()) {
                fileWriter.write(tokenizer.nextToken() + "\n");
            }
            fileWriter.close();

            count++;
        }
    }

    /**
     * Given the submission URL, parses and returns the source code of the submission.
     * @param url URL of the submission.
     * @return Source code of the submission.
     */
    private String fetchSourceCode(String url) {
        String solutionPage = null;
        System.out.printf("Hitting URL: %s\n", url);
        try {
            solutionPage = getPageContent(url);
        } catch (IOException | InterruptedException e) {
            System.out.printf("Failed to fetch html response for url: %s. Reason:%s \n", url, e);
        }

        // Parse the source code out of the HTML response.
        Document document = Jsoup.parse(solutionPage);
        document.outputSettings(new Document.OutputSettings().prettyPrint(false));
        return document.select("pre[class*=program-source]").get(0).text();
    }

    /**
     * Given a URL, fetches the html page contents of it.
     * @param urlToGet URL of webpage whose HTML is to be fetched.
     * @return HTML response of GET request to urlToGet.
     * @throws IOException
     * @throws InterruptedException
     */
    private String getPageContent(String urlToGet) throws IOException, InterruptedException {
        URL url = new URL(urlToGet);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setUseCaches(false);

        try {
            throttler.acquire();
            int responseCode = connection.getResponseCode();
            assert (responseCode == 200);
        } catch (IOException e) {
            System.out.printf("Failed to connect to url: %s. Reason: %s\n", urlToGet, e);
        }

        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        String inputLine, response = new String();
        while ((inputLine = bufferedReader.readLine()) != null) {
            response = response + inputLine + "\n";
        }
        return response;
    }

    /**
     * Given programming language, returns the appropriate program file extension for it.
     * @param programmingLanguage The programming language.
     * @return String representing the file extension. Example: ".c", ".cpp", etc.
     */
    private String getFileExtension(String programmingLanguage) {
        if (programmingLanguage.contains("Java")) {
            return ".java";
        } else if (programmingLanguage.contains("Py")) {
            return ".py";
        } else if (programmingLanguage.contains("GNU C++")) {
            return ".cpp";
        } else if (programmingLanguage.contains("GNU C")) {
            return ".c";
        } else {
            return "";
        }
    }

    /**
     * Private class to wrap submission details:
     * 1. submission Id
     * 2. contest Id
     * 3. problem name
     * 4. programming language name
     */
    private class Submission {
        private final int submissionId;
        private final int contestId;
        private final String problemName;
        private final String programmingLanguage;

        public Submission(int submissionId, int contestId, String problemName, String programmingLanguage) {
            this.submissionId = submissionId;
            this.contestId = contestId;
            this.problemName = problemName;
            this.programmingLanguage = programmingLanguage;
        }

        public int getSubmissionId() {
            return submissionId;
        }

        public int getContestId() {
            return contestId;
        }

        public String getProblemName() {
            return problemName;
        }

        public String getProgrammingLanguage() {
            return programmingLanguage;
        }
    }
}