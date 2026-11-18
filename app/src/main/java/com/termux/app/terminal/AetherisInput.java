package com.termux.app.terminal;

import android.content.Context;
import android.util.AttributeSet;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.EditText;

/** Multi-line command box: grows with pasted/wrapped text, Enter key sends the command. */
public class AetherisInput extends EditText {

    public AetherisInput(Context context) { super(context); }
    public AetherisInput(Context context, AttributeSet attrs) { super(context, attrs); }
    public AetherisInput(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        InputConnection ic = super.onCreateInputConnection(outAttrs);
        outAttrs.imeOptions &= ~EditorInfo.IME_FLAG_NO_ENTER_ACTION;
        outAttrs.imeOptions = (outAttrs.imeOptions & ~EditorInfo.IME_MASK_ACTION) | EditorInfo.IME_ACTION_SEND;
        return ic;
    }
}
