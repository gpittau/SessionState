---
name: Hazelcast test execution
description: How to verify local Hazelcast integration tests in this environment.
---

Run Hazelcast-backed Maven tests as background shell tasks and wait for the final Surefire/Maven summary before judging the result. Direct shell output may stop around partition initialization even though the tests later complete successfully.

**Why:** A direct Maven invocation showed only Hazelcast startup logs and no Surefire report, while the same isolated test completed successfully when run in the background.

**How to apply:** For future Hazelcast integration or concurrency tests, launch `mvn clean test` as a background task and inspect its final log and Surefire reports.
