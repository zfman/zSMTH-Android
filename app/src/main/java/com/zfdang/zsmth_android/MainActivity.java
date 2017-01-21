package com.zfdang.zsmth_android;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.mikepenz.aboutlibraries.LibsBuilder;
import com.oguzdev.circularfloatingactionmenu.library.FloatingActionMenu;
import com.oguzdev.circularfloatingactionmenu.library.SubActionButton;
import com.umeng.analytics.MobclickAgent;
import com.zfdang.SMTHApplication;
import com.zfdang.zsmth_android.fresco.WrapContentDraweeView;
import com.zfdang.zsmth_android.listeners.OnBoardFragmentInteractionListener;
import com.zfdang.zsmth_android.listeners.OnMailInteractionListener;
import com.zfdang.zsmth_android.listeners.OnTopicFragmentInteractionListener;
import com.zfdang.zsmth_android.listeners.OnVolumeUpDownListener;
import com.zfdang.zsmth_android.models.Board;
import com.zfdang.zsmth_android.models.Mail;
import com.zfdang.zsmth_android.models.MailListContent;
import com.zfdang.zsmth_android.models.Topic;
import com.zfdang.zsmth_android.newsmth.AjaxResponse;
import com.zfdang.zsmth_android.newsmth.SMTHHelper;
import com.zfdang.zsmth_android.services.MaintainUserStatusService;
import com.zfdang.zsmth_android.services.UserStatusReceiver;

import java.lang.reflect.Field;

import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

