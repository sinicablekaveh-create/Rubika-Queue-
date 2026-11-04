package com.sinicable.remotesender;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class MainActivity extends Activity {
    private static final int PICK_XLSX = 11;
    private static final int PICK_IMAGE = 12;
    private static final int CONTACT_PERMISSION = 13;

    private QueueStore store;
    private TextView status;
    private TextView current;
    private TextView imageStatus;

    private EditText message;
    private EditText baleMinutes;
    private EditText eitaaMinutes;
    private EditText rubikaMinutes;
    private EditText testPhone;

    private CheckBox bale;
    private CheckBox eitaa;
    private CheckBox rubika;

    private Uri selectedImage;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        store = new QueueStore(this);
        selectedImage = store.imageUri();
        setContentView(buildUi());
        refresh();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(28));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        scroll.addView(root);

        TextView title = text("Remote Sender 1.1", 27, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView subtitle = text(
                "Bale • Eitaa • Rubika | opt-in XLSX queue",
                14,
                false
        );
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle);

        status = text("", 14, true);
        root.addView(status);

        section(root, "1) Excel contacts");
        TextView excelHelp = text(
                "Required columns: phone + opt_in. Only rows with yes / true / 1 are imported.",
                13,
                false
        );
        root.addView(excelHelp);

        Button importButton = button("Import XLSX");
        importButton.setOnClickListener(v -> chooseXlsx());
        root.addView(importButton);

        testPhone = edit("+989106119487", "Optional single approved recipient");
        root.addView(testPhone);

        Button addButton = button("Add approved recipient manually");
        addButton.setOnClickListener(v -> {
            String normalized = PhoneNormalizer.normalize(testPhone.getText().toString());
            if (normalized == null) {
                toast("Invalid phone number.");
                return;
            }
            store.add(normalized);
            toast("Added: " + normalized);
            refresh();
        });
        root.addView(addButton);

        section(root, "2) Messenger targets");
        bale = check("Bale / بله", true);
        eitaa = check("Eitaa / ایتا", true);
        rubika = check("Rubika / روبیکا", true);
        root.addView(bale);
        root.addView(eitaa);
        root.addView(rubika);

        section(root, "3) Message");
        message = edit(store.message(), "Message text");
        message.setMinLines(4);
        root.addView(message);

        imageStatus = text("", 13, false);
        root.addView(imageStatus);

        Button imageButton = button("Choose optional image");
        imageButton.setOnClickListener(v -> chooseImage());
        root.addView(imageButton);

        Button clearImage = button("Remove image");
        clearImage.setOnClickListener(v -> {
            selectedImage = null;
            refresh();
        });
        root.addView(clearImage);

        section(root, "4) Minutes between sends");
        baleMinutes = numberEdit(String.valueOf(store.interval(QueueStore.BALE)), "Bale minutes");
        eitaaMinutes = numberEdit(String.valueOf(store.interval(QueueStore.EITAA)), "Eitaa minutes");
        rubikaMinutes = numberEdit(String.valueOf(store.interval(QueueStore.RUBIKA)), "Rubika minutes");
        root.addView(baleMinutes);
        root.addView(eitaaMinutes);
        root.addView(rubikaMinutes);

        section(root, "5) Queue");
        Button buildQueue = button("BUILD / RESTART QUEUE");
        buildQueue.setTypeface(Typeface.DEFAULT_BOLD);
        buildQueue.setOnClickListener(v -> buildQueue());
        root.addView(buildQueue);

        current = text("", 17, true);
        root.addView(current);

        Button prepare = button("PREPARE NEXT MESSAGE");
        prepare.setTextSize(18);
        prepare.setTypeface(Typeface.DEFAULT_BOLD);
        prepare.setOnClickListener(v -> prepareCurrent());
        root.addView(prepare);

        Button sent = button("MARK SENT");
        sent.setOnClickListener(v -> {
            QueueStore.Selection selection = store.selection();
            if (selection.task == null) {
                toast("Queue is empty.");
                return;
            }
            store.complete(selection.task, "SENT", true);
            refresh();
        });
        root.addView(sent);

        Button skip = button("SKIP CURRENT");
        skip.setOnClickListener(v -> {
            QueueStore.Selection selection = store.selection();
            if (selection.task == null) {
                toast("Queue is empty.");
                return;
            }
            store.complete(selection.task, "SKIPPED", false);
            refresh();
        });
        root.addView(skip);

        Button clear = button("CLEAR QUEUE");
        clear.setOnClickListener(v -> {
            store.clear();
            refresh();
        });
        root.addView(clear);

        Button refreshButton = button("Refresh status");
        refreshButton.setOnClickListener(v -> refresh());
        root.addView(refreshButton);

        section(root, "Installed apps");
        root.addView(text(
                "Bale: " + appState("ir.nasim")
                        + "\nEitaa: " + appState("ir.eitaa.messenger")
                        + "\nRubika: " + appState("ir.resaneh1.iptv"),
                13,
                false
        ));

        return scroll;
    }

    private void buildQueue() {
        String body = message.getText().toString().trim();
        if (body.isEmpty()) {
            toast("Message text is required.");
            return;
        }
        if (store.numbers().isEmpty()) {
            toast("Import approved recipients first.");
            return;
        }

        int baleGap = parseMinutes(baleMinutes);
        int eitaaGap = parseMinutes(eitaaMinutes);
        int rubikaGap = parseMinutes(rubikaMinutes);

        if (baleGap < 1 || eitaaGap < 1 || rubikaGap < 1) {
            toast("Intervals must be between 1 and 1440 minutes.");
            return;
        }

        Set<String> apps = new HashSet<>();
        if (bale.isChecked()) apps.add(QueueStore.BALE);
        if (eitaa.isChecked()) apps.add(QueueStore.EITAA);
        if (rubika.isChecked()) apps.add(QueueStore.RUBIKA);

        if (apps.isEmpty()) {
            toast("Select at least one messenger.");
            return;
        }

        store.saveConfig(body, baleGap, eitaaGap, rubikaGap, apps, selectedImage);
        store.rebuild();
        toast("Queue created.");
        refresh();
    }

    private void prepareCurrent() {
        QueueStore.Selection selection = store.selection();
        QueueStore.Task task = selection.task;

        if (task == null) {
            toast("Queue finished.");
            return;
        }

        if (selection.waitMillis > 0) {
            long minutes = (selection.waitMillis + 59_999L) / 60_000L;
            toast("Next eligible message in about " + minutes + " minute(s).");
            return;
        }

        if (checkSelfPermission(Manifest.permission.WRITE_CONTACTS)
                != PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{
                            Manifest.permission.READ_CONTACTS,
                            Manifest.permission.WRITE_CONTACTS
                    },
                    CONTACT_PERMISSION
            );
            toast("Grant Contacts permission, then tap PREPARE NEXT MESSAGE again.");
            return;
        }

        try {
            String contactName = ContactProvisioner.ensure(this, task.phone);

            ClipboardManager clipboard =
                    (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(
                    ClipData.newPlainText("Remote Sender message", store.message())
            );

            Intent share = new Intent(Intent.ACTION_SEND);
            share.setPackage(QueueStore.packageName(task.app));
            share.putExtra(Intent.EXTRA_TEXT, store.message());
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            Uri image = store.imageUri();
            if (image != null) {
                share.setType("image/*");
                share.putExtra(Intent.EXTRA_STREAM, image);
                share.setClipData(ClipData.newUri(
                        getContentResolver(),
                        "Remote Sender image",
                        image
                ));
            } else {
                share.setType("text/plain");
            }

            if (share.resolveActivity(getPackageManager()) != null) {
                startActivity(share);
            } else {
                Intent launch = getPackageManager()
                        .getLaunchIntentForPackage(QueueStore.packageName(task.app));
                if (launch == null) throw new IllegalStateException("Target messenger is not installed.");
                startActivity(launch);
            }

            toast(
                    "Recipient contact: " + contactName
                            + "  " + task.phone
                            + ". Message is copied. Confirm the recipient and tap Send in the messenger."
            );
        } catch (Exception e) {
            toast("Prepare failed: " + e.getMessage());
        }
    }

    private void chooseXlsx() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        startActivityForResult(intent, PICK_XLSX);
    }

    private void chooseImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, PICK_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;

        Uri uri = data.getData();

        if (requestCode == PICK_XLSX) {
            new Thread(() -> {
                try {
                    XlsxReader.Result result = XlsxReader.read(this, uri, 1000);
                    store.setNumbers(result.phones);
                    runOnUiThread(() -> {
                        toast(
                                "Imported " + result.phones.size()
                                        + " approved unique numbers. No-consent skipped: "
                                        + result.skippedNoConsent
                                        + ", invalid skipped: "
                                        + result.skippedInvalid
                        );
                        refresh();
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> toast("Excel error: " + e.getMessage()));
                }
            }).start();
        } else if (requestCode == PICK_IMAGE) {
            try {
                int flags = data.getFlags()
                        & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                getContentResolver().takePersistableUriPermission(uri, flags);
            } catch (Exception ignored) {}
            selectedImage = uri;
            refresh();
        }
    }

    private void refresh() {
        status.setText(
                "Approved recipients: " + store.numbers().size()
                        + "\n" + store.stats()
        );

        imageStatus.setText(
                selectedImage == null
                        ? "Image: none"
                        : "Image selected and stored for queue sharing."
        );

        QueueStore.Selection selection = store.selection();
        if (selection.task == null) {
            current.setText("No pending task.");
        } else if (selection.waitMillis > 0) {
            long minutes = (selection.waitMillis + 59_999L) / 60_000L;
            current.setText(
                    "Next: " + selection.task.app
                            + "  " + selection.task.phone
                            + "\nReady in ~" + minutes + " minute(s)"
            );
        } else {
            current.setText(
                    "Ready: " + selection.task.app
                            + "  " + selection.task.phone
            );
        }
    }

    private int parseMinutes(EditText input) {
        try {
            int value = Integer.parseInt(input.getText().toString().trim());
            return value >= 1 && value <= 1440 ? value : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    private String appState(String packageName) {
        try {
            getPackageManager().getPackageInfo(packageName, 0);
            return "installed";
        } catch (Exception e) {
            return "missing";
        }
    }

    private void section(LinearLayout root, String title) {
        TextView spacer = new TextView(this);
        spacer.setHeight(dp(12));
        root.addView(spacer);
        root.addView(text(title, 19, true));
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setPadding(dp(2), dp(6), dp(2), dp(6));
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private EditText edit(String value, String hint) {
        EditText input = new EditText(this);
        input.setText(value);
        input.setHint(hint);
        input.setTextSize(16);
        return input;
    }

    private EditText numberEdit(String value, String hint) {
        EditText input = edit(value, hint);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        return input;
    }

    private CheckBox check(String label, boolean checked) {
        CheckBox box = new CheckBox(this);
        box.setText(label);
        box.setChecked(checked);
        box.setTextSize(16);
        return box;
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        return button;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
