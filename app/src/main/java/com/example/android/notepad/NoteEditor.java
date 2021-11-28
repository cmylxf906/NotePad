/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.notepad;

import java.util.Date;
import java.text.SimpleDateFormat;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.EditText;

/**
 * This Activity handles "editing" a note, where editing is responding to
 * {@link Intent#ACTION_VIEW} (request to view data), edit a note
 * {@link Intent#ACTION_EDIT}, create a note {@link Intent#ACTION_INSERT}, or
 * create a new note from the current contents of the clipboard {@link Intent#ACTION_PASTE}.
 *
 * NOTE: Notice that the provider operations in this Activity are taking place on the UI thread.
 * This is not a good practice. It is only done here to make the code more readable. A real
 * application should use the {@link android.content.AsyncQueryHandler}
 * or {@link android.os.AsyncTask} object to perform operations asynchronously on a separate thread.
 */
public class NoteEditor extends Activity {
    //用于日志记录和调试目的
    private static final String TAG = "NoteEditor";

    /*
     * 创建一个投影，返回注释ID和注释内容。
     */
    private static final String[] PROJECTION =
            new String[] {
                    NotePad.Notes._ID,
                    NotePad.Notes.COLUMN_NAME_TITLE,
                    NotePad.Notes.COLUMN_NAME_NOTE
            };

    //活动保存状态的标签
    private static final String ORIGINAL_CONTENT = "origContent";

    // 此活动可以由多个操作启动。每个动作都表示为一个“状态”常量
    private static final int STATE_EDIT = 0;
    private static final int STATE_INSERT = 1;

    // 全局可变变量
    private int mState;
    private Uri mUri;
    private Cursor mCursor;
    private EditText mText;
    private String mOriginalContent;

    /**
     * Defines a custom EditText View that draws lines between each line of text that is displayed.
     */
    public static class LinedEditText extends EditText {
        private Rect mRect;
        private Paint mPaint;

        // 此构造函数由LayoutInflater使用
        public LinedEditText(Context context, AttributeSet attrs) {
            super(context, attrs);

            // 创建矩形和绘制对象，并设置绘制对象的样式和颜色。
            mRect = new Rect();
            mPaint = new Paint();
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setColor(0x800000FF);
        }

        /**
         * This is called to draw the LinedEditText object
         * @param canvas The canvas on which the background is drawn.
         */
        @Override
        protected void onDraw(Canvas canvas) {

            // 获取视图中的文本行数。
            int count = getLineCount();

            //获取全局Rect和Paint对象
            Rect r = mRect;
            Paint paint = mPaint;

            /*
             * 为EditText中的每行文本在矩形中绘制一行
             */
            for (int i = 0; i < count; i++) {
                // 获取当前文本行的基线坐标
                int baseline = getLineBounds(i, r);
                /*
                 * Draws a line in the background from the left of the rectangle to the right,
                 * at a vertical position one dip below the baseline, using the "paint" object
                 * for details.
                 */
                canvas.drawLine(r.left, baseline + 1, r.right, baseline + 1, paint);
            }

            // 通过调用父方法完成
            super.onDraw(canvas);
        }
    }

