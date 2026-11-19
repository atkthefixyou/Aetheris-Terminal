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
import java.util.List;
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
        View copyBtn = activity.findViewById(R.id.aetheris_copy_output);
        copyBtn.setOnClickListener(v -> copyAllOutput());
        copyBtn.setOnLongClickListener(v -> { copyOutput(); return true; });

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
                if (mInternalEdit) return;
                if (forwardLive(s, start, before, count)) return;
                if (count != 1 || before != 0) return;
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

    /** Lenh dang chay (khong phai vim/htop): chu go vao INPUT gui thang vao lenh, de tra loi cac cau hoi [Y/n]. */
    private boolean forwardLive(CharSequence s, int start, int before, int count) {
        TerminalSession sess = mActivity.getCurrentSession();
        State st = sess == null ? null : mStates.get(sess);
        if (st == null || !st.running || mAlt) return false;
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < before; i++) out.append('\u007f');
        for (int i = start; i < start + count && i < s.length(); i++) {
            char c = s.charAt(i);
            out.append(c == '\n' ? '\r' : c);
        }
        if (out.length() == 0) return false;
        mInternalEdit = true;
        try { mInput.getText().delete(0, mInput.getText().length()); } finally { mInternalEdit = false; }
        sess.write(out.toString());
        return true;
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
        State cur = mStates.get(session);
        boolean busy = cur != null && cur.running;
        boolean alt = emu != null && emu.isAlternateBufferActive();
        if (busy || alt) {
            // Chuong trinh dang chay doc stdin: gui nguyen van.
            session.write(line.replace("\r\n", "\r").replace('\n', '\r') + "\r");
            return;
        }
        List<String> cmds = splitCommands(line);
        if (cmds.isEmpty()) { session.write("\r"); return; }
        Queue q = mQueues.get(session);
        if (q == null) { q = new Queue(); mQueues.put(session, q); }
        q.pending.clear();
        for (int i = 1; i < cmds.size(); i++) q.pending.add(cmds.get(i));
        sendCommand(session, cmds.get(0), q);
    }

    // ---- Chay tung lenh: moi lenh (khoi) gui sau khi lenh truoc ket thuc (OSC 133;D) ----
    private static class Queue {
        final java.util.ArrayDeque<String> pending = new java.util.ArrayDeque<>();
        boolean awaitingStart;
    }
    private static final java.util.Map<TerminalSession, Queue> mQueues = new java.util.WeakHashMap<>();
    private static final android.os.Handler sHandler = new android.os.Handler(android.os.Looper.getMainLooper());

    private static void sendCommand(final TerminalSession session, String cmd, final Queue q) {
        TerminalEmulator emu = session.getEmulator();
        State st = mStates.get(session);
        if (st == null) { st = new State(); mStates.put(session, st); }
        if (emu != null && !emu.isAlternateBufferActive()) {
            st.submitAbs = emu.getCursorRow() + 1 + emu.getScreen().getActiveTranscriptRows();
            st.startAbs = -1;
            st.endAbs = -1;
        }
        q.awaitingStart = true;
        session.write(cmd.replace("\r\n", "\r").replace('\n', '\r') + "\r");
        if (!q.pending.isEmpty()) {
            // Du phong: lenh khong sinh su kien (vd. chi la comment) thi van chay tiep.
            sHandler.postDelayed(() -> {
                if (q.awaitingStart) { q.awaitingStart = false; pump(session); }
            }, 2500);
        }
    }

    private static void pump(TerminalSession session) {
        Queue q = mQueues.get(session);
        if (q == null || q.pending.isEmpty()) return;
        sendCommand(session, q.pending.poll(), q);
    }

    /** Tach thanh cac lenh hoan chinh; khoi nhieu dong (if/for/heredoc/quote/\) giu nguyen. */
    static List<String> splitCommands(String text) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        Splitter sp = new Splitter();
        for (String line : text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
            if (cur.length() == 0 && sp.idle() && (line.trim().isEmpty() || line.trim().startsWith("#"))) continue;
            if (cur.length() > 0) cur.append('\n');
            cur.append(line);
            if (sp.feed(line)) { out.add(cur.toString()); cur.setLength(0); sp = new Splitter(); }
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    private static class Splitter {
        int depth, paren;
        char q;
        boolean cont, cmdStart;
        final List<String> heredocs = new ArrayList<>();
        boolean heredocTrim;

        boolean idle() { return depth == 0 && paren == 0 && q == 0 && !cont && heredocs.isEmpty(); }

        boolean feed(String line) {
            if (!heredocs.isEmpty()) {
                String t = heredocTrim ? line.replaceAll("^\\t+", "") : line;
                if (t.equals(heredocs.get(0))) heredocs.remove(0);
                return idle();
            }
            cont = false;
            cmdStart = q == 0;
            StringBuilder w = new StringBuilder();
            int n = line.length();
            for (int i = 0; i < n; i++) {
                char c = line.charAt(i);
                if (q != 0) {
                    if (q == '\'') { if (c == '\'') q = 0; }
                    else if (c == '\\') i++;
                    else if (c == '"') q = 0;
                    continue;
                }
                if (c == '\\') {
                    if (i == n - 1) cont = true; else i++;
                    w.append('x'); cmdStart = false;
                } else if (c == '\'' || c == '"') {
                    word(w); q = c; cmdStart = false;
                } else if (c == '#' && w.length() == 0) {
                    break;
                } else if (c == ';' || c == '&' || c == '|') {
                    word(w); cmdStart = true;
                } else if (c == '(') {
                    word(w); paren++; cmdStart = true;
                } else if (c == ')') {
                    word(w); if (paren > 0) paren--;
                } else if (c == '{' && w.length() == 0 && (i == n - 1 || Character.isWhitespace(line.charAt(i + 1)))) {
                    depth++; cmdStart = true;
                } else if (c == '}' && w.length() == 0 && depth > 0 && (i == n - 1 || ";&| \t)".indexOf(line.charAt(i + 1)) >= 0)) {
                    depth--; cmdStart = false;
                } else if (c == '<' && i + 1 < n && line.charAt(i + 1) == '<' && !(i + 2 < n && line.charAt(i + 2) == '<')) {
                    word(w);
                    int j = i + 2;
                    boolean trim = false;
                    if (j < n && line.charAt(j) == '-') { trim = true; j++; }
                    while (j < n && line.charAt(j) == ' ') j++;
                    StringBuilder d = new StringBuilder();
                    while (j < n && "'\"\\".indexOf(line.charAt(j)) >= 0) j++;
                    while (j < n && " \t;|&<>'\")".indexOf(line.charAt(j)) < 0) d.append(line.charAt(j++));
                    while (j < n && (line.charAt(j) == '\'' || line.charAt(j) == '"')) j++;
                    if (d.length() > 0) { heredocs.add(d.toString()); heredocTrim = trim; }
                    i = j - 1;
                } else if (Character.isWhitespace(c)) {
                    word(w);
                } else {
                    w.append(c);
                }
            }
            word(w);
            return idle();
        }

        private void word(StringBuilder w) {
            if (w.length() == 0) return;
            String s = w.toString();
            w.setLength(0);
            if (!cmdStart) return;
            switch (s) {
                case "if": case "for": case "while": case "until": case "case": case "select": depth++; break;
                case "then": case "do": case "else": case "elif": case "!": case "time": break;
                case "fi": case "done": case "esac": if (depth > 0) depth--; cmdStart = false; break;
                default: cmdStart = false;
            }
        }
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

    private static String readAsset(Context context, String name) throws java.io.IOException {
        try (InputStream in = context.getAssets().open(name);
             java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toString("UTF-8");
        }
    }

    private static void writeText(File f, String text) throws java.io.IOException {
        try (FileOutputStream os = new FileOutputStream(f)) {
            os.write(text.getBytes("UTF-8"));
        }
    }

    /**
     * Package prefix compatibility: official Termux .deb files target /data/data/com.termux.
     * Installs an apt hook (DPkg::Pre-Install-Pkgs) that rewrites each verified .deb to this
     * app's prefix right before dpkg unpacks it. Idempotent; overwritten on every start.
     */
    public static void installPackageCompat(Context context) {
        try {
            String prefix = TermuxConstants.TERMUX_PREFIX_DIR_PATH;
            if (!new File(prefix + "/etc").isDirectory()) return;
            File libexec = new File(prefix + "/libexec/aetheris");
            if (!libexec.isDirectory() && !libexec.mkdirs()) return;
            File script = new File(libexec, "rewrite-debs");
            writeText(script, readAsset(context, "aetheris-rewrite-debs.sh")
                .replace("@PREFIX@", prefix)
                .replace("@NEWPKG@", TermuxConstants.TERMUX_PACKAGE_NAME));
            script.setExecutable(true, false);
            File confDir = new File(prefix + "/etc/apt/apt.conf.d");
            if (!confDir.isDirectory() && !confDir.mkdirs()) return;
            String path = script.getAbsolutePath();
            writeText(new File(confDir, "99aetheris-prefix"),
                "DPkg::Pre-Install-Pkgs { \"" + path + "\"; };\n"
                + "DPkg::Tools::Options::" + path + "::Version \"3\";\n");
        } catch (Exception ignored) {
        }
    }

    /** Installs the shell integration script into $PREFIX/etc/profile.d if the prefix exists. */
    public static void installShellIntegration(Context context) {
        installPackageCompat(context);
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
                Queue aq = mQueues.get(session);
                if (aq != null) aq.awaitingStart = false;
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
                Queue dq = mQueues.get(session);
                if (dq != null) {
                    if (st.exit == 130) dq.pending.clear(); // Ctrl+C dung ca chuoi
                    else sHandler.post(() -> pump(session));
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

    /** Copies the entire terminal output: whole scrollback transcript plus the visible screen. */
    private void copyAllOutput() {
        TerminalSession session = mActivity.getCurrentSession();
        TerminalEmulator emu = session == null ? null : session.getEmulator();
        String text = null;
        if (emu != null) {
            int t = emu.getScreen().getActiveTranscriptRows();
            text = trimEnd(emu.getScreen().getSelectedText(0, -t, emu.mColumns, emu.mRows - 1));
        }
        if (text == null || text.isEmpty()) {
            Toast.makeText(mActivity, "Không có output để chép", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager cm = (ClipboardManager) mActivity.getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("Aetheris output", text));
        Toast.makeText(mActivity, "Đã chép toàn bộ output (" + text.split("\\n", -1).length + " dòng)", Toast.LENGTH_SHORT).show();
    }

    /** Copies only the output of the last command (long-press on the copy button). */
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
        if (text == null) return null;
        // Bo cac dong dau hieu hoan tat lenh ("─── ✔ ───", "─── ✖ 1 ───"): chi gom ─, ✔/✖, so, khoang trang.
        StringBuilder sb = new StringBuilder();
        for (String line : text.split("\\r?\\n", -1)) {
            if (isMarkerLine(line)) continue;
            sb.append(line).append('\n');
        }
        return sb.toString().replaceAll("\\s+$", "");
    }

    private static boolean isMarkerLine(String line) {
        boolean dash = false, mark = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\u2500' || c == '\u2501' || c == '-') dash = true;
            else if (c == '\u2714' || c == '\u2716' || c == '\u2713' || c == '\u2717' || c == '\uFE0F') mark = true;
            else if (!(Character.isWhitespace(c) || Character.isDigit(c) || c == '\u00A0')) return false;
        }
        return dash && mark;
    }
}
