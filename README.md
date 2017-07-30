# CodeforcesCrawler
Web crawler that downloads all unique, successul, public solutions of a user on Codeforces.

Feedback link: https://goo.gl/forms/kijsxr0BatZhbTxE3

Usage Instructions:
---------------------

Download CodeforcesCrawler.jar from the repository.

$ java -jar CodeforcesCrawler.jar <username>

Example:
---------
$ java -jar CodeforcesCrawler.jar mb1994

Features:
----------
1. Fetches a single AC solution for each of the problems.

2. Fetches the latest AC solutions for all problems.

3. It times the entire process, to give exact runtime of the crawling performed.

4. Provides clear output statements to keep the user aware about progress being made, while downloading solutions.

5. Supports downloading JAVA, CPP, C and PYTHON codes, since these are the most used. Rest are downloaded without any file extension.

6. Codeforces API asks us to follow a max of 5QPS for GET requests, so I have added a throttler that throttles queries above 5QPS. Used the Guava API for that.