    /**
     * This method is called by Android when the Activity is first started. From the incoming
     * Intent, it determines what kind of editing is desired, and then does it.
     */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        /*
         * 创建在将活动对象的结果发送回调用方时使用的意图。
         */
        final Intent intent = getIntent();
        /*
         *  根据为传入意图指定的操作设置编辑。
         */
        // 获取触发此活动的意图筛选器的操作
        final String action = intent.getAction();
        // 对于编辑操作：
        if (Intent.ACTION_EDIT.equals(action)) {
            // 设置要编辑的活动状态，并获取要编辑的数据的URI。
            mState = STATE_EDIT;
            mUri = intent.getData();
            //对于插入或粘贴操作：
        } else if (Intent.ACTION_INSERT.equals(action)
                || Intent.ACTION_PASTE.equals(action)) {
            // 设置要插入的活动状态，获取常规注释URI，并在提供程序中插入空记录
            mState = STATE_INSERT;
            mUri = getContentResolver().insert(intent.getData(), null);
            /*
             * If the attempt to insert the new note fails, shuts down this Activity. The
             * originating Activity receives back RESULT_CANCELED if it requested a result.
             * Logs that the insert failed.
             */
            if (mUri == null) {
                // 写入失败的日志标识符、消息和URI。
                Log.e(TAG, "Failed to insert new note into " + getIntent().getData());
                // Closes the activity.
                finish();
                return;
            }
            // 由于创建了新条目，因此将设置要返回的结果并设置要返回的结果。
            setResult(RESULT_OK, (new Intent()).setAction(mUri.toString()));
            // 如果操作不是“编辑”或“插入”：
        } else {
            // 记录操作未被理解的错误，完成活动，并将RESULT_CANCELED返回给原始活动。
            Log.e(TAG, "Unknown action, exiting");
            finish();
            return;
        }
        /*
         * Using the URI passed in with the triggering Intent, gets the note or notes in
         * the provider.
         * Note: This is being done on the UI thread. It will block the thread until the query
         * completes. In a sample app, going against a simple provider based on a local database,
         * the block will be momentary, but in a real app you should use
         * android.content.AsyncQueryHandler or android.os.AsyncTask.
         */
        mCursor = managedQuery(
                mUri,         // 从提供程序获取多个注释的URI。
                PROJECTION,   // 返回每个注释的注释ID和注释内容的投影。
                null,         // No "where" clause selection criteria.
                null,         // No "where" clause selection values.
                null          // Use the default sort order (modification date, descending)
        );
        // For a paste, initializes the data from clipboard.
        // (Must be done after mCursor is initialized.)
        if (Intent.ACTION_PASTE.equals(action)) {
            // Does the paste
            performPaste();
            // Switches the state to EDIT so the title can be modified.
            mState = STATE_EDIT;
        }
        // Sets the layout for this Activity. See res/layout/note_editor.xml
        setContentView(R.layout.note_editor);
        // Gets a handle to the EditText in the the layout.
        mText = (EditText) findViewById(R.id.note);
        /*
         * If this Activity had stopped previously, its state was written the ORIGINAL_CONTENT
         * location in the saved Instance state. This gets the state.
         */
        if (savedInstanceState != null) {
            mOriginalContent = savedInstanceState.getString(ORIGINAL_CONTENT);
        }
    }
    /**
     * This method is called when the Activity is about to come to the foreground. This happens
     * when the Activity comes to the top of the task stack, OR when it is first starting.
     *
     * Moves to the first note in the list, sets an appropriate title for the action chosen by
     * the user, puts the note contents into the TextView, and saves the original text as a
     * backup.
     */
    @Override
    protected void onResume() {
        super.onResume();
        /*
         * mCursor is initialized, since onCreate() always precedes onResume for any running
         * process. This tests that it's not null, since it should always contain data.
         */
        if (mCursor != null) {
            // Requery in case something changed while paused (such as the title)
            mCursor.requery();
            /* Moves to the first record. Always call moveToFirst() before accessing data in
             * a Cursor for the first time. The semantics of using a Cursor are that when it is
             * created, its internal index is pointing to a "place" immediately before the first
             * record.
             */
            mCursor.moveToFirst();
            // 根据当前活动状态修改活动的窗口标题。
            if (mState == STATE_EDIT) {
                // 设置活动的标题以包含注释标题
                int colTitleIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_TITLE);
                String title = mCursor.getString(colTitleIndex);
                Resources res = getResources();
                String text = String.format(res.getString(R.string.title_edit), title);
                setTitle(text);
                // 将插入的标题设置为“创建”
            } else if (mState == STATE_INSERT) {
                setTitle(getText(R.string.title_create));
            }
            /*
             * onResume() may have been called after the Activity lost focus (was paused).
             * The user was either editing or creating a note when the Activity paused.
             * The Activity should re-display the text that had been retrieved previously, but
             * it should not move the cursor. This helps the user to continue editing or entering.
             */
            // Gets the note text from the Cursor and puts it in the TextView, but doesn't change
            // the text cursor's position.
            int colNoteIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_NOTE);
            String note = mCursor.getString(colNoteIndex);
            mText.setTextKeepState(note);
            // 存储原始注释文本，以允许用户恢复更改。
            if (mOriginalContent == null) {
                mOriginalContent = note;
            }
            /*
             * 出现问题。光标应始终包含数据。在便笺中报告错误。
             */
        } else {
            setTitle(getText(R.string.error_title));
            mText.setText(getText(R.string.error_message));
        }
    }
    /**
     * This method is called when an Activity loses focus during its normal operation, and is then
     * later on killed. The Activity has a chance to save its state so that the system can restore
     * it.
     *
     * Notice that this method isn't a normal part of the Activity lifecycle. It won't be called
     * if the user simply navigates away from the Activity.
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        //保存原始文本，以便在暂停时需要终止活动时仍保留它。
        outState.putString(ORIGINAL_CONTENT, mOriginalContent);
    }
    /**
     * This method is called when the Activity loses focus.
     *
     * For Activity objects that edit information, onPause() may be the one place where changes are
     * saved. The Android application model is predicated on the idea that "save" and "exit" aren't
     * required actions. When users navigate away from an Activity, they shouldn't have to go back
     * to it to complete their work. The act of going away should save everything and leave the
     * Activity in a state where Android can destroy it if necessary.
     *
     * If the user hasn't done anything, then this deletes or clears out the note, otherwise it
     * writes the user's work to the provider.
     */
    @Override
    protected void onPause() {
        super.onPause();
        /*
         * Tests to see that the query operation didn't fail (see onCreate()). The Cursor object
         * will exist, even if no records were returned, unless the query failed because of some
         * exception or error.
         *
         */
        if (mCursor != null) {
            // 获取当前注释文本。
            String text = mText.getText().toString();
            int length = text.length();
            /*
             * If the Activity is in the midst of finishing and there is no text in the current
             * note, returns a result of CANCELED to the caller, and deletes the note. This is done
             * even if the note was being edited, the assumption being that the user wanted to
             * "clear out" (delete) the note.
             */
            if (isFinishing() && (length == 0)) {
                setResult(RESULT_CANCELED);
                deleteNote();
                /*
                 * Writes the edits to the provider. The note has been edited if an existing note was
                 * retrieved into the editor *or* if a new note was inserted. In the latter case,
                 * onCreate() inserted a new empty note into the provider, and it is this new note
                 * that is being edited.
                 */
            } else if (mState == STATE_EDIT) {
                // 创建一个映射以包含列的新值
                updateNote(text, null);
            } else if (mState == STATE_INSERT) {
                updateNote(text, text);
                mState = STATE_EDIT;
            }
        }
    }
    /**
     * This method is called when the user clicks the device's Menu button the first time for
     * this Activity. Android passes in a Menu object that is populated with items.
     *
     * Builds the menus for editing and inserting, and adds in alternative actions that
     * registered themselves to handle the MIME types for this application.
     *
     * @param menu A Menu object to which items should be added.
     * @return True to display the menu.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // 从XML资源中展开菜单
       MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.editor_options_menu, menu);
        // 仅为已保存的便笺添加额外的菜单项
        if (mState == STATE_EDIT) {
            // 附加到任何其他活动的菜单项，这些活动也可以用它来做一些事情。
            // 这会在系统上查询对我们的数据执行ALTERNATIVE_ACTION的任何活动，为找到的每个活动添加一个菜单项。
            Intent intent = new Intent(null, mUri);
            intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
            menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0,
                    new ComponentName(this, NoteEditor.class), null, intent, 0, null);
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        //检查注释是否已更改，并启用/禁用还原选项
        int colNoteIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_NOTE);
        String savedNote = mCursor.getString(colNoteIndex);
        String currentNote = mText.getText().toString();
        if (savedNote.equals(currentNote)) {
            menu.findItem(R.id.menu_revert).setVisible(false);
        } else {
            menu.findItem(R.id.menu_revert).setVisible(true);
        }
        return super.onPrepareOptionsMenu(menu);
    }
    /**
     * This method is called when a menu item is selected. Android passes in the selected item.
     * The switch statement in this method calls the appropriate method to perform the action the
     * user chose.
     *
     * @param item The selected MenuItem
     * @return True to indicate that the item was processed, and no further work is necessary. False
     * to proceed to further processing as indicated in the MenuItem object.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // 处理所有可能的菜单操作。
        switch (item.getItemId()) {
            case R.id.menu_search:
                Intent intent = new Intent();
                intent.setClass(this, NoteSearch.class);
                this.startActivity(intent);
                return true;

            case R.id.menu_save:
                String text = mText.getText().toString();
                updateNote(text, null);
                finish();
                break;
            case R.id.menu_delete:
                deleteNote();
                finish();
                break;
            case R.id.menu_revert:
                cancelNote();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

//BEGIN_INCLUDE(paste)
    /**
     * A helper method that replaces the note's data with the contents of the clipboard.
     */
    private final void performPaste() {
        // Gets a handle to the Clipboard Manager
        ClipboardManager clipboard = (ClipboardManager)
                getSystemService(Context.CLIPBOARD_SERVICE);
        // 获取内容解析程序实例
        ContentResolver cr = getContentResolver();
        // 从剪贴板获取剪贴板数据
        ClipData clip = clipboard.getPrimaryClip();
        if (clip != null) {
            String text=null;
            String title=null;
            //从剪贴板数据中获取第一项
            ClipData.Item item = clip.getItemAt(0);
            //尝试将项的内容作为指向注释的URI获取
            Uri uri = item.getUri();
            // 测试以确定该项实际上是一个URI，并且该URI是一个指向提供程序的内容URI，
            // 该提供程序的MIME类型与记事本提供程序支持的MIME类型相同。
            if (uri != null && NotePad.Notes.CONTENT_ITEM_TYPE.equals(cr.getType(uri))) {
                //剪贴板保存对Notes MIME类型数据的引用。This copies it.
                Cursor orig = cr.query(
                        uri,            // 内容提供程序的URI
                        PROJECTION,     // 获取投影中引用的列
                        null,           // 无选择变量
                        null,           // 没有选择变量，因此不需要标准
                        null            // 使用默认的排序顺序
                );
                // If the Cursor is not null, and it contains at least one record
                // (moveToFirst() returns true), then this gets the note data from it.
                if (orig != null) {
                    if (orig.moveToFirst()) {
                        int colNoteIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_NOTE);
                        int colTitleIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_TITLE);
                        text = orig.getString(colNoteIndex);
                        title = orig.getString(colTitleIndex);
                    }
                    // 关闭光标。
                    orig.close();
                }
            }
            // 如果剪贴板的内容不是对注释的引用，那么这会将任何内容转换为文本。
            if (text == null) {
                text = item.coerceToText(this).toString();
            }
            // 使用检索到的标题和文本更新当前注释。
            updateNote(text, title);
        }
    }
