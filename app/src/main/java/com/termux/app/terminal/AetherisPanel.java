package com.termux.app.terminal;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.widget.TextView;
import android.widget.Toast;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.shared.termux.TermuxConstants;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Locale;
import java.util.WeakHashMap;

/** Aetheris INPUT/OUTPUT status cards driven by shell integration events (OSC 133 / 6973). */
public class AetherisPanel {

    private static class State {
        String cwd = "";
        String branch = "";
        boolean running;
        int exit = -1;
        long durationMs;
        long startNs;
        int startAbs = -1;
        int endAbs = -1;
    }

    private static final int GREEN = Color.parseColor("#7FE08A");
    private static final int YELLOW = Color.parseColor("#E6C07B");
    private static final int RED = Color.parseColor("#FF6B81");
    private static final int DIM = Color.parseColor("#8A94B8");

    private final TermuxActivity mActivity;
    private final TextView mCwd, mBranch, mState, mExit;
    private final WeakHashMap<TerminalSession, State> mStates = new WeakHashMap<>();

    public AetherisPanel(TermuxActivity activity) {
        mActivity = activity;
        mCwd = activity.findViewById(R.id.aetheris_cwd);
        mBranch = activity.findViewById(R.id.aetheris_branch);
        mState = activity.findViewById(R.id.aetheris_state);
        mExit = activity.findViewById(R.id.aetheris_exit);
        activity.findViewById(R.id.aetheris_copy_output).setOnClickListener(v -> copyOutput());
    }

    /** Installs the shell integration script into $PREFIX/etc/profile.d if the prefix exists. */
    public static void installShellIntegration(Context context) {
        try {
            File dir = new File(TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/etc/profile.d");
            if (!new File(TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/etc").isDirectory()) return;
            if (!dir.isDirectory() && !dir.mkdirs()) return;
            File out = new File(dir, "aetheris.sh");
            try (InputStream in = context.getAssets().open("aetheris-shell-integration.sh");
                 FileOutputStream os = new FileOutputStream(out)) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
            }
        } catch (Exception ignored) {
        }
    }

    public void onShellEvent(TerminalSession session, int code, String param) {
        mActivity.runOnUiThread(() -> handle(session, code, param));
    }

    private void handle(TerminalSession session, int code, String param) {
        State st = mStates.get(session);
        if (st == null) { st = new State(); mStates.put(session, st); }
        TerminalEmulator emu = session.getEmulator();

        if (code == 6973) {
            int i = param.lastIndexOf(';');
            st.cwd = i < 0 ? param : param.substring(0, i);
            st.branch = i < 0 ? "" : param.substring(i + 1);
        } else if (code == 133 && !param.isEmpty()) {
            char kind = param.charAt(0);
            if (kind == 'C') {
                st.running = true;
                st.startNs = System.nanoTime();
                st.startAbs = -1;
                st.endAbs = -1;
                if (emu != null && !emu.isAlternateBufferActive()) {
                    st.startAbs = emu.getCursorRow() + emu.getScreen().getActiveTranscriptRows();
                }
            } else if (kind == 'D' && st.running) {
                st.running = false;
                st.durationMs = (System.nanoTime() - st.startNs) / 1_000_000L;
                st.exit = 0;
                String[] parts = param.split(";");
                if (parts.length > 1) {
                    try { st.exit = Integer.parseInt(parts[1].trim()); } catch (NumberFormatException ignored) {}
                }
                if (emu != null && st.startAbs >= 0) {
                    int endAbs = emu.getCursorRow() + emu.getScreen().getActiveTranscriptRows();
                    st.endAbs = emu.getCursorCol() == 0 ? endAbs - 1 : endAbs;
                }
            }
        }
        if (session == mActivity.getCurrentSession()) render(st);
    }

    /** Re-renders the panel for the session now being displayed. */
    public void refresh(TerminalSession session) {
        State st = session == null ? null : mStates.get(session);
        render(st == null ? new State() : st);
    }

    private void render(State st) {
        String cwd = st.cwd.isEmpty() ? "~" : st.cwd;
        String home = TermuxConstants.TERMUX_HOME_DIR_PATH;
        if (cwd.equals(home)) cwd = "~";
        else if (cwd.startsWith(home + "/")) cwd = "~" + cwd.substring(home.length());
        mCwd.setText("› " + cwd);

        if (st.branch.isEmpty()) {
            mBranch.setVisibility(android.view.View.GONE);
        } else {
            mBranch.setText("⎇ " + st.branch);
            mBranch.setVisibility(android.view.View.VISIBLE);
        }

        mState.setText(st.running ? "● RUNNING" : "● READY");
        mState.setTextColor(st.running ? YELLOW : GREEN);

        if (st.exit < 0) {
            mExit.setText("— chưa có lệnh nào");
            mExit.setTextColor(DIM);
        } else {
            mExit.setText((st.exit == 0 ? "✔ EXIT 0 " : "✖ EXIT " + st.exit + " ") + "(" + formatDuration(st.durationMs) + ")");
            mExit.setTextColor(st.exit == 0 ? GREEN : RED);
        }
    }

    private static String formatDuration(long ms) {
        if (ms >= 60_000) return String.format(Locale.US, "%dm %02ds", ms / 60_000, (ms / 1000) % 60);
        return String.format(Locale.US, "%.3fs", ms / 1000.0);
    }

    private void copyOutput() {
        TerminalSession session = mActivity.getCurrentSession();
        State st = session == null ? null : mStates.get(session);
        TerminalEmulator emu = session == null ? null : session.getEmulator();
        String text = null;
        if (st != null && emu != null && !emu.isAlternateBufferActive() && st.startAbs >= 0 && st.endAbs >= st.startAbs) {
            int t = emu.getScreen().getActiveTranscriptRows();
            text = emu.getScreen().getSelectedText(0, st.startAbs - t, emu.mColumns, st.endAbs - t);
            if (text != null) text = text.replaceAll("\\s+$", "");
        }
        if (text == null || text.isEmpty()) {
            Toast.makeText(mActivity, "Không có output để chép", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager cm = (ClipboardManager) mActivity.getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("Aetheris output", text));
        Toast.makeText(mActivity, "Đã chép output", Toast.LENGTH_SHORT).show();
    }
}
