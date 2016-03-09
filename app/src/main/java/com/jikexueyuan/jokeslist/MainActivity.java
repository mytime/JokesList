package com.jikexueyuan.jokeslist;

/**
 * ListView应用
 */

import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Toast;

import com.jikexueyuan.jokeslist.Db.Db;
import com.jikexueyuan.jokeslist.control.NetworkUtils;
import com.jikexueyuan.jokeslist.control.PullRefreshView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private PullRefreshView pullRefreshView;
    private ListView listView;
    private View pullUpFooter;//分页加载提示View
    private SimpleAdapter adapter;
    private List<Map<String, Object>> listMap;
    private Map<String, Object> map;
    private Db db;
    private SQLiteDatabase dbRead, dbWrite;
    private Cursor c;
    private StringBuilder builder;
    private long dataCount;//数据库条数
    private NetworkUtils networkUtils;//联网判断
    public final static String URI = "http://dingding9.applinzi.com/latestposts.php";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        init();//初始化
        pullRefresh();//下拉刷新
        itemClick();//子项目被点击
    }

    /**
     * 初始化
     */
    private void init() {

        pullRefreshView = (PullRefreshView) findViewById(R.id.pull_refresh_view);
        listView = (ListView) findViewById(R.id.listView);
        listMap = new ArrayList<Map<String, Object>>();
        pullUpFooter = MainActivity.this.getLayoutInflater()
                .inflate(R.layout.pull_up_footer, null);
        listView.setOnScrollListener(new scrollListener());

        //初始化数据库
        db = new Db(this);
        dbWrite = db.getWritableDatabase();
        dbRead = db.getReadableDatabase();

        dataCount = getCount(); //获取数据库条数
        maxpage = (int) (dataCount % number ==
                0 ? dataCount / number : dataCount / number + 1);//获取最大页数

        if (dataCount != 0) {
            refreshListView(1);//加载缓存数据
        }else{
            getData(); //首次打开直接下载数据
        }

        adapter = new SimpleAdapter(
                this,
                listMap,
                R.layout.list_item,
                new String[]{"title", "date"},
                new int[]{R.id.tvTitle, R.id.tvTime});//初始化listView适配器


        //在适配器之前加页脚，这样适配器会重新被封装成 '有页脚的适配器'
        listView.addFooterView(pullUpFooter);
        listView.setAdapter(adapter);
        if (dataCount == 0) {
            listView.removeFooterView(pullUpFooter);
        }

    }
    /**
     * 下拉刷新
     */
    private void pullRefresh() {
        pullRefreshView.setOnRefreshListener(new PullRefreshView.RefreshListener() {
            @Override
            public void onRefresh() {
                getData(); //获取网络数据
                pullRefreshView.finishRefresh();
            }
        });
    }

    /**
     * 获取网络数据
     */
    private void getData() {
        //判断是否联网
        if (networkUtils.isNetworkConnected(
                getApplicationContext()) || networkUtils.isWifi(getApplicationContext())) {
            //启用异步线程，来获取网络数据
            new GetDataTask().execute(URI);
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 项目点击事件
     */
    private void itemClick() {
        listView.setOnItemClickListener(listClickListener);
    }

    /**
     * 刷新listView
     * @param showPage
     */
    private void refreshListView(int showPage) {
        c = dbRead.rawQuery("SELECT * FROM user WHERE pages = ?",
                new String[]{String.valueOf(showPage)});
        while (c.moveToNext()) {
            for (int i = 0; i < c.getCount(); ++i) {
                c.moveToPosition(i);
                map = new HashMap<String, Object>();
                map.put("title", c.getString(c.getColumnIndex("title")));
                map.put("date", c.getString(c.getColumnIndex("date")));
                map.put("sql_id", c.getInt(c.getColumnIndex("_id")));
                listMap.add(map);
            }
        }
    }

    /*查询数据库记录总数*/
    public long getCount() {
        Cursor cursor = dbRead.rawQuery("select count(*)from user", null);
        cursor.moveToFirst();
        long count = cursor.getLong(0);
        cursor.close();
        return count;
    }

    /**
     * 启用异步线程，来获取网络数据
     */
    class GetDataTask extends AsyncTask<String, Void, StringBuilder> {
        @Override
        protected StringBuilder doInBackground(String... params) {
            try {
                //URL中数据的读取
                URL url = new URL(params[0]);
                URLConnection connection = url.openConnection();

                InputStream is = connection.getInputStream();
                InputStreamReader isr = new InputStreamReader(is, "UTF-8");
                BufferedReader br = new BufferedReader(isr);
                String line;
                builder = new StringBuilder();
                while ((line = br.readLine()) != null) {
                    builder.append(line);
                }
                //关闭流
                br.close();
                isr.close();
                is.close();

            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return builder;
        }

        @Override
        protected void onProgressUpdate(Void... values) {
            super.onProgressUpdate(values);
        }

        /**
         * 解析数据 写入数据库
         * @param builder
         */
        @Override
        protected void onPostExecute(StringBuilder builder) {
            dealDate();
            super.onPostExecute(builder);
        }
    }

    /**
     * 下载数据
     */
    private void dealDate() {
        try {
            dbWrite.delete("user", null, null);//清空user表
            JSONArray jsonArray = new JSONArray(builder.toString());
            dataCount = jsonArray.length();//总共的条数
            int page = 1;
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                page = i / number + 1;//每页10条
                int ID = jsonObject.getInt("ID");//自增唯一ID
                String post_date = jsonObject.getString("post_date");//发布时间
                String post_title = jsonObject.getString("post_title");//标题
                String post_content = jsonObject.getString("post_content");//正文

                //写入数据库
                DBInsert(page, ID, post_date, post_title, post_content);
            }
            maxpage = page;
            listMap.clear();
            refreshListView(1);//刷新main列表
        } catch (JSONException e) {
            e.printStackTrace();
        }

    }

    /**
     * 写入数据库
     */
    private void DBInsert(Integer page, Integer ID, String date, String title, String content) {
        ContentValues cv = new ContentValues();
        cv.put("pages", page);
        cv.put("ID", ID);
        cv.put("date", date);
        cv.put("title", title);
        cv.put("content", content);
        dbWrite.insert("user", null, cv);
    }

    /**
     * listView点击
     */
    private AdapterView.OnItemClickListener listClickListener =
            new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            try {
                ListView listView = (ListView) parent;
                HashMap<String, Object> data = (HashMap<String, Object>) listView.getItemAtPosition(position);
                int itemId = (int) data.get("sql_id");
                if (itemId > 0) {
                    Intent intent = new Intent(MainActivity.this, ContentActivity.class);
                    intent.putExtra("db_id", itemId);
                    startActivity(intent);
                }
            } catch (Exception e) {
                //捕获异常，为防止点击最后footerView的情况
                e.printStackTrace();
            }
        }
    };

    /**
     * 销毁Activity
     * 关闭数据库
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (c != null){
            c.close();
        }

        if (db != null){
            db.close();
        }

    }

    private int number = 10; // 每次获取多少条数据
    private int maxpage = 1; // 总共有多少页
    private boolean loadfinish = true; // 指示数据是否加载完成
    private static final int SHOW_FOOTER = 1;//继续加载
    private static final int NONE_FOOTER = 0;//已经到达最底部，加载完成

    private final class scrollListener implements AbsListView.OnScrollListener {

        /**
         * 滑动状态改变时被调用
         */
        @Override
        public void onScrollStateChanged(AbsListView view, int scrollState) {
        }

        /**
         * 滑动时被调用
         */
        @Override
        public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {

            //如果所有的记录选项等于数据集的条数，则移除列表底部视图
            if (totalItemCount == dataCount + 1 && dataCount != 0) {
                listView.removeFooterView(pullUpFooter);
                Toast.makeText(MainActivity.this, "数据全部加载完!", Toast.LENGTH_LONG).show();
            }

            int lastItemId = firstVisibleItem + visibleItemCount - 1;

            // 达到数据的最后一条记录
            if (lastItemId + 1 == totalItemCount && dataCount != 0) {
                if (lastItemId > 0) {
                    int currentPage = lastItemId % number == 0 ? lastItemId / number
                            : lastItemId / number + 1;
                    final int nextPage = currentPage + 1;

                    if (nextPage <= maxpage && loadfinish) {
                        loadfinish = false;
                        listView.addFooterView(pullUpFooter);

                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    Thread.sleep(2000);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                                handler.sendMessage(handler.obtainMessage(SHOW_FOOTER, nextPage));
                            }
                        }).start();
                    }
                }
            }
        }
    }

    /**
     * 更新UI
     */
    private Handler handler = new Handler() {
        // 告诉ListView数据已经发生改变，要求ListView更新界面显示
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case SHOW_FOOTER:
                    refreshListView((Integer) msg.obj);
                    adapter.notifyDataSetChanged();
                    if (listView.getFooterViewsCount() > 0) { // 如果有底部视图
                        listView.removeFooterView(pullUpFooter);
                    }
                    loadfinish = true; // 加载完成
                    break;
                default:
                    break;
            }
        }
    };
}