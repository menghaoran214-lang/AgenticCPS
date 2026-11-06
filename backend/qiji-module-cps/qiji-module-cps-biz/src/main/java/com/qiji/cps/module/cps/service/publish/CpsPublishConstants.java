package com.qiji.cps.module.cps.service.publish;

public final class CpsPublishConstants {

    private CpsPublishConstants() {
    }

    public static final class TaskStatus {
        public static final String DRAFT = "DRAFT";
        public static final String ONLINE = "ONLINE";
        public static final String OFFLINE = "OFFLINE";

        private TaskStatus() {
        }
    }

    public static final class SourceType {
        public static final String MANUAL = "MANUAL";

        private SourceType() {
        }
    }

    public static final class ChannelCode {
        public static final String SMS = "SMS";
        public static final String NOTIFY = "NOTIFY";
        public static final String CLIPBOARD = "CLIPBOARD";

        private ChannelCode() {
        }
    }

    public static final class TargetType {
        public static final String SPECIFIED = "SPECIFIED";
        public static final String REBATE_MEMBER = "REBATE_MEMBER";

        private TargetType() {
        }
    }

    public static final class LogStatus {
        public static final String PROCESSING = "PROCESSING";
        public static final String ACCEPTED = "ACCEPTED";
        public static final String PREVIEW = "PREVIEW";
        public static final String SUCCESS = "SUCCESS";
        public static final String FAILED = "FAILED";
        public static final String SKIPPED = "SKIPPED";

        private LogStatus() {
        }
    }

    public static final class Placeholder {
        public static final String MEMBER_ID = "{member_id}";
        public static final String TASK_NAME = "{task_name}";
        public static final String URL = "{url}";

        private Placeholder() {
        }
    }

}
