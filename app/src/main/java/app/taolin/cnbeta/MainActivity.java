package app.taolin.cnbeta;

import android.content.Intent;
import android.content.res.Resources;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.NetworkImageView;
import com.baoyz.swipemenulistview.SwipeMenu;
import com.baoyz.swipemenulistview.SwipeMenuCreator;
import com.baoyz.swipemenulistview.SwipeMenuItem;
import com.baoyz.swipemenulistview.SwipeMenuListView;
import com.viewpagerindicator.IconPagerAdapter;
import com.viewpagerindicator.PageIndicator;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import app.taolin.cnbeta.adapter.ContentListAdapter;
import app.taolin.cnbeta.dao.DaoMaster;
import app.taolin.cnbeta.dao.DaoSession;
import app.taolin.cnbeta.dao.Headline;
import app.taolin.cnbeta.dao.HeadlineDao;
import app.taolin.cnbeta.dao.ListItem;
import app.taolin.cnbeta.dao.ListItemDao;
import app.taolin.cnbeta.models.ArticleModel;
import app.taolin.cnbeta.models.ListItemModel;
import app.taolin.cnbeta.models.HeadlineModel;
import app.taolin.cnbeta.utils.Constants;
import app.taolin.cnbeta.utils.ContentUtil;
import app.taolin.cnbeta.utils.GsonRequest;
import app.taolin.cnbeta.utils.VolleySingleton;
import cn.appsdream.nestrefresh.base.AbsRefreshLayout;
import cn.appsdream.nestrefresh.base.OnPullListener;
import cn.appsdream.nestrefresh.normalstyle.NestRefreshLayout;

public class MainActivity extends AppCompatActivity implements OnPullListener {

    private static final String MAX_TIME = "max_time";
    private static final String MIN_TIME = "0";
    private static final long REFRESH_INTERVAL = 5 * 60 * 1000;
    private static final int HEADLINE_NUM = 3;

    private List<View> mHeadlineViews;
    private List<ListItem> mDataList;
    private ContentListAdapter mContentListAdapter;
    private NestRefreshLayout mLoader;

    private ListItemDao mListItemDao;
    private HeadlineDao mHeadlineDao;

