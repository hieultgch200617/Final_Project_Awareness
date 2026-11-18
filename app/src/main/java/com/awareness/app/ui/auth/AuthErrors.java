package com.awareness.app.ui.auth;

import android.util.Log;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;

import java.util.Locale;

/**
 * Turns a Firebase sign-in / sign-up failure into a message that says what actually went wrong.
 *
 * The old code matched fragments of Firebase's English error text, which newer Firebase versions
 * no longer produce (e.g. a wrong password now comes back as "ERROR_INVALID_CREDENTIAL"), so almost
 * everything fell through to a generic "Login failed". Every failure is also written to Logcat under
 * the tag "AwarenessAuth" so it can be diagnosed.
 */
final class AuthErrors {

    private static final String TAG = "AwarenessAuth";

    private AuthErrors() {}

    static String describe(Exception e, boolean registering) {
        Log.w(TAG, (registering ? "Registration" : "Sign-in") + " failed: "
                + e.getClass().getSimpleName() + ": " + e.getMessage(), e);

        String raw = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.US);

        // Setup problems (show up as a hang or an odd error, so name them plainly)
        if (raw.contains("api key not valid") || raw.contains("api_key_invalid")
                || raw.contains("are blocked") || raw.contains("api_key_service_blocked")) {
            return "The app's Firebase API key was rejected. Check the key restrictions in "
                    + "Google Cloud Console → APIs & Services → Credentials.";
        }
        if (raw.contains("app check") || raw.contains("app attestation")) {
            return "Blocked by Firebase App Check. Turn enforcement off while developing.";
        }

        if (e instanceof FirebaseNetworkException) {
            return "Can't reach the server. Check your internet connection. School or public "
                    + "Wi-Fi can block Firebase, so try mobile data.";
        }
        if (e instanceof FirebaseTooManyRequestsException) {
            return "Too many attempts. Please wait a few minutes and try again.";
        }
        if (e instanceof FirebaseAuthUserCollisionException) {
            return "This email is already registered. Try signing in instead.";
        }
        if (e instanceof FirebaseAuthWeakPasswordException) {
            return "That password is too weak. Use at least 6 characters.";
        }
        if (e instanceof FirebaseAuthInvalidUserException) {
            String code = ((FirebaseAuthInvalidUserException) e).getErrorCode();
            return "ERROR_USER_DISABLED".equals(code)
                    ? "This account has been disabled."
                    : "No account found with this email.";
        }
        if (e instanceof FirebaseAuthInvalidCredentialsException) {
            String code = ((FirebaseAuthInvalidCredentialsException) e).getErrorCode();
            if ("ERROR_INVALID_EMAIL".equals(code)) return "Please enter a valid email address.";
            return registering
                    ? "Please check the email address and password."
                    : "Incorrect email or password.";
        }
        if (e instanceof FirebaseAuthException) {
            String code = ((FirebaseAuthException) e).getErrorCode();
            if ("ERROR_OPERATION_NOT_ALLOWED".equals(code)) {
                return "Email/password sign-in isn't switched on in Firebase yet "
                        + "(Firebase Console → Authentication → Sign-in method).";
            }
            return (registering ? "Couldn't create the account" : "Couldn't sign in")
                    + " (" + code + ").";
        }
        return "Something went wrong (" + e.getClass().getSimpleName() + "). Please try again.";
    }
}
