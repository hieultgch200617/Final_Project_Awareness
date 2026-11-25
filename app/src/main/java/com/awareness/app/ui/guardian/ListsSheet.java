package com.awareness.app.ui.guardian;

import android.content.Context;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.awareness.app.adapter.PhoneEntryAdapter;
import com.awareness.app.adapter.SiteEntryAdapter;
import com.awareness.app.databinding.SheetListsBinding;
import com.awareness.app.model.PhoneEntry;
import com.awareness.app.model.SiteEntry;
import com.awareness.app.utils.Constants;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

/**
 * Black / Gray / White lists, with TWO tabs inside each list: 📞 Phone numbers and 🌐 Websites.
 *
 *   user lists   : users/{uid}/phone_lists  and  users/{uid}/site_lists     (private to the user)
 *   server lists : server_phone_lists       and  server_site_lists          (admins only)
 *
 * Usage:
 *   ListsSheet.show(requireContext(), ListsSheet.TYPE_PHONES);   // the user's own lists
 *   ListsSheet.show(requireContext(), ListsSheet.TYPE_SITES);
 *   ListsSheet.showServer(requireContext());                      // admins: the server's lists
 */
public final class ListsSheet {

    public static final int TYPE_PHONES = 0;
    public static final int TYPE_SITES  = 1;

    private static final String[] LISTS = {
            PhoneEntry.LIST_BLACK, PhoneEntry.LIST_GRAY, PhoneEntry.LIST_WHITE};
    private static final String[] TITLES = {"⛔ Blacklist", "⚠️ Graylist", "✅ Whitelist"};
    private static final String[] LIST_NAMES = {"Blacklist", "Graylist", "Whitelist"};

    private final Context context;
    private final boolean serverMode;
    private final CollectionReference phoneCollection;
    private final CollectionReference siteCollection;
    private final SheetListsBinding b;
    private final BottomSheetDialog dialog;
    private final PhoneEntryAdapter phoneAdapter;
    private final SiteEntryAdapter siteAdapter;

    private ListenerRegistration phoneRegistration;
    private ListenerRegistration siteRegistration;
    private List<PhoneEntry> phones = new ArrayList<>();
    private List<SiteEntry> sites = new ArrayList<>();
    private int selectedList = 0;
    private int selectedType;
    private boolean phonesLoaded = false;
    private boolean sitesLoaded = false;
    private String phonesError = null;
    private String sitesError = null;

    // ── Entry points ──────────────────────────────────────────────

