package com.awareness.app.network;

import java.util.Arrays;
import java.util.List;

public class SafeBrowsingRequest {

    private ClientInfo   client;
    private ThreatInfo   threatInfo;

    // ── Constructor — builds a ready-to-send request for one URL ──
    public SafeBrowsingRequest(String url) {
        this.client = new ClientInfo("awareness-app", "1.0");
        this.threatInfo = new ThreatInfo(
                Arrays.asList(
                        "MALWARE",
                        "SOCIAL_ENGINEERING",        // Phishing
                        "UNWANTED_SOFTWARE",
                        "POTENTIALLY_HARMFUL_APPLICATION"
                ),
                Arrays.asList("ANY_PLATFORM"),
                Arrays.asList("URL"),
                Arrays.asList(new ThreatEntry(url))
        );
    }

    // ── Inner classes (Gson serializes these as nested JSON) ──────

    public static class ClientInfo {
        private String clientId;
        private String clientVersion;
        ClientInfo(String id, String version) {
            this.clientId = id; this.clientVersion = version;
        }
    }

    public static class ThreatInfo {
        private List<String>       threatTypes;
        private List<String>       platformTypes;
        private List<String>       threatEntryTypes;
        private List<ThreatEntry>  threatEntries;

        ThreatInfo(List<String> tTypes, List<String> pTypes,
                   List<String> eTypes, List<ThreatEntry> entries) {
            this.threatTypes      = tTypes;
            this.platformTypes    = pTypes;
            this.threatEntryTypes = eTypes;
            this.threatEntries    = entries;
        }
    }

    public static class ThreatEntry {
        private String url;
        ThreatEntry(String url) { this.url = url; }
    }
}
