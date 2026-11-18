package com.awareness.app.model;

import com.google.firebase.firestore.DocumentId;
import java.util.List;

public class ScamScenario {

    @DocumentId
    private String scenarioId;

    private String title;           // e.g. "Suspicious Bank SMS"
    private String category;        // e.g. "Phishing", "VoIP / Caller ID"
    private String scenarioType;    // "sms", "email", "webpage"
    private String senderLabel;     // e.g. "Vietcombank" or "+84 912 345 678"
    private String message;         // The full body text of the fake scam message
    private int    xpReward;        // Base XP for completing (bonus per flag found)
    private List<RedFlag> redFlags; // The hidden suspicious elements
    private boolean published;     // Only published scenarios are offered to players

    // ── Required no-arg constructor ──────────────────────────────
    public ScamScenario() {}

    // ── Inner class: RedFlag ─────────────────────────────────────
    // A RedFlag is a suspicious segment inside the message text.
    //
    // Two ways to say WHERE it is:
    //   1. text  (recommended): the exact words, e.g. "http://vcb-secure.tk/verify".
    //      The game finds them in the message itself, so you never have to count characters.
    //   2. start / end: character indices into the message (the original way).
    //      Easy to get wrong by a few characters, which makes the "right" word count as a mistake.
    // If `text` is set and found in the message, it wins over start/end.
    public static class RedFlag {
        private int    start;           // Start index in message text
        private int    end;             // End index in message text
        private String text;            // OPTIONAL: exact words of the flag (see above)
        private String label;           // Short name e.g. "Suspicious URL"
        private String explanation;     // Full explanation shown after submit

        public RedFlag() {}

        public int    getStart()                   { return start; }
        public void   setStart(int start)          { this.start = start; }

        public int    getEnd()                     { return end; }
        public void   setEnd(int end)              { this.end = end; }

        public String getText()                    { return text; }
        public void   setText(String text)         { this.text = text; }

        public String getLabel()                   { return label; }
        public void   setLabel(String label)       { this.label = label; }

        public String getExplanation()                         { return explanation; }
        public void   setExplanation(String explanation)       { this.explanation = explanation; }
    }

    // ── Getters & Setters ────────────────────────────────────────

    public String getScenarioId()                          { return scenarioId; }
    public void   setScenarioId(String scenarioId)         { this.scenarioId = scenarioId; }

    public String getTitle()                               { return title; }
    public void   setTitle(String title)                   { this.title = title; }

    public String getCategory()                            { return category; }
    public void   setCategory(String category)             { this.category = category; }

    public String getScenarioType()                        { return scenarioType; }
    public void   setScenarioType(String scenarioType)     { this.scenarioType = scenarioType; }

    public String getSenderLabel()                         { return senderLabel; }
    public void   setSenderLabel(String senderLabel)       { this.senderLabel = senderLabel; }

    public String getMessage()                             { return message; }
    public void   setMessage(String message)               { this.message = message; }

    public int    getXpReward()                            { return xpReward; }
    public void   setXpReward(int xpReward)                { this.xpReward = xpReward; }

    public boolean isPublished()                           { return published; }
    public void    setPublished(boolean published)         { this.published = published; }

    public List<RedFlag> getRedFlags()                     { return redFlags; }
    public void          setRedFlags(List<RedFlag> flags)  { this.redFlags = flags; }
}
