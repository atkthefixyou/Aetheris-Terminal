package com.termux.app.terminal;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.extrakeys.SpecialButton;
import com.termux.shared.view.KeyboardUtils;
import com.termux.view.TerminalView;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Locale;
import java.util.WeakHashMap;

/** Aetheris INPUT/OUTPUT status cards driven by shell integration events (OSC 133 / 6973). */
public class AetherisPanel {

    static class State {
        String cwd = "";
        String branch = "";
        boolean running;
        int exit = -1;
        long durationMs;
        long startNs;
        int startAbs = -1;
        int submitAbs = -1;
        int endAbs = -1;
    }

    private static final int GREEN = Color.parseColor("#7FE08A");
    private static final int YELLOW = Color.parseColor("#E6C07B");
    private static final int RED = Color.parseColor("#FF6B81");
    private static final int DIM = Color.parseColor("#8A94B8");

    private final TermuxActivity mActivity;
    private final TextView mCwd, mBranch, mState, mExit;
    private static final WeakHashMap<TerminalSession, State> mStates = new WeakHashMap<>();
    private static String sDraft = "";
    private final EditText mInput;
    private static final ArrayList<String> mHistory = new ArrayList<>();
    private int mHistPos = 0;
    private boolean mAlt = false;
    private boolean mInternalEdit = false;