    public static void show(Context context, int startOnType) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(context, "Please sign in first.", Toast.LENGTH_SHORT).show();
            return;
        }
        UserCollections me = new UserCollections(user.getUid());
        new ListsSheet(context, me.phoneLists(), me.siteLists(), false, startOnType).open();
    }

    /** Admins only (the Firestore rules enforce it). */
    public static void showServer(Context context) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        new ListsSheet(context, db.collection("server_phone_lists"),
                db.collection("server_site_lists"), true, TYPE_PHONES).open();
    }

    /** Builds the user's two private collections in one place. */
    private static final class UserCollections {
        private final String uid;
        UserCollections(String uid) { this.uid = uid; }
        CollectionReference phoneLists() {
            return FirebaseFirestore.getInstance().collection(Constants.COLLECTION_USERS)
                    .document(uid).collection("phone_lists");
        }
        CollectionReference siteLists() {
            return FirebaseFirestore.getInstance().collection(Constants.COLLECTION_USERS)
                    .document(uid).collection("site_lists");
        }
    }

    private ListsSheet(Context context, CollectionReference phoneCollection,
                       CollectionReference siteCollection, boolean serverMode, int startOnType) {
        this.context = context;
        this.phoneCollection = phoneCollection;
        this.siteCollection = siteCollection;
        this.serverMode = serverMode;
        this.selectedType = startOnType == TYPE_SITES ? TYPE_SITES : TYPE_PHONES;

        b = SheetListsBinding.inflate(LayoutInflater.from(context));
        dialog = new BottomSheetDialog(context);
        dialog.setContentView(b.getRoot());

        if (serverMode) {
            b.tvSheetTitle.setText("🗂 Server lists");
            b.tvSheetDesc.setText("Every phone number and website any user has checked, added once with the "
                    + "first result seen. Only admins can see this.");
        }

        phoneAdapter = new PhoneEntryAdapter(new PhoneEntryAdapter.Listener() {
            @Override public void onOpen(PhoneEntry entry)   { showPhoneDetails(entry); }
            @Override public void onRemove(PhoneEntry entry) { confirmRemovePhone(entry); }
        }, serverMode);
        siteAdapter = new SiteEntryAdapter(new SiteEntryAdapter.Listener() {
            @Override public void onOpen(SiteEntry entry)   { showSiteDetails(entry); }
            @Override public void onRemove(SiteEntry entry) { confirmRemoveSite(entry); }
        }, serverMode);

        b.rvEntries.setLayoutManager(new LinearLayoutManager(context));
        b.btnClose.setOnClickListener(v -> dialog.dismiss());

        b.tabLists.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) { selectedList = tab.getPosition(); refresh(); }
            @Override public void onTabUnselected(TabLayout.Tab tab) { }
            @Override public void onTabReselected(TabLayout.Tab tab) { }
        });
        b.tabTypes.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) { selectedType = tab.getPosition(); refresh(); }
            @Override public void onTabUnselected(TabLayout.Tab tab) { }
            @Override public void onTabReselected(TabLayout.Tab tab) { }
        });

        // Start on the requested tab (Phone numbers or Websites)
        TabLayout.Tab startTab = b.tabTypes.getTabAt(selectedType);
        if (startTab != null) startTab.select();
    }

    private void open() {
        dialog.setOnShowListener(d -> expandFully());
        dialog.setOnDismissListener(d -> {                 // stop listening when closed
            if (phoneRegistration != null) phoneRegistration.remove();
            if (siteRegistration != null) siteRegistration.remove();
        });
        startListening();
        refresh();
        dialog.show();
    }

    /** Open at full height instead of the half-height default. */
    private void expandFully() {
        View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet == null) return;
        sheet.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
        BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(sheet);
        behavior.setSkipCollapsed(true);
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    // ── Data ──────────────────────────────────────────────────────

    private void startListening() {
        phoneRegistration = phoneCollection
                .orderBy("checkedAt", Query.Direction.DESCENDING)      // newest first
                .limit(300)
                .addSnapshotListener((snap, e) -> {
                    phonesLoaded = true;
                    if (e != null || snap == null) {
                        phonesError = friendlyError(e);
                        refresh();
                        return;
                    }
                    phonesError = null;
                    List<PhoneEntry> list = new ArrayList<>();
                    for (DocumentSnapshot doc : snap.getDocuments()) {   // one by one: an odd document can't break the list
                        try {
                            PhoneEntry entry = doc.toObject(PhoneEntry.class);
                            if (entry != null) list.add(entry);
                        } catch (RuntimeException ignored) { }
                    }
                    phones = list;
                    refresh();
                });

        siteRegistration = siteCollection
                .orderBy("checkedAt", Query.Direction.DESCENDING)
                .limit(300)
                .addSnapshotListener((snap, e) -> {
                    sitesLoaded = true;
                    if (e != null || snap == null) {
                        sitesError = friendlyError(e);
                        refresh();
                        return;
                    }
                    sitesError = null;
                    List<SiteEntry> list = new ArrayList<>();
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        try {
                            SiteEntry entry = doc.toObject(SiteEntry.class);
                            if (entry != null) list.add(entry);
                        } catch (RuntimeException ignored) { }
                    }
                    sites = list;
                    refresh();
                });
    }

    private String friendlyError(@Nullable Exception e) {
        String msg = e != null ? e.getMessage() : "unknown error";
        if (serverMode && msg != null && msg.toLowerCase().contains("permission")) {
            return "Only admins can see the server lists.";
        }
        return msg;
    }

    /** Sorts everything into the 3 lists and 2 types, then updates tab counts, rows and messages. */
    private void refresh() {
        int[] phoneCounts = new int[LISTS.length];
        int[] siteCounts  = new int[LISTS.length];
        List<PhoneEntry> visiblePhones = new ArrayList<>();
        List<SiteEntry>  visibleSites  = new ArrayList<>();

        for (PhoneEntry entry : phones) {
            int i = indexOfList(entry.getList());
            if (i < 0) continue;
            phoneCounts[i]++;
            if (i == selectedList) visiblePhones.add(entry);
        }
        for (SiteEntry entry : sites) {
            int i = indexOfList(entry.getList());
            if (i < 0) continue;
            siteCounts[i]++;
            if (i == selectedList) visibleSites.add(entry);
        }

        // Level 1 titles: total in that list (numbers + websites)
        for (int i = 0; i < LISTS.length; i++) {
            TabLayout.Tab tab = b.tabLists.getTabAt(i);
            if (tab != null) tab.setText(TITLES[i] + " (" + (phoneCounts[i] + siteCounts[i]) + ")");
        }
        // Level 2 titles: counts inside the selected list
        TabLayout.Tab phoneTab = b.tabTypes.getTabAt(TYPE_PHONES);
        TabLayout.Tab siteTab  = b.tabTypes.getTabAt(TYPE_SITES);
        if (phoneTab != null) phoneTab.setText("📞 Phone numbers (" + phoneCounts[selectedList] + ")");
        if (siteTab != null)  siteTab.setText("🌐 Websites (" + siteCounts[selectedList] + ")");

        boolean showingPhones = selectedType == TYPE_PHONES;
        if (showingPhones) {
            b.rvEntries.setAdapter(phoneAdapter);
            phoneAdapter.submit(visiblePhones);
        } else {
            b.rvEntries.setAdapter(siteAdapter);
            siteAdapter.submit(visibleSites);
        }

        boolean loaded = showingPhones ? phonesLoaded : sitesLoaded;
        String error   = showingPhones ? phonesError : sitesError;
        int visible    = showingPhones ? visiblePhones.size() : visibleSites.size();

        b.progress.setVisibility(loaded ? View.GONE : View.VISIBLE);
        if (error != null) {
            b.tvEmpty.setText("Couldn't load the lists.\n" + error);
            b.tvEmpty.setVisibility(View.VISIBLE);
        } else if (loaded && visible == 0) {
            b.tvEmpty.setText(emptyText());
            b.tvEmpty.setVisibility(View.VISIBLE);
        } else {
            b.tvEmpty.setVisibility(View.GONE);
        }
    }

    private static int indexOfList(String list) {
        for (int i = 0; i < LISTS.length; i++) if (LISTS[i].equals(list)) return i;
        return -1;
    }

    private String emptyText() {
        String what = selectedType == TYPE_PHONES ? "phone numbers" : "websites";
        String rating = selectedList == 0 ? "MALICIOUS" : selectedList == 1 ? "SUSPICIOUS" : "SAFE";
        String name = LIST_NAMES[selectedList];
        return serverMode
                ? "The server " + name + " has no " + what + " yet.\n"
                    + "They appear here the first time any user checks one rated " + rating + "."
                : "Your " + name + " has no " + what + " yet.\n"
                    + "Checked " + what + " rated " + rating + " appear here.";
    }

    // ── Details ───────────────────────────────────────────────────

    private void showPhoneDetails(PhoneEntry e) {
        int index = indexOfList(e.getList());
        long times = e.getCheckCount() != null ? e.getCheckCount() : 1;
        String when = relative(e.getCheckedAt());

        StringBuilder sb = new StringBuilder();
        sb.append("List:  ").append(index >= 0 ? LIST_NAMES[index] : "—").append('\n');
        sb.append("Result:  ").append(e.getRating() != null ? e.getRating() : "—").append('\n');
        sb.append("Fraud score:  ")
                .append(e.getFraudScore() != null ? e.getFraudScore() + " / 100" : "not available").append('\n');
        sb.append("Carrier:  ").append(nonEmpty(e.getCarrier())).append('\n');
        sb.append("Line type:  ").append(nonEmpty(e.getLineType())).append('\n');
        sb.append("Country code:  ").append(nonEmpty(e.getCountryCode())).append('\n');
        sb.append("VoIP:  ").append(e.isVoip() ? "Yes" : "No").append('\n');

        sb.append("Warning signs:  ");
        if (e.getSignals() == null || e.getSignals().isEmpty()) sb.append("none found");
        else sb.append(TextUtils.join("; ", e.getSignals()));

        appendWhen(sb, times, when);

        new MaterialAlertDialogBuilder(context)
                .setTitle(e.getNumber() != null ? e.getNumber() : e.getId())
                .setMessage(sb.toString())
                .setPositiveButton("Close", null)
                .show();
    }

    private void showSiteDetails(SiteEntry e) {
        int index = indexOfList(e.getList());
        long times = e.getCheckCount() != null ? e.getCheckCount() : 1;
        String when = relative(e.getCheckedAt());

        StringBuilder sb = new StringBuilder();
        sb.append("List:  ").append(index >= 0 ? LIST_NAMES[index] : "—").append('\n');
        sb.append("Result:  ").append(e.getRating() != null ? e.getRating() : "—").append('\n');
        sb.append("Threat:  ").append(SiteEntryAdapter.describeThreat(e.getThreatType())).append('\n');
        sb.append("Website:  ").append(nonEmpty(e.getHost())).append('\n');
        sb.append("Address:  ").append(nonEmpty(e.getUrl())).append('\n');
        sb.append("\nRated by Google Safe Browsing when it was checked. “Safe” only means the address wasn't "
                + "on Google's lists at that moment, not that it is guaranteed safe. "
                + "Only the address up to the path is saved: the part after “?” is never stored.");

        appendWhen(sb, times, when);

        new MaterialAlertDialogBuilder(context)
                .setTitle(e.getHost() != null ? e.getHost() : "Website")
                .setMessage(sb.toString())
                .setPositiveButton("Close", null)
                .show();
    }

    private void appendWhen(StringBuilder sb, long times, String when) {
        if (serverMode) {
            sb.append("\n\nAdded to the server list ").append(when)
                    .append(". It keeps the first result seen and isn't updated by later checks.");
        } else {
            sb.append("\n\nChecked ").append(times).append(times == 1 ? " time" : " times")
                    .append(", last ").append(when).append('.');
        }
    }

    // ── Removing ──────────────────────────────────────────────────

    private void confirmRemovePhone(PhoneEntry e) {
        if (e.getId() == null) return;
        confirmRemove(e.getNumber() != null ? e.getNumber() : e.getId(),
                () -> phoneCollection.document(e.getId()).delete().addOnFailureListener(this::removeFailed));
    }

    private void confirmRemoveSite(SiteEntry e) {
        if (e.getId() == null) return;
        confirmRemove(e.getUrl() != null ? e.getUrl() : e.getId(),
                () -> siteCollection.document(e.getId()).delete().addOnFailureListener(this::removeFailed));
    }

    private void confirmRemove(String what, Runnable remove) {
        new MaterialAlertDialogBuilder(context)
                .setTitle("Remove this entry?")
                .setMessage(what + (serverMode
                        ? " will be removed from the server list. The next time any user checks it, it will be added again."
                        : " will be removed from your lists. If you check it again, it will be added back."))
                .setPositiveButton("Remove", (d, w) -> remove.run())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void removeFailed(Exception err) {
        Toast.makeText(context, "Couldn't remove it: " + err.getMessage(), Toast.LENGTH_SHORT).show();
    }

    // ── Helpers ───────────────────────────────────────────────────

    private static String relative(java.util.Date date) {
        return date != null
                ? DateUtils.getRelativeTimeSpanString(date.getTime(), System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS).toString()
                : "just now";
    }

    private static String nonEmpty(String s) {
        return s == null || s.isEmpty() ? "—" : s;
    }
}