//END_INCLUDE(paste)
    /**
     * Replaces the current note contents with the text and title provided as arguments.
     * @param text The new note contents to use.
     * @param title The new note title to use
     */
    private final void updateNote(String text, String title) {

        //设置包含要在提供程序中更新的值的映射。
        ContentValues values = new ContentValues();
        long now = System.currentTimeMillis();
        Date date = new Date(now);
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yy-MM-dd HH:mm:ss");
        String dateFormat = simpleDateFormat.format(date);
        values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, dateFormat);

        // 如果操作是插入新注释，则会为其创建初始标题。
        if (mState == STATE_INSERT) {
            //如果没有提供标题作为参数，请根据注释文本创建一个标题。
            if (title == null) {
                // Get the note's length
                int length = text.length();
                // 通过获取长度为31个字符的文本子字符串来设置标题
                // 或注释中的字符数加一，以较小者为准。
                title = text.substring(0, Math.min(30, length));
                // 如果结果长度超过30个字符，请删除所有尾随空格
                if (length > 30) {
                    int lastSpace = title.lastIndexOf(' ');
                    if (lastSpace > 0) {
                        title = title.substring(0, lastSpace);
                    }
                }
            }
            // 在“值”映射中，设置标题的值
            values.put(NotePad.Notes.COLUMN_NAME_TITLE, title);
        } else if (title != null) {
            // 在“值”映射中，设置标题的值
            values.put(NotePad.Notes.COLUMN_NAME_TITLE, title);
        }
        //这会将所需的注释文本放入地图中。
        values.put(NotePad.Notes.COLUMN_NAME_NOTE, text);
        /*
         * Updates the provider with the new values in the map. The ListView is updated
         * automatically. The provider sets this up by setting the notification URI for
         * query Cursor objects to the incoming URI. The content resolver is thus
         * automatically notified when the Cursor for the URI changes, and the UI is
         * updated.
         * Note: This is being done on the UI thread. It will block the thread until the
         * update completes. In a sample app, going against a simple provider based on a
         * local database, the block will be momentary, but in a real app you should use
         * android.content.AsyncQueryHandler or android.os.AsyncTask.
         */
        getContentResolver().update(
                mUri,    // 要更新的记录的URI。
                values,  // 列名和要应用于它们的新值的映射。
                null,    // 没有使用选择标准，因此不需要列。
                null     // 不使用列，因此不需要参数。
        );

    }

    /**
     * This helper method cancels the work done on a note.  It deletes the note if it was
     * newly created, or reverts to the original text of the note i
     */
    private final void cancelNote() {
        if (mCursor != null) {
            if (mState == STATE_EDIT) {
                // 将原始注释文本放回数据库
                mCursor.close();
                mCursor = null;
                ContentValues values = new ContentValues();
                values.put(NotePad.Notes.COLUMN_NAME_NOTE, mOriginalContent);
                getContentResolver().update(mUri, values, null, null);
            } else if (mState == STATE_INSERT) {
                // 插入了一张空便条，请确保将其删除
                deleteNote();
            }
        }
        setResult(RESULT_CANCELED);
        finish();
    }
    /**
     * Take care of deleting a note.  Simply deletes the entry.
     */
    private final void deleteNote() {
        if (mCursor != null) {
            mCursor.close();
            mCursor = null;
            getContentResolver().delete(mUri, null, null);
            mText.setText("");
        }
    }
}