    public AetherisPanel(TermuxActivity activity) {
        mActivity = activity;
        mCwd = activity.findViewById(R.id.aetheris_cwd);
        mBranch = activity.findViewById(R.id.aetheris_branch);
        mState = activity.findViewById(R.id.aetheris_state);
        mExit = activity.findViewById(R.id.aetheris_exit);
        activity.findViewById(R.id.aetheris_copy_output).setOnClickListener(v -> copyOutput());

        mInput = activity.findViewById(R.id.aetheris_input);
        mInput.setOnEditorActionListener((v, actionId, ev) -> { submit(); return true; });
        mInput.setOnKeyListener((v, keyCode, ev) -> {
            if (keyCode == KeyEvent.KEYCODE_ENTER && ev.getAction() == KeyEvent.ACTION_DOWN) { submit(); return true; }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP && ev.getAction() == KeyEvent.ACTION_DOWN) { historyMove(-1); return true; }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && ev.getAction() == KeyEvent.ACTION_DOWN) { historyMove(1); return true; }
            return false;
        });
        mInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void afterTextChanged(Editable e) { sDraft = e.toString(); }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (mInternalEdit || count != 1 || before != 0) return;
                try {
                    Boolean ctrl = mActivity.getExtraKeysView().readSpecialButton(SpecialButton.CTRL, true);
                    if (Boolean.TRUE.equals(ctrl)) {
                        char ch = s.charAt(start);
                        mInternalEdit = true;
                        mInput.getText().delete(start, start + 1);
                        mInternalEdit = false;
                        TerminalSession sess = mActivity.getCurrentSession();
                        if (sess != null) sess.write(String.valueOf((char) (Character.toLowerCase(ch) & 0x1f)));
                    }
                } catch (Exception ignored) {
                    mInternalEdit = false;
                }
            }
        });
        applyMode(false);
        if (!sDraft.isEmpty()) { mInternalEdit = true; mInput.setText(sDraft); mInput.setSelection(sDraft.length()); mInternalEdit = false; }
    }

    public boolean isAlt() { return mAlt; }

    /** View that should receive the soft keyboard: INPUT card normally, terminal for full-screen programs. */
    public View getKeyboardTarget() {
        return mAlt ? mActivity.getTerminalView() : mInput;
    }

    public void requestInputFocus() {
        (mAlt ? (View) mActivity.getTerminalView() : mInput).requestFocus();
    }

    /** Called when terminal text changes: switches input between the INPUT card and the terminal (vim, htop...). */
    public void checkMode(TerminalSession session) {
        if (session != mActivity.getCurrentSession()) return;
        TerminalEmulator emu = session.getEmulator();
        boolean alt = emu != null && emu.isAlternateBufferActive();
        if (alt != mAlt) {
            mAlt = alt;
            applyMode(alt);
            if (alt) KeyboardUtils.showSoftKeyboard(mActivity, mActivity.getTerminalView());
            else KeyboardUtils.showSoftKeyboard(mActivity, mInput);
        }
    }

    private void applyMode(boolean alt) {
        TerminalView tv = mActivity.getTerminalView();
        tv.setFocusable(true);
        tv.setFocusableInTouchMode(true);
        tv.mTextInputDisabled = !alt;
        mInput.setEnabled(!alt);
        mInput.setAlpha(alt ? 0.4f : 1f);
        mInput.setHint(alt ? "Chương trình toàn màn hình: gõ trực tiếp" : "Nhập lệnh…");
        if (alt) tv.requestFocus(); else mInput.requestFocus();
    }

    private void submit() {
        TerminalSession session = mActivity.getCurrentSession();
        if (session == null) return;
        String line = mInput.getText().toString();
        if (!line.trim().isEmpty() && (mHistory.isEmpty() || !mHistory.get(mHistory.size() - 1).equals(line))) mHistory.add(line);
        mHistPos = mHistory.size();
        TerminalEmulator emu = session.getEmulator();
        if (emu != null && !emu.isAlternateBufferActive()) {
            State st = mStates.get(session);
            if (st == null) { st = new State(); mStates.put(session, st); }
            st.submitAbs = emu.getCursorRow() + 1 + emu.getScreen().getActiveTranscriptRows();
            st.startAbs = -1;
            st.endAbs = -1;
        }
        mInternalEdit = true;
        mInput.setText("");
        mInternalEdit = false;
        session.write(line.replace("\r\n", "\r").replace('\n', '\r') + "\r");
    }

    private void historyMove(int dir) {
        if (mHistory.isEmpty()) return;
        mHistPos = Math.max(0, Math.min(mHistory.size(), mHistPos + dir));
        String t = mHistPos == mHistory.size() ? "" : mHistory.get(mHistPos);
        mInternalEdit = true;
        mInput.setText(t);
        mInput.setSelection(t.length());
        mInternalEdit = false;
    }

    /** Extra-keys interception while typing in the INPUT card. Returns true if consumed. */
    public boolean handleExtraKey(String key) {
        if (mAlt) return false;
        int len = mInput.getText().length();
        int sel = Math.max(0, mInput.getSelectionStart());
        switch (key) {
            case "UP": historyMove(-1); return true;
            case "DOWN": historyMove(1); return true;
            case "LEFT": mInput.setSelection(Math.max(0, sel - 1)); return true;
            case "RIGHT": mInput.setSelection(Math.min(len, sel + 1)); return true;
            case "HOME": mInput.setSelection(0); return true;
            case "END": mInput.setSelection(len); return true;
            case "DEL": if (sel < len) mInput.getText().delete(sel, sel + 1); return true;
            case "TAB": return true;
            default: return false;
        }
    }

    /** Installs the shell integration script into $PREFIX/etc/profile.d if the prefix exists. */
    public static void installShellIntegration(Context context) {
        try {
            File dir = new File(TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/etc/profile.d");
            if (!new File(TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/etc").isDirectory()) return;
            if (!dir.isDirectory() && !dir.mkdirs()) return;
            try { new FileOutputStream(new File(TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/etc/motd")).close(); } catch (Exception ignored) {}
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
        State st = applyEvent(session, code, param);
        if (session == mActivity.getCurrentSession()) render(st);
        checkMode(session);
    }

    /** UI-independent state update; also called while the activity is gone (service client). */
    public static State applyEvent(TerminalSession session, int code, String param) {
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
        return st;
    }

    /** Re-renders the panel for the session now being displayed. */
    public void refresh(TerminalSession session) {
        State st = session == null ? null : mStates.get(session);
        render(st == null ? new State() : st);
        if (session != null) checkMode(session);
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
        if (emu != null && !emu.isAlternateBufferActive()) {
            int t = emu.getScreen().getActiveTranscriptRows();
            int curAbs = emu.getCursorRow() + t;
            if (st != null) {
                int start = st.startAbs >= 0 ? st.startAbs : st.submitAbs;
                int end = (st.startAbs >= 0 && st.endAbs >= 0) ? st.endAbs : (st.running ? curAbs : curAbs - 1);
                if (start >= 0 && end >= start) {
                    text = trimEnd(emu.getScreen().getSelectedText(0, start - t, emu.mColumns, end - t));
                }
            }
            if (text == null || text.isEmpty()) {
                // Marks stale (screen cleared / redrawn): copy the visible screen above the prompt.
                int last = emu.getCursorRow() - (emu.getCursorCol() == 0 ? 1 : 0);
                last = Math.max(0, Math.min(emu.mRows - 1, last));
                text = trimEnd(emu.getScreen().getSelectedText(0, 0, emu.mColumns, last));
                if (text == null || text.isEmpty()) {
                    text = trimEnd(emu.getScreen().getSelectedText(0, 0, emu.mColumns, emu.mRows - 1));
                }
            }
        }
        if (text == null || text.isEmpty()) {
            Toast.makeText(mActivity, "Không có output để chép", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager cm = (ClipboardManager) mActivity.getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("Aetheris output", text));
        Toast.makeText(mActivity, "Đã chép output", Toast.LENGTH_SHORT).show();
    }

    private static String trimEnd(String text) {
        return text == null ? null : text.replaceAll("\\s+$", "");
    }
}
