package com.awareness.app.ui.academy;

import com.awareness.app.model.QuizQuestion;
import com.awareness.app.utils.Constants;

import java.util.ArrayList;
import java.util.List;

/**
 * Built-in questions, used when Firestore has fewer than 3 published quizzes
 * (first run, or offline). Lets the Daily Quiz work on day one.
 * Add your own in Firestore → "quizzes" and they take over automatically.
 */
public final class DefaultQuizBank {

    private DefaultQuizBank() {}

    public static List<QuizQuestion> get() {
        List<QuizQuestion> q = new ArrayList<>();

        q.add(new QuizQuestion(Constants.CATEGORY_PHISHING,
                "A caller says they're from your bank and asks for the OTP code you just received by SMS. What should you do?",
                new String[]{
                        "Read it out so they can verify you",
                        "Hang up and call the number printed on your bank card",
                        "Send the code by text instead",
                        "Ask them to call back later"},
                1,
                "Banks never ask for OTP codes. Scammers trigger a real code and need YOU to read it out. Hang up and call the bank yourself on an official number."));

        q.add(new QuizQuestion(Constants.CATEGORY_PHISHING,
                "Which link is most likely a phishing site?",
                new String[]{
                        "https://www.mybank.com/login",
                        "https://mybank.com/support",
                        "https://mybank.com.secure-login.example.net",
                        "https://help.mybank.com"},
                2,
                "Only the part right before the first single “/” (the real domain) matters. Here the real domain is example.net, and “mybank.com” is just a decoy at the front."));

        q.add(new QuizQuestion(Constants.CATEGORY_VOIP,
                "The caller ID shows the exact number of your bank. Does that prove the call is genuine?",
                new String[]{
                        "Yes, caller ID can't be faked",
                        "Yes, if the call is during business hours",
                        "No, caller ID can be spoofed",
                        "Only if the caller knows your name"},
                2,
                "Scammers can fake caller ID (“spoofing”). Never trust the number on screen. Hang up and call back on a number you looked up yourself."));

        q.add(new QuizQuestion(Constants.CATEGORY_DEEPFAKE,
                "You get a video call from a “relative” asking for urgent money. The face and voice look right. What's the best response?",
                new String[]{
                        "Send the money, it clearly looks like them",
                        "Hang up and call them back on the number you already have",
                        "Ask them to send a selfie",
                        "Transfer half now and half later"},
                1,
                "AI can clone faces and voices. Verify through a different channel you already trust, or ask a question only the real person could answer."));

        q.add(new QuizQuestion(Constants.CATEGORY_ROMANCE,
                "Someone you met online 3 weeks ago says their “insider” crypto platform guarantees big returns and asks you to invest. What is this most likely?",
                new String[]{
                        "A friendly tip from a new partner",
                        "A romance-investment scam",
                        "A normal bank offer",
                        "A government program"},
                1,
                "This is a classic romance-investment scam. Guaranteed returns, a new online “partner”, and pressure to use their platform are all red flags."));

        q.add(new QuizQuestion(Constants.CATEGORY_INVESTMENT,
                "You “win” a lottery you never entered, but must pay a small “processing fee” first. What is going on?",
                new String[]{
                        "A normal admin fee",
                        "An advance-fee scam: the prize doesn't exist",
                        "A tax payment",
                        "A bank promotion"},
                1,
                "Real prizes never require you to pay first. Once you pay, they invent more fees."));

        q.add(new QuizQuestion(Constants.CATEGORY_PHISHING,
                "A text says your parcel couldn't be delivered and includes a shortened link. Safest action?",
                new String[]{
                        "Tap the link and pay the small fee",
                        "Reply STOP",
                        "Ignore the link and check the courier's official app or website",
                        "Forward it to friends as a warning"},
                2,
                "Delivery-fee texts are a very common phishing method. Go to the courier's official app or site yourself instead of tapping links."));

        q.add(new QuizQuestion(Constants.CATEGORY_PHISHING,
                "“Tech support” calls about a virus and asks you to install a remote-control app so they can fix it. What should you do?",
                new String[]{
                        "Install it, they're helping",
                        "Install it but watch the screen",
                        "Hang up, this is a scam",
                        "Give them only your email password"},
                2,
                "Remote-access apps give a stranger full control of your device and banking apps. Real companies don't cold-call about viruses."));

        q.add(new QuizQuestion(Constants.CATEGORY_PHISHING,
                "Besides a strong password, what best protects your account if a scammer steals it?",
                new String[]{
                        "Using the same password everywhere",
                        "Two-factor authentication, and never sharing the codes",
                        "Changing it once a decade",
                        "Writing it in your phone's notes"},
                1,
                "Two-factor authentication blocks most account takeovers, but only if you never hand the codes to anyone."));

        q.add(new QuizQuestion(Constants.CATEGORY_VOIP,
                "A caller claiming to be police says you'll be arrested unless you transfer money right now. What's the right move?",
                new String[]{
                        "Transfer quickly to avoid arrest",
                        "Stay on the line and follow instructions",
                        "Hang up. Real authorities don't demand instant payment by phone",
                        "Give them your ID photo first"},
                2,
                "Urgency and fear are the scammer's tools. Real agencies don't threaten arrest over the phone or demand immediate transfers."));

        return q;
    }
}