/**
 private void showColor(){
 AlertDialog alertDialog=new AlertDialog.Builder(this).setTitle("请选择颜色").
 setIcon(R.mipmap.ic_launcher).setView(R.layout.color_layout)
 .setPositiveButton("确定", new DialogInterface.OnClickListener() {
@Override
public void onClick(DialogInterface dialog, int which) {
dialog.dismiss();
}
}).create();
 alertDialog.show();
 }
 public void onClick(View v) {
 switch (v.getId()){
 case R.id.orange:
 if(isFlag){
 mText.setBackgroundColor(Color.parseColor("#FF8C00"));
 colorBack="#FF8C00";
 }else{
 mText.setTextColor(Color.parseColor("#FF8C00"));
 colorText="#FF8C00";
 }
 break;
 case R.id.chocolate:
 if(isFlag){
 mText.setBackgroundColor(Color.parseColor("#D2691E"));
 colorBack="#D2691E";
 }else{
 mText.setTextColor(Color.parseColor("#D2691E"));
 colorText="#D2691E";
 }
 break;
 case R.id.aqua:
 if(isFlag){
 mText.setBackgroundColor(Color.parseColor("#00FFFF"));
 colorBack="#00FFFF";
 }else{
 mText.setTextColor(Color.parseColor("#00FFFF"));
 colorText="#00FFFF";
 }
 break;
 case R.id.gray:
 if(isFlag){
 mText.setBackgroundColor(Color.parseColor("#696969"));
 colorBack="#696969";
 }else{
 mText.setTextColor(Color.parseColor("#696969"));
 colorText="#696969";
 }
 break;
 case R.id.pink:
 if(isFlag){
 mText.setBackgroundColor(Color.parseColor("#D81B60"));
 colorBack="#D81B60";
 }else{
 mText.setTextColor(Color.parseColor("#D81B60"));
 colorText="#D81B60";
 }
 break;
 case R.id.green:
 if(isFlag){
 mText.setBackgroundColor(Color.parseColor("#00FF7F"));
 colorBack="#00FF7F";
 }else{
 mText.setTextColor(Color.parseColor("#00FF7F"));
 colorText="#00FF7F";
 }
 break;
 }
 }
 **/