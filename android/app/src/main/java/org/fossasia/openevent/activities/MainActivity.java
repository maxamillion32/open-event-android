package org.fossasia.openevent.activities;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
import android.text.method.LinkMovementMethod;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.gson.Gson;
import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;
import com.squareup.picasso.Picasso;

import org.fossasia.openevent.OpenEventApp;
import org.fossasia.openevent.R;
import org.fossasia.openevent.api.Urls;
import org.fossasia.openevent.api.protocol.EventDatesResponseList;
import org.fossasia.openevent.api.protocol.EventResponseList;
import org.fossasia.openevent.api.protocol.MicrolocationResponseList;
import org.fossasia.openevent.api.protocol.SessionResponseList;
import org.fossasia.openevent.api.protocol.SpeakerResponseList;
import org.fossasia.openevent.api.protocol.SponsorResponseList;
import org.fossasia.openevent.api.protocol.TrackResponseList;
import org.fossasia.openevent.data.Event;
import org.fossasia.openevent.data.EventDates;
import org.fossasia.openevent.data.Microlocation;
import org.fossasia.openevent.data.Session;
import org.fossasia.openevent.data.SessionSpeakersMapping;
import org.fossasia.openevent.data.Speaker;
import org.fossasia.openevent.data.Sponsor;
import org.fossasia.openevent.data.Track;
import org.fossasia.openevent.dbutils.DataDownloadManager;
import org.fossasia.openevent.dbutils.DbSingleton;
import org.fossasia.openevent.events.CounterEvent;
import org.fossasia.openevent.events.DataDownloadEvent;
import org.fossasia.openevent.events.EventDatesDownloadEvent;
import org.fossasia.openevent.events.EventDownloadEvent;
import org.fossasia.openevent.events.JsonReadEvent;
import org.fossasia.openevent.events.MicrolocationDownloadEvent;
import org.fossasia.openevent.events.NoInternetEvent;
import org.fossasia.openevent.events.RefreshUiEvent;
import org.fossasia.openevent.events.RetrofitError;
import org.fossasia.openevent.events.RetrofitResponseEvent;
import org.fossasia.openevent.events.SessionDownloadEvent;
import org.fossasia.openevent.events.ShowNetworkDialogEvent;
import org.fossasia.openevent.events.SpeakerDownloadEvent;
import org.fossasia.openevent.events.SponsorDownloadEvent;
import org.fossasia.openevent.events.TracksDownloadEvent;
import org.fossasia.openevent.fragments.BookmarksFragment;
import org.fossasia.openevent.fragments.LocationsFragment;
import org.fossasia.openevent.fragments.ScheduleFragment;
import org.fossasia.openevent.fragments.SpeakerFragment;
import org.fossasia.openevent.fragments.SponsorsFragment;
import org.fossasia.openevent.fragments.TracksFragment;
import org.fossasia.openevent.utils.CommonTaskLoop;
import org.fossasia.openevent.utils.ConstantStrings;
import org.fossasia.openevent.utils.NetworkUtils;
import org.fossasia.openevent.utils.SmoothActionBarDrawerToggle;
import org.fossasia.openevent.widget.DialogFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import butterknife.Bind;
import butterknife.ButterKnife;
import timber.log.Timber;

public class MainActivity extends BaseActivity {


    private static final String COUNTER_TAG = "Donecounter";

    private final static String STATE_FRAGMENT = "stateFragment";

    private static final String NAV_ITEM = "navItem";

    private static final String BOOKMARK = "bookmarks";

    private final String FRAGMENT_TAG = "FTAG";

    private String errorType;

    private String errorDesc;

    private SharedPreferences sharedPreferences;

    @Bind(R.id.toolbar)
    Toolbar toolbar;

    @Bind(R.id.nav_view)
    NavigationView navigationView;

    @Bind(R.id.progress)
    ProgressBar downloadProgress;

    @Bind(R.id.layout_main)
    CoordinatorLayout mainFrame;