public class MainActivity extends SMTHBaseActivity
        implements NavigationView.OnNavigationItemSelectedListener,
        View.OnClickListener,
        OnTopicFragmentInteractionListener,
        OnBoardFragmentInteractionListener,
        OnMailInteractionListener
{
    // used by startActivityForResult
    static final int LOGIN_ACTIVITY_REQUEST_CODE = 9527;  // The request code
    private static final String TAG = "MainActivity";
    // guidance fragment: display hot topics
    // this fragment is using RecyclerView to show all hot topics
    HotTopicFragment hotTopicFragment = null;
    FavoriteBoardFragment favoriteBoardFragment = null;
    AllBoardFragment allBoardFragment = null;
    MailListFragment mailListFragment = null;
    MyPreferenceFragment preferenceFragment = null;
    Fragment aboutFragment = null;
    private WrapContentDraweeView mAvatar = null;
    private TextView mUsername = null;

    private DrawerLayout mDrawer = null;
    private ActionBarDrawerToggle mToggle = null;

    private UserStatusReceiver mReceiver;
    // press BACK in 2 seconds, app will quit
    private boolean mDoubleBackToExit = false;
    private Handler mHandler = null;
    private FloatingActionMenu mActionMenu;
    private NavigationView mNavigationView;

    private static final int notificationID = 273;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // how to adjust the height of toolbar
        // http://stackoverflow.com/questions/17439683/how-to-change-action-bar-size
        // zsmth_actionbar_size @ dimen ==> ThemeOverlay.ActionBar @ styles ==> theme @ app_bar_main.xml

        // init floating action button & circular action menu
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        initCircularActionMenu(fab);

        mDrawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        mToggle = new ActionBarDrawerToggle(
                this, mDrawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        mDrawer.addDrawerListener(mToggle);
        mToggle.syncState();

        mNavigationView = (NavigationView) findViewById(R.id.nav_view);
        mNavigationView.setNavigationItemSelectedListener(this);
        mNavigationView.setCheckedItem(R.id.nav_guidance);

        // http://stackoverflow.com/questions/33161345/android-support-v23-1-0-update-breaks-navigationview-get-find-header
        View headerView = mNavigationView.getHeaderView(0);
        mAvatar = (WrapContentDraweeView) headerView.findViewById(R.id.nav_user_avatar);
        mAvatar.setOnClickListener(this);

        mUsername = (TextView) headerView.findViewById(R.id.nav_user_name);
        mUsername.setOnClickListener(this);


        // http://stackoverflow.com/questions/27097126/marquee-title-in-toolbar-actionbar-in-android-with-lollipop-sdk
        TextView titleTextView = null;
        try {
            Field f = toolbar.getClass().getDeclaredField("mTitleTextView");
            f.setAccessible(true);
            titleTextView = (TextView) f.get(toolbar);
            titleTextView.setEllipsize(TextUtils.TruncateAt.START);
        } catch (NoSuchFieldException e) {
        } catch (IllegalAccessException e) {
        }

        // init all fragments
        initFragments();

        FragmentManager fm = getSupportFragmentManager();
        if(Settings.getInstance().isLaunchHotTopic()) {
            fm.beginTransaction().replace(R.id.content_frame, hotTopicFragment).commit();
        } else {
            fm.beginTransaction().replace(R.id.content_frame, favoriteBoardFragment).commit();
        }

        getSupportFragmentManager().addOnBackStackChangedListener(
                new FragmentManager.OnBackStackChangedListener() {
                    public void onBackStackChanged() {
                        //Enable Up button only  if there are entries in the back stack
                        boolean canback = getSupportFragmentManager().getBackStackEntryCount() > 0;
                        if (canback) {
                            mToggle.setDrawerIndicatorEnabled(false);
                            getSupportActionBar().setDisplayShowHomeEnabled(true);
                            getSupportActionBar().setHomeButtonEnabled(true);
                            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                        } else {
                            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
                            mToggle.setDrawerIndicatorEnabled(true);
                            mDrawer.addDrawerListener(mToggle);
                        }
                    }
                });

        // start service to maintain user status
        setupUserStatusReceiver();
        updateUserStatusNow();
        UpdateNavigationViewHeader();

        // schedule the periodical run
        MaintainUserStatusService.schedule(MainActivity.this, mReceiver);

        if(Settings.getInstance().isFirstRun()) {
            // show info dialog after 5 seconds for the first run
            final Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    showInfoDialog();
                }
            }, 2000);
        }
    }

    private void initCircularActionMenu(FloatingActionButton fab) {
        SubActionButton.Builder itemBuilder = new SubActionButton.Builder(this);

        ImageView itemIcon1 = new ImageView(this);
        itemIcon1.setImageDrawable(getResources().getDrawable(R.drawable.ic_whatshot_white_48dp));
        SubActionButton button1 = itemBuilder.setContentView(itemIcon1)
                .setBackgroundDrawable(getResources().getDrawable(R.drawable.navigation_button_background))
                .build();
        button1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mActionMenu.close(true);
                onNavigationItemID(R.id.nav_guidance);
                mNavigationView.setCheckedItem(R.id.nav_guidance);
            }
        });

        ImageView itemIcon2 = new ImageView(this);
        itemIcon2.setImageDrawable(getResources().getDrawable(R.drawable.ic_star_white_48dp));
        SubActionButton button2 = itemBuilder.setContentView(itemIcon2)
                .setBackgroundDrawable(getResources().getDrawable(R.drawable.navigation_button_background))
                .build();
        button2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mActionMenu.close(true);
                onNavigationItemID(R.id.nav_favorite);
                mNavigationView.setCheckedItem(R.id.nav_favorite);
            }
        });

        ImageView itemIcon3 = new ImageView(this);
        itemIcon3.setImageDrawable(getResources().getDrawable(R.drawable.ic_format_list_bulleted_white_48dp));
        SubActionButton button3 = itemBuilder.setContentView(itemIcon3)
                .setBackgroundDrawable(getResources().getDrawable(R.drawable.navigation_button_background))
                .build();
        button3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mActionMenu.close(true);
                onNavigationItemID(R.id.nav_all_boards);
                mNavigationView.setCheckedItem(R.id.nav_all_boards);
            }
        });

        ImageView itemIcon4 = new ImageView(this);
        itemIcon4.setImageDrawable(getResources().getDrawable(R.drawable.ic_email_white_48dp));
        SubActionButton button4 = itemBuilder.setContentView(itemIcon4)
                .setBackgroundDrawable(getResources().getDrawable(R.drawable.navigation_button_background))
                .build();
        button4.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mActionMenu.close(true);
                onNavigationItemID(R.id.nav_mail);
                mNavigationView.setCheckedItem(R.id.nav_mail);
            }
        });

        mActionMenu = new FloatingActionMenu.Builder(this)
                .addSubActionView(button1)
                .addSubActionView(button2)
                .addSubActionView(button3)
                .addSubActionView(button4)
                .attachTo(fab)
                .build();
    }

    // triger the background service right now
    private void updateUserStatusNow() {
        Intent intent = new Intent(this, MaintainUserStatusService.class);
        intent.putExtra(SMTHApplication.USER_SERVICE_RECEIVER, mReceiver);
        startService(intent);
    }

    private void setupUserStatusReceiver() {
        mReceiver = new UserStatusReceiver(new Handler());
        mReceiver.setReceiver(new UserStatusReceiver.Receiver() {
            @Override
            public void onReceiveResult(int resultCode, Bundle resultData) {
                if (resultCode == RESULT_OK) {
                    // Log.d(TAG, "onReceiveResult: " + "to update navigationview" + SMTHApplication.activeUser.toString());
                    UpdateNavigationViewHeader();

                    // show notification if necessary
                    String message = resultData.getString(SMTHApplication.SERVICE_NOTIFICATION_MESSAGE);
                    if(message != null) {
                        showNotification(message);
                    }
                }
            }
        });
    }


    private void showNotification(String text) {
        // http://stackoverflow.com/questions/13602190/java-lang-securityexception-requires-vibrate-permission-on-jelly-bean-4-2
        try{
            Intent notificationIntent = new Intent(MainActivity.this, MainActivity.class);
            notificationIntent.putExtra(SMTHApplication.SERVICE_NOTIFICATION_MESSAGE, text);
            // http://stackoverflow.com/questions/26608627/how-to-open-fragment-page-when-pressed-a-notification-in-android
            notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent resultPendingIntent = PendingIntent.getActivity(MainActivity.this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

            NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this)
                    .setSmallIcon(R.drawable.ic_launcher)
                    .setContentTitle("zSMTH提醒")
                    .setWhen(System.currentTimeMillis())
                    .setAutoCancel(true)
                    .setOnlyAlertOnce(true)
                    .setDefaults(Notification.DEFAULT_VIBRATE)
                    .setContentText(text)
                    .setContentIntent(resultPendingIntent);
            Notification notification = mBuilder.build();

            NotificationManager mNotifyMgr = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            mNotifyMgr.notify(notificationID, notification);
        } catch (Exception se) {
            Log.e(TAG, "showNotification: " + se.toString());
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.content_frame);
            if (fragment instanceof OnVolumeUpDownListener) {
                OnVolumeUpDownListener frag = (OnVolumeUpDownListener) fragment;
                return frag.onVolumeUpDown(keyCode);
            }
            return false;
        }

        return super.onKeyDown(keyCode, event);
    }

    // http://stackoverflow.com/questions/4500354/control-volume-keys
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        // disable the beep sound when volume up/down is pressed
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_UP) || (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    protected void onPause() {
        super.onPause();
        MobclickAgent.onPause(this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        // this method will be triggered by showNotification(message);
        FragmentManager fm = getSupportFragmentManager();
        Bundle bundle = intent.getExtras();
        if(bundle != null) {
            // this activity is launched by notification, show mail fragment now
            // http://www.androiddesignpatterns.com/2013/08/fragment-transaction-commit-state-loss.html
            // http://stackoverflow.com/questions/7575921/illegalstateexception-can-not-perform-this-action-after-onsaveinstancestate-wit
            // java.lang.IllegalStateException: Can not perform this action after onSaveInstanceState
            String message = bundle.getString(SMTHApplication.SERVICE_NOTIFICATION_MESSAGE);
            if(message != null) {
                // find the actual folder for the new message
                if(message.contains(SMTHApplication.NOTIFICATION_NEW_MAIL)) {
                    mailListFragment.setCurrentFolder(MailListFragment.INBOX_LABEL);
                } else if(message.contains(SMTHApplication.NOTIFICATION_NEW_LIKE)) {
                    mailListFragment.setCurrentFolder(MailListFragment.LIKE_LABEL);
                } else if(message.contains(SMTHApplication.NOTIFICATION_NEW_AT)) {
                    mailListFragment.setCurrentFolder(MailListFragment.AT_LABEL);
                } else if(message.contains(SMTHApplication.NOTIFICATION_NEW_REPLY)) {
                    mailListFragment.setCurrentFolder(MailListFragment.REPLY_LABEL);
                }
                // force mail fragment to reload
                MailListContent.clear();

                fm.beginTransaction().replace(R.id.content_frame, mailListFragment).commitAllowingStateLoss();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        MobclickAgent.onResume(this);
    }

    protected void initFragments() {
        hotTopicFragment = new HotTopicFragment();

        // following initilization can be delayed
        favoriteBoardFragment = new FavoriteBoardFragment();
        allBoardFragment = new AllBoardFragment();
        mailListFragment = new MailListFragment();

        preferenceFragment = new MyPreferenceFragment();
        aboutFragment = new LibsBuilder().supportFragment();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == LOGIN_ACTIVITY_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                updateUserStatusNow();
            }
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem login = menu.findItem(R.id.main_action_login);
        MenuItem logout = menu.findItem(R.id.main_action_logout);
        if (SMTHApplication.isValidUser()) {
            login.setVisible(false);
            logout.setVisible(true);
        } else {
            login.setVisible(true);
            logout.setVisible(false);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    // update header view in navigation header
    public void UpdateNavigationViewHeader() {
        // update optionMenu
        getWindow().invalidatePanelMenu(Window.FEATURE_OPTIONS_PANEL);

        if (SMTHApplication.isValidUser()) {
            // update user to login user
            mUsername.setText(SMTHApplication.activeUser.getId());
            String faceURL = SMTHApplication.activeUser.getFace_url();
            if(faceURL != null){
                mAvatar.setImageFromStringURL(faceURL);
            }
        } else {
            // only user to guest
            mUsername.setText(getString(R.string.nav_header_click_to_login));
            mAvatar.setImageResource(R.drawable.ic_person_black_48dp);
        }
    }

    @Override
    public void onBackPressed() {
        if (mDrawer.isDrawerOpen(GravityCompat.START)) {
            mDrawer.closeDrawer(GravityCompat.START);
            return;
        }

        // handle back button for all fragment
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.content_frame);
        if (fragment instanceof FavoriteBoardFragment) {
            if (!favoriteBoardFragment.atFavoriteRoot()) {
                favoriteBoardFragment.popFavoritePath();
                favoriteBoardFragment.RefreshFavoriteBoards();
                return;
            }
        }

        if (fragment != hotTopicFragment) {
            // return to hottopic if we are not there yet
            String title = "首页导读";
            FragmentManager fm = getSupportFragmentManager();
            fm.beginTransaction().replace(R.id.content_frame, hotTopicFragment).commit();
            setTitle(SMTHApplication.App_Title_Prefix + title);
            return;
        }

        // for other cases, double back to exit app
        DoubleBackToExit();

    }

    private void DoubleBackToExit() {
        if (mDoubleBackToExit) {
            // if mDoubleBackToExit is true, exit now
            quitNow();
        } else {
            // set mDoubleBackToExit = true, and set delayed task to
            // reset it to false
            mDoubleBackToExit = true;
            if (mHandler == null) {
                mHandler = new Handler();
            }
            // reset will be run after 2000 ms
            mHandler.postDelayed(new PendingDoubleBackToExit(), 2000);
            Toast.makeText(this, "再按一次退出zSMTH", Toast.LENGTH_SHORT).show();
        }
    }

    private void quitNow() {
        // stop background service
        MaintainUserStatusService.unschedule(MainActivity.this);

        // quit
        finish();
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(0);
    }


    // show information dialog, called by first run
    private void showInfoDialog() {
        // read version info from androidmanifest.xml
        String versionName = "unknown";
        int versionCode = 0;
        PackageManager pm = getPackageManager();
        try {
            PackageInfo pi = pm.getPackageInfo(getPackageName(), 0);
            versionName = pi.versionName;
            versionCode = pi.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "showInfoDialog: " + Log.getStackTraceString(e) );
        }

        // generate about_content with version from manifest
        String content_with_version = getString(R.string.about_content, versionName, versionCode);

        // linkify
        final SpannableString msg = new SpannableString(content_with_version);
        Linkify.addLinks(msg, Linkify.WEB_URLS);

        final AlertDialog dlg = new AlertDialog.Builder(this).setIcon(R.drawable.ic_launcher).setTitle(R.string.about_title)
                .setMessage(msg).create();

        dlg.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.about_close),
                new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // do nothing here
                    }
                });

        dlg.show();

        // Make the textview clickable. Must be called after show()
        ((TextView) dlg.findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.main_action_login) {
            onLogin();
            return true;
        } else if (id == R.id.main_action_logout) {
            onLogout();
            return true;
        } else if (id == android.R.id.home) {
            this.onBackPressed();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void onLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        startActivityForResult(intent, LOGIN_ACTIVITY_REQUEST_CODE);
    }

    public void onLogout() {
        Settings.getInstance().setUserOnline(false);
        if (SMTHApplication.activeUser != null) {
            SMTHApplication.activeUser.setId("guest");
        }
        UpdateNavigationViewHeader();

        SMTHHelper helper = SMTHHelper.getInstance();
        helper.wService.logout()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe(new Subscriber<AjaxResponse>() {
                    @Override
                    public void onCompleted() {
                    }

                    @Override
                    public void onError(Throwable e) {
                        Toast.makeText(MainActivity.this, "退出登录失败!\n" + e.toString(), Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onNext(AjaxResponse ajaxResponse) {
                        Toast.makeText(MainActivity.this, ajaxResponse.getAjax_msg(), Toast.LENGTH_LONG).show();
                    }
                });

    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        int id = item.getItemId();
        return onNavigationItemID(id);
    }

    public boolean onNavigationItemID(int menuID) {
        // Handle navigation view item clicks here.
        int id = menuID;

        Fragment fragment = null;
        String title = "";

        if (id == R.id.nav_guidance) {
            fragment = hotTopicFragment;
            title = "首页导读";
        } else if (id == R.id.nav_favorite) {
            fragment = favoriteBoardFragment;
            title = "收藏夹";
        } else if (id == R.id.nav_all_boards) {
            fragment = allBoardFragment;
            title = "所有版面";
        } else if (id == R.id.nav_mail) {
            fragment = mailListFragment;
            title = "邮件";
        } else if (id == R.id.nav_setting) {
//            fragment = settingFragment;
            fragment =  preferenceFragment;
            title = "设置";
        } else if (id == R.id.nav_about) {
            fragment = aboutFragment;
            title = "关于";
        }

        // switch fragment
        if (fragment != null) {
            FragmentManager fm = getSupportFragmentManager();
            fm.beginTransaction().replace(R.id.content_frame, fragment).commit();
            setTitle(SMTHApplication.App_Title_Prefix + title);
        }

        mDrawer.closeDrawer(GravityCompat.START);
        return true;
    }


    public void testCodes() {
    }


    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.nav_user_avatar || id == R.id.nav_user_name) {
            // 点击图标或者文字，都弹出登录对话框或者profiel对话框
            mDrawer.closeDrawer(GravityCompat.START);
            if (SMTHApplication.activeUser != null && !SMTHApplication.activeUser.getId().equals("guest")) {
                Intent intent = new Intent(this, QueryUserActivity.class);
                intent.putExtra(SMTHApplication.QUERY_USER_INFO, SMTHApplication.activeUser.getId());
                startActivity(intent);
            } else {
                onLogin();
            }
        }
    }

    @Override
    public void onTopicFragmentInteraction(Topic item) {
        // will be triggered in HotTopicFragment
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.content_frame);
        if (fragment == hotTopicFragment) {
            if (item.isCategory) return;
            Intent intent = new Intent(this, PostListActivity.class);
            intent.putExtra(SMTHApplication.TOPIC_OBJECT, item);
            intent.putExtra(SMTHApplication.FROM_BOARD, SMTHApplication.FROM_BOARD_HOT);
            startActivity(intent);
        }
    }

    @Override
    public void onMailInteraction(Mail item, int position) {
        if (item.isCategory) return;

        // mark item as readed
        mailListFragment.markMailAsReaded(position);
        // MailListFragment
        Intent intent = new Intent(this, MailContentActivity.class);
        intent.putExtra(SMTHApplication.MAIL_OBJECT, (Parcelable) item);
        startActivity(intent);
    }

    @Override
    public void onBoardFragmentInteraction(Board item) {
        // shared by FavoriteBoard & AllBoard fragment
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.content_frame);
        if (fragment == favoriteBoardFragment) {
            // favorite fragment, we might enter a folder
            if (item.isFolder()) {
                if(item.isValidFolder()) {
                    favoriteBoardFragment.pushFavoritePath(item.getFolderID(), item.getFolderName());
                    favoriteBoardFragment.RefreshFavoriteBoards();
                }
            } else {
                startBoardTopicActivity(item);
            }

        } else if (fragment == allBoardFragment) {
            startBoardTopicActivity(item);
        }
    }

    @Override
    public void onBoardLongClick(final Board board) {
        // shared by FavoriteBoard & AllBoard fragment
        // long click to remove board from favorite
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.content_frame);
        if (fragment == favoriteBoardFragment) {
            // favorite fragment, remove the board
            if (! board.isFolder() ) {
                // confirm dialog
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                String title = String.format("将版面\"%s\"从收藏夹中删除么？", board.getBoardName());
                builder.setTitle("收藏夹操作").setMessage(title);

                builder.setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();

                        SMTHHelper helper = SMTHHelper.getInstance();
                        helper.wService.manageFavoriteBoard(favoriteBoardFragment.getCurrentFavoritePath(), "db", board.getBoardEngName())
                                .subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(new Subscriber<AjaxResponse>() {
                                    @Override
                                    public void onCompleted() {
                                    }

                                    @Override
                                    public void onError(Throwable e) {
                                        Toast.makeText(MainActivity.this, "删除收藏版面失败！\n" + e.toString(), Toast.LENGTH_LONG).show();
                                    }

                                    @Override
                                    public void onNext(AjaxResponse ajaxResponse) {
                                        Log.d(TAG, "onNext: " + ajaxResponse.toString());
                                        if(ajaxResponse.getAjax_st() == AjaxResponse.AJAX_RESULT_OK) {
                                            Toast.makeText(MainActivity.this, ajaxResponse.getAjax_msg() + "\n" + "请刷新收藏夹！", Toast.LENGTH_SHORT).show();
                                        } else {
                                            Toast.makeText(MainActivity.this, ajaxResponse.toString(), Toast.LENGTH_LONG).show();
                                        }
                                    }
                                });

                    }
                });
                builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
                AlertDialog noticeDialog = builder.create();
                noticeDialog.show();
            }
        }
    }

    public void startBoardTopicActivity(Board board) {
        Intent intent = new Intent(this, BoardTopicActivity.class);
        intent.putExtra(SMTHApplication.BOARD_OBJECT, (Parcelable) board);
        startActivity(intent);
    }

    class PendingDoubleBackToExit implements Runnable {
        public void run() {
            mDoubleBackToExit = false;
        }
    }

}
