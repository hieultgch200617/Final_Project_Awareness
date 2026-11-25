package com.awareness.app.ui.guardian;

import android.content.Context;

/**
 * Old name, kept so any leftover reference still compiles. The lists sheet now covers phone numbers
 * AND websites and lives in {@link ListsSheet}. You can delete this file once nothing uses it.
 */
@Deprecated
public final class PhoneListsSheet {

    private PhoneListsSheet() {}

    public static void show(Context context) {
        ListsSheet.show(context, ListsSheet.TYPE_PHONES);
    }

    public static void showServer(Context context) {
        ListsSheet.showServer(context);
    }
}