    @Bind(R.id.drawer)
    DrawerLayout drawerLayout;

    private int counter;

    private int eventsDone;

    private int currentMenuItemId;

    private SmoothActionBarDrawerToggle smoothActionBarToggle;
    private AppBarLayout appBarLayout;

    public static Intent createLaunchFragmentIntent(Context context) {
        return new Intent(context, MainActivity.class)
                .putExtra(NAV_ITEM, BOOKMARK);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            View v = getCurrentFocus();
            if (v instanceof EditText) {
                Rect outRect = new Rect();
                v.getGlobalVisibleRect(outRect);
                if (!outRect.contains((int) event.getRawX(), (int) event.getRawY())) {
                    v.clearFocus();
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                }
            }
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        counter = 0;
        eventsDone = 0;
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        appBarLayout = (AppBarLayout) findViewById(R.id.appbar);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        setUpToolbar();
        setUpNavDrawer();

        downloadProgress.setVisibility(View.VISIBLE);
        downloadProgress.setIndeterminate(true);
        this.findViewById(android.R.id.content).setBackgroundColor(Color.LTGRAY);
        if (NetworkUtils.haveNetworkConnection(this)) {
            if (!sharedPreferences.getBoolean(ConstantStrings.IS_DOWNLOAD_DONE, false)) {
                AlertDialog.Builder downloadDialog = new AlertDialog.Builder(this);
                downloadDialog.setTitle(R.string.download_assets);
                downloadDialog.setIcon(R.drawable.ic_file_download_black_24dp);
                downloadDialog.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        DbSingleton.getInstance().clearDatabase();
                        OpenEventApp.postEventOnUIThread(new DataDownloadEvent());
                        sharedPreferences.edit().putBoolean(ConstantStrings.IS_DOWNLOAD_DONE, true).apply();
                        TracksFragment.setVisibility(true);
                    }
                });
                downloadDialog.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        downloadFromAssets();
                    }
                });
                downloadDialog.show();
            }
            else {
                OpenEventApp.postEventOnUIThread(new DataDownloadEvent());
            }
        } else if (!sharedPreferences.getBoolean(ConstantStrings.DATABASE_RECORDS_EXIST, false)) {
            downloadFromAssets();
        } else {
            //TODO : Add some feedback on the error
            downloadProgress.setVisibility(View.GONE);
        }
        if (savedInstanceState == null) {
            currentMenuItemId = R.id.nav_tracks;
        } else {
            currentMenuItemId = savedInstanceState.getInt(STATE_FRAGMENT);
        }

        if (getIntent().hasExtra(NAV_ITEM)) {
            if (getIntent().getStringExtra(NAV_ITEM).equalsIgnoreCase(BOOKMARK)) {
                currentMenuItemId = R.id.nav_bookmarks;
            }
        }

        if (getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG) == null) {
            doMenuAction(currentMenuItemId);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        OpenEventApp.getEventBus().unregister(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        OpenEventApp.getEventBus().register(this);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(STATE_FRAGMENT, currentMenuItemId);
        super.onSaveInstanceState(outState);
    }

    @Override
    public boolean onPrepareOptionsMenu(final Menu menu) {
        setupDrawerContent(navigationView, menu);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_tracks, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Will close the drawer if the home button is pressed
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        switch (id) {
            case R.id.home:
                drawerLayout.openDrawer(GravityCompat.START);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(navigationView)) {
            drawerLayout.closeDrawer(navigationView);
        } else {
            super.onBackPressed();
        }
    }

    private void setUpToolbar() {
        if (toolbar != null) {
            setSupportActionBar(toolbar);
        }
    }

    private void setUpNavDrawer() {
        if (toolbar != null) {
            final ActionBar ab = getSupportActionBar();
            assert ab != null;
            smoothActionBarToggle = new SmoothActionBarDrawerToggle(this,
                    drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);

            drawerLayout.addDrawerListener(smoothActionBarToggle);
            ab.setHomeAsUpIndicator(R.drawable.ic_menu);
            ab.setDisplayHomeAsUpEnabled(true);
            ab.setDisplayHomeAsUpEnabled(true);
            smoothActionBarToggle.syncState();
        }
    }

    private void syncComplete() {
        downloadProgress.setVisibility(View.GONE);
        Bus bus = OpenEventApp.getEventBus();
        bus.post(new RefreshUiEvent());
        DbSingleton dbSingleton = DbSingleton.getInstance();
        try {
            if (!(dbSingleton.getEventDetails().getLogo().isEmpty())) {
                ImageView headerDrawer = (ImageView) findViewById(R.id.headerDrawer);
                Picasso.with(getApplicationContext()).load(dbSingleton.getEventDetails().getLogo()).into(headerDrawer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        Snackbar.make(mainFrame, getString(R.string.download_complete), Snackbar.LENGTH_SHORT).show();
        Timber.d("Download done");
    }

    private void downloadFailed() {
        downloadProgress.setVisibility(View.GONE);
        Snackbar.make(mainFrame, getString(R.string.download_failed), Snackbar.LENGTH_LONG).show();

    }

    private void setupDrawerContent(NavigationView navigationView, final Menu menu) {
        navigationView.setNavigationItemSelectedListener(
                new NavigationView.OnNavigationItemSelectedListener() {
                    @Override
                    public boolean onNavigationItemSelected(MenuItem menuItem) {
                        int id = menuItem.getItemId();
                        menu.clear();
                        doMenuAction(id);
                        return true;
                    }
                });
    }

    private void doMenuAction(int menuItemId) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        addShadowToAppBar(true);
        switch (menuItemId) {
            case R.id.nav_tracks:
                fragmentManager.beginTransaction()
                        .replace(R.id.content_frame, new TracksFragment(), FRAGMENT_TAG).commit();
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setTitle(R.string.menu_tracks);
                }
                break;
            case R.id.nav_schedule:
                fragmentManager.beginTransaction()
                        .replace(R.id.content_frame, new ScheduleFragment(), FRAGMENT_TAG).commit();
                addShadowToAppBar(false);
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setTitle(R.string.menu_schedule);
                }
                break;
            case R.id.nav_bookmarks:
                DbSingleton dbSingleton = DbSingleton.getInstance();
                if (!dbSingleton.isBookmarksTableEmpty()) {
                    fragmentManager.beginTransaction()
                            .replace(R.id.content_frame, new BookmarksFragment(), FRAGMENT_TAG).commit();
                    if (getSupportActionBar() != null) {
                        getSupportActionBar().setTitle(R.string.menu_bookmarks);
                    }
                } else {
                    DialogFactory.createSimpleActionDialog(this, R.string.bookmarks, R.string.empty_list, null).show();
                }
                break;
            case R.id.nav_speakers:
                fragmentManager.beginTransaction()
                        .replace(R.id.content_frame, new SpeakerFragment(), FRAGMENT_TAG).commit();
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setTitle(R.string.menu_speakers);
                }
                break;
            case R.id.nav_sponsors:
                fragmentManager.beginTransaction()
                        .replace(R.id.content_frame, new SponsorsFragment(), FRAGMENT_TAG).commit();
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setTitle(R.string.menu_sponsor);
                }
                break;
            case R.id.nav_locations:
                fragmentManager.beginTransaction()
                        .replace(R.id.content_frame, new LocationsFragment(), FRAGMENT_TAG).commit();
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setTitle(R.string.menu_locations);
                }
                break;
            case R.id.nav_map:
                FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();

                fragmentTransaction.replace(R.id.content_frame,
                        ((OpenEventApp) getApplication())
                                .getMapModuleFactory()
                                .provideMapModule()
                                .provideMapFragment(), FRAGMENT_TAG).commit();
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setTitle(R.string.menu_map);
                }
                break;
            case R.id.nav_settings:
                final Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                smoothActionBarToggle.runWhenIdle(new Runnable() {
                    @Override
                    public void run() {
                        startActivity(intent);
                        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_left);
                    }
                });
                break;
            case R.id.nav_about:
                AlertDialog.Builder builder =
                        new AlertDialog.Builder(this);
                builder.setTitle(String.format("%1$s", getString(R.string.app_name)));
                builder.setMessage(getResources().getText(R.string.about_text));
                builder.setPositiveButton("OK", null);
                builder.setIcon(R.mipmap.ic_launcher);
                AlertDialog welcomeAlert = builder.create();
                welcomeAlert.show();
                ((TextView) welcomeAlert.findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());
                break;
        }
        currentMenuItemId = menuItemId;
        drawerLayout.closeDrawers();
    }

    public void addShadowToAppBar(boolean addShadow) {
        if (addShadow) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                appBarLayout.setElevation(12);
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                appBarLayout.setElevation(0);
            }
        }
    }

    public void showErrorDialog(String errorType, String errorDesc) {
        downloadProgress.setVisibility(View.GONE);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.error)
                .setMessage(errorType + ": " + errorDesc)
                .setNeutralButton(R.string.ok, null)
                .create();
        builder.show();
    }

    //Subscribe Events
    @Subscribe
    public void onCounterReceiver(CounterEvent event) {
        counter = event.getRequestsCount();
        Timber.tag(COUNTER_TAG).d(counter + "");
        if (counter == 0) {
            syncComplete();
        }
    }

    @Subscribe
    public void onTracksDownloadDone(TracksDownloadEvent event) {
        if (event.isState()) {
            eventsDone++;
            Timber.tag(COUNTER_TAG).d(eventsDone + " " + counter);
            if (counter == eventsDone) {
                syncComplete();
            }
        } else {
            downloadFailed();
        }
    }

    @Subscribe
    public void onSponsorsDownloadDone(SponsorDownloadEvent event) {
        if (event.isState()) {
            eventsDone++;
            Timber.tag(COUNTER_TAG).d(eventsDone + " " + counter);
            if (counter == eventsDone) {
                syncComplete();
            }
        } else {

            downloadFailed();
        }
    }

    @Subscribe
    public void onSpeakersDownloadDone(SpeakerDownloadEvent event) {
        if (event.isState()) {
            eventsDone++;
            Timber.tag(COUNTER_TAG).d(eventsDone + " " + counter);
            if (counter == eventsDone) {
                syncComplete();
            }
        } else {

            downloadFailed();
        }
    }

    @Subscribe
    public void onSessionDownloadDone(SessionDownloadEvent event) {
        if (event.isState()) {
            eventsDone++;
            Timber.tag(COUNTER_TAG).d(eventsDone + " " + counter);
            if (counter == eventsDone) {
                syncComplete();
            }
        } else {

            downloadFailed();
        }
    }

    @Subscribe
    public void noInternet(NoInternetEvent event) {
        downloadFailed();
    }

    @Subscribe
    public void onEventsDownloadDone(EventDownloadEvent event) {
        if (event.isState()) {
            eventsDone++;
            Timber.tag(COUNTER_TAG).d(eventsDone + " " + counter);
            if (counter == eventsDone) {
                syncComplete();
            }
        } else {

            downloadFailed();
        }
    }

    @Subscribe
    public void onMicrolocationsDownloadDone(MicrolocationDownloadEvent event) {
        if (event.isState()) {
            eventsDone++;
            Timber.tag(COUNTER_TAG).d(eventsDone + " " + counter);
            if (counter == eventsDone) {
                syncComplete();
            }
        } else {

            downloadFailed();
        }

    }

    @Subscribe
    public void onEventDatesDownloadDone(EventDatesDownloadEvent event) {
        if (event.isState()) {
            eventsDone++;
            Timber.tag(COUNTER_TAG).d(eventsDone + " " + counter);
            if (counter == eventsDone) {
                syncComplete();
            }
        } else {
            downloadFailed();
        }
    }

    @Subscribe
    public void showNetworkDialog(ShowNetworkDialogEvent event) {
        downloadProgress.setVisibility(View.GONE);
        DialogFactory.createSimpleActionDialog(this,
                R.string.net_unavailable,
                R.string.turn_on,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent setNetworkIntent = new Intent(Settings.ACTION_SETTINGS);
                        startActivity(setNetworkIntent);
                    }
                }).show();
    }

    @Subscribe
    public void downloadData(DataDownloadEvent event) {
        if (Urls.getBaseUrl().equals(Urls.INVALID_LINK)) {
            showErrorDialog("Invalid Api", "Api link doesn't seem to be valid");
        } else {
            DataDownloadManager.getInstance().downloadVersions();
        }
        downloadProgress.setVisibility(View.VISIBLE);
        Timber.d("Download has started");
    }

    @Subscribe
    public void handleResponseEvent(RetrofitResponseEvent responseEvent) {
        Integer statusCode = responseEvent.getStatusCode();
        if (statusCode.equals(404)) {
            showErrorDialog("HTTP Error", statusCode + "Api Not Found");
        }
    }

    @Subscribe
    public void handleJsonEvent(JsonReadEvent jsonReadEvent) {
        final String name = jsonReadEvent.getName();
        final String json = jsonReadEvent.getJson();
        CommonTaskLoop.getInstance().post(new Runnable() {
            @Override
            public void run() {
                final Gson gson = new Gson();
                if (name.equals(ConstantStrings.Events)) {
                    CommonTaskLoop.getInstance().post(new Runnable() {
                        @Override
                        public void run() {
                            EventResponseList eventResponseList = gson.fromJson(json, EventResponseList.class);
                            ArrayList<String> queries = new ArrayList<String>();
                            for (Event current : eventResponseList.event) {
                                queries.add(current.generateSql());
                            }
                            DbSingleton.getInstance().insertQueries(queries);
                            OpenEventApp.postEventOnUIThread(new EventDownloadEvent(true));
                        }
                    });
                } else if (name.equals(ConstantStrings.Tracks)) {
                    CommonTaskLoop.getInstance().post(new Runnable() {
                        @Override
                        public void run() {
                            TrackResponseList trackResponseList = gson.fromJson(json, TrackResponseList.class);
                            ArrayList<String> queries = new ArrayList<String>();
                            for (Track current : trackResponseList.tracks) {
                                queries.add(current.generateSql());
                            }
                            DbSingleton.getInstance().insertQueries(queries);
                            OpenEventApp.postEventOnUIThread(new TracksDownloadEvent(true));
                        }
                    });
                } else if (name.equals(ConstantStrings.Sessions)) {

                    SessionResponseList sessionResponseList = gson.fromJson(json, SessionResponseList.class);
                    ArrayList<String> queries = new ArrayList<String>();
                    for (Session current : sessionResponseList.sessions) {
                        current.setStartDate(current.getStartTime().split("T")[0]);
                        queries.add(current.generateSql());
                    }
                    DbSingleton.getInstance().insertQueries(queries);
                    OpenEventApp.postEventOnUIThread(new SessionDownloadEvent(true));

                } else if (name.equals(ConstantStrings.Speakers)) {

                    SpeakerResponseList speakerResponseList = gson.fromJson(json, SpeakerResponseList.class);
                    ArrayList<String> queries = new ArrayList<String>();
                    for (Speaker current : speakerResponseList.speakers) {
                        for (int i = 0; i < current.getSession().length; i++) {
                            SessionSpeakersMapping sessionSpeakersMapping = new SessionSpeakersMapping(current.getSession()[i], current.getId());
                            String query_ss = sessionSpeakersMapping.generateSql();
                            queries.add(query_ss);
                        }

                        queries.add(current.generateSql());
                    }
                    DbSingleton.getInstance().insertQueries(queries);
                    OpenEventApp.postEventOnUIThread(new SpeakerDownloadEvent(true));

                } else if (name.equals(ConstantStrings.EventDates)) {
                    CommonTaskLoop.getInstance().post(new Runnable() {
                        @Override
                        public void run() {
                            EventDatesResponseList eventDatesResponseList = gson.fromJson(json, EventDatesResponseList.class);
                            ArrayList<String> queries = new ArrayList<String>();
                            for (EventDates current : eventDatesResponseList.event) {
                                queries.add(current.generateSql());
                            }
                            DbSingleton.getInstance().insertQueries(queries);
                            OpenEventApp.postEventOnUIThread(new EventDatesDownloadEvent(true));
                        }
                    });
                } else if (name.equals(ConstantStrings.Sponsors)) {
                    CommonTaskLoop.getInstance().post(new Runnable() {
                        @Override
                        public void run() {
                            SponsorResponseList sponsorResponseList = gson.fromJson(json, SponsorResponseList.class);
                            ArrayList<String> queries = new ArrayList<String>();
                            for (Sponsor current : sponsorResponseList.sponsors) {
                                queries.add(current.generateSql());
                            }
                            DbSingleton.getInstance().insertQueries(queries);
                            OpenEventApp.postEventOnUIThread(new SponsorDownloadEvent(true));
                        }
                    });
                } else if (name.equals(ConstantStrings.Microlocations)) {
                    CommonTaskLoop.getInstance().post(new Runnable() {
                        @Override
                        public void run() {
                            MicrolocationResponseList microlocationResponseList = gson.fromJson(json, MicrolocationResponseList.class);
                            ArrayList<String> queries = new ArrayList<String>();
                            for (Microlocation current : microlocationResponseList.microlocations) {
                                queries.add(current.generateSql());
                            }
                            DbSingleton.getInstance().insertQueries(queries);
                            OpenEventApp.postEventOnUIThread(new MicrolocationDownloadEvent(true));
                        }
                    });
                }
            }
        });

    }

    @Subscribe
    public void errorHandlerEvent(RetrofitError error) {
        ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netinfo = connMgr.getActiveNetworkInfo();
        if (!(netinfo != null && netinfo.isConnected())) {
            OpenEventApp.postEventOnUIThread(new ShowNetworkDialogEvent());
        } else {
            if (error.getThrowable() instanceof IOException) {
                errorType = "Timeout";
                errorDesc = String.valueOf(error.getThrowable().getCause());
            } else if (error.getThrowable() instanceof IllegalStateException) {
                errorType = "ConversionError";
                errorDesc = String.valueOf(error.getThrowable().getCause());
            } else {
                errorType = "Other Error";
                errorDesc = String.valueOf(error.getThrowable().getLocalizedMessage());
            }
            Timber.tag(errorType).e(errorDesc);
            showErrorDialog(errorType, errorDesc);
        }
    }

    public void downloadFromAssets() {
        //TODO: Add and Take counter value from to config.json
        sharedPreferences.edit().putBoolean(ConstantStrings.DATABASE_RECORDS_EXIST, true).apply();
        counter = 7;
        readJsonAsset("events");
        readJsonAsset("tracks");
        readJsonAsset("speakers");
        readJsonAsset("eventDates");
        readJsonAsset("sessions");
        readJsonAsset("sponsors");
        readJsonAsset("microlocations");

    }

    public void readJsonAsset(final String name) {
        CommonTaskLoop.getInstance().post(new Runnable() {
            String json = null;

            @Override
            public void run() {
                try {
                    InputStream inputStream = getAssets().open(name + ".json");
                    int size = inputStream.available();
                    byte[] buffer = new byte[size];
                    inputStream.read(buffer);
                    inputStream.close();
                    json = new String(buffer, "UTF-8");


                } catch (IOException e) {
                    e.printStackTrace();


                }
                OpenEventApp.postEventOnUIThread(new JsonReadEvent(name, json));

            }
        });
    }
}