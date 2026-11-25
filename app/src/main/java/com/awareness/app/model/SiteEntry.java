package com.awareness.app.model;

import com.google.firebase.firestore.DocumentId;

import java.util.Date;

/**
 * One checked website in a user's private lists (users/{uid}/site_lists/{hash}) or in the server's
 * admin-only list (server_site_lists/{hash}).
 *
 * The document id is the SHA-256 (64 hex characters) of the stored `url`, because a URL contains
 * characters that can't be used in a document id. Checking the same address again therefore updates
 * the entry instead of creating a duplicate.
 *
 * Only scheme + host + path are stored: the query string (?a=b) and fragment (#x) are dropped, because
 * links often carry private tokens (password resets, session ids) that must not end up in a database.
 */
public class SiteEntry {

    @DocumentId
    private String id;                    // sha-256 of url

    private String url;                   // "https://example.com/login"
    private String host;                  // "example.com"
    private String list;                  // black / gray / white  (same values as PhoneEntry)
    private String rating;                // MALICIOUS / SUSPICIOUS / SAFE
    private String threatType;            // MALWARE / PHISHING / UNWANTED_SOFTWARE / …; null if none found
    private Date   checkedAt;             // user list: last check. server list: when it was first added
    private Long   checkCount;            // user list: how many times checked. server list: always 1

    public SiteEntry() {}                 // required by Firestore

    public String getId()                      { return id; }
    public void   setId(String id)             { this.id = id; }

    public String getUrl()                     { return url; }
    public void   setUrl(String url)           { this.url = url; }

    public String getHost()                    { return host; }
    public void   setHost(String host)         { this.host = host; }

    public String getList()                    { return list; }
    public void   setList(String list)         { this.list = list; }

    public String getRating()                  { return rating; }
    public void   setRating(String rating)     { this.rating = rating; }

    public String getThreatType()              { return threatType; }
    public void   setThreatType(String t)      { this.threatType = t; }

    public Date   getCheckedAt()               { return checkedAt; }
    public void   setCheckedAt(Date checkedAt) { this.checkedAt = checkedAt; }

    public Long   getCheckCount()              { return checkCount; }
    public void   setCheckCount(Long c)        { this.checkCount = c; }
}
