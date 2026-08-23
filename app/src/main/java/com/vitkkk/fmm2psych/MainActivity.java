package com.vitkkk.fmm2psych;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQ_PICK = 1001;
    private static final int REQ_SAVE = 1002;

    private Uri selectedUri;
    private String selectedName;
    private File convertedZip;
    private String suggestedOutputName;

    private LinearLayout filePickerCard;
    private LinearLayout progressCard;
    private LinearLayout resultCard;
    private TextView fileName;
    private TextView fileMeta;
    private TextView progressText;
    private TextView resultStats;
    private TextView errorText;
    private ProgressBar progressBar;
    private Button convertButton;
    private Button saveButton;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        filePickerCard = findViewById(R.id.filePickerCard);
        progressCard = findViewById(R.id.progressCard);
        resultCard = findViewById(R.id.resultCard);
        fileName = findViewById(R.id.fileName);
        fileMeta = findViewById(R.id.fileMeta);
        progressText = findViewById(R.id.progressText);
        resultStats = findViewById(R.id.resultStats);
        errorText = findViewById(R.id.errorText);
        progressBar = findViewById(R.id.progressBar);
        convertButton = findViewById(R.id.convertButton);
        saveButton = findViewById(R.id.saveButton);

        filePickerCard.setOnClickListener(v -> pickFile());
        convertButton.setOnClickListener(v -> startConversion());
        saveButton.setOnClickListener(v -> saveZip());

        Intent incoming = getIntent();
        if (incoming != null && Intent.ACTION_VIEW.equals(incoming.getAction()) && incoming.getData() != null) {
            acceptUri(incoming.getData());
        }
    }

    private void pickFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/zip", "application/octet-stream", "application/x-zip-compressed"});
        startActivityForResult(i, REQ_PICK);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;

        if (requestCode == REQ_PICK && data.getData() != null) {
            Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) { }
            acceptUri(uri);
        } else if (requestCode == REQ_SAVE && data.getData() != null && convertedZip != null) {
            copyResultTo(data.getData());
        }
    }

    private void acceptUri(Uri uri) {
        selectedUri = uri;
        selectedName = queryDisplayName(uri);
        if (selectedName == null || selectedName.trim().isEmpty()) selectedName = "mod.FNMM";
        long size = querySize(uri);
        fileName.setText(selectedName);
        fileMeta.setText(size > 0 ? formatBytes(size) + "  •  pronto para converter" : "Arquivo selecionado  •  pronto para converter");
        convertButton.setEnabled(true);
        resultCard.setVisibility(View.GONE);
        errorText.setVisibility(View.GONE);
        convertedZip = null;
    }

    private void startConversion() {
        if (selectedUri == null) return;

        convertButton.setEnabled(false);
        filePickerCard.setEnabled(false);
        resultCard.setVisibility(View.GONE);
        errorText.setVisibility(View.GONE);
        progressCard.setVisibility(View.VISIBLE);
        progressBar.setProgress(0);
        progressText.setText("Abrindo pacote do Funky Maker…");

        worker.submit(() -> {
            File input = null;
            try {
                File workRoot = new File(getCacheDir(), "convert_" + System.currentTimeMillis());
                if (!workRoot.mkdirs() && !workRoot.isDirectory()) throw new Exception("Não foi possível criar a pasta temporária.");
                input = new File(workRoot, sanitizeInputName(selectedName));
                copyUriToFile(selectedUri, input);

                FnmmConverter converter = new FnmmConverter();
                FnmmConverter.ConversionResult result = converter.convert(input, workRoot, (percent, message) ->
                        runOnUiThread(() -> {
                            progressBar.setProgress(percent);
                            progressText.setText(message);
                        })
                );

                convertedZip = result.zipFile;
                suggestedOutputName = result.suggestedName;
                String stats = result.notes + " notas  •  " + result.sustains + " sustains\n"
                        + result.eventActions + " ações de evento  •  " + result.characters + " personagens\n"
                        + result.stageSprites + " sprites de cenário  •  áudio preservado";

                runOnUiThread(() -> {
                    progressBar.setProgress(100);
                    progressText.setText("Finalizando ZIP…");
                    progressCard.setVisibility(View.GONE);
                    resultStats.setText(stats);
                    resultCard.setVisibility(View.VISIBLE);
                    convertButton.setEnabled(true);
                    filePickerCard.setEnabled(true);
                    Toast.makeText(this, "Mod convertido!", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                final String msg = readableError(e);
                runOnUiThread(() -> {
                    progressCard.setVisibility(View.GONE);
                    errorText.setText("Não foi possível converter este arquivo:\n" + msg);
                    errorText.setVisibility(View.VISIBLE);
                    convertButton.setEnabled(true);
                    filePickerCard.setEnabled(true);
                });
            }
        });
    }

    private void saveZip() {
        if (convertedZip == null || !convertedZip.exists()) return;
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/zip");
        i.putExtra(Intent.EXTRA_TITLE, suggestedOutputName != null ? suggestedOutputName : "psych_mod.zip");
        startActivityForResult(i, REQ_SAVE);
    }

    private void copyResultTo(Uri uri) {
        worker.submit(() -> {
            try (InputStream in = new FileInputStream(convertedZip);
                 OutputStream out = getContentResolver().openOutputStream(uri, "w")) {
                if (out == null) throw new Exception("Android não abriu o destino escolhido.");
                byte[] buffer = new byte[1024 * 128];
                int n;
                while ((n = in.read(buffer)) >= 0) out.write(buffer, 0, n);
                out.flush();
                runOnUiThread(() -> Toast.makeText(this, "ZIP salvo.", Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Erro ao salvar: " + readableError(e), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void copyUriToFile(Uri uri, File dest) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(dest)) {
            if (in == null) throw new Exception("Android não conseguiu abrir o arquivo selecionado.");
            byte[] buffer = new byte[1024 * 128];
            int n;
            while ((n = in.read(buffer)) >= 0) out.write(buffer, 0, n);
        }
    }

    private String queryDisplayName(Uri uri) {
        if (ContentResolver.SCHEME_FILE.equals(uri.getScheme())) return new File(uri.getPath()).getName();
        try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) { }
        return null;
    }

    private long querySize(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (c != null && c.moveToFirst() && !c.isNull(0)) return c.getLong(0);
        } catch (Exception ignored) { }
        return -1;
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb);
        return String.format(Locale.US, "%.1f MB", kb / 1024.0);
    }

    private static String sanitizeInputName(String name) {
        String n = name == null ? "mod.FNMM" : name.replaceAll("[^A-Za-z0-9._-]", "_");
        if (n.isEmpty()) n = "mod.FNMM";
        return n;
    }

    private static String readableError(Throwable t) {
        Throwable x = t;
        while (x.getCause() != null && x.getCause() != x) x = x.getCause();
        String m = x.getMessage();
        return (m == null || m.trim().isEmpty()) ? x.getClass().getSimpleName() : m;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        worker.shutdownNow();
    }
}
