# NotePad

## 实验过程如下：
### 1.时间戳功能实现：

####  布局文件 noteslist_item.xml 中默认带有一个显示标题的TextView，为了让其显示时间戳，我为其新增一个TextView用于显示时间戳，改进后代码如下：
<RelativeLayout android:layout_height="match_parent"
    android:layout_width="match_parent"
    xmlns:android="http://schemas.android.com/apk/res/android">
    <TextView xmlns:android="http://schemas.android.com/apk/res/android"
        android:id="@android:id/text1"
        android:layout_width="match_parent"
        android:layout_height="?android:attr/listPreferredItemHeight"
        android:textAppearance="?android:attr/textAppearanceLarge"
        android:paddingLeft="5dip"
        android:singleLine="true"
        android:paddingTop="8dp"
        />
    <TextView
        android:id="@+id/text2"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:paddingLeft="5dip"
        android:singleLine="true"
        android:layout_marginTop="42dp"/>
</RelativeLayout>

#### 要增加时间戳，首先要获取当前时间,把时间戳改为以时间格式存入

        Date date = new Date(now);
        SimpleDateFormat format = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        format.setTimeZone(TimeZone.getTimeZone("GMT+08:00"));
        String formatDate = format.format(date);
        
#### 要将时间显示，首先要在PROJECTION中定义显示的时间

        private static final String[] PROJECTION = new String[] {
               NotePad.Notes._ID, // 0
               NotePad.Notes.COLUMN_NAME_TITLE, // 1
               NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE,
               NotePad.Notes.COLUMN_NAME_BACK_COLOR, 
        };
        
#### Cursor不变，在dataColumns，viewIDs中补充时间部分：

        String[] dataColumns = { NotePad.Notes.COLUMN_NAME_TITLE ,  NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE } ;
        int[] viewIDs = { android.R.id.text1 , R.id.text1_time };
        
#### 在NoteEditor.java  updateNote()方法里也添加获取时间的功能（这里是编辑Note时获取的时间），不然无法正确显示编辑Note后的时间
        long now = System.currentTimeMillis();
        Date date = new Date(now);
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yy-MM-dd HH:mm:ss");
        String dateFormat = simpleDateFormat.format(date);
        values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, dateFormat);
        
####  
### 2.搜索功能的实现：

#### 新建一个note_search.xml 的布局文件，用于显示搜索功能，布局文件代码如下：
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">
    <SearchView
        android:id="@+id/search_view"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:iconifiedByDefault="false"
        />
    <ListView
        android:id="@+id/list_view"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        />
</LinearLayout>

#### 要添加笔记查询功能，就要在应用中增加一个搜索的入口。找到菜单的xml文件，list_options_menu.xml，添加一个搜索的item，搜索图标用安卓自带的图标，设为总是显示：用于点击进入搜索:

  <item
        android:id="@+id/menu_search"
        android:icon="@android:drawable/ic_menu_search"
        android:title="search_note"
        android:showAsAction="always" />

#### 新建一个NoteSearch.java  代码如下：


    public class NoteSearch extends Activity implements SearchView.OnQueryTextListener
       {
       ListView listView;
       SQLiteDatabase sqLiteDatabase;
       /**
        * The columns needed by the cursor adapter
        */
    private static final String[] PROJECTION = new String[]{
            NotePad.Notes._ID, // 0
            NotePad.Notes.COLUMN_NAME_TITLE, // 1
            NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE//时间
    };

    public boolean onQueryTextSubmit(String query) {
        Toast.makeText(this, "您选择的是："+query, Toast.LENGTH_SHORT).show();
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.note_search);
        SearchView searchView = findViewById(R.id.search_view);
        Intent intent = getIntent();
        if (intent.getData() == null) {
            intent.setData(NotePad.Notes.CONTENT_URI);
        }
        listView = findViewById(R.id.list_view);
        sqLiteDatabase = new NotePadProvider.DatabaseHelper(this).getReadableDatabase();
        //设置该SearchView显示搜索按钮
        searchView.setSubmitButtonEnabled(true);

        //设置该SearchView内默认显示的提示文本
        searchView.setQueryHint("查找");
        searchView.setOnQueryTextListener(this);

    }
    public boolean onQueryTextChange(String string) {
        String selection1 = NotePad.Notes.COLUMN_NAME_TITLE+" like ? or "+NotePad.Notes.COLUMN_NAME_NOTE+" like ?";
        String[] selection2 = {"%"+string+"%","%"+string+"%"};
        Cursor cursor = sqLiteDatabase.query(
                NotePad.Notes.TABLE_NAME,
                PROJECTION, // The columns to return from the query
                selection1, // The columns for the where clause
                selection2, // The values for the where clause
                null,          // don't group the rows
                null,          // don't filter by row groups
                NotePad.Notes.DEFAULT_SORT_ORDER // The sort order
        );
        // The names of the cursor columns to display in the view, initialized to the title column
        String[] dataColumns = {
                NotePad.Notes.COLUMN_NAME_TITLE,
                NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE
        } ;
        // The view IDs that will display the cursor columns, initialized to the TextView in
        // noteslist_item.xml
        int[] viewIDs = {
                android.R.id.text1,
                android.R.id.text2
        };
        // Creates the backing adapter for the ListView.
        SimpleCursorAdapter adapter
                = new SimpleCursorAdapter(
                this,                             // The Context for the ListView
                R.layout.noteslist_item,         // Points to the XML for a list item
                cursor,                           // The cursor to get items from
                dataColumns,
                viewIDs
        );
        // Sets the ListView's adapter to be the cursor adapter that was just created.
        listView.setAdapter(adapter);
        return true;
    }}

#### 在NoteList中找到onOptionsItemSelected方法，在switch中添加搜索的case语句:
 
    case R.id.menu_search:
    Intent intent = new Intent();
    intent.setClass(NotesList.this,NoteSearch.class);
    NotesList.this.startActivity(intent);
    return true;
    
#### 最后要在AndroidManifest.xml注册NoteSearch：

    <activity
        android:name="NoteSearch"
        android:label="@string/title_notes_search">
    </activity>
 

## 实验截图如下：
![image](https://github.com/cmylxf906/NotePad/blob/master/imges/empty.png)

![image](https://github.com/cmylxf906/NotePad/blob/master/imges/add.png)

![image](https://github.com/cmylxf906/NotePad/blob/master/imges/showtime.png)

![image](https://github.com/cmylxf906/NotePad/blob/master/imges/searchEmpty.png)

![image](https://github.com/cmylxf906/NotePad/blob/master/imges/search1.png)

![image](https://github.com/cmylxf906/NotePad/blob/master/imges/search2.png)

