package com.awareness.app.ui.guardian;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.fragment.app.Fragment;

import com.awareness.app.R;
import com.awareness.app.databinding.FragmentGuardianBinding;
import com.awareness.app.network.TelesignResponse;
import com.awareness.app.repository.GuardianRepository;
import com.awareness.app.utils.Constants;
import com.google.android.material.tabs.TabLayout;

/**
 * Guardian Tools tab (the nav graph already points at com.awareness.app.ui.guardian.GuardianFragment).
 *
 *  Tab 0  URL Scanner    -> Google Safe Browsing  (GuardianRepository.scanUrl)
 *  Tab 1  Phone Checker  -> Telesign via Cloud Function (GuardianRepository.checkPhone)
 *
 * All view ids come from res/layout/fragment_guardian.xml.
 */
public class GuardianFragment extends Fragment {

    private FragmentGuardianBinding binding;
    private GuardianRepository repository;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding    = FragmentGuardianBinding.inflate(inflater, container, false);
        repository = GuardianRepository.getInstance();
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupTabs();
        setupUrlScanner();
        setupPhoneChecker();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;   // network callbacks check for null so a late response can't crash
    }

    // ── Tabs ──────────────────────────────────────────────────────

    private void setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                boolean urlTab = tab.getPosition() == 0;
                binding.panelUrlScanner.setVisibility(urlTab ? View.VISIBLE : View.GONE);
                binding.panelPhoneChecker.setVisibility(urlTab ? View.GONE : View.VISIBLE);
                hideKeyboard();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) { }
            @Override public void onTabReselected(TabLayout.Tab tab) { }
        });
    }

    // ══════════════════════════════════════════════════════════════
    //  URL SCANNER
    // ══════════════════════════════════════════════════════════════

    private void setupUrlScanner() {
        binding.btnScanUrl.setOnClickListener(v -> scanUrl());
        binding.etUrl.setOnEditorActionListener((tv, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                scanUrl();
                return true;
            }
            return false;
        });
    }

    private void scanUrl() {
        String input = textOf(binding.etUrl);

        if (input.isEmpty()) {
            binding.tilUrl.setError("Paste a link to scan");
            return;
        }
        if (input.contains(" ") || input.length() > 2000) {
            binding.tilUrl.setError("That doesn't look like a single valid link");
            return;
        }
        binding.tilUrl.setError(null);

        hideKeyboard();
        setUrlLoading(true);
        binding.cardUrlResult.setVisibility(View.GONE);

        repository.scanUrl(input, new GuardianRepository.UrlScanCallback() {
            @Override
            public void onResult(String rating, String threatType) {
                if (binding == null) return;
                setUrlLoading(false);
                showUrlResult(input, rating, threatType);
            }

            @Override
            public void onError(String message) {
                if (binding == null) return;
                setUrlLoading(false);
                binding.tilUrl.setError(message);
            }
        });
    }

    private void setUrlLoading(boolean loading) {
        binding.progressUrl.setIndeterminate(true);
        binding.progressUrl.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnScanUrl.setEnabled(!loading);
    }

    private void showUrlResult(String url, String rating, String threatType) {
        int color = ratingColor(rating);

        binding.imgUrlRatingIcon.setImageResource(ratingIcon(rating));
        ImageViewCompat.setImageTintList(binding.imgUrlRatingIcon, ColorStateList.valueOf(color));

        binding.tvUrlRatingLabel.setText(rating);
        binding.tvUrlRatingLabel.setTextColor(color);
        binding.tvUrlThreatType.setText(describeThreat(threatType));
        binding.tvScannedUrl.setText(url);
        binding.tvUrlAdvice.setText(urlAdvice(rating));

        binding.cardUrlResult.setVisibility(View.VISIBLE);
    }

    private String describeThreat(@Nullable String threatType) {
        if (threatType == null) return "No threats detected";
        switch (threatType) {
            case "MALWARE":            return "Malware — can infect your device";
            case "PHISHING":           return "Phishing — tries to steal your logins or money";
            case "UNWANTED_SOFTWARE":  return "Unwanted software";
            case "POTENTIALLY_HARMFUL_APPLICATION": return "Potentially harmful app";
            default:                   return threatType;
        }
    }

    private String urlAdvice(String rating) {
        switch (rating) {
            case Constants.RATING_MALICIOUS:
                return "Do NOT open this link or enter any information on it. If it arrived in a "
                        + "message or email, delete it, and consider reporting it in the Community tab.";
            case Constants.RATING_SUSPICIOUS:
                return "Google has flagged something unusual about this site. Avoid entering "
                        + "passwords, OTP codes or payment details, and don't download anything from it.";
            default:
                return "Google has no record of this link being dangerous. That does not guarantee "
                        + "it is safe — brand-new scam sites appear every hour. Never enter passwords, "
                        + "OTP codes or bank details because of a link someone sent you.";
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  PHONE CHECKER
    // ══════════════════════════════════════════════════════════════

    private void setupPhoneChecker() {
        binding.btnCheckPhone.setOnClickListener(v -> checkPhone());
        binding.etPhone.setOnEditorActionListener((tv, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                checkPhone();
                return true;
            }
            return false;
        });
    }

    private void checkPhone() {
        String input  = textOf(binding.etPhone);
        String digits = input.replaceAll("[^\\d]", "");

        if (digits.length() < 8 || digits.length() > 15) {
            binding.tilPhone.setError("Enter a valid phone number (e.g. 0912 345 678)");
            return;
        }
        binding.tilPhone.setError(null);

        hideKeyboard();
        setPhoneLoading(true);
        binding.cardPhoneResult.setVisibility(View.GONE);
        binding.cardPhoneAdvice.setVisibility(View.GONE);

        repository.checkPhone(input, new GuardianRepository.PhoneCheckCallback() {
            @Override
            public void onResult(TelesignResponse response) {
                if (binding == null) return;
                setPhoneLoading(false);
                showPhoneResult(response);
            }

            @Override
            public void onError(String message) {
                if (binding == null) return;
                setPhoneLoading(false);
                binding.tilPhone.setError(message);
            }
        });
    }

    private void setPhoneLoading(boolean loading) {
        binding.progressPhone.setIndeterminate(true);
        binding.progressPhone.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnCheckPhone.setEnabled(!loading);
    }

    private void showPhoneResult(TelesignResponse r) {
        String rating = r.getAppRating();   // SAFE / SUSPICIOUS / MALICIOUS / UNKNOWN
        int color = ratingColor(rating);

        binding.imgPhoneRatingIcon.setImageResource(ratingIcon(rating));
        ImageViewCompat.setImageTintList(binding.imgPhoneRatingIcon, ColorStateList.valueOf(color));

        binding.tvPhoneRatingLabel.setText("UNKNOWN".equals(rating) ? "NO RISK DATA" : rating);
        binding.tvPhoneRatingLabel.setTextColor(color);

        // Risk comes from Telesign's Score product. If it isn't enabled on the account the
        // Cloud Function returns risk = null and we say so instead of showing a fake 0.
        binding.tvPhoneRiskScore.setText(r.getRisk() != null
                ? "Risk Score: " + r.getRiskScore() + " / 1000"
                : "Risk score not available");

        binding.tvCarrier.setText(r.getCarrierName());
        binding.tvLineType.setText(r.getPhoneTypeDescription());
        binding.tvVoipStatus.setText(r.isVoip() ? "Yes — internet (VoIP) number" : "No");
        binding.tvCountryCode.setText(r.getCountryCode().isEmpty() ? "—" : r.getCountryCode());

        binding.tvPhoneAdvice.setText(phoneAdvice(rating, r.isVoip()));

        binding.cardPhoneResult.setVisibility(View.VISIBLE);
        binding.cardPhoneAdvice.setVisibility(View.VISIBLE);
    }

    private String phoneAdvice(String rating, boolean voip) {
        String base;
        switch (rating) {
            case Constants.RATING_MALICIOUS:
                base = "This number is considered high risk. Don't share OTP codes, bank details "
                        + "or personal info, and don't call back. Hang up and block it.";
                break;
            case Constants.RATING_SUSPICIOUS:
                base = "This number has some risk signals. Be careful: real banks and government "
                        + "offices never ask for OTP codes or PINs over the phone.";
                break;
            case Constants.RATING_SAFE:
                base = "No major risk signals found. Scammers can still use clean numbers, so "
                        + "never share OTP codes or passwords with a caller.";
                break;
            default:
                base = "We couldn't get a risk score for this number, so treat it with normal "
                        + "caution. Never share OTP codes or passwords with a caller.";
        }
        if (voip) {
            base += " Note: VoIP numbers are cheap to create and often used to hide a caller's identity.";
        }
        return base;
    }

    // ── Shared helpers ────────────────────────────────────────────

    private int ratingColor(String rating) {
        int res;
        switch (rating) {
            case Constants.RATING_SAFE:       res = R.color.rating_safe;       break;
            case Constants.RATING_SUSPICIOUS: res = R.color.rating_suspicious; break;
            case Constants.RATING_MALICIOUS:  res = R.color.rating_malicious;  break;
            default:                          res = R.color.text_secondary;
        }
        return ContextCompat.getColor(requireContext(), res);
    }

    @DrawableRes
    private int ratingIcon(String rating) {
        switch (rating) {
            case Constants.RATING_SAFE:       return R.drawable.ic_check_circle;
            case Constants.RATING_SUSPICIOUS: return R.drawable.ic_warning;
            case Constants.RATING_MALICIOUS:  return R.drawable.ic_cancel;
            default:                          return R.drawable.ic_info;
        }
    }

    private String textOf(TextView view) {
        return view.getText() == null ? "" : view.getText().toString().trim();
    }

    private void hideKeyboard() {
        if (!isAdded()) return;
        View focus = requireActivity().getCurrentFocus();
        if (focus == null) return;
        InputMethodManager imm = (InputMethodManager)
                requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
    }
}
