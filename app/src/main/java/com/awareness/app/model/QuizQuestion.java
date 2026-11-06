package com.awareness.app.model;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.Exclude;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * One multiple-choice question in the "quizzes" Firestore collection.
 *
 * Document shape (add these in Firebase Console → Firestore → quizzes):
 *   question     string   "A caller asks for the OTP you just received…"
 *   options      array    ["Read it out", "Hang up", ...]      (2 to 4 strings)
 *   correctIndex number   1                                    (0-based index into options)
 *   explanation  string   shown after answering
 *   category     string   optional, e.g. "Phishing"
 *   published    boolean  true = eligible for the daily quiz
 */
public class QuizQuestion {

    @DocumentId
    private String id;

    private String question;
    private List<String> options = new ArrayList<>();
    private int correctIndex;
    private String explanation;
    private String category;
    private boolean published = true;

    public QuizQuestion() {}   // required by Firestore

    public QuizQuestion(String category, String question, String[] options,
                        int correctIndex, String explanation) {
        this.id          = "default_" + question.hashCode();
        this.category    = category;
        this.question    = question;
        this.options     = new ArrayList<>(Arrays.asList(options));
        this.correctIndex = correctIndex;
        this.explanation = explanation;
    }

    /** A malformed question from Firestore is skipped instead of crashing the quiz. */
    @Exclude   // not a stored field
    public boolean isValid() {
        return question != null && !question.trim().isEmpty()
                && options != null && options.size() >= 2 && options.size() <= 4
                && correctIndex >= 0 && correctIndex < options.size();
    }

    public String getId()                       { return id; }
    public void   setId(String id)              { this.id = id; }

    public String getQuestion()                 { return question; }
    public void   setQuestion(String question)  { this.question = question; }

    public List<String> getOptions()            { return options; }
    public void setOptions(List<String> options){ this.options = options; }

    public int  getCorrectIndex()               { return correctIndex; }
    public void setCorrectIndex(int i)          { this.correctIndex = i; }

    public String getExplanation()              { return explanation; }
    public void   setExplanation(String e)      { this.explanation = e; }

    public String getCategory()                 { return category; }
    public void   setCategory(String category) { this.category = category; }

    public boolean isPublished()                { return published; }
    public void    setPublished(boolean p)      { this.published = p; }
}