    private long mLastRefreshTime;
    private int mLastKeyCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initDatabase();
        initView();
        requestData(true);
    }

    private void initView() {
        SwipeMenuListView contentList = (SwipeMenuListView) findViewById(R.id.content_list);
        addHeaderView(contentList);
        addSwipeMenu(contentList);

        mDataList = new ArrayList<>();
        mContentListAdapter = new ContentListAdapter(mDataList, false);
        contentList.setAdapter(mContentListAdapter);
        contentList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                //position 0 is the listview header
                ListItem item = mContentListAdapter.getItem(position - 1);
                item.setIsread(true);
                openContent(item.getSid());
                mContentListAdapter.notifyDataSetChanged();
                updateDatabase(item);
            }
        });

        mLoader = new NestRefreshLayout(contentList);
        mLoader.setPullRefreshEnable(true);
        mLoader.setPullLoadEnable(true);
        mLoader.setOnLoadingListener(this);
    }

    private void addHeaderView(final SwipeMenuListView listView) {
        // add header
        View header = LayoutInflater.from(this).inflate(R.layout.header_view, listView, false);
        final ViewPager headlines = (ViewPager) header.findViewById(R.id.headline_list);
        mHeadlineViews = new ArrayList<>();
        final LayoutInflater inflator = LayoutInflater.from(this);
        for (int i = 0; i < HEADLINE_NUM; i++) {
            mHeadlineViews.add(inflator.inflate(R.layout.headline, null));
        }
        headlines.setOffscreenPageLimit(2);
        ViewPagerAdapter headlineAdapter = new ViewPagerAdapter();
        headlines.setAdapter(headlineAdapter);
        PageIndicator pageIndicator = (PageIndicator) header.findViewById(R.id.indicator);
        pageIndicator.setViewPager(headlines);
        listView.addHeaderView(header);
    }

    private void addSwipeMenu(final SwipeMenuListView listView) {
        // add swipe menu
        SwipeMenuCreator creator = new SwipeMenuCreator() {
            @Override
            public void create(SwipeMenu menu) {
                Resources res = MainActivity.this.getResources();
                SwipeMenuItem menuItem = new SwipeMenuItem(MainActivity.this);
                menuItem.setBackground(R.color.colorPrimary);
                menuItem.setWidth(res.getDimensionPixelSize(R.dimen.swipe_menu_item_width));
                menuItem.setTitle(R.string.settings_favor);
                menuItem.setTitleSize(16);
                menuItem.setTitleColor(Color.WHITE);
                menu.addMenuItem(menuItem);
            }
        };
        listView.setMenuCreator(creator);
        listView.setSwipeDirection(SwipeMenuListView.DIRECTION_LEFT);
        listView.setOnMenuItemClickListener(new SwipeMenuListView.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(int position, SwipeMenu menu, int index) {
                switch (index) {
                    case 0:
                        ListItem item = mDataList.get(position);
                        item.setIsfavor(true);
                        item.setCollecttime(ContentUtil.getFormatTime());
                        updateDatabase(item);
                        Toast.makeText(MainActivity.this, R.string.favor_toast, Toast.LENGTH_SHORT).show();
                        break;
                }
                return false;
            }
        });
    }

    private void requestData(final boolean isRefresh) {
        if (isRefresh) {
            final long currentTime = System.currentTimeMillis();
            if (currentTime - mLastRefreshTime < REFRESH_INTERVAL) {
                mContentListAdapter.notifyDataSetChanged();
                mLoader.onLoadFinished();
                return;
            }
        }
        final int size = mDataList.size();
        final String sid = (isRefresh || size == 0) ? Integer.MAX_VALUE + "" : mDataList.get(size - 1).getSid();
        GsonRequest contentRequest = new GsonRequest<>(ContentUtil.getContentListUrl(sid), ListItemModel.class, null,
                new Response.Listener<ListItemModel>() {
                    @Override
                    public void onResponse(ListItemModel response) {
                        if ("success".equals(response.status)) {
                            saveToDatabase(response.result);
                            if (isRefresh) {
                                String pubTime = MIN_TIME;
                                if (mDataList.size() > 0) {
                                    pubTime = mDataList.get(0).getPubtime();
                                }
                                mDataList.addAll(loadFromDatabase(pubTime, false));
                                mLastRefreshTime = System.currentTimeMillis();
                            } else {
                                String pubTime = MAX_TIME;
                                if (mDataList.size() > 0) {
                                    pubTime = mDataList.get(mDataList.size() - 1).getPubtime();
                                }
                                mDataList.addAll(loadFromDatabase(pubTime, true));
                            }
                            removeDuplicate(mDataList);
                            mContentListAdapter.notifyDataSetChanged();
                        } else {
                            final boolean isEmpty = mDataList.size() == 0;
                            if (!isRefresh || isEmpty) {
                                String pubTime = isEmpty ? MAX_TIME: mDataList.get(mDataList.size() - 1).getPubtime();
                                mDataList.addAll(loadFromDatabase(pubTime, true));
                                removeDuplicate(mDataList);
                                mContentListAdapter.notifyDataSetChanged();
                            }
                            if (isRefresh) {
                                mLastRefreshTime = 0;
                            }
                        }
                        mLoader.onLoadFinished();
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e("Taolin", error.getMessage());
                final boolean isEmpty = mDataList.size() == 0;
                if (!isRefresh || isEmpty) {
                    String pubTime = isEmpty ? MAX_TIME: mDataList.get(size - 1).getPubtime();
                    mDataList.addAll(loadFromDatabase(pubTime, true));
                    removeDuplicate(mDataList);
                    mContentListAdapter.notifyDataSetChanged();
                }
                if (isRefresh) {
                    mLastRefreshTime = 0;
                }
                mLoader.onLoadFinished();
            }
        });
        contentRequest.setShouldCache(false);
        VolleySingleton.getInstance().addToRequestQueue(contentRequest);

        if (isRefresh) {
            refreshHeadline();
        }
    }

    private void refreshHeadline() {
        List<HeadlineModel> headlineList = loadHeadlineFromDatabase();
        if (headlineList.size() == HEADLINE_NUM) {
            setHeadlineView(headlineList);
        }
        requestHeadlineList(headlineList);
    }

    private List<HeadlineModel> loadHeadlineFromDatabase() {
        List<HeadlineModel> headlineList = new ArrayList<>();
        List<Headline> heads = mHeadlineDao.queryBuilder().orderAsc(HeadlineDao.Properties.Index).list();
        for (Headline head: heads) {
            HeadlineModel headData = new HeadlineModel();
            headData.sid = head.getSid();
            headData.title = head.getTitle();
            headData.thumb = head.getThumb();
            headData.index = head.getIndex();
            headlineList.add(headData);
        }
        return headlineList;
    }

    private void setHeadlineView(final List<HeadlineModel> headList) {
        for (int i = 0; i < HEADLINE_NUM; i++) {
            final View headView = mHeadlineViews.get(i);
            final HeadlineModel headData = headList.get(i);
            headView.setTag(headData.sid);

            final NetworkImageView thumb = (NetworkImageView) headView.findViewById(R.id.headline_thumb);
            final TextView title = (TextView) headView.findViewById(R.id.headline_title);
            thumb.setImageUrl(headData.thumb, VolleySingleton.getInstance().getImageLoader());
            title.setText(headData.title);

            headView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openContent(headData.sid);
                }
            });
        }
    }

    private void requestHeadlineList(final List<HeadlineModel> headlineList) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Document document = Jsoup.connect("http://www.cnbeta.com/").timeout(10000).get();
                    Elements elements = document.select(".main_content .headline dl");
                    List<HeadlineModel> tempList = new ArrayList<>();
                    boolean needUpdate = false;
                    for (Element e: elements) {
                        HeadlineModel headline = new HeadlineModel();
                        final String link = e.select("dt a").first().attr("href");
                        headline.sid = link.substring(link.lastIndexOf("/") + 1, link.lastIndexOf("."));
                        headline.title = e.select("dt a").first().text();
                        headline.thumb = e.select("dd a img").first().attr("src");
                        headline.index = elements.indexOf(e);
                        tempList.add(headline);
                        boolean tempNeedUpdate = false;
                        if (headlineList.size() != HEADLINE_NUM) {
                            needUpdate = true;
                            tempNeedUpdate = true;
                            headlineList.clear();
                            mHeadlineDao.deleteAll();
                        } else {
                            for (HeadlineModel head: headlineList) {
                                if (head.index == headline.index && !head.equals(headline)) {
                                    needUpdate = true;
                                    tempNeedUpdate = true;
                                    mHeadlineDao.deleteByKey(head.sid);
                                }
                            }
                        }
                        requestHeadlineItem(headline, tempNeedUpdate);
                    }
                    if (needUpdate && tempList.size() == HEADLINE_NUM) {
                        headlineList.clear();
                        headlineList.addAll(tempList);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void requestHeadlineItem(final HeadlineModel data, final boolean needUpdate) {
        GsonRequest contentRequest = new GsonRequest<>(ContentUtil.getContentUrl(data.sid),
                ArticleModel.class, null,
                new Response.Listener<ArticleModel>() {
                    @Override
                    public void onResponse(ArticleModel response) {
                        try {
                            if ("success".equals(response.status)) {
                                data.title = response.result.title.trim();
                                Document document = Jsoup.parse(response.result.bodytext);
                                Element imgElement = document.select("img").first();
                                if (imgElement != null) {
                                    data.thumb = imgElement.attr("src");
                                }
                                if (needUpdate) {
                                    saveToDatabase(data);
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            updateSingleHeadline(data);
                                        }
                                    });
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e("Taolin", error.getMessage());
            }
        });
        contentRequest.setShouldCache(false);
        VolleySingleton.getInstance().addToRequestQueue(contentRequest);
    }

    private void updateSingleHeadline(final HeadlineModel data) {
        final View headView = mHeadlineViews.get(data.index);
        headView.setTag(data.sid);

        final NetworkImageView thumb = (NetworkImageView) headView.findViewById(R.id.headline_thumb);
        final TextView title = (TextView) headView.findViewById(R.id.headline_title);
        thumb.setImageUrl(data.thumb, VolleySingleton.getInstance().getImageLoader());
        title.setText(data.title);

        headView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openContent(data.sid);
            }
        });
    }

    private void initDatabase() {
        DaoMaster.DevOpenHelper helper = new DaoMaster.DevOpenHelper(this, Constants.DATABASE_NAME, null);
        SQLiteDatabase database = helper.getWritableDatabase();
        DaoMaster daoMaster = new DaoMaster(database);
        DaoSession daoSession = daoMaster.newSession();
        mListItemDao = daoSession.getListItemDao();
        mHeadlineDao = daoSession.getHeadlineDao();
    }

    private void openContent(final String sid) {
        Intent intent = new Intent(this, ContentActivity.class);
        intent.putExtra(Constants.KEY_EXTRA_SID, sid);
        startActivity(intent);
    }

    private void removeDuplicate(List<ListItem> list) {
        ArrayList<ListItem> l = new ArrayList<>(new LinkedHashSet<>(list));
        list.clear();
        list.addAll(l);
        Collections.sort(list);
    }

    private List<ListItem> loadFromDatabase(final String pubTime, final boolean lessThan) {
        return mListItemDao.queryBuilder()
                .where(lessThan ? ListItemDao.Properties.Pubtime.lt(pubTime):
                    ListItemDao.Properties.Pubtime.gt(pubTime))
                .orderDesc(ListItemDao.Properties.Pubtime)
                .limit(20)
                .list();
    }

    private void saveToDatabase(final List<ListItemModel.Result> list) {
        for (ListItemModel.Result r: list) {
            ListItem item = new ListItem();
            item.setSid(r.sid);
            item.setTitle(r.title);
            item.setPubtime(r.pubtime);
            item.setIsread(r.is_read);
            item.setIsfavor(r.is_favor);
            item.setCollecttime(r.collect_time);
            try {
                mListItemDao.insert(item);
            } catch (SQLiteConstraintException e) {
                Log.e("Taolin", "primary key duplicated, skip this error.");
            }
        }
    }

    private void saveToDatabase(HeadlineModel headline) {
        Headline item = new Headline();
        item.setSid(headline.sid);
        item.setTitle(headline.title);
        item.setThumb(headline.thumb);
        item.setIndex(headline.index);
        try {
            mHeadlineDao.insertOrReplace(item);
        } catch (SQLiteConstraintException e) {
            Log.e("Taolin", "primary key duplicated, skip this error.");
        }
    }

    private void updateDatabase(final ListItem item) {
        mListItemDao.update(item);
    }

    @Override
    public void onRefresh(AbsRefreshLayout listLoader) {
        requestData(true);
    }

    @Override
    public void onLoading(AbsRefreshLayout listLoader) {
        requestData(false);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (mLastKeyCode == KeyEvent.KEYCODE_BACK) {
                finish();
            } else {
                Toast.makeText(this, R.string.press_back_again_to_exit, Toast.LENGTH_SHORT).show();
                mLastKeyCode = keyCode;
            }
            return true;
        }
        mLastKeyCode = keyCode;
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.settings:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private class ViewPagerAdapter extends PagerAdapter implements IconPagerAdapter {

        View getItem(int pos) {
            return mHeadlineViews.get(pos);
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            container.addView(getItem(position), 0);
            return getItem(position);
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            container.removeView(getItem(position));
        }

        @Override
        public int getIconResId(int index) {
            return R.drawable.viewpager_indicator;
        }

        @Override
        public int getCount() {
            return mHeadlineViews.size();
        }
        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }
    }
}
