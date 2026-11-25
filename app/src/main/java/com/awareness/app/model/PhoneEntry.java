package com.awareness.app.model;

import com.google.firebase.firestore.DocumentId;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * One checked phone number in a user's private lists:  users/{uid}/phone_lists/{digits}
 *
 * list:  "black" (rated MALICIOUS), "gray" (SUSPICIOUS) or "white" (SAFE)
 * The document id is the number's digits (e.g. 84912345678), so checking the same number again
 * updates this entry (and may move it to another list) instead of creating a duplicate.
 */
public class PhoneEntry {

    public static final String LIST_BLACK = "black";
    public static final String LIST_GRAY  = "gray";
    public static final String LIST_WHITE = "white";

    @DocumentId
    private String id;                       // digits only

    private String number;                   // "+84912345678"
    private String list;                     // black / gray / white
    private String rating;                   // MALICIOUS / SUSPICIOUS / SAFE
    private Long   fraudScore;               // 0-100 from IPQualityScore; null when not provided
    private String carrier;
    private String lineType;
    private String countryCode;              // "+84"
    private boolean voip;
    private List<String> signals = new ArrayList<>();   // warning signs found, in plain words
    private Date   checkedAt;                // last time it was checked
    private Long   checkCount;               // how many times it was checked

    public PhoneEntry() {}                   // required by Firestore

    public String getId()                        { return id; }
    public void   setId(String id)               { this.id = id; }

    public String getNumber()                    { return number; }
    public void   setNumber(String number)       { this.number = number; }

    public String getList()                      { return list; }
    public void   setList(String list)           { this.list = list; }

    public String getRating()                    { return rating; }
    public void   setRating(String rating)       { this.rating = rating; }

    public Long   getFraudScore()                { return fraudScore; }
    public void   setFraudScore(Long fraudScore) { this.fraudScore = fraudScore; }

    public String getCarrier()                   { return carrier; }
    public void   setCarrier(String carrier)     { this.carrier = carrier; }

    public String getLineType()                  { return lineType; }
    public void   setLineType(String lineType)   { this.lineType = lineType; }

    public String getCountryCode()               { return countryCode; }
    public void   setCountryCode(String c)       { this.countryCode = c; }

    public boolean isVoip()                      { return voip; }
    public void    setVoip(boolean voip)         { this.voip = voip; }

    public List<String> getSignals()             { return signals; }
    public void setSignals(List<String> signals) { this.signals = signals; }

    public Date   getCheckedAt()                 { return checkedAt; }
    public void   setCheckedAt(Date checkedAt)   { this.checkedAt = checkedAt; }

    public Long   getCheckCount()                { return checkCount; }
    public void   setCheckCount(Long checkCount) { this.checkCount = checkCount; }
}
