package com.easysol.weborderapp;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.nfc.Tag;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.Toast;

import com.easysol.weborderapp.AdapterCollection.DownloadOptionAdapter;
import com.easysol.weborderapp.Classes.CustomerMaster;
import com.easysol.weborderapp.Classes.DialogOptionModel;
import com.easysol.weborderapp.Classes.GetUrl;
import com.easysol.weborderapp.Classes.OrderTracking;
import com.easysol.weborderapp.Classes.OutStandingDetail;
import com.easysol.weborderapp.Classes.PlaceOrderDetail;
import com.easysol.weborderapp.Classes.SendDeliveryDetails;
import com.easysol.weborderapp.Classes.SendItemDetail;
import com.easysol.weborderapp.Classes.SendMasterDetails;
import com.easysol.weborderapp.Classes.SendSaleDetails;
import com.easysol.weborderapp.Classes.SendSettingDetail;
import com.easysol.weborderapp.Util.Gzip;
import com.google.gson.Gson;
import com.squareup.okhttp.MediaType;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static android.database.sqlite.SQLiteDatabase.CREATE_IF_NECESSARY;
import static java.lang.Thread.sleep;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

public class SynchronizeView extends AppCompatActivity {

    public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    public static String message = null;
    public Boolean mActivityClosing = false;
    public String mLastItemUpdateDate, mLastCustomerUpdateDate;
    private ArrayList<String> mcode = new ArrayList<String>();
    private ArrayList<String> mquantity = new ArrayList<String>();
    private ArrayList<String> mitemquantity = new ArrayList<String>();
    private ArrayList<String> mprice = new ArrayList<String>();
    private ArrayList<String> mcustomerlist = new ArrayList<String>();
    private ArrayList<String> mcustomerNamelist = new ArrayList<String>();
    private ArrayList<String> mcustomertotallist = new ArrayList<String>();
    private float mtotalcost = 0, mbooktotalcost = 0;
    private ProgressDialog mprogressDialogcircular;

    private ArrayList<DialogOptionModel> mProductArrayList = new ArrayList<DialogOptionModel>();
    private DownloadOptionAdapter adapter;
    private String guserId;
    private SQLiteDatabase gsdb = null;
    private String mUserType = "", mSupplierName = "", mSalesmanID = "", mSinklastdate = "01-Jan-2000", mMergeDBName = "";
    private String mOptionString;
    private ListView mList;
    private Button mBtnSync;
    private int mDownloading = 0;
    private int mUpdating = 0;
    private int mExport = 0;
    private boolean canExport = true;

    @SuppressLint("WrongConstant")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_synchronize_view);
        gsdb = openOrCreateDatabase("EasySolDatabase.db", CREATE_IF_NECESSARY, null);
        mList = findViewById(R.id.optionlist);
        mBtnSync = findViewById(R.id.synchronize);
        CanExport();
        gerUserType();
        getOptionString();
        GetMergeDBName();

        addToList();
        getUserInformation();
        if (GetConfirmation() > 0) {
            CheckBox mCheck = findViewById(R.id.exportorder);
            mCheck.setChecked(true);
        }
        mprogressDialogcircular = new ProgressDialog(this);
        mprogressDialogcircular.setMessage("Exporting Order Please Wait...");
        mprogressDialogcircular.setIndeterminate(false);
        mprogressDialogcircular.setCanceledOnTouchOutside(false);
        mprogressDialogcircular.setProgressStyle(ProgressDialog.STYLE_SPINNER);
    }

    @SuppressLint("Range")
    public void GetMergeDBName() {
        Cursor lcs1 = null;
        try {
            String lselectQuery = "SELECT ifnull(MergeDBName, '') as MergeDBName FROM Login where UserType='SL' ";
            lcs1 = gsdb.rawQuery(lselectQuery, null);
            if (lcs1 != null && lcs1.moveToFirst()) {
                do {
                    mMergeDBName = lcs1.getString(lcs1.getColumnIndex("MergeDBName"));
                } while (lcs1.moveToNext());
            }
        } catch (Exception ex) {
            final GlobalParameter globalVariable = (GlobalParameter) getApplicationContext();
            globalVariable.appendLog(ex);
        } finally {
            if (lcs1 != null) {
                lcs1.close();
            }
        }
    }

    @SuppressLint("Range")
    public void CanExport() {
        Cursor lcs = null;
        try {
            String lselectQuery = "SELECT * FROM Setting  ";
            lcs = gsdb.rawQuery(lselectQuery, null);
            if (lcs != null && lcs.moveToFirst()) {

                String mMaxOrder = lcs.getString(lcs.getColumnIndex("OfflineMaxOrdno"));
                String mLockOrder = lcs.getString(lcs.getColumnIndex("NoOrder"));
                if (mMaxOrder != null && mMaxOrder != "" && mLockOrder != null && mLockOrder != "") {
                    if (Integer.parseInt(mLockOrder) > 0 && Integer.parseInt(mMaxOrder) > Integer.parseInt(mLockOrder))
                        canExport = false;
                }
            }

        } catch (Exception ex) {
            final GlobalParameter globalVariable = (GlobalParameter) getApplicationContext();
            globalVariable.appendLog(ex);
            //Toast.makeText(getApplicationContext(),globalVariable.getErrorToastMessage(),Toast.LENGTH_SHORT).show();

        }
        if (canExport == false) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage("You exceed Trial Order Limit\nCannot Export Order")
                    .setCancelable(false)
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            //do things
                        }
                    });
            AlertDialog alert = builder.create();
            alert.show();
        }
    }

    public void onSynchronize(View view) {
        if (CheckNetworkStatus()) {
            if (mDownloading == 0 && mUpdating == 0) {
                int count = 0;
                for (int i = 0; i < adapter.getCount(); i++) {
                    if (adapter.getItem(i).getIsChecked()) {
                        count = count + 1;
                        //new SynchronizeView.DownloadAsyncTask().execute(adapter.getItem(i));
                        // new SynchronizeView.UpdateAsyncTask().execute(adapter.getItem(i));
						/*DownloadAsyncTask task1=new DownloadAsyncTask();
				task1.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, adapter.getItem(i));
				DownloadAsyncTask task2=new DownloadAsyncTask();
				task2.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, adapter.getItem(i));*/
                        SyncClass lsc = new SyncClass();
                        lsc.mOptionCode = adapter.getItem(i).getID();
                        lsc.mDOM = adapter.getItem(i);
                        lsc.mResponse = "";
                        DownloadAsyncTask task = new DownloadAsyncTask();
                        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, lsc);
                    }
                }
                if (count == 0) {
                    CheckBox mCheck = findViewById(R.id.exportorder);
                    if (mCheck.isChecked() == true && canExport == true) {
                        ExportAsyncTask task = new ExportAsyncTask();
                        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                    } else {
                        //Toast.makeText(getApplicationContext(), "Not Item Selected", Toast.LENGTH_SHORT).show();
                    }
                }
                DownloadingSettingTask settingTask = new DownloadingSettingTask();
                settingTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

            } else {
                AlertDialog.Builder adb = new AlertDialog.Builder(this);

                adb.setTitle("Alert");
                adb.setMessage("Datasync in Progress, Want to Abort!");
                adb.setIcon(android.R.drawable.ic_dialog_alert);
                adb.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            mActivityClosing = true;
                            sleep(100);

                        } catch (Exception ex) {
                            final GlobalParameter globalVariable = (GlobalParameter) getApplicationContext();
                            globalVariable.appendLog(ex);
                            Log.d("datasync", ex.toString());
                            //Toast.makeText(getApplicationContext(), globalVariable.getErrorToastMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }
                });
                adb.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {

                    }
                });
                adb.show();
            }
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage("Please Enable Mobile Network!")
                    .setCancelable(false)
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            //do things
                        }
                    });
            AlertDialog alert = builder.create();
            alert.show();
        }
    }

    public boolean CheckNetworkStatus() {
        ConnectivityManager cm = (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo nInfo = cm.getActiveNetworkInfo();
        boolean connected = nInfo != null && nInfo.isAvailable() && nInfo.isConnected();
        return connected;
    }

    @SuppressLint("Range")
    public void getUserInformation() {
        Cursor lcs1 = null, lcs2 = null;
        try {
            String lselectQuery = "SELECT * FROM Login where UserType='SL' ";
            lcs1 = gsdb.rawQuery(lselectQuery, null);
            if (lcs1 != null && lcs1.moveToFirst()) {
                do {
                    mSupplierName = lcs1.getString(lcs1.getColumnIndex("UserId"));

                } while (lcs1.moveToNext());
            }
            String lselectQuery1 = "SELECT * FROM Login where UserType='SM' or UserType='CL' ";
            lcs2 = gsdb.rawQuery(lselectQuery1,  null);
            if (lcs2 != null && lcs2.moveToFirst()) {
                do {
                    mSalesmanID = lcs2.getString(lcs2.getColumnIndex("UserId"));

                    //set user detail in Global Parameter
                    final GlobalParameter globalVariable = (GlobalParameter) getApplicationContext();
                    globalVariable.setUserID(lcs2.getString(lcs2.getColumnIndex("UserId")));
                    globalVariable.setUserType(lcs2.getString(lcs2.getColumnIndex("UserType")));

                } while (lcs2.moveToNext());
            }

        } catch (Exception ex) {
            final GlobalParameter globalVariable = (GlobalParameter) getApplicationContext();
            globalVariable.appendLog(ex);
            Log.d("loginexception", ex.toString());
            //Toast.makeText(getApplicationContext(),globalVariable.getErrorToastMessage(),Toast.LENGTH_SHORT).show();

        } finally {
            if (lcs1 != null) {
                lcs1.close();
            }

            if (lcs2 != null) {
                lcs2.close();
            }
        }

    }

    public void addToList() {
        if (mOptionString == null)
            return;
        String[] separated = mOptionString.split("#");
        Arrays.sort(separated);

        // Done By Shivam 16-09-2022

        Date d1 = Calendar.getInstance().getTime();
        SimpleDateFormat dateFormat1 = new SimpleDateFormat("dd-MMM-yyyy");
        String lDefaultDate = dateFormat1.format(d1);
        String value = lDefaultDate.split("-")[1].toString().length()>3?lDefaultDate.split("-")[1].toString().substring(0, 3):lDefaultDate.split("-")[1].toString();
        lDefaultDate = lDefaultDate.split("-")[0].toString()+"-"+value+"-"+lDefaultDate.split("-")[2].toString();



//        SimpleDateFormat formatter = new SimpleDateFormat("dd-MMM-yyyy");
//        lDefaultDate = formatter.format(Date.parse(lDefaultDate));
        //Done By Shivam 16-09-2022


        for (int i = 0; i < separated.length; i++) {

//            Comment By Shivam 16-09-2022
//            Date d1 = Calendar.getInstance().getTime();
//            SimpleDateFormat dateFormat1 = new SimpleDateFormat("dd-MMM-yyyy");
//            String lDefaultDate = dateFormat1.format(d1);
//            Comment By Shivam 16-09-2022


            DialogOptionModel dom;
            if (separated[i].equals("1001")) {
                dom = new DialogOptionModel("Item Master", lDefaultDate, false, true, 0, ".", "1001");
                mProductArrayList.add(dom);
            }

            if (separated[i].equals("1002") && mUserType.equals("CL")) {
                dom = new DialogOptionModel("Self Detail", lDefaultDate, false, false, 0, "", "1002");
                dom.setIsChecked(true);
                mProductArrayList.add(dom);

            }
            if (separated[i].equals("1002") && mUserType.equals("SM")) {
                dom = new DialogOptionModel("Customer Master", lDefaultDate, false, true, 0, "", "1002");
                mProductArrayList.add(dom);
            }
            if (separated[i].equals("1003") && mUserType.equals("CL")) {
                dom = new DialogOptionModel("Due", lDefaultDate, false, false, 0, "", "1003");
                dom.setIsChecked(true);
                mProductArrayList.add(dom);
            }
            if (separated[i].equals("1003") && mUserType.equals("SM")) {
                dom = new DialogOptionModel("Due", lDefaultDate, false, true, 0, "", "1003");
                mProductArrayList.add(dom);
            }
            if (separated[i].equals("1004") && mUserType.equals("CL")) {
                dom = new DialogOptionModel("PDC", lDefaultDate, false, false, 0, "", "1004");
                dom.setIsChecked(true);
                mProductArrayList.add(dom);
            }
            if (separated[i].equals("1004") && mUserType.equals("SM")) {
                dom = new DialogOptionModel("PDC", lDefaultDate, false, true, 0, "", "1004");
                mProductArrayList.add(dom);
            }
            if (separated[i].equals("1005")) {
                dom = new DialogOptionModel("Sale Detail", lDefaultDate, true, true, 0, "", "1005");
                mProductArrayList.add(dom);
            }
            if (separated[i].equals("1006")) {
                dom = new DialogOptionModel("Sync Shortage", lDefaultDate, false, true, 0, "", "1006");
                mProductArrayList.add(dom);
            }
            if (separated[i].equals("1007")) {
                dom = new DialogOptionModel("Sync Tracking", lDefaultDate, false, true, 0, "", "1007");
                mProductArrayList.add(dom);
            }
            if (separated[i].equals("1008")) {
                dom = new DialogOptionModel("Sync Expiry", lDefaultDate, false, true, 0, "", "1008");
                mProductArrayList.add(dom);
            }
            if (separated[i].equals("1009")) {
                dom = new DialogOptionModel("Sync Delivery", lDefaultDate, false, true, 0, "", "1009");
                mProductArrayList.add(dom);
            }
        }
        adapter = new DownloadOptionAdapter(this, mProductArrayList);
        mList.setAdapter(adapter);
    }

    @SuppressLint("Range")
    public void getOptionString() {
        try {
            String lselectQuery = "SELECT * FROM setting ";
            Cursor lcs = gsdb.rawQuery(lselectQuery, null);
            if (lcs != null && lcs.moveToFirst()) {
                mOptionString = lcs.getString(lcs.getColumnIndex("OptionCode"));

            }
        } catch (Exception ex) {
            final GlobalParameter globalVariable = (GlobalParameter) getApplicationContext();
            globalVariable.appendLog(ex);
            Log.d("settingexception", ex.toString());
            //Toast.makeText(getApplicationContext(),globalVariable.getErrorToastMessage(),Toast.LENGTH_SHORT).show();
        }
    }

    @SuppressLint("Range")
    public void gerUserType() {
        try {
            String lselectQuery = "SELECT * FROM Login where UserType='SM' or  UserType='CL'";
            Cursor lcs = gsdb.rawQuery(lselectQuery, null);
            if (lcs != null && lcs.moveToFirst()) {
                mUserType = lcs.getString(lcs.getColumnIndex("UserType"));

            }
        } catch (Exception ex) {
            final GlobalParameter globalVariable = (GlobalParameter) getApplicationContext();
            globalVariable.appendLog(ex);
            //Toast.makeText(getApplicationContext(),globalVariable.getErrorToastMessage(),Toast.LENGTH_SHORT).show();
        }
    }

    public void UpdateAdapter() {
        runOnUiThread(new Runnable() {
            public void run() {
                Cursor lcs1 = null;
                try {
                    adapter.notifyDataSetChanged();
                } catch (Exception e) {
                    final GlobalParameter globalVariable = (GlobalParameter) getApplicationContext();
                    globalVariable.appendLog(e);
                    //Toast.makeText(getApplicationContext(),globalVariable.getErrorToastMessage(),Toast.LENGTH_SHORT).show();
                    e.printStackTrace();
                } finally {

                }
            }
        });
    }

    @SuppressLint("Range")
    public String GetAlterCode(String mUserCode, String mType) {
        String mAlterCode = "0000";

        String selectQuery = "select * from login where userid=" + mUserCode + "";
        Cursor cs = gsdb.rawQuery(selectQuery, null);
        if (cs != null && cs.moveToFirst()) {
            mAlterCode = cs.getString(cs.getColumnIndex("Altercode"));
        }

        return mAlterCode;
    }

    @SuppressLint("Range")
    public void LoadGlobalVariables() {
        final GlobalParameter globalVariable = (GlobalParameter) getApplicationContext();
        Cursor cs1 = null;
        try {
            String selectQuery = "SELECT * FROM Setting";
            cs1 = gsdb.rawQuery(selectQuery, null);
            if (cs1 != null && cs1.moveToFirst()) {
                do {

                    globalVariable.global_BalQtyType = cs1.getString(cs1.getColumnIndex("BalQtyType"));
                    globalVariable.global_showinactiveitem = cs1.getString(cs1.getColumnIndex("ShowInactiveItem"));
                    globalVariable.global_showinactivecustomer = cs1.getString(cs1.getColumnIndex("ShowInactiveCustomer"));
                    globalVariable.global_showallcustomer = cs1.getString(cs1.getColumnIndex("ShowAllCustomer"));
                    globalVariable.global_showfree = cs1.getString(cs1.getColumnIndex("AllowFreeQty"));
                    globalVariable.global_itemsch = cs1.getString(cs1.getColumnIndex("ItemScm"));
                    globalVariable.global_ConfirmOrder = cs1.getString(cs1.getColumnIndex("ConfirmOrder"));
                    globalVariable.global_Allowed = cs1.getString(cs1.getColumnIndex("Allowed"));
                    globalVariable.AndroidVersion = cs1.getString(cs1.getColumnIndex("AndroidVersion"));
                    globalVariable.DlExpiryAllow = cs1.getString(cs1.getColumnIndex("DlExpiryAllow"));
                    globalVariable.LockDetailAllow = cs1.getString(cs1.getColumnIndex("LockDetailAllow"));
                } while (cs1.moveToNext());
            }
        } catch (Exception ex) {
            globalVariable.appendLog(ex);
            //Toast.makeText(getApplicationContext(),globalVariable.getErrorToastMessage(),Toast.LENGTH_SHORT).show();
        } finally {
            if (cs1 != null)
                cs1.close();
        }
        Cursor cs = null;
        try {
            String selectQuery = "SELECT * FROM login where UserType='SL'";
            cs = gsdb.rawQuery(selectQuery, null);
            if (cs != null && cs.moveToFirst())
                globalVariable.setSupplierID(cs.getString(cs.getColumnIndex("SuppId")));

        } catch (Exception ex) {
            globalVariable.appendLog(ex);
            // Toast.makeText(getApplicationContext(),globalVariable.getErrorToastMessage(),Toast.LENGTH_SHORT).show();
        } finally {
            if (cs != null)
                cs.close();
        }
    }

    public void UpdateDataBase(SyncClass pSC) {
        if (pSC.mOptionCode.equals("1001")) {
            UpdateItemData(pSC);
        }
        if (pSC.mOptionCode.equals("102")) {
            UpdateCustItemLock(pSC);
        }
        if (pSC.mOptionCode.equals("101")) {
            UpdateMasterData(pSC);
        }
        if (pSC.mOptionCode.equals("1002")) {
            UpdateCustomerData(pSC);
        }
        if (pSC.mOptionCode.equals("1003")) {
            UpdateDUEData(pSC);
        }
        if (pSC.mOptionCode.equals("1004")) {
            UpdatePDCData(pSC);
        }
        if (pSC.mOptionCode.equals("1005")) {
            UpdateSaleData(pSC);
        }
        if (pSC.mOptionCode.equals("1006")) {
            UpdateShortageData(pSC);
        }
        if (pSC.mOptionCode.equals("1007")) {
            UpdateTrackingData(pSC);
        }
        if (pSC.mOptionCode.equals("1008")) {
            UpdateExpiryData(pSC);
        }
        if (pSC.mOptionCode.equals("1009")) {
            UpdateDeliveryData(pSC);
        }
    }

    public void UpdateDeliveryData(SyncClass pSC) {
        try {
            Log.d("Thread", "UpdateDeliveryData: "+Thread.currentThread().getName());
            pSC.mDOM.setStatusControl("Compiling Data...");
            UpdateAdapter();
            JSONArray larr = null;

            try {
                larr = new JSONArray(pSC.mResponse);
            } catch (Exception ex) {
                final GlobalParameter globalVariable = (GlobalParameter) getApplicationContext();
                globalVariable.appendLog(ex);
            }
            pSC.mResponse = "";
            //  int mlengthofarray = larr.length();
            pSC.mDOM.setStatusControl("Processing Data...");
            UpdateAdapter();
            int counter = pastInsertDelivery(larr, pSC);
            if (counter > 0) {
                pSC.mDOM.setCurrentValue(counter);
                pSC.mDOM.setStatusControl("Validating Data...");
                UpdateAdapter();
                gsdb.execSQL(" delete from CustomerDelivery where Stage='DU'");
                pSC.mDOM.setStatusControl("Updating Data...");
                UpdateAdapter();
                gsdb.execSQL("insert into CustomerDelivery select * from CustomerDeliveryNew");
                gsdb.execSQL("delete from CustomerDeliveryNew");
            }
        } catch (Exception ex) {
            final GlobalParameter globalVariable = (GlobalParameter) getApplicationContext();
            globalVariable.appendLog(ex);
        }
    }



    public void UpdateExpiryData(SyncClass pSC) {
        try {
            Log.d("Thread", "UpdateDeliveryData: "+Thread.currentThread().getName());
            pSC.mDOM.setStatusControl("Compiling Data...");
            UpdateAdapter();

            JSONArray larr = null;

            try {
                larr = new JSONArray(pSC.mResponse);
            } catch (Exception ex) {
                final GlobalParameter globalVariable = (GlobalParameter) getApplicationContext();
                globalVariable.appendLog(ex);
            }
            pSC.mResponse = "";
            //  int mlengthofarray = larr.length();
            pSC.mDOM.setStatusControl("Processing Data...");
            UpdateAdapter();
            int counter = pastInsertExpiry(larr, pSC);
            if (counter > 0) {
                pSC.mDOM.setCurrentValue(counter);
                pSC.mDOM.setStatusControl("Validating Data...");
                UpdateAdapter();
                gsdb.execSQL(" delete from Expiry");
                pSC.mDOM.setStatusControl("Updating Data...");
                UpdateAdapter();
                gsdb.execSQL("insert into Expiry select * from ExpiryNew");
                gsdb.execSQL("delete from ExpiryNew");
            }
        } catch (Exception ex) {
            final GlobalParameter globalVariable = (GlobalParameter) getApplicationContext();
            globalVariable.appendLog(ex);
        }
    }


    public void UpdateSaleData(SyncClass pSC) {
        try {

            Log.d("Thread", "UpdateDeliveryData: "+Thread.currentThread().getName());
            pSC.mDOM.setStatusControl("Compiling Data...");

            UpdateAdapter();
            JSONArray larr = new JSONArray(pSC.mResponse);
            pSC.mResponse = "";
            int mtotal = 0;
            int mlengthofarray = larr.length();
            pSC.mDOM.setStatusControl("Updating Data...");
            UpdateAdapter();
            if (larr.length() > 0) {

                String date = pSC.mDOM.getDefaultDate();
                String value = date.split("-")[1].toString().length()>3?date.split("-")[1].toString().substring(0, 3):
                        date.split("-")[1].toString();
                date = date.split("-")[0].toString()+"-"+value+"-"+date.split("-")[2].toString();

                String strJulDate = convertDateToSQLLITE(date);
                String selectQuerysinkdate = "delete from saledet where vdt in (select vdt from  saledet S inner join Month M on  substr(S.vdt,4,3)  like M.MonStr  where JulianDay(substr(S.vdt, 8, 4) || '-' || M.MonNo || '-' || substr(S.vdt, 1, 2))>= JulianDay('" + strJulDate + "') )";
                gsdb.execSQL(selectQuerysinkdate);

                for (int i = 0; i < larr.length(); i++) {
                    ContentValues lvalues = new ContentValues();
                    JSONObject ljsonObj1 = larr.getJSONObject(i);
                    //mitemname = ljsonObj1.getString("Name");

                    lvalues.put("Acno ", ljsonObj1.getString("Acno"));
                    lvalues.put("Vtype ", ljsonObj1.getString("Vtype"));
                    lvalues.put("Vdt ", ljsonObj1.getString("Vdt"));
                    lvalues.put("Vno ", ljsonObj1.getString("Vno"));
                    lvalues.put("Amt ", ljsonObj1.getString("Amt"));
                    lvalues.put("Sman ", ljsonObj1.getString("Sman"));
                    lvalues.put("Area ", ljsonObj1.getString("Area"));
                    lvalues.put("Route ", ljsonObj1.getString("Route"));

                    int lvalue = ((i * 100) / mlengthofarray);
                    pSC.mDOM.setProgressValue(lvalue);
                    pSC.mDOM.setCurrentValue(i + 1);
                    pSC.mDOM.setMaxValue(mlengthofarray);
                    UpdateAdapter();

                    gsdb.insert("SaleDet", null, lvalues);
                    if (mActivityClosing == true)
                        break;

                }

            }

        } catch (Exception ex) {
            final GlobalParameter globalVariable = (GlobalParameter) getApplicationContext();
            Log.d("TAG@@", "UpdateSaleData: "+ ex.toString());
            globalVariable.appendLog(ex);
//            Toast.makeText(getApplicationContext(),globalVariable.getErrorToastMessage(),Toast.LENGTH_SHORT).show();
        }
    }

    public void UpdateMasterData(SyncClass pSC) {
        try {
            pSC.mDOM.setStatusControl("Compiling Data...");
            UpdateAdapter();
            JSONArray larr = new JSONArray(pSC.mResponse);
            pSC.mResponse = "";
            int mtotal = 0;
            int mlengthofarray = larr.length();
            pSC.mDOM.setStatusControl("Updating Data...");
            UpdateAdapter();
            if (larr.length() > 0) {

                for (int i = 0; i < larr.length(); i++) {
                    ContentValues lvalues = new ContentValues();
                    JSONObject ljsonObj1 = larr.getJSONObject(i);
                    String mitemname = ljsonObj1.getString("Name");

                    lvalues.put("Code ", ljsonObj1.getString("Code"));
                    lvalues.put("Name ", ljsonObj1.getString("Name"));
                    lvalues.put("TrimName ", ljsonObj1.getString("TrimName"));
                    lvalues.put("Altercode ", ljsonObj1.getString("Altercode"));
                    lvalues.put("Slcd ", ljsonObj1.getString("Slcd"));
                    lvalues.put("Address ", ljsonObj1.getString("Address"));
                    lvalues.put("Address1 ", ljsonObj1.getString("Address1"));
                    lvalues.put("Address2 ", ljsonObj1.getString("Address2"));
                    lvalues.put("Telephone ", ljsonObj1.getString("Telephone"));
                    lvalues.put("Telephone1 ", ljsonObj1.getString("Telephone1"));
                    lvalues.put("Mobile ", ljsonObj1.getString("Mobile"));
                    lvalues.put("Email ", ljsonObj1.getString("Email"));
                    lvalues.put("Status ", ljsonObj1.getString("Status"));
                    lvalues.put("Transport ", ljsonObj1.getString("Transport"));
                    lvalues.put("LockDate ", ljsonObj1.getString("LockDate"));
                    lvalues.put("BranchCode ", ljsonObj1.getString("BranchCode"));
                    lvalues.put("Pwd ", ljsonObj1.getString("Pwd"));
                    lvalues.put("Updated_at ", ljsonObj1.getString("Updated_at"));
                    lvalues.put("MonthlyTarget ", ljsonObj1.getString("MonthlyTarget"));
                    lvalues.put("MaxDueAmt ", ljsonObj1.getString("MaxDueAmt"));
                    lvalues.put("MaxTotAmt ", ljsonObj1.getString("MaxTotAmt"));
                    lvalues.put("DeviceID ", ljsonObj1.getString("DeviceID"));

                    int lvalue = ((i * 100) / mlengthofarray);
                    pSC.mDOM.setProgressValue(lvalue);
                    pSC.mDOM.setCurrentValue(i + 1);
                    pSC.mDOM.setMaxValue(mlengthofarray);
                    UpdateAdapter();

                    String lselectQuery = "delete from Master where Code=" + ljsonObj1.getString("Code") + " ";
                    gsdb.execSQL(lselectQuery);

                    gsdb.insert("Master", null, lvalues);
                    if (mActivityClosing == true)
                        break;

                }

            }

        } catch (Exception ex) {
            final GlobalParameter globalVariable = (GlobalParameter) getApplicationContext();
            globalVariable.appendLog(ex);
            // Toast.makeText(getApplicationContext(),globalVariable.getErrorToastMessage(),Toast.LENGTH_SHORT).show();
        }
    }

    public void UpdatePDCData(SyncClass pSC) {
        try {
            pSC.mDOM.setStatusControl("Compiling Data...");
            UpdateAdapter();
            JSONArray larr = null;

            try {
                larr = new JSONArray(pSC.mResponse);
            } catch (Exception ex) {
                final GlobalParameter globalVariable = (GlobalParameter) getApplicationContext();
                globalVariable.appendLog(ex);
                //Toast.makeText(getApplicationContext(),globalVariable.getErrorToastMessage(),Toast.LENGTH_SHORT).show();
            }
            Log.d("laresponse", larr.toString());
            pSC.mResponse = "";
            int mtotal = 0;
            int mlengthofarray = larr.length();
            pSC.mDOM.setStatusControl("Processing Data...");
            UpdateAdapter();
            int counter = pastPDC(larr, pSC);
            if (counter > 0) {
                pSC.mDOM.setCurrentValue(counter);
                pSC.mDOM.setStatusControl("Validating Data...");
                UpdateAdapter();
                gsdb.execSQL(" delete from PDC");
                pSC.mDOM.setStatusControl("Updating Data...");
                UpdateAdapter();
                final GlobalParameter globalVariable = (GlobalParameter) getApplicationContext();
                if (globalVariable.global_showallcustomer.equals("2"))
                    gsdb.execSQL("delete from PDCNew where SMan !=" + globalVariable.getUserID() + "");
//                if(!globalVariable.getUserID().equals("")){
//
//                }
//                if (globalVariable.global_showallcustomer.equals("3")) {
//                    gsdb.execSQL("delete from PDCNew where Sman not IN (SELECT code FROM Master WHERE substr(Altercode, 1, 3) IN ( SELECT substr(Altercode, 1, 3) FROM master WHERE code = " + globalVariable.getUserID() + "))");
//                }
                gsdb.execSQL("insert into PDC select * from PDCNew");
//                gsdb.execSQL("update PDC set Customer_name=( select  Customer_name from CustomerData where CustomerData.Acno=PDC.Acno) ");
//                gsdb.execSQL("update PDC set AlterCode=( select  Altercode from CustomerData where CustomerData.Acno=PDC.Acno) ");
                gsdb.execSQL("delete from PDCNew");
            }
        } catch (Exception ex) {
            final GlobalParameter globalVariable = (GlobalParameter) getApplicationContext();
            globalVariable.appendLog(ex);
        }
    }

    public void UpdateDUEData(SyncClass pSC) {
        try {
            pSC.mDOM.setStatusControl("Compiling Data...");
            UpdateAdapter();
            JSONArray larr = null;

            try {
                larr = new JSONArray(pSC.mResponse);
            } catch (Exception ex) {
                final GlobalParameter globalVariable = (GlobalParameter) getApplicationContext();
                globalVariable.appendLog(ex);
            }
            pSC.mResponse = "";
            int mtotal = 0;
            int mlengthofarray = larr.length();
            pSC.mDOM.setStatusControl("Processing Data...");
            UpdateAdapter();
            int counter = pastDUE(larr, pSC);
            if (counter > 0) {
                pSC.mDOM.setCurrentValue(counter);
                pSC.mDOM.setStatusControl("Validating Data...");
                UpdateAdapter();
                //deletedatafromOutstanding();
                gsdb.execSQL(" delete from OutStanding");
                pSC.mDOM.setStatusControl("Updating Data...");
                UpdateAdapter();

                gsdb.execSQL("insert into OutStanding select * from OutStandingNew");
                gsdb.execSQL("delete from OutStandingNew");

            }
        } catch (Exception ex) {
            final GlobalParameter globalVariable = (GlobalParameter) getApplicationContext();
            globalVariable.appendLog(ex);
        }
    }

    public void UpdateItemData(SyncClass pSC) {
        try {
            pSC.mDOM.setStatusControl("Compiling Data...");
            UpdateAdapter();
            JSONArray larr = null;
            try {
                larr = new JSONArray(pSC.mResponse);
            } catch (Exception ex) {
                final GlobalParameter globalVariable = (GlobalParameter) getApplicationContext();
                globalVariable.appendLog(ex);
            }
            pSC.mResponse = "";
            int mtotal = 0;
            int mlengthofarray = larr.length();
            pSC.mDOM.setStatusControl("Processing Data...");
            UpdateAdapter();
            int counter = pastInsertItem(larr, pSC);
            if (counter > 0) {
                pSC.mDOM.setCurrentValue(counter);
                pSC.mDOM.setStatusControl("Validating Data...");
                UpdateAdapter();
                gsdb.execSQL(" delete from itemdata where itemcode in (select itemcode from ItemDataNew)");
                pSC.mDOM.setStatusControl("Updating Data...");
                UpdateAdapter();
                gsdb.execSQL("insert into itemdata select * from ItemDataNew");
                gsdb.execSQL("delete from ItemDataNew");
            }
            getSinKItemDate();

        } catch (Exception ex) {
            final GlobalParameter globalVariable = (GlobalParameter) getApplicationContext();
            globalVariable.appendLog(ex);
        }
    }

    public void UpdateCustomerData(SyncClass pSC) {
        final GlobalParameter globalVariable = (GlobalParameter) getApplicationContext();
        try {
            pSC.mDOM.setStatusControl("Compiling Data...");
            UpdateAdapter();
            JSONArray larr = null;
            larr = new JSONArray(pSC.mResponse);
            pSC.mResponse = "";
            pSC.mDOM.setStatusControl("Processing Data...");
            UpdateAdapter();
            int counter = pastInsertCustomer(larr, pSC);
            if (counter > 0) {
                pSC.mDOM.setCurrentValue(counter);
                pSC.mDOM.setStatusControl("Validating Data...");
                UpdateAdapter();
                if (globalVariable.global_showallcustomer.equals("1")) {
                    gsdb.execSQL(" delete from CustomerData where Acno in (select Acno from CustomerDataNew )");
                    pSC.mDOM.setStatusControl("Updating Data...");
                    UpdateAdapter();
                    gsdb.execSQL("insert into CustomerData select * from CustomerDataNew");
                    gsdb.execSQL("delete from CustomerDataNew");

                } else {
                    gsdb.execSQL(" delete from CustomerData");
                    pSC.mDOM.setStatusControl("Updating Data...");
                    UpdateAdapter();
                    gsdb.execSQL("insert into CustomerData select * from CustomerDataNew");
                    gsdb.execSQL("delete from CustomerDataNew");
                }
            }
            getSinKItemDate();

        } catch (Exception ex) {
            globalVariable.appendLog(ex);
        }
    }

    public void UpdateShortageData(SyncClass pSC) {
        try {
            Log.d("Thread", "UpdateDeliveryData: "+Thread.currentThread().getName());
            pSC.mDOM.setStatusControl("Compiling Data...");
            UpdateAdapter();
            JSONArray larr = null;

            try {
                larr = new JSONArray(pSC.mResponse);
            } catch (Exception ex) {
                final GlobalParameter globalVariable = (GlobalParameter) getApplicationContext();
                globalVariable.appendLog(ex);
            }
            pSC.mResponse = "";
            int mtotal = 0;
            int mlengthofarray = larr.length();
            pSC.mDOM.setStatusControl("Processing Data...");
            UpdateAdapter();
            int counter = pastInsertShortage(larr, pSC);
            if (counter > 0) {
                pSC.mDOM.setCurrentValue(counter);
                pSC.mDOM.setStatusControl("Validating Data...");
                UpdateAdapter();
                gsdb.execSQL("delete from Shortage");
                pSC.mDOM.setStatusControl("Updating Data...");
                UpdateAdapter();
                gsdb.execSQL("insert into Shortage select * from ShortageNew");
                gsdb.execSQL("update porder set BounceItem=( select  count(*) noi from shortage where shortage.Acno=Porder.Acno and shortage.CustOrdNo=Porder.OrderNo and shortage.UID like 'EsAnd%'  ) ");
                gsdb.execSQL("update porder set IssuedQty=( select  Shortage from shortage where shortage.Acno=Porder.Acno and shortage.CustOrdNo=Porder.OrderNo and shortage.UID like 'EsAnd%' and shortage.Itemc= Porder.ItemCode ) ");
                gsdb.execSQL("delete from ShortageNew");

            }
            getSinKItemDate();

        } catch (Exception ex) {
            final GlobalParameter globalVariable = (GlobalParameter) getApplicationContext();
            globalVariable.appendLog(ex);
        }
    }

    public void UpdateTrackingData(SyncClass pSC) {
        try {
            Log.d("Thread", "UpdateDeliveryData: "+Thread.currentThread().getName());
            pSC.mDOM.setStatusControl("Compiling Data...");
            UpdateAdapter();
            JSONArray larr = null;

            try {
                larr = new JSONArray(pSC.mResponse);
            } catch (Exception ex) {
                final GlobalParameter globalVariable = (GlobalParameter) getApplicationContext();
                globalVariable.appendLog(ex);
            }
            pSC.mResponse = "";
            int mtotal = 0;
            int mlengthofarray = larr.length();
            pSC.mDOM.setStatusControl("Processing Data...");
            UpdateAdapter();
            int counter = pastInsertTracking(larr, pSC);
            if (counter > 0) {
                pSC.mDOM.setCurrentValue(counter);
                pSC.mDOM.setStatusControl("Validating Data...");
                UpdateAdapter();
                gsdb.execSQL(" delete from Tracking");
                pSC.mDOM.setStatusControl("Updating Data...");
                UpdateAdapter();
                gsdb.execSQL("insert into Tracking select * from TrackingNew");
                gsdb.execSQL("delete from TrackingNew");
                gsdb.execSQL("update porder set tracking=( select Remarks from Tracking where Tracking.Acno=Porder.Acno and Tracking.CustOrdNo=Porder.OrderNo and Tracking.UID like 'EsAnd%'  ) ");
                gsdb.execSQL("update porder set InvNo=( select Vno from Tracking where Tracking.Acno=Porder.Acno and Tracking.CustOrdNo=Porder.OrderNo and Tracking.UID like 'EsAnd%'  ) ");
                gsdb.execSQL("update porder set InvAmt=( select Amt from Tracking where Tracking.Acno=Porder.Acno and Tracking.CustOrdNo=Porder.OrderNo and Tracking.UID like 'EsAnd%'  ) ");
                gsdb.execSQL("update porder set InvDate=( select Vdt from Tracking where Tracking.Acno=Porder.Acno and Tracking.CustOrdNo=Porder.OrderNo and Tracking.UID like 'EsAnd%'  ) ");

            }
            getSinKItemDate();

        } catch (Exception ex) {
            final GlobalParameter globalVariable = (GlobalParameter) getApplicationContext();
            globalVariable.appendLog(ex);
        }
    }

    public void UpdateCustItemLock(SyncClass pSC) {
        try {
            pSC.mDOM.setStatusControl("Compiling Data...");
            UpdateAdapter();
            JSONArray larr = null;

            try {
                larr = new JSONArray(pSC.mResponse);
            } catch (Exception ex) {
                final GlobalParameter globalVariable = (GlobalParameter) getApplicationContext();
                globalVariable.appendLog(ex);
            }
            pSC.mResponse = "";
            int mtotal = 0;
            int mlengthofarray = larr.length();
            pSC.mDOM.setStatusControl("Processing Data...");
            UpdateAdapter();
            int counter = pastInsertCustItemLock(larr, pSC);
            if (counter > 0) {
                pSC.mDOM.setCurrentValue(counter);
                pSC.mDOM.setStatusControl("Validating Data...");
                UpdateAdapter();
                gsdb.execSQL(" delete from CustItemLock");
                pSC.mDOM.setStatusControl("Updating Data...");
                UpdateAdapter();
                gsdb.execSQL("insert into CustItemLock select * from CustItemLockNew");
                gsdb.execSQL("delete from CustItemLockNew");
            }
        } catch (Exception ex) {
            final GlobalParameter globalVariable = (GlobalParameter) getApplicationContext();
            globalVariable.appendLog(ex);
        }
    }

    @SuppressLint("Range")
    public SyncClass PrepareData(SyncClass pSC) {
        final GlobalParameter globalVariable = (GlobalParameter) getApplicationContext();
        SyncClass lSC = new SyncClass();
        lSC.mOptionCode = pSC.mOptionCode;
        lSC.mDOM = pSC.mDOM;
        if (pSC.mOptionCode.equals("1001")) {
            GetlastSyncDate();
            lSC.mAPI = "getitemszip";
            lSC.mAPIResult = "getitemszipResult";
            if (mMergeDBName.equals("") || mMergeDBName.equals(null) || mMergeDBName.length() == 0) {
                SendItemDetail lsenditem = new SendItemDetail(mSupplierName, mLastItemUpdateDate, "0", "FULL");
                Gson lgson = new Gson();
                String ljson = lgson.toJson(lsenditem);
                Log.d("json_item",ljson);
                lSC.mJSONBody = ljson;
            } else {
                SendItemDetail lsenditem = new SendItemDetail(mSupplierName + "," + mMergeDBName, mLastItemUpdateDate, "0", "FULL");
                Gson lgson = new Gson();
                String ljson = lgson.toJson(lsenditem);
                Log.d("json_item",ljson);
                lSC.mJSONBody = ljson;
            }
        }
        if (pSC.mOptionCode.equals("1002")) {
            GetlastSyncDate();
            Gson lgson = new Gson();
            if (mUserType.equals("SM") == true)
                lSC.mAPI = "getcustomersfilterzip";
            if (mUserType.equals("CL") == true)
                lSC.mAPI = "getcustomers_self";
            if (mUserType.equals("SM") == true)
                lSC.mAPIResult = "getcustomersfilterzipResult";
            if (mUserType.equals("CL") == true)
                lSC.mAPIResult = "getcustomers_selfResult";
            if (mUserType.equals("CL") == true) {
                CustomerMasterModel lcustomerDetail = new CustomerMasterModel(mSupplierName, globalVariable.global_showallcustomer, globalVariable.getUserID(), mLastCustomerUpdateDate);
                String ljson = lgson.toJson(lcustomerDetail);
                lSC.mJSONBody = ljson;
            } else {
                CustomerMaster lSaleMaDetail = new CustomerMaster(mSupplierName, globalVariable.global_showallcustomer, globalVariable.getUserID(), mLastCustomerUpdateDate);
                String ljson = lgson.toJson(lSaleMaDetail);
                lSC.mJSONBody = ljson;
            }
        }
        if (pSC.mOptionCode.equals("1003")) {
            lSC.mAPI = "getbillostzip";
            lSC.mAPIResult = "getbillostzipResult";
            OutStandingDetail loutstanding = new OutStandingDetail(mSupplierName, globalVariable.getUserType(), globalVariable.getUserID());
            if (globalVariable.getUserType().equals("SM") && (globalVariable.global_showallcustomer.equals("1") || globalVariable.global_showallcustomer.equals("3"))) {
                loutstanding.setlLoginType("OTH");
            }
            Gson lgson = new Gson();
            String ljson = lgson.toJson(loutstanding);
            Log.d("json_requeest",ljson+","+lSC.mAPI);
            lSC.mJSONBody = ljson;
        }
        if (pSC.mOptionCode.equals("101")) {
            lSC.mAPI = "getsman_area_route_android";
            lSC.mAPIResult = "getsman_area_route_androidResult";
            mSinklastdate = GetMasterLastSyncDate();
            SendMasterDetails lsenditem = new SendMasterDetails(mSupplierName, mSinklastdate);
            Gson lgson = new Gson();
            String ljson = lgson.toJson(lsenditem);
            lSC.mJSONBody = ljson;
        }
        if (pSC.mOptionCode.equals("1004")) {
            lSC.mAPI = "getpdczip";
            lSC.mAPIResult = "getpdczipResult";
            OutStandingDetail loutstanding = new OutStandingDetail(mSupplierName, globalVariable.getUserType(), globalVariable.getUserID());
            if (globalVariable.getUserType().equals("SM") && (globalVariable.global_showallcustomer.equals("1"))) {
                loutstanding.setlLoginType("ALL");   //loutstanding.setlLoginType("SM");
            }
            if(globalVariable.getUserType().equals("SM") &&  globalVariable.global_showallcustomer.equals("3")){
                loutstanding.setlLoginType("OTH");
            }
            Gson lgson = new Gson();
            String ljson = lgson.toJson(loutstanding);
            Log.d("pdc_request", ljson);
            lSC.mJSONBody = ljson;
        }
        if (pSC.mOptionCode.equals("102")) {
            lSC.mAPI = "getItemLockszip";
            lSC.mAPIResult = "getItemLockszipResult";
            mSinklastdate = "01-Jan-1900";
            SendMasterDetails lsenditem = new SendMasterDetails(mSupplierName, mSinklastdate);
            Gson lgson = new Gson();
            String ljson = lgson.toJson(lsenditem);
            lSC.mJSONBody = ljson;
        }
        if (pSC.mOptionCode.equals("1005")) {
            lSC.mAPI = "getsaledet";
            lSC.mAPIResult = "getsaledetResult";
            mSinklastdate = pSC.mDOM.getDefaultDate();
            SimpleDateFormat format = new SimpleDateFormat("dd-MMM-yyyy");
            String date = format.format(Date.parse(mSinklastdate));

            String value = date.split("-")[1].toString().length()>3?date.split("-")[1].toString().substring(0, 3):date.split("-")[1].toString();
            date = date.split("-")[0].toString()+"-"+value+"-"+date.split("-")[2].toString();

            SendSaleDetails lsenditem = new SendSaleDetails(mSupplierName, date, globalVariable.getUserType(), globalVariable.getUserID());
            Gson lgson = new Gson();
            String ljson = lgson.toJson(lsenditem);
            lSC.mJSONBody = ljson;
        }
        if (pSC.mOptionCode.equals("1006")) {
            GetlastSyncDate();
            lSC.mAPI = "getshortagezip";
            lSC.mAPIResult = "getshortagezipResult";
            SendItemDetail lsenditem = new SendItemDetail(mSupplierName, "01-Jan-1900", "0", "FULL");
            Gson lgson = new Gson();
            String ljson = lgson.toJson(lsenditem);
            lSC.mJSONBody = ljson;
        }
        if (pSC.mOptionCode.equals("1007")) {
            GetlastSyncDate();
            lSC.mAPI = "gettrackingzip";
            lSC.mAPIResult = "gettrackingzipResult";
            //lSC.mAPI = "getOrdertrackingzip";
            //lSC.mAPIResult = "getOrdertrackingzipResult";
            SendItemDetail lsenditem = new SendItemDetail(mSupplierName, "01-Jan-1900", "0", "FULL");
            //ArrayList<OrderTracking> lsenditem=GetOrderTracking();
            Gson lgson = new Gson();
            String ljson = lgson.toJson(lsenditem);
            lSC.mJSONBody = ljson;
			/*if(lsenditem.size() == 0)
				lSC.mJSONBody="";*/
        }
        if (pSC.mOptionCode.equals("1008")) {
            lSC.mAPI = "AndroidgetExpiryzip";
            lSC.mAPIResult = "AndroidgetExpiryzipResult";
            SendMasterDetails lsenditem = new SendMasterDetails(mSupplierName);
            //ArrayList<OrderTracking> lsenditem=GetOrderTracking();
            Gson lgson = new Gson();
            String ljson = lgson.toJson(lsenditem);
            lSC.mJSONBody = ljson;
			/*if(lsenditem.size() == 0)
				lSC.mJSONBody="";*/
        }
        if (pSC.mOptionCode.equals("1009")) {
            String mlsmancode = "";
            lSC.mAPI = "GetDeliveryDetailsZip";
            lSC.mAPIResult = "GetDeliveryDetailsZipResult";
            String lselectQuery = "SELECT * FROM Login where UserType='SM' or  UserType='CL'";
            Cursor lcs = gsdb.rawQuery(lselectQuery, null);
            if (lcs != null && lcs.moveToFirst()) {
                mlsmancode = lcs.getString(lcs.getColumnIndex("SuppId"));
            }
            SendDeliveryDetails lsenditem = new SendDeliveryDetails(mSupplierName, mlsmancode);
            Gson lgson = new Gson();
            String ljson = lgson.toJson(lsenditem);
            lSC.mJSONBody = ljson;
        }
        return lSC;

    }

    @SuppressLint("Range")
    public ArrayList<OrderTracking> GetOrderTracking() {
        ArrayList<OrderTracking> ot = new ArrayList<OrderTracking>();
        String lselectQuerysinkdate = "SELECT distinct OrderNo FROM POrder where Tag='X' and (Tracking is null or Tracking ='') ";
        Cursor lcs = gsdb.rawQuery(lselectQuerysinkdate, null);
        if (lcs != null && lcs.moveToFirst()) {
            do {
                String lorderNo = "'" + lcs.getString(lcs.getColumnIndex("OrderNo")) + "'";
                OrderTracking mot = new OrderTracking(mSupplierName, lorderNo);
                ot.add(mot);
            } while (lcs.moveToNext());
        }
        return ot;
    }

    public void getSinKItemDate() {
        Cursor lcs1 = null;
        try {
            String selectQuery = "SELECT * FROM UpdateCustomerItem ";
            lcs1 = gsdb.rawQuery(selectQuery, null);

//            //
//            String value1 = mLastItemUpdateDate.split("-")[1].toString().length()>3?mLastItemUpdateDate.split("-")[1].toString().substring(0, 3):mLastItemUpdateDate.split("-")[1].toString();
//            mLastItemUpdateDate = mLastItemUpdateDate.split("-")[0].toString()+"-"+value1+"-"+mLastItemUpdateDate.split("-")[2].toString();
//            //
//
//            //
//            String value = mLastCustomerUpdateDate.split("-")[1].toString().length()>3?mLastCustomerUpdateDate.split("-")[1].toString().substring(0, 3):mLastCustomerUpdateDate.split("-")[1].toString();
//            mLastCustomerUpdateDate = mLastCustomerUpdateDate.split("-")[0].toString()+"-"+value+"-"+mLastCustomerUpdateDate.split("-")[2].toString();
//            //

            if (lcs1.getCount() == 0) {
                ContentValues lvalue = new ContentValues();

                lvalue.put("SinkItemDate", mLastItemUpdateDate);
                lvalue.put("SinkCustomerDate", mLastCustomerUpdateDate);

                gsdb.insert("UpdateCustomerItem", null, lvalue);
            } else {
                gsdb.execSQL("update UpdateCustomerItem set SinkItemDate='" + mLastItemUpdateDate + "',SinkCustomerDate='" + mLastCustomerUpdateDate + "'");
            }
        } catch (Exception e) {
            final GlobalParameter globalVariable = (GlobalParameter) getApplicationContext();
            globalVariable.appendLog(e);
            // Toast.makeText(getApplicationContext(),globalVariable.getErrorToastMessage(),Toast.LENGTH_SHORT).show();
        } finally {
            if (lcs1 != null) {
                lcs1.close();
            }
        }
    }

    @SuppressLint("Range")
    public String GetMasterLastSyncDate() {
        String lmasterdate = "01-Jan-2000";
        Cursor lcs1 = null;
        try {
            String lselectQuerysinkdate = "select max( JulianDay(substr(F.Updated_at, 8, 4) || '-' || M.MonNo || '-' || substr(F.Updated_at, 1, 2))),F.Updated_at from Master F LEFT JOIN Month M ON substr(F .Updated_at, 4, 3) LIKE M.MonStr";
            Cursor lcs = gsdb.rawQuery(lselectQuerysinkdate, null);
            if (lcs != null && lcs.moveToFirst()) {
                do {
                    lmasterdate = lcs.getString(lcs.getColumnIndex("Updated_at"));
                    lcs.close();
                    if (lmasterdate == null)
                        lmasterdate = "01-Jan-2000";
                    return lmasterdate;
                } while (lcs.moveToNext());
            } else {
                lcs.close();
                if (lmasterdate == null)
                    lmasterdate = "01-Jan-2000";
                lmasterdate = "01-Jan-2000";
                return lmasterdate;
            }

        } catch (Exception e) {
            final GlobalParameter globalVariable = (GlobalParameter) getApplicationContext();
            globalVariable.appendLog(e);
            // Toast.makeText(getApplicationContext(),globalVariable.getErrorToastMessage(),Toast.LENGTH_SHORT).show();
        } finally {
            if (lcs1 != null) {
                lcs1.close();
            }
        }
        return lmasterdate;
    }

    @SuppressLint("Range")
    public void GetlastSyncDate() {
        String lselectQuerysinkdate = "SELECT * FROM UpdateCustomerItem ";
        Cursor lcs = gsdb.rawQuery(lselectQuerysinkdate, null);
        if (lcs != null && lcs.moveToFirst()) {
            do {
                String lcustomerdate = lcs.getString(lcs.getColumnIndex("SinkCustomerDate"));

                //
                String value1 = lcustomerdate.split("-")[1].toString().length()>3?lcustomerdate.split("-")[1].toString().substring(0, 3):lcustomerdate.split("-")[1].toString();
                lcustomerdate = lcustomerdate.split("-")[0].toString()+"-"+value1+"-"+lcustomerdate.split("-")[2].toString();
                //

                String litemdate = lcs.getString(lcs.getColumnIndex("SinkItemDate"));

                //
                String value = litemdate.split("-")[1].toString().length()>3?litemdate.split("-")[1].toString().substring(0, 3):litemdate.split("-")[1].toString();
                litemdate = litemdate.split("-")[0].toString()+"-"+value+"-"+litemdate.split("-")[2].toString();
                //

                if (litemdate != null) {

                    mLastItemUpdateDate = litemdate;
                } else {
                    mLastItemUpdateDate = "01-Jan-2000";
                }
                if (lcustomerdate != null) {
                    mLastCustomerUpdateDate = lcustomerdate;
                } else {
                    mLastCustomerUpdateDate = "01-Jan-2000";
                }
            } while (lcs.moveToNext());
        } else {
            mLastItemUpdateDate = "01-Jan-2000";
            mLastCustomerUpdateDate = "01-Jan-2000";
        }
    }

    @Override
    public void onStop() {
        //do your stuff here
        super.onStop();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                //finish();
                int i = 0;
                onBackPressed();
                break;
        }
        return true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            onBackPressed();
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        try {
            if (mUpdating > 0 || mDownloading > 0) {
                AlertDialog.Builder adb = new AlertDialog.Builder(this);
                adb.setTitle("Alert");
                adb.setMessage("Datasync in Progress, Want to Abort!");
                adb.setIcon(android.R.drawable.ic_dialog_alert);
                adb.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            mActivityClosing = true;
                            sleep(100);
                            finish();
                        } catch (Exception ex) {
                            final GlobalParameter globalVariable = (GlobalParameter) getApplicationContext();
                            globalVariable.appendLog(ex);
                            //Toast.makeText(getApplicationContext(),globalVariable.getErrorToastMessage(),Toast.LENGTH_SHORT).show();
                        }
                    }
                });
                adb.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) { }
                });
                adb.show();
            } else {
                finish();
            }
        } catch (Exception ex) {
            final GlobalParameter globalVariable = (GlobalParameter) getApplicationContext();
            globalVariable.appendLog(ex);
            // Toast.makeText(getApplicationContext(),globalVariable.getErrorToastMessage(),Toast.LENGTH_SHORT).show();
        }
    }

    public void setOnCheckedChangeListener(View view) {

        final GlobalParameter globalVariable = (GlobalParameter) getApplicationContext();
        if (globalVariable.global_ConfirmOrder.equals("Y")) {
            ((CheckBox) view).setChecked(false);
            try {
                if (mUpdating > 0 || mDownloading > 0) {
                    AlertDialog.Builder adb = new AlertDialog.Builder(this);
                    adb.setTitle("Alert");
                    adb.setMessage("Datasync in Progress, Want to Abort!");
                    adb.setIcon(android.R.drawable.ic_dialog_alert);
                    adb.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            try {
                                mActivityClosing = true;
                                sleep(100);
                                try {
                                    mActivityClosing = true;
                                    sleep(100);
                                    CheckBox lcb = findViewById(R.id.exportorder);
                                    if (GetConfirmation() > 0)
                                        lcb.setChecked(true);
                                    else
                                        lcb.setChecked(false);
                                    Intent i = new Intent(getApplicationContext(), OrderConfirmationActivity.class);
                                    startActivityForResult(i, 1);
                                } catch (Exception ex) {
                                    final GlobalParameter globalVariable = (GlobalParameter) getApplicationContext();
                                    globalVariable.appendLog(ex);
                                    //  Toast.makeText(getApplicationContext(),globalVariable.getErrorToastMessage(),Toast.LENGTH_SHORT).show();
                                }
                            } catch (Exception ex) {
                                final GlobalParameter globalVariable = (GlobalParameter) getApplicationContext();
                                globalVariable.appendLog(ex);
                                //     Toast.makeText(getApplicationContext(),globalVariable.getErrorToastMessage(),Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                    adb.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) { }
                    });
                    adb.show();
                } else {
                    Intent i = new Intent(getApplicationContext(), OrderConfirmationActivity.class);
                    startActivityForResult(i, 1);
                }
            } catch (Exception ex) {
                globalVariable.appendLog(ex);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        mActivityClosing = false;
        CheckBox lcb = findViewById(R.id.exportorder);
        if (GetConfirmation() > 0)
            lcb.setChecked(true);
        else
            lcb.setChecked(false);
    }

    @SuppressLint("Range")
    public int GetConfirmation() {
        Cursor lcs = null;
        try {
            String lselectQuery = "SELECT Count(*) as ItemCount FROM POrder  where Tag='P' and Confirm='C' ";
            lcs = gsdb.rawQuery(lselectQuery, null);
            if (lcs != null && lcs.moveToFirst()) {
                String mCount = lcs.getString(lcs.getColumnIndex("ItemCount"));
                return Integer.parseInt(mCount);
            }
        } catch (Exception ex) {
            final GlobalParameter globalVariable = (GlobalParameter) getApplicationContext();
            globalVariable.appendLog(ex);
            //Toast.makeText(getApplicationContext(),globalVariable.getErrorToastMessage(),Toast.LENGTH_SHORT).show();
        }
        return 0;
    }

    private String convertDateToSQLLITE(String mdate) {

        //Done by shivam 16-09-2022
        SimpleDateFormat formatter = new SimpleDateFormat("dd-MMM-yyyy");
        String date = formatter.format(Date.parse(mdate));
        //Done by shivam 16-09-2022

        SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MMM-yyyy");
        Date myDate = null;
        try {
            myDate = dateFormat.parse(date);
        } catch (Exception ex) {
            final GlobalParameter globalVariable = (GlobalParameter) getApplicationContext();
            globalVariable.appendLog(ex);
            // Toast.makeText(getApplicationContext(),globalVariable.getErrorToastMessage(),Toast.LENGTH_SHORT).show();
        }
        SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd");
        String finalDate = timeFormat.format(myDate);
        return finalDate;
    }

    private int pastInsertExpiry(JSONArray products, SyncClass pSC) throws JSONException {

        int counter = 0;

        gsdb.execSQL("DROP TABLE IF EXISTS 'ExpiryNew'");
        gsdb.execSQL(" CREATE TABLE ExpiryNew AS   SELECT * FROM Expiry  where Itemc=-999999");
        String sql = "INSERT into ExpiryNew (Itemc , Batch , Expiry , MRP) VALUES (?,?,?,?)";
        int dbProductCount = products.length();
        int counterprogress = counterProgress(dbProductCount);
        int count = 0;
        // begin transactions
        gsdb.beginTransaction();
        SQLiteStatement stmt = gsdb.compileStatement(sql);
        for (int x = 0; x < dbProductCount; x++) {
            count = count + 1;
            JSONObject product = products.getJSONObject(x);

            try {
                stmt.bindString(1, product.getString("Itemc"));
                stmt.bindString(2, product.getString("Batch"));
                stmt.bindString(3, product.getString("Expiry"));
                stmt.bindString(4, product.getString("Mrp"));
                stmt.executeInsert();
                stmt.clearBindings();
            } catch (Exception ex) {
                final GlobalParameter globalVariable = (GlobalParameter) getApplicationContext();
                globalVariable.appendLog(ex);
            }
            counter = counter + 1;
            if (count > counterprogress) {
                count = 0;
                int lvalue = ((x * 100) / dbProductCount);
                pSC.mDOM.setProgressValue(lvalue);
                pSC.mDOM.setCurrentValue(x + 1);
                pSC.mDOM.setMaxValue(dbProductCount);
                UpdateAdapter();
            }
            if (mActivityClosing == true) {
                pSC.mDOM.setStatusControl("Stopped...");
                UpdateAdapter();
                break;
            }
        }
        // set transactions successful
        gsdb.setTransactionSuccessful();
        // end transaction
        gsdb.endTransaction();
        return counter;
    }

    private int pastInsertDelivery(JSONArray products, SyncClass pSC) throws JSONException {

        int counter = 0;
        gsdb.execSQL("DROP TABLE IF EXISTS 'CustomerDeliveryNew'");
        gsdb.execSQL(" CREATE TABLE CustomerDeliveryNew AS   SELECT * FROM CustomerDelivery  where Acno=-999999");
        String sql = "INSERT into CustomerDeliveryNew (Vtype,Vdt,Acno,Vno,NOI,Amt,Stage,Remarks,Box,PolyBag,IcePack,Tag,ICase,SlipNo,Dman,SAlterCode,Name,Altercode,Address,Address1,Address2,Mobile,City) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        int dbProductCount = products.length();
        int counterprogress = counterProgress(dbProductCount);
        int count = 0;
        // begin transactions
        gsdb.beginTransaction();
        SQLiteStatement stmt = gsdb.compileStatement(sql);
        for (int x = 0; x < dbProductCount; x++) {
            count = count + 1;
            JSONObject product = products.getJSONObject(x);
            try {
                stmt.bindString(1, product.getString("Vtype"));
                String Vdt_date = product.getString("Vdt");
                stmt.bindString(2, formatter.format(java.sql.Date.parse(Vdt_date)));
                stmt.bindString(3, product.getString("Acno"));
                stmt.bindString(4, product.getString("Vno"));
                stmt.bindString(5, product.getString("NOI"));
                stmt.bindString(6, product.getString("Amt"));
                stmt.bindString(7, product.getString("Stage"));
                stmt.bindString(8, product.getString("Remarks"));
                stmt.bindString(9, product.getString("Box"));
                stmt.bindString(10, product.getString("PolyBag"));
                stmt.bindString(11, product.getString("IcePack"));
                stmt.bindString(12, "0");
                stmt.bindString(13, product.getString("ICase"));
                stmt.bindString(14, product.getString("SlipNo"));
                stmt.bindString(15, product.getString("Dman"));
                stmt.bindString(16, product.getString("SAlterCode"));
                stmt.bindString(17, product.getString("Name"));
                stmt.bindString(18, product.getString("Altercode"));
                stmt.bindString(19, product.getString("Address"));
                stmt.bindString(20, product.getString("Address1"));
                stmt.bindString(21, product.getString("Address2"));
                stmt.bindString(22, product.getString("Mobile"));
                stmt.bindString(23, product.getString("City"));
                stmt.executeInsert();
                stmt.clearBindings();
            } catch (Exception ex) {
                final GlobalParameter globalVariable = (GlobalParameter) getApplicationContext();
                globalVariable.appendLog(ex);

            }
            counter = counter + 1;
            if (count > counterprogress) {
                count = 0;
                int lvalue = ((x * 100) / dbProductCount);
                pSC.mDOM.setProgressValue(lvalue);
                pSC.mDOM.setCurrentValue(x + 1);
                pSC.mDOM.setMaxValue(dbProductCount);
                UpdateAdapter();
            }
            if (mActivityClosing == true) {
                pSC.mDOM.setStatusControl("Stopped...");
                UpdateAdapter();
                break;
            }
        }
        // set transactions successful
        gsdb.setTransactionSuccessful();
        // end transaction
        gsdb.endTransaction();
        return counter;
    }

    private int pastInsertItem(JSONArray products, SyncClass pSC) throws JSONException {
        int counter = 0;
        gsdb.execSQL("DROP TABLE IF EXISTS 'ItemDataNew'");
        gsdb.execSQL(" CREATE TABLE ItemDataNew AS   SELECT * FROM ItemData  where ItemCode=-999999");
        String sql = "INSERT into ItemDataNew (Item_name,Itemcode,Item_pack,Item_mrp,Item_margin,Item_vdt,Item_dis,Item_vat,Company_name,Salt,Srate,Scheme,MaxInvQty,LockBilling,MiscSettings,Clqty,Unit,Flag,Status,Compcode,Updated_at,TrimName,Box,ItCat,Hscm,QScm,Sscm1,Sscm2,zsbp,DefSaleOnMrpLess,GST,AScm1,AScm2,BScm1,BScm2,CompGroupCode,CompGroupName,MergeDBName,Name1,Name2,Name3,Name4,Name5,ItCase,Splrate) VALUES (?, ?, ?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        int dbProductCount = products.length();
        int counterprogress = counterProgress(dbProductCount);
        int count = 0;
        gsdb.beginTransaction();
        SQLiteStatement stmt = gsdb.compileStatement(sql);
        for (int x = 0; x < dbProductCount; x++) {
            count = count + 1;
            JSONObject product = products.getJSONObject(x);
            try {
                stmt.bindString(1, product.getString("Name"));
                stmt.bindString(2, product.getString("Code"));
                stmt.bindString(3, product.getString("Pic"));
                stmt.bindString(4, product.getString("Mrp"));
                stmt.bindString(5, product.getString("Margin"));
                stmt.bindString(6, product.getString("Vdt"));
                stmt.bindString(7, product.getString("Dis"));
                stmt.bindString(8, product.getString("ClQty"));
                stmt.bindString(9, product.getString("Compname"));
                stmt.bindString(10, product.getString("Salt"));
                stmt.bindString(11, product.getString("Srate"));
                stmt.bindString(12, product.getString("Scheme"));
                stmt.bindString(13, product.getString("MaxInvQty"));
                stmt.bindString(14, product.getString("LockBilling"));
                stmt.bindString(15, product.getString("MiscSettings"));
                stmt.bindString(16, product.getString("ClQty"));
                stmt.bindString(17, product.getString("Unit"));
                stmt.bindString(18, product.getString("Flag"));
                stmt.bindString(19, product.getString("Status"));
                stmt.bindString(20, product.getString("Compcode"));
                stmt.bindString(21, product.getString("Updated_at"));
                stmt.bindString(22, product.getString("TrimName"));
                stmt.bindString(23, product.getString("Box"));
                stmt.bindString(24, product.getString("ItCat"));
                stmt.bindString(25, product.getString("Hscm"));
                stmt.bindString(26, product.getString("QScm"));
                stmt.bindString(27, product.getString("Sscm1"));
                stmt.bindString(28, product.getString("Sscm2"));
                stmt.bindString(29, product.getString("zsbp"));
                stmt.bindString(30, product.getString("DefSaleOnMrpLess"));
                stmt.bindString(31, product.getString("GST"));
                stmt.bindString(32, product.getString("AScm1"));
                stmt.bindString(33, product.getString("AScm2"));
                stmt.bindString(34, product.getString("BScm1"));
                stmt.bindString(35, product.getString("BScm2"));
                stmt.bindString(36, product.getString("CompGroupCode"));
                stmt.bindString(37, product.getString("CompGroupName"));
                stmt.bindString(38, product.getString("SuppName"));
                stmt.bindString(39, product.getString("Name1"));
                stmt.bindString(40, product.getString("Name2"));
                stmt.bindString(41, product.getString("Name3"));
                stmt.bindString(42, product.getString("Name4"));
                stmt.bindString(43, product.getString("Name5"));
                stmt.bindString(44, product.getString("ItCase"));
                stmt.bindString(45, product.getString("OneMgCode"));
                String mLastDate = product.getString("Updated_at");


                //
                String value = mLastDate.split("-")[1].toString().length()>3?mLastDate.split("-")[1].toString().substring(0, 3):
                        mLastDate.split("-")[1].toString();
                mLastDate = mLastDate.split("-")[0].toString()+"-"+value+"-"+mLastDate.split("-")[2].toString();
                //

                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH);
                    Date strDate = sdf.parse(mLastDate);
                    Date strDate1 = sdf.parse(mLastItemUpdateDate);
                    if (strDate.getTime() > strDate1.getTime()) {
                        mLastItemUpdateDate = mLastDate;
                    }
                } catch (Exception ex) {
                    final GlobalParameter globalVariable = (GlobalParameter) getApplicationContext();
                    globalVariable.appendLog(ex);
                }
                stmt.executeInsert();
                stmt.clearBindings();
            } catch (Exception ex) {
                final GlobalParameter globalVariable = (GlobalParameter) getApplicationContext();
                globalVariable.appendLog(ex);
            }
            counter = counter + 1;
            if (count > counterprogress) {
                count = 0;
                int lvalue = ((x * 100) / dbProductCount);
                pSC.mDOM.setProgressValue(lvalue);
                pSC.mDOM.setCurrentValue(x + 1);
                pSC.mDOM.setMaxValue(dbProductCount);
                UpdateAdapter();
            }
            if (mActivityClosing == true) {
                pSC.mDOM.setStatusControl("Stopped...");
                UpdateAdapter();
                break;
            }
        }
        gsdb.setTransactionSuccessful();
        gsdb.endTransaction();
        return counter;
    }

    private int pastInsertCustomer(JSONArray products, SyncClass pSC) throws JSONException {

        int counter = 0;
        String sql = "";
        try {
            gsdb.execSQL("DROP TABLE IF EXISTS 'CustomerDataNew'");
            gsdb.execSQL(" CREATE TABLE CustomerDataNew AS   SELECT * FROM CustomerData  where Acno=-999999");
            sql = "INSERT into CustomerDataNew (Customer_name,Acno,Sman,Address,Address1,Address2,Telephone,Telephone1,Mobile,CrLimitLock,Status,TrimName,Slcd,Altercode,Email,Area,Route,CrLimit,Dis,DLExpiry,MaxOsAmt,MaxOsInv,CustId,MiscSettings,Updated_at,branchcode,GSTNo,Cstno,CITY,Sequence,LockStatus,LockReason,altdis,CustCat,VisitDays,MinOrdAmt) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        } catch (Exception ex) {
            final GlobalParameter globalVariable = (GlobalParameter) getApplicationContext();
            globalVariable.appendLog(ex);
            // Toast.makeText(getApplicationContext(),globalVariable.getErrorToastMessage(),Toast.LENGTH_SHORT).show();
        }
        int dbProductCount = products.length();
        int counterprogress = counterProgress(dbProductCount);
        int count = 0;
        gsdb.beginTransaction();
        SQLiteStatement stmt = gsdb.compileStatement(sql);
        for (int x = 0; x < dbProductCount; x++) {
            count = count + 1;
            JSONObject product = products.getJSONObject(x);
            try {
                stmt.bindString(1, product.getString("Name"));
                stmt.bindString(2, product.getString("Code"));
                stmt.bindString(3, product.getString("Sman"));
                stmt.bindString(4, product.getString("Address"));
                stmt.bindString(5, product.getString("Address1"));
                stmt.bindString(6, product.getString("Address2"));
                stmt.bindString(7, product.getString("Telephone"));
                stmt.bindString(8, product.getString("Telephone1"));
                stmt.bindString(9, product.getString("Mobile"));
                stmt.bindString(10, product.getString("CrLimitLock"));
                stmt.bindString(11, product.getString("Status"));
                stmt.bindString(12, product.getString("TrimName"));
                stmt.bindString(13, product.getString("Slcd"));
                stmt.bindString(14, product.getString("Altercode"));
                stmt.bindString(15, product.getString("Email"));
                stmt.bindString(16, product.getString("Area"));
                stmt.bindString(17, product.getString("Route"));
                stmt.bindString(18, product.getString("CrLimit"));
                stmt.bindString(19, product.getString("Dis"));
                stmt.bindString(20, product.getString("DLExpiry"));
                stmt.bindString(21, product.getString("MaxOsAmt"));
                stmt.bindString(22, product.getString("MaxOsInv"));
                stmt.bindString(23, product.getString("CustId"));
                stmt.bindString(24, product.getString("MiscSetting"));
                stmt.bindString(25, product.getString("Updated_at"));
                stmt.bindString(26, product.getString("BranchCode"));
                stmt.bindString(27, product.getString("GSTNo"));
                stmt.bindString(28, product.getString("CSTNO"));
                stmt.bindString(29, product.getString("CITY"));
                stmt.bindString(30, product.getString("Sequence"));
                stmt.bindString(31, product.getString("LockStatus"));
                stmt.bindString(32, product.getString("LockReason"));
                stmt.bindString(33, product.getString("altdis"));
                stmt.bindString(34, product.getString("CustCat"));
                stmt.bindString(35, product.getString("VisitDays"));
                stmt.bindString(36, product.getString("MinOrdAmt"));
                String mLastDate = product.getString("Updated_at");

                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("dd-MMM-yyyy",Locale.ENGLISH);
                    Date strDate = sdf.parse(mLastDate);
                    Date strDate1 = sdf.parse(mLastCustomerUpdateDate);
                    if (strDate.getTime() > strDate1.getTime()) {
                        mLastCustomerUpdateDate = mLastDate;
                    }
                } catch (Exception ex) {
                    final GlobalParameter globalVariable = (GlobalParameter) getApplicationContext();
                    globalVariable.appendLog(ex);
                    // Toast.makeText(getApplicationContext(),globalVariable.getErrorToastMessage(),Toast.LENGTH_SHORT).show();
                }
                stmt.executeInsert();
                stmt.clearBindings();
            } catch (Exception ex) {
                final GlobalParameter globalVariable = (GlobalParameter) getApplicationContext();
                globalVariable.appendLog(ex);
                // Toast.makeText(getApplicationContext(),globalVariable.getErrorToastMessage(),Toast.LENGTH_SHORT).show();
            }
            counter = counter + 1;
            if (count > counterprogress) {
                count = 0;
                int lvalue = ((x * 100) / dbProductCount);
                pSC.mDOM.setProgressValue(lvalue);
                pSC.mDOM.setCurrentValue(x + 1);
                pSC.mDOM.setMaxValue(dbProductCount);
                UpdateAdapter();
            }
            if (mActivityClosing == true) {
                pSC.mDOM.setStatusControl("Stopped...");
                UpdateAdapter();
                break;
            }
        }
        gsdb.setTransactionSuccessful();
        gsdb.endTransaction();
        return counter;
    }

    private int pastInsertShortage(JSONArray products, SyncClass pSC) throws JSONException {
        int counter = 0;
        try {
            gsdb.execSQL("DROP TABLE IF EXISTS 'ShortageNew'");
            gsdb.execSQL("CREATE TABLE ShortageNew AS  SELECT * FROM Shortage  where Acno=-999999");
        } catch (Exception ex) {
            final GlobalParameter globalVariable = (GlobalParameter) getApplicationContext();
            globalVariable.appendLog(ex);
            // Toast.makeText(getApplicationContext(),globalVariable.getErrorToastMessage(),Toast.LENGTH_SHORT).show();
        }
        String sql = "INSERT into ShortageNew (Ordno ,Custordno ,Odt ,Acno ,OrdQty ,IssuedQty ,Shortage ,Itemc ,Sman ,Area ,Route ,Vno ,Vdt ,Vtype ,Remarks ,Updated_at,UID) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        int dbProductCount = products.length();
        int counterprogress = counterProgress(dbProductCount);
        int count = 0;
        gsdb.beginTransaction();
        SQLiteStatement stmt = gsdb.compileStatement(sql);
        for (int x = 0; x < dbProductCount; x++) {
            count = count + 1;
            JSONObject product = products.getJSONObject(x);
            try {
                stmt.bindString(1, product.getString("Ordno"));
                stmt.bindString(2, product.getString("Custordno"));
                stmt.bindString(3, product.getString("Odt"));
                stmt.bindString(4, product.getString("Acno"));
                stmt.bindString(5, product.getString("OrdQty"));
                stmt.bindString(6, product.getString("IssuedQty"));
                stmt.bindString(7, product.getString("Shortage"));
                stmt.bindString(8, product.getString("Itemc"));
                stmt.bindString(9, product.getString("Sman"));
                stmt.bindString(10, product.getString("Area"));
                stmt.bindString(11, product.getString("Route"));
                stmt.bindString(12, product.getString("Vno"));
                stmt.bindString(13, product.getString("Vdt"));
                stmt.bindString(14, product.getString("Vtype"));
                stmt.bindString(15, product.getString("Remarks"));
                stmt.bindString(16, product.getString("Updated_at"));
                stmt.bindString(17, product.getString("UID"));

                String mLastDate = product.getString("Updated_at");
//				try {
//					SimpleDateFormat sdf = new SimpleDateFormat("dd-MMM-yyyy");
//					Date strDate = sdf.parse(mLastDate);
//
//					SimpleDateFormat sdf1 = new SimpleDateFormat("dd-MMM-yyyy");
//					Date strDate1 = sdf.parse(mLastItemUpdateDate);
//					if (strDate.getTime() > strDate1.getTime()) {
//						mLastItemUpdateDate = mLastDate;
//					}
//				} catch (Exception ex) {
//					final GlobalParameter globalVariable = (GlobalParameter) getApplicationContext();
//					globalVariable.appendLog(ex);
//				}
                stmt.executeInsert();
                stmt.clearBindings();
            } catch (Exception ex) {
                final GlobalParameter globalVariable = (GlobalParameter) getApplicationContext();
                globalVariable.appendLog(ex);
                //Toast.makeText(getApplicationContext(),globalVariable.getErrorToastMessage(),Toast.LENGTH_SHORT).show();
            }
            counter = counter + 1;
            if (count > counterprogress) {
                count = 0;
                int lvalue = ((x * 100) / dbProductCount);
                pSC.mDOM.setProgressValue(lvalue);
                pSC.mDOM.setCurrentValue(x + 1);
                pSC.mDOM.setMaxValue(dbProductCount);
                UpdateAdapter();
            }
            if (mActivityClosing == true) {
                pSC.mDOM.setStatusControl("Stopped...");
                UpdateAdapter();
                break;
            }
        }
        gsdb.setTransactionSuccessful();
        gsdb.endTransaction();
        return counter;
    }

    private int pastInsertTracking(JSONArray products, SyncClass pSC) throws JSONException {
        int counter = 0;
        gsdb.execSQL("DROP TABLE IF EXISTS 'TrackingNew'");
        gsdb.execSQL(" CREATE TABLE TrackingNew AS   SELECT * FROM Tracking  where Acno=-999999");
        String sql = "INSERT into TrackingNew (Vno , Vtype , Vdt ,Amt , Sman ,Area ,Route ,Acno ,Remarks ,Ordno ,CustOrdNo ,Updated_at,UID) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";
        int dbProductCount = products.length();
        int counterprogress = counterProgress(dbProductCount);
        int count = 0;
        gsdb.beginTransaction();
        SQLiteStatement stmt = gsdb.compileStatement(sql);
        for (int x = 0; x < dbProductCount; x++) {
            count = count + 1;
            JSONObject product = products.getJSONObject(x);
            try {
                stmt.bindString(1, product.getString("Vno"));
                stmt.bindString(2, product.getString("Vtype"));
                stmt.bindString(3, product.getString("Vdt"));
                stmt.bindString(4, product.getString("Amt"));
                stmt.bindString(5, product.getString("Sman"));
                stmt.bindString(6, product.getString("Area"));
                stmt.bindString(7, product.getString("Route"));
                stmt.bindString(8, product.getString("Acno"));
                stmt.bindString(9, product.getString("Remarks"));
                stmt.bindString(10, product.getString("Ordno"));
                stmt.bindString(11, product.getString("CustOrdNo"));
                stmt.bindString(12, product.getString("Updated_at"));
                stmt.bindString(13, product.getString("UID"));
                String mLastDate = product.getString("Updated_at");
//				try {
//					SimpleDateFormat sdf = new SimpleDateFormat("dd-MMM-yyyy");
//					Date strDate = sdf.parse(mLastDate);
//
//					SimpleDateFormat sdf1 = new SimpleDateFormat("dd-MMM-yyyy");
//					Date strDate1 = sdf.parse(mLastItemUpdateDate);
//					if (strDate.getTime() > strDate1.getTime()) {
//						mLastItemUpdateDate = mLastDate;
//					}
//				} catch (Exception ex) {
//					final GlobalParameter globalVariable = (GlobalParameter) getApplicationContext();
//					globalVariable.appendLog(ex);
//				}
                stmt.executeInsert();
                stmt.clearBindings();
            } catch (Exception ex) {
                final GlobalParameter globalVariable = (GlobalParameter) getApplicationContext();
                globalVariable.appendLog(ex);
                //Toast.makeText(getApplicationContext(),globalVariable.getErrorToastMessage(),Toast.LENGTH_SHORT).show();
            }
            counter = counter + 1;
            if (count > counterprogress) {
                count = 0;
                int lvalue = ((x * 100) / dbProductCount);
                pSC.mDOM.setProgressValue(lvalue);
                pSC.mDOM.setCurrentValue(x + 1);
                pSC.mDOM.setMaxValue(dbProductCount);
                UpdateAdapter();
            }
            if (mActivityClosing == true) {
                pSC.mDOM.setStatusControl("Stopped...");
                UpdateAdapter();
                break;
            }

        }
        gsdb.setTransactionSuccessful();
        gsdb.endTransaction();
        return counter;
    }

    private int pastInsertCustItemLock(JSONArray products, SyncClass pSC) throws JSONException {
        int counter = 0;
        gsdb.execSQL("DROP TABLE IF EXISTS 'CustItemLockNew'");
        gsdb.execSQL(" CREATE TABLE CustItemLockNew AS   SELECT * FROM CustItemLock  where Acno=-999999");
        String sql = "INSERT into CustItemLockNew (Slcd,Acno,LockType,LockCode,LockQty,Updated_at) VALUES (?,?,?,?,?,?)";
        int dbProductCount = products.length();
        int counterprogress = counterProgress(dbProductCount);
        int count = 0;
        gsdb.beginTransaction();
        SQLiteStatement stmt = gsdb.compileStatement(sql);
        for (int x = 0; x < dbProductCount; x++) {
            count = count + 1;

            JSONObject product = products.getJSONObject(x);

            try {
                stmt.bindString(1, product.getString("Slcd"));
                stmt.bindString(2, product.getString("Acno"));
                stmt.bindString(3, product.getString("LockType"));
                stmt.bindString(4, product.getString("LockCode"));
                stmt.bindString(5, product.getString("LockQty"));
                stmt.bindString(6, product.getString("Updated_at"));


                stmt.executeInsert();
                stmt.clearBindings();
            } catch (Exception ex) {
                final GlobalParameter globalVariable = (GlobalParameter) getApplicationContext();
                globalVariable.appendLog(ex);
                //Toast.makeText(getApplicationContext(),globalVariable.getErrorToastMessage(),Toast.LENGTH_SHORT).show();
            }
            counter = counter + 1;
            if (count > counterprogress) {
                count = 0;
                int lvalue = ((x * 100) / dbProductCount);
                pSC.mDOM.setProgressValue(lvalue);
                pSC.mDOM.setCurrentValue(x + 1);
                pSC.mDOM.setMaxValue(dbProductCount);
                UpdateAdapter();
            }
            if (mActivityClosing == true) {
                pSC.mDOM.setStatusControl("Stopped...");
                UpdateAdapter();
                break;
            }

        }
        gsdb.setTransactionSuccessful();
        gsdb.endTransaction();
        return counter;
    }

    private int pastDUE(JSONArray products, SyncClass pSC) throws JSONException {
        int counter = 0;
        gsdb.execSQL("DROP TABLE IF EXISTS 'OutStandingNew'");
        gsdb.execSQL(" CREATE TABLE OutStandingNew AS   SELECT * FROM OutStanding  where Acno=-999999");
        String sql = "INSERT into OutStandingNew (Acno ,Vtype ,OsAmt , Vdt  , Vno , AcAmt , Duedate , Sman , Area , Route ) VALUES (?,?,?,?,?,?,?,?,?,?)";
        int dbProductCount = products.length();
        int counterprogress = counterProgress(dbProductCount);
        int count = 0;
        gsdb.beginTransaction();
        SQLiteStatement stmt = gsdb.compileStatement(sql);
        for (int x = 0; x < dbProductCount; x++) {
            count = count + 1;
            JSONObject product = products.getJSONObject(x);
            try {
                stmt.bindString(1, product.getString("Acno"));
                stmt.bindString(2, product.getString("Vtype"));
                stmt.bindString(3, product.getString("OsAmt"));
                stmt.bindString(4, product.getString("Vdt"));
                stmt.bindString(5, product.getString("Vno"));
                stmt.bindString(6, product.getString("AcAmt"));
                stmt.bindString(7, product.getString("DueDate"));
                stmt.bindString(8, product.getString("Sman"));
                stmt.bindString(9, product.getString("Area"));
                stmt.bindString(10, product.getString("Route"));
                stmt.executeInsert();
                stmt.clearBindings();
            } catch (Exception ex) {
                final GlobalParameter globalVariable = (GlobalParameter) getApplicationContext();
                globalVariable.appendLog(ex);
            }
            counter = counter + 1;
            if (count > counterprogress) {
                count = 0;
                int lvalue = ((x * 100) / dbProductCount);
                pSC.mDOM.setProgressValue(lvalue);
                pSC.mDOM.setCurrentValue(x + 1);
                pSC.mDOM.setMaxValue(dbProductCount);
                UpdateAdapter();
            }
            if (mActivityClosing == true) {
                pSC.mDOM.setStatusControl("Stopped...");
                UpdateAdapter();
                break;
            }
        }
        gsdb.setTransactionSuccessful();
        gsdb.endTransaction();
        return counter;
    }

    private int pastPDC(JSONArray products, SyncClass pSC) throws JSONException {
        int counter = 0;
        gsdb.execSQL("DROP TABLE IF EXISTS 'PDCNew'");
        gsdb.execSQL(" CREATE TABLE PDCNew AS   SELECT * FROM PDC  where Acno=-999999");
        String sql = "INSERT into PDCNew (Acno , Vtype ,Amount , Vdt , Vno , Bacno , Chqno , Chqdt , Tag , Sman , Area , Route, AlterCode,Customer_name ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        int dbProductCount = products.length();
        int counterprogress = counterProgress(dbProductCount);
        int count = 0;
        gsdb.beginTransaction();
        SQLiteStatement stmt = gsdb.compileStatement(sql);
        ArrayList<String> stringArrayList = new ArrayList<>();
        for (int x = 0; x < dbProductCount; x++) {
            count = count + 1;
            stringArrayList.add(products.getString(x));
            JSONObject product = products.getJSONObject(x);
            try {
                stmt.bindString(1, product.getString("Acno"));
                stmt.bindString(2, product.getString("Vtype"));
                stmt.bindString(3, product.getString("Amt"));
                stmt.bindString(4, product.getString("Vdt"));
                stmt.bindString(5, product.getString("Vno"));
                stmt.bindString(6, product.getString("Bacno"));
                stmt.bindString(7, product.getString("Chqno"));
                stmt.bindString(8, product.getString("Chqdt"));
                stmt.bindString(9, product.getString("Tag"));
                stmt.bindString(10, product.getString("Sman"));
                stmt.bindString(11, product.getString("Area"));
                stmt.bindString(12, product.getString("Route"));
                stmt.bindString(13, product.getString("AlterCode"));
                stmt.bindString(14, product.getString("Name"));
                stmt.executeInsert();
                stmt.clearBindings();
            } catch (Exception ex) {
                final GlobalParameter globalVariable = (GlobalParameter) getApplicationContext();
                globalVariable.appendLog(ex);
            }
            counter = counter + 1;
            if (count > counterprogress) {
                count = 0;
                int lvalue = ((x * 100) / dbProductCount);
                pSC.mDOM.setProgressValue(lvalue);
                pSC.mDOM.setCurrentValue(x + 1);
                pSC.mDOM.setMaxValue(dbProductCount);
                UpdateAdapter();
            }
            if (mActivityClosing == true) {
                pSC.mDOM.setStatusControl("Stopped...");
                UpdateAdapter();
                break;
            }
        }
        gsdb.setTransactionSuccessful();
        gsdb.endTransaction();
        return counter;
    }

    public int counterProgress(int mLength) {
        if (mLength > 100 && mLength <= 1000)
            return 1;
        if (mLength > 1000 && mLength <= 10000)
            return 100;
        if (mLength > 10000 && mLength <= 30000)
            return 500;
        if (mLength > 30000 && mLength <= 100000)
            return 1000;
        if (mLength > 100000)
            return 5000;
        return 1;
    }

    public class DownloadAsyncTask extends AsyncTask<SyncClass, DialogOptionModel, SyncClass> {
        @Override
        protected void onPreExecute() {
            mDownloading = mDownloading + 1;
            CheckBox cb = findViewById(R.id.selectall);
            cb.setEnabled(false);
        }
        @Override
        protected SyncClass doInBackground(SyncClass... params) {
            SyncClass mParameters = params[0];
            try {
                final GlobalParameter globalVariable = (GlobalParameter) getApplicationContext();
                mParameters.mDOM.setStatusControl("Downloading...");
                mParameters = PrepareData(mParameters);
                if (mParameters.mJSONBody.length() == 0) {
                    mParameters.mDOM.setStatusControl("No Data to Retrive...");
                    mParameters.mDOM.setCurrentValue(100);
                    mParameters.mDOM.setMaxValue(100);
                    mParameters.mDOM.mStatus = 1;
                    UpdateAdapter();
                    return mParameters;
                }
                URL lurl = globalVariable.getActiveURL(mParameters.mAPI);
                GetUrl gu = new GetUrl();
                mParameters.mResponse = gu.postAsync(lurl.toString(), mParameters.mJSONBody, adapter, mParameters, SynchronizeView.this);
                if (mParameters.mResponse != null && mParameters.mResponse.length() > 0) {
                    if (mParameters.mOptionCode.equals("1001")) {
                        try {
                            JSONObject ljsonObj = new JSONObject(mParameters.mResponse);
                            mParameters.mResponse = "";
                            mParameters.mDOM.setStatusControl("Retriving Data...");
                            UpdateAdapter();
                            JSONObject oarr = ljsonObj.getJSONObject(mParameters.mAPIResult);
                            mParameters.mDOM.setStatusControl("Extracting Data...");
                            UpdateAdapter();
                            String bsf = oarr.getString("ItemData");
                            mParameters.mDOM.setStatusControl("Unzipping Data...");
                            UpdateAdapter();
                            byte[] abc = Base64.decode(bsf, Base64.DEFAULT);
                            mParameters.mResponse = Gzip.decompress(abc);
                        } catch (Exception ex) {
                            mParameters.mDOM.mStatus = 0;
                            globalVariable.appendLog(ex);
                            return mParameters;
                        }
                    }
                    if (mParameters.mOptionCode.equals("1002")) {
                        try {
                            if (mUserType.equals("CL")) {
                                JSONObject ljsonObj = new JSONObject(mParameters.mResponse);
                                mParameters.mResponse = "";
                                mParameters.mDOM.setStatusControl("Retriving Data...");
                                UpdateAdapter();
                                JSONArray larr = null;
                                larr = ljsonObj.getJSONArray(mParameters.mAPIResult);
                                mParameters.mResponse = larr.toString();
                            } else if (mUserType.equals("SM")) {
                                Log.d("user_name",mParameters.mResponse);
                                JSONObject ljsonObj = new JSONObject(mParameters.mResponse);
                                mParameters.mResponse = "";
                                mParameters.mDOM.setStatusControl("Retriving Data...");
                                UpdateAdapter();
                                JSONObject oarr = ljsonObj.getJSONObject(mParameters.mAPIResult);
                                mParameters.mDOM.setStatusControl("Extracting Data...");
                                UpdateAdapter();
                                String bsf = oarr.getString("ItemData");
                                mParameters.mDOM.setStatusControl("Unzipping Data...");
                                UpdateAdapter();
                                byte[] abc = Base64.decode(bsf, Base64.DEFAULT);
                                mParameters.mResponse = Gzip.decompress(abc);
                            }
                        } catch (Exception ex) {
                            mParameters.mDOM.mStatus = 0;
                            globalVariable.appendLog(ex);
                            return mParameters;
                        }
                    }
                    if (mParameters.mOptionCode.equals("1003")) {
                        try {
                            JSONObject ljsonObj = new JSONObject(mParameters.mResponse);
                            mParameters.mResponse = "";
                            mParameters.mDOM.setStatusControl("Retriving Data...");
                            UpdateAdapter();
                            JSONObject oarr = ljsonObj.getJSONObject(mParameters.mAPIResult);
                            mParameters.mDOM.setStatusControl("Extracting Data...");
                            UpdateAdapter();
                            String bsf = oarr.getString("ItemData");
                            mParameters.mDOM.setStatusControl("Unzipping Data...");
                            UpdateAdapter();
                            byte[] abc = Base64.decode(bsf, Base64.DEFAULT);
                            mParameters.mResponse = Gzip.decompress(abc);
                        } catch (Exception ex) {
                            mParameters.mDOM.mStatus = 0;
                            globalVariable.appendLog(ex);
                            return mParameters;
                        }
                    }
                    if (mParameters.mOptionCode.equals("101")) {
                        try {
                            JSONObject ljsonObj = new JSONObject(mParameters.mResponse);
                            mParameters.mResponse = "";
                            mParameters.mDOM.setStatusControl("Retriving Data...");
                            UpdateAdapter();
                            JSONArray larr = null;
                            larr = ljsonObj.getJSONArray(mParameters.mAPIResult);
                            mParameters.mResponse = larr.toString();
                        } catch (Exception ex) {
                            mParameters.mDOM.mStatus = 0;
                            globalVariable.appendLog(ex);
                            return mParameters;
                        }
                    }
                    if (mParameters.mOptionCode.equals("1004")) {
                        try {
                            JSONObject ljsonObj = new JSONObject(mParameters.mResponse);
                            mParameters.mResponse = "";
                            mParameters.mDOM.setStatusControl("Retriving Data...");
                            UpdateAdapter();
                            JSONObject oarr = ljsonObj.getJSONObject(mParameters.mAPIResult);
                            mParameters.mDOM.setStatusControl("Extracting Data...");
                            UpdateAdapter();
                            String bsf = oarr.getString("ItemData");
                            mParameters.mDOM.setStatusControl("Unzipping Data...");
                            UpdateAdapter();
                            byte[] abc = Base64.decode(bsf, Base64.DEFAULT);
                            mParameters.mResponse = Gzip.decompress(abc);
                        } catch (Exception ex) {
                            mParameters.mDOM.mStatus = 0;
                            globalVariable.appendLog(ex);
                            return mParameters;
                        }
                    }
                    if (mParameters.mOptionCode.equals("102")) {
                        try {
                            JSONObject ljsonObj = new JSONObject(mParameters.mResponse);
                            mParameters.mResponse = "";
                            mParameters.mDOM.setStatusControl("Retriving Data...");
                            UpdateAdapter();
                            JSONObject oarr = ljsonObj.getJSONObject(mParameters.mAPIResult);
                            mParameters.mDOM.setStatusControl("Extracting Data...");
                            UpdateAdapter();
                            String bsf = oarr.getString("ItemData");
                            mParameters.mDOM.setStatusControl("Unzipping Data...");
                            UpdateAdapter();
                            byte[] abc = Base64.decode(bsf, Base64.DEFAULT);
                            mParameters.mResponse = Gzip.decompress(abc);
                            if (mParameters.mResponse.equals("[]"))
                                mParameters.mDOM.mStatus = 1;
                        } catch (Exception ex) {
                            mParameters.mDOM.mStatus = 0;
                            globalVariable.appendLog(ex);
                            return mParameters;
                        }
                    }
                    if (mParameters.mOptionCode.equals("1005")) {
                        try {
                            JSONObject ljsonObj = new JSONObject(mParameters.mResponse);
                            mParameters.mResponse = "";
                            mParameters.mDOM.setStatusControl("Retriving Data...");
                            UpdateAdapter();
                            JSONArray larr = null;
                            larr = ljsonObj.getJSONArray(mParameters.mAPIResult);
                            mParameters.mResponse = larr.toString();
                        } catch (Exception ex) {
                            mParameters.mDOM.mStatus = 0;
                            globalVariable.appendLog(ex);

                            return mParameters;
                        }
                    }
                    if (mParameters.mOptionCode.equals("1006")) {
                        try {
                            JSONObject ljsonObj = new JSONObject(mParameters.mResponse);
                            mParameters.mResponse = "";
                            mParameters.mDOM.setStatusControl("Retriving Data...");
                            UpdateAdapter();
                            JSONObject oarr = ljsonObj.getJSONObject(mParameters.mAPIResult);
                            mParameters.mDOM.setStatusControl("Extracting Data...");
                            UpdateAdapter();
                            String bsf = oarr.getString("ItemData");
                            mParameters.mDOM.setStatusControl("Unzipping Data...");
                            UpdateAdapter();
                            byte[] abc = Base64.decode(bsf, Base64.DEFAULT);
                            mParameters.mResponse = Gzip.decompress(abc);
                        } catch (Exception ex) {
                            mParameters.mDOM.mStatus = 0;
                            globalVariable.appendLog(ex);
                            return mParameters;
                        }

                    }
                    if (mParameters.mOptionCode.equals("1007")) {
                        try {
                            JSONObject ljsonObj = new JSONObject(mParameters.mResponse);
                            mParameters.mResponse = "";
                            mParameters.mDOM.setStatusControl("Retriving Data...");
                            UpdateAdapter();
                            JSONObject oarr = ljsonObj.getJSONObject(mParameters.mAPIResult);
                            mParameters.mDOM.setStatusControl("Extracting Data...");
                            UpdateAdapter();
                            String bsf = oarr.getString("ItemData");
                            mParameters.mDOM.setStatusControl("Unzipping Data...");
                            UpdateAdapter();
                            byte[] abc = Base64.decode(bsf, Base64.DEFAULT);
                            mParameters.mResponse = Gzip.decompress(abc);
                        } catch (Exception ex) {
                            mParameters.mDOM.mStatus = 0;
                            globalVariable.appendLog(ex);
                            return mParameters;
                        }
                    }
                    if (mParameters.mOptionCode.equals("1008")) {
                        try {
                            JSONObject ljsonObj = new JSONObject(mParameters.mResponse);
                            mParameters.mResponse = "";
                            mParameters.mDOM.setStatusControl("Retriving Data...");
                            UpdateAdapter();
                            JSONObject oarr = ljsonObj.getJSONObject(mParameters.mAPIResult);
                            mParameters.mDOM.setStatusControl("Extracting Data...");
                            UpdateAdapter();
                            String bsf = oarr.getString("ItemData");
                            mParameters.mDOM.setStatusControl("Unzipping Data...");
                            UpdateAdapter();
                            byte[] abc = Base64.decode(bsf, Base64.DEFAULT);
                            mParameters.mResponse = Gzip.decompress(abc);
                        } catch (Exception ex) {
                            mParameters.mDOM.mStatus = 0;
                            globalVariable.appendLog(ex);
                            return mParameters;
                        }
                    }
                    if (mParameters.mOptionCode.equals("1009")) {
                        try {
                            JSONObject ljsonObj = new JSONObject(mParameters.mResponse);
                            mParameters.mResponse = "";
                            mParameters.mDOM.setStatusControl("Retriving Data...");
                            UpdateAdapter();
                            JSONObject oarr = ljsonObj.getJSONObject(mParameters.mAPIResult);
                            mParameters.mDOM.setStatusControl("Extracting Data...");
                            UpdateAdapter();
                            String bsf = oarr.getString("ItemData");
                            mParameters.mDOM.setStatusControl("Unzipping Data...");
                            UpdateAdapter();
                            byte[] abc = Base64.decode(bsf, Base64.DEFAULT);
                            mParameters.mResponse = Gzip.decompress(abc);
                        } catch (Exception ex) {
                            mParameters.mDOM.mStatus = 0;
                            globalVariable.appendLog(ex);
                            return mParameters;
                        }
                    }
                }
            } catch (Exception ex) {
                final GlobalParameter globalVariable = (GlobalParameter) getApplicationContext();
                globalVariable.appendLog(ex);
                return mParameters;
            }
            return mParameters;
        }

        @Override
        protected void onPostExecute(final SyncClass result) {
            mDownloading = mDownloading - 1;
            runOnUiThread(new Runnable() {
                public void run() {
                    Cursor lcs1 = null;
                    try {
                        UpdateAsyncTask task = new UpdateAsyncTask();
                        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, result);
                    } catch (Exception e) {
                        final GlobalParameter globalVariable = (GlobalParameter) getApplicationContext();
                        globalVariable.appendLog(e);
                        Log.d("allsync", e.toString());
                        Toast.makeText(getApplicationContext(), globalVariable.getErrorToastMessage(), Toast.LENGTH_SHORT).show();
                    } finally { }
                }
            });
        }
    }

    public class UpdateAsyncTask extends AsyncTask<SyncClass, SyncClass, SyncClass> {
        @Override
        protected void onPreExecute() {
            mUpdating = mUpdating + 1;
        }

        @Override
        protected SyncClass doInBackground(SyncClass... params) {
            SyncClass mParameters = params[0];
            mParameters.mDOM.setCurrentValue(0);
            mParameters.mDOM.setMaxValue(0);
            try {
                mParameters.mDOM.setProgressValue(0);
                if (mParameters.mResponse.length() > 0) {
                    UpdateAdapter();
                    UpdateDataBase(mParameters);
                }
            } catch (Exception ex) {
                final GlobalParameter globalVariable = (GlobalParameter) getApplicationContext();
                globalVariable.appendLog(ex);
            }
            return mParameters;
        }

        @Override
        protected void onPostExecute(SyncClass result) {
            mUpdating = mUpdating - 1;
            result.mDOM.setProgressValue(0);
            if (result.mDOM.getIsObjectEnabled() == false) { } else {
                if (result.mOptionCode.equals("101") || result.mOptionCode.equals("102"))
                    result.mDOM.setIsChecked(false);
                else
                    result.mDOM.setIsChecked(false);
            }
            if (result.mDOM.mStatus == 1) {
                if (mActivityClosing == false)
                    result.mDOM.setStatusControl("Completed...");
                if (mActivityClosing == true)
                    result.mDOM.setStatusControl("Stopped...");
            } else {
                result.mDOM.setStatusControl("Error Downloading...");
            }
            runOnUiThread(new Runnable() {
                public void run() {
                    try {
                        adapter.notifyDataSetChanged();
                    } catch (Exception e) {
                        final GlobalParameter globalVariable = (GlobalParameter) getApplicationContext();
                        globalVariable.appendLog(e);
                        Log.d("finalsync", e.toString());
                        Toast.makeText(getApplicationContext(), globalVariable.getErrorToastMessage(), Toast.LENGTH_SHORT).show();
                    } finally { }
                }
            });
            if (mDownloading == 0 && mUpdating == 0) {
                CheckBox cb = findViewById(R.id.selectall);
                cb.setEnabled(true);
                cb.setChecked(false);
                cb.setText("Select All");
                CheckBox mCheck = findViewById(R.id.exportorder);
                if (mCheck.isChecked() == true && canExport == true) {
                    ExportAsyncTask task = new ExportAsyncTask();
                    task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                }
            }
        }
    }

    @SuppressLint("Range")
    public class ExportAsyncTask extends AsyncTask<SyncClass, SyncClass, SyncClass> {
        @Override
        protected void onPreExecute() {
            mExport = mExport + 1;
            mprogressDialogcircular.show();
        }

        @Override
        protected SyncClass doInBackground(SyncClass... params) {
            String mcompanyholderid = "";
            Cursor lcs1 = null, lcs2 = null, lcs3 = null, lcs4 = null, lcssid = null;
            mtotalcost = 0;
            mcode.clear();
            mquantity.clear();
            mcustomerlist.clear();
            mcustomerNamelist.clear();
            DateFormat ldateFormat = new SimpleDateFormat("dd/MM/yyyy");
            Date ldate = new Date();
            SimpleDateFormat lf1 = new SimpleDateFormat("MMM");
            String lbookdate = ldateFormat.format(ldate);
            String lname, lcode, lpack, lsrate, lmrp, lOrderdate, ljson;
            long litemId = 0;
            final GlobalParameter globalVariable = (GlobalParameter) getApplicationContext();
            URL lurl = globalVariable.getActiveURL("UploadAppOrder");
            try {
                String ltag = "P";
                int lcode1 = 0;
                String lselectQuery = "SELECT distinct Acno,CustomerName,OrderNo FROM POrder where Tag='" + ltag + "' and Confirm ='C'";
                lcs1 = gsdb.rawQuery(lselectQuery, null);
                if (lcs1 != null && lcs1.moveToFirst()) {
                    do {
                        ljson = "";
                        mcustomerlist.add(lcs1.getString(lcs1.getColumnIndex("Acno")));
                        mcustomerNamelist.add(lcs1.getString(lcs1.getColumnIndex("CustomerName")));
                        litemId = lcs1.getLong(lcs1.getColumnIndex("OrderNo"));
                        String lselectQuery12 = "select * from Login where UserType='SL'";
                        lcssid = gsdb.rawQuery(lselectQuery12, null);
                        if (lcssid != null && lcssid.moveToFirst()) {
                            mcompanyholderid = lcssid.getString(lcssid.getColumnIndex("SuppId"));
                            String Code = lcssid.getString(lcssid.getColumnIndex("Password"));
                            lcode1 = Integer.parseInt(Code);
                        }
                        if (litemId == 0) {
                            String ltjson = "{\"dbName" + "\":" + "\"" + mcompanyholderid + "\", " + "\"code" + "\":" + lcode1 + "}";
                            URL lturl = globalVariable.getActiveURL("getOfflineOrder");
                            String result = GetUrl.post(lturl.toString(), ltjson);
                            JSONObject ljsonObj = new JSONObject(result);
                            String labc = ljsonObj.getString("getofflineorderResult");
                            litemId = Integer.parseInt(labc);
                            gsdb.execSQL("update POrder set OrderNo=" + String.valueOf(litemId) + " where Acno=" + lcs1.getString(lcs1.getColumnIndex("Acno")) + " And Tag='" + ltag + "'");
                        }
                        String selectQuery11 = "SELECT * FROM POrder where OrderNo= " + String.valueOf(litemId);
                        lcs2 = gsdb.rawQuery(selectQuery11, null);
                        if (lcs2 != null && lcs2.moveToFirst() && lcssid != null) {
                            do {
                                DateFormat ldateFormat1 = new SimpleDateFormat("dd/MM/yyyy");
                                Date ldate1 = new Date();
                                SimpleDateFormat f = new SimpleDateFormat("MMM");
                                String bookdate = ldateFormat1.format(ldate1);
                                String lar[] = bookdate.split("/");
                                int ldat = Integer.parseInt(lar[0]);
                                int lyear = Integer.parseInt(lar[2]);
                                String month = f.format(new Date());
                                lOrderdate = String.valueOf(ldat) + "-" + month.substring(0,3) + "-" + String.valueOf(lyear);
                                String msalesmanAleadyId = lcs2.getString(lcs2.getColumnIndex("Sman_id"));
                                String mslcd = lcs2.getString(lcs2.getColumnIndex("Slcd"));
                                lcode = lcs2.getString(lcs2.getColumnIndex("ItemCode"));
                                lname = lcs2.getString(lcs2.getColumnIndex("ItemName"));
                                lpack = lcs2.getString(lcs2.getColumnIndex("Pac"));
                                lsrate = lcs2.getString(lcs2.getColumnIndex("Srate"));
                                lmrp = lcs2.getString(lcs2.getColumnIndex("Mrp"));
                                String lUid = lcs2.getString(lcs2.getColumnIndex("Uid"));
                                mitemquantity.add(lcs2.getString(lcs2.getColumnIndex("Qty")));
                                String lfqty = lcs2.getString(lcs2.getColumnIndex("Fqty"));
                                String lTrnid = lcs2.getString(lcs2.getColumnIndex("TrnId"));
                                String llong = lcs2.getString(lcs2.getColumnIndex("longitude"));
                                String llat = lcs2.getString(lcs2.getColumnIndex("latitude"));
                                String lremark = lcs2.getString(lcs2.getColumnIndex("remark"));
                                String ldis = lcs2.getString(lcs2.getColumnIndex("Dis"));
                                String lAlterCode = lcs2.getString(lcs2.getColumnIndex("AlterCode"));
                                String lDBName = lcs2.getString(lcs2.getColumnIndex("DBName"));
                                if (lremark == null)
                                    lremark = "";
                                if (llong == null || llong == "")
                                    llong = "0.0";
                                if (llat == null || llat == "")
                                    llat = "0.0";
                                mbooktotalcost += Float.parseFloat(lsrate) * Float.parseFloat(lcs2.getString(lcs2.getColumnIndex("Qty")));
                                mtotalcost = Float.parseFloat(lsrate) * Float.parseFloat(lcs2.getString(lcs2.getColumnIndex("Qty")));
                                mprice.add(String.valueOf(Float.parseFloat(lsrate) * Integer.parseInt(lcs2.getString(lcs2.getColumnIndex("Qty")))));
                                PlaceOrderDetail orderDetail = new PlaceOrderDetail(lOrderdate, String.valueOf(litemId), "SO", mslcd, lcs1.getString(lcs1.getColumnIndex("Acno")), lcode, lpack, lcs2.getString(lcs2.getColumnIndex("Qty")), lfqty, lmrp, lsrate, String.valueOf(mtotalcost), msalesmanAleadyId, lUid, mcompanyholderid, lTrnid, llong, llat, lremark, ldis, lAlterCode, lDBName, mMergeDBName);
                                Gson lgson = new Gson();
                                String ljson1 = lgson.toJson(orderDetail);
                                ljson = ljson1 + "," + ljson;
                            } while (lcs2.moveToNext());
                        }
                        ljson = "[" + ljson.substring(0, ljson.length() - 1) + "]";
                        String lresult = GetUrl.post(lurl.toString(), ljson);
                        JSONObject ljsonObj = new JSONObject(lresult);
                        String lreturnString22 = ljsonObj.getString("Serials");
                        Status=ljsonObj.getString("Status");
                            //mreturnJsonString = lreturnString22;
                            if(Status.equals("True")){
                                DateFormat ldateFormat1 = new SimpleDateFormat("dd/MM/yyyy");
                                Date ldate1 = new Date();
                                SimpleDateFormat lsimpDate;
                                lsimpDate = new SimpleDateFormat("hh:mm a");
                                String ltagbook = "X";
                                gsdb.execSQL("update POrder set OrderNo=" + String.valueOf(litemId) + ",Tag='" + ltagbook + "',OrderTime='" + lsimpDate.format(ldate1) + "',OrderDate='" + ldateFormat1.format(ldate1) + "' where Acno=" + lcs1.getString(lcs1.getColumnIndex("Acno")) + " And Tag='" + ltag + "'");
                            }
//                        int mlengthofarray = lreturnString22.length();
//                        if(lreturnString22.equals("Customer Code not found")){
//                            Toast.makeText(SynchronizeView.this, "Customer Code not found", Toast.LENGTH_SHORT).show();
//                        }else{
//                            if (mlengthofarray > 0 && !lreturnString22.equals("-12345")) {
//
//                            }
//                        }
                    } while (lcs1.moveToNext());
                }
            } catch (Exception e) {
                globalVariable.appendLog(e);
                runOnUiThread(new Runnable() {
                    public void run() { }
                });
            } finally {
                if (lcs1 != null) {
                    lcs1.close();
                }
                if (lcs2 != null) {
                    lcs2.close();
                }
                if (lcs3 != null) {
                    lcs3.close();
                }
                if (lcs4 != null) {
                    lcs4.close();
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(SyncClass result) {
            mExport = mExport - 1;
            CheckBox mCheck = findViewById(R.id.exportorder);
            mCheck.setChecked(false);
            mprogressDialogcircular.dismiss();
        }
    }

    String Status="False";
//    @RequiresApi(api = Build.VERSION_CODES.CUPCAKE)
//    @SuppressLint("Range")
//    public class ExportAsyncTask extends AsyncTask<SyncClass, SyncClass, SyncClass> {
//        @Override
//        protected void onPreExecute() {
//            mExport = mExport + 1;
//            mprogressDialogcircular.show();
//        }
//
//        @Override
//        protected SyncClass doInBackground(SyncClass... params) {
//            String mcompanyholderid = "";
//            Cursor lcs1 = null, lcs2 = null, lcs3 = null, lcs4 = null, lcssid = null;
//            mtotalcost = 0;
//            mcode.clear();
//            mquantity.clear();
//            mcustomerlist.clear();
//            mcustomerNamelist.clear();
//            DateFormat ldateFormat = new SimpleDateFormat("dd/MM/yyyy");
//            Date ldate = new Date();
//            SimpleDateFormat lf1 = new SimpleDateFormat("MMM");
//            String lbookdate = ldateFormat.format(ldate);
//            String lname, lcode, lpack, lsrate, lmrp, lOrderdate, ljson;
//            long litemId = 0;
//            final GlobalParameter globalVariable = (GlobalParameter) getApplicationContext();
//            URL lurl = globalVariable.getActiveURL("UploadAppOrder");
//            try {
//                String ltag = "P";
//                int lcode1 = 0;
//                String lselectQuery = "SELECT distinct Acno,CustomerName,OrderNo FROM POrder where Tag='" + ltag + "' and Confirm ='C'";
//                lcs1 = gsdb.rawQuery(lselectQuery, null);
//                if (lcs1 != null && lcs1.moveToFirst()) {
//                    do {
//                        ljson = "";
//                        mcustomerlist.add(lcs1.getString(lcs1.getColumnIndex("Acno")));
//                        mcustomerNamelist.add(lcs1.getString(lcs1.getColumnIndex("CustomerName")));
//                        litemId = lcs1.getLong(lcs1.getColumnIndex("OrderNo"));
//                        String lselectQuery12 = "select * from Login where UserType='SL'";
//                        lcssid = gsdb.rawQuery(lselectQuery12, null);
//                        if (lcssid != null && lcssid.moveToFirst()) {
//                            mcompanyholderid = lcssid.getString(lcssid.getColumnIndex("SuppId"));
//                            String Code = lcssid.getString(lcssid.getColumnIndex("Password"));
//                            lcode1 = Integer.parseInt(Code);
//                        }
//                        if (litemId == 0) {
//                            String ltjson = "{\"dbName" + "\":" + "\"" + mcompanyholderid + "\", " + "\"code" + "\":" + lcode1 + "}";
//                            URL lturl = globalVariable.getActiveURL("getOfflineOrder");
//                            String result = GetUrl.post(lturl.toString(), ltjson);
//                            JSONObject ljsonObj = new JSONObject(result);
//                            String labc = ljsonObj.getString("getofflineorderResult");
//                            litemId = Integer.parseInt(labc);
//                            gsdb.execSQL("update POrder set OrderNo=" + String.valueOf(litemId) + " where Acno=" + lcs1.getString(lcs1.getColumnIndex("Acno")) + " And Tag='" + ltag + "'");
//                        }
//                        String selectQuery11 = "SELECT * FROM POrder where OrderNo= " + String.valueOf(litemId);
//                        lcs2 = gsdb.rawQuery(selectQuery11, null);
//                        if (lcs2 != null && lcs2.moveToFirst() && lcssid != null) {
//                            do {
//                                DateFormat ldateFormat1 = new SimpleDateFormat("dd/MM/yyyy");
//                                Date ldate1 = new Date();
//                                SimpleDateFormat f = new SimpleDateFormat("MMM");
//                                String bookdate = ldateFormat1.format(ldate1);
//                                String lar[] = bookdate.split("/");
//                                int ldat = Integer.parseInt(lar[0]);
//                                int lyear = Integer.parseInt(lar[2]);
//                                lOrderdate = String.valueOf(ldat) + "-" + f.format(new Date()) + "-" + String.valueOf(lyear);
//                                String msalesmanAleadyId = lcs2.getString(lcs2.getColumnIndex("Sman_id"));
//                                String mslcd = lcs2.getString(lcs2.getColumnIndex("Slcd"));
//                                lcode = lcs2.getString(lcs2.getColumnIndex("ItemCode"));
//                                lname = lcs2.getString(lcs2.getColumnIndex("ItemName"));
//                                lpack = lcs2.getString(lcs2.getColumnIndex("Pac"));
//                                lsrate = lcs2.getString(lcs2.getColumnIndex("Srate"));
//                                lmrp = lcs2.getString(lcs2.getColumnIndex("Mrp"));
//                                String lUid = lcs2.getString(lcs2.getColumnIndex("Uid"));
//                                mitemquantity.add(lcs2.getString(lcs2.getColumnIndex("Qty")));
//                                String lfqty = lcs2.getString(lcs2.getColumnIndex("Fqty"));
//                                String lTrnid = lcs2.getString(lcs2.getColumnIndex("TrnId"));
//                                String llong = lcs2.getString(lcs2.getColumnIndex("longitude"));
//                                String llat = lcs2.getString(lcs2.getColumnIndex("latitude"));
//                                String lremark = lcs2.getString(lcs2.getColumnIndex("remark"));
//                                String ldis = lcs2.getString(lcs2.getColumnIndex("Dis"));
//                                String lAlterCode = lcs2.getString(lcs2.getColumnIndex("AlterCode"));
//                                String lDBName = lcs2.getString(lcs2.getColumnIndex("DBName"));
//                                if (lremark == null)
//                                    lremark = "";
//                                if (llong == null || llong == "")
//                                    llong = "0.0";
//                                if (llat == null || llat == "")
//                                    llat = "0.0";
//                                mbooktotalcost += Float.parseFloat(lsrate) * Float.parseFloat(lcs2.getString(lcs2.getColumnIndex("Qty")));
//                                mtotalcost = Float.parseFloat(lsrate) * Float.parseFloat(lcs2.getString(lcs2.getColumnIndex("Qty")));
//                                mprice.add(String.valueOf(Float.parseFloat(lsrate) * Integer.parseInt(lcs2.getString(lcs2.getColumnIndex("Qty")))));
//                                PlaceOrderDetail orderDetail = new PlaceOrderDetail(lOrderdate, String.valueOf(litemId), "SO", mslcd, lcs1.getString(lcs1.getColumnIndex("Acno")), lcode, lpack, lcs2.getString(lcs2.getColumnIndex("Qty")), lfqty, lmrp, lsrate, String.valueOf(mtotalcost), msalesmanAleadyId, lUid, mcompanyholderid, lTrnid, llong, llat, lremark, ldis, lAlterCode, lDBName, mMergeDBName);
//                                Gson lgson = new Gson();
//                                String ljson1 = lgson.toJson(orderDetail);
//                                ljson = ljson1 + "," + ljson;
//                            } while (lcs2.moveToNext());
//                        }
//                        ljson = "[" + ljson.substring(0, ljson.length() - 1) + "]";
//                        Log.d("data_json",ljson);
//                        String lresult = GetUrl.post(lurl.toString(), ljson);
//                        if(!lresult.equals("")){
//                            JSONObject ljsonObj1 = new JSONObject(lresult);
//                            String lreturnString22 = ljsonObj1.getString("Serials");
//                            Status=ljsonObj1.getString("Status");
//                            //mreturnJsonString = lreturnString22;
//                            if(Status.equals("True")){
//                                DateFormat ldateFormat1 = new SimpleDateFormat("dd/MM/yyyy");
//                                Date ldate1 = new Date();
//                                SimpleDateFormat lsimpDate;
//                                lsimpDate = new SimpleDateFormat("hh:mm a");
//                                String ltagbook = "X";
//                                gsdb.execSQL("update POrder set OrderNo=" + String.valueOf(litemId) + ",Tag='" + ltagbook + "',OrderTime='" + lsimpDate.format(ldate1) + "',OrderDate='" + ldateFormat1.format(ldate1) + "' where Acno=" + lcs1.getString(lcs1.getColumnIndex("Acno")) + " And Tag='" + ltag + "'");
//                                Toast.makeText(SynchronizeView.this, "Order Placed Successfully", Toast.LENGTH_SHORT).show();
//                            }else{
//                                Log.d("data",lreturnString22);
//                            }
//                        }
//                    } while (lcs1.moveToNext());
//                }
//            } catch (Exception e) {
//                globalVariable.appendLog(e);
//                runOnUiThread(new Runnable() {
//                    public void run() { }
//                });
//            } finally {
//                if (lcs1 != null) {
//                    lcs1.close();
//                }
//                if (lcs2 != null) {
//                    lcs2.close();
//                }
//                if (lcs3 != null) {
//                    lcs3.close();
//                }
//                if (lcs4 != null) {
//                    lcs4.close();
//                }
//            }
//            return null;
//        }
//
//        @Override
//        protected void onPostExecute(SyncClass result) {
//            mExport = mExport - 1;
//            CheckBox mCheck = findViewById(R.id.exportorder);
//            mCheck.setChecked(false);
//            mprogressDialogcircular.hide();
//        }
//    }

    class DownloadingSettingTask extends AsyncTask {
        @Override
        protected Object doInBackground(Object... arg0) {
            try {
                final GlobalParameter globalVariable = (GlobalParameter) getApplicationContext();
                URL lurl = globalVariable.getActiveURL("getSetting");
                String mAlterCode = GetAlterCode(globalVariable.getUserID(), mUserType);
                SendSettingDetail lsenditem = new SendSettingDetail(globalVariable.getSupplierID(), mAlterCode, mUserType, BuildConfig.VERSION_NAME);
                Gson lgson = new Gson();
                String ljson = lgson.toJson(lsenditem);
                String lresult = GetUrl.post(lurl.toString(), ljson);
                JSONObject ljsonObj = new JSONObject(lresult);
                if (ljsonObj != null) {
                    JSONObject larr = ljsonObj.getJSONObject("getSettingResult");
                    if (larr != null) {
                        ContentValues lvalues = new ContentValues();
                        lvalues.put("BalQtyType", larr.getString("BalQtyType"));
                        lvalues.put("ItemScm", larr.getString("ItemScm"));
                        lvalues.put("OfflineMaxOrdno", larr.getString("OfflineMaxOrdno"));
                        lvalues.put("ShowInactiveCustomer", larr.getString("ShowInactiveCustomer"));
                        lvalues.put("ShowInactiveItem", larr.getString("ShowInactiveItem"));
                        lvalues.put("ShowAllCustomer", larr.getString("ShowAllCustomer"));
                        lvalues.put("ItemSearch", larr.getString("ItemSearch"));
                        lvalues.put("ReportCode", larr.getString("ShowReports"));
                        lvalues.put("AllowFreeQty", larr.getString("AllowFreeQty"));
                        lvalues.put("NoOrder", larr.getString("NoOrder"));
                        lvalues.put("DlExpiryAllow", larr.getString("DlExpiryAllow"));
                        lvalues.put("LockDetailAllow", larr.getString("LockDetailAllow"));
                        lvalues.put("AndroidVersion", larr.getString("AndroidVersion"));
                        lvalues.put("OptionCode", larr.getString("OptionCode"));
                        lvalues.put("Validity", larr.getString("Validity"));
                        lvalues.put("ReturnMessage", larr.getString("ReturnMessage"));
                        lvalues.put("StatusMessage", larr.getString("StatusMessage"));
                        lvalues.put("Allowed", larr.getString("Allowed"));
                        lvalues.put("ConfirmOrder", larr.getString("ConfirmOrder"));
                        lvalues.put("NearExpiry", larr.getString("NearExpiry"));
                        lvalues.put("ReportOther", larr.getString("ReportOther"));
                        lvalues.put("ChangeCustomer", larr.getString("ChangeCustomer"));
                        lvalues.put("VisitDays", larr.getString("VisitDays"));
                        lvalues.put("CheckDeviceId", larr.getString("CheckDeviceId"));
                        lvalues.put("ShowCollection", larr.getString("ShowCollection"));
                        lvalues.put("ShowExpiry", larr.getString("ShowExpiry"));
                        lvalues.put("AllowSMS", larr.getString("AllowSMS"));
                        lvalues.put("SMSBody", larr.getString("SMSBody"));
                        lvalues.put("AllowDM", larr.getString("ShowDM"));
                        lvalues.put("Contact", larr.getString("Contact"));
                        lvalues.put("MfgType", larr.getString("MfgType"));
                        lvalues.put("ShowAllDue", larr.getString("ShowAllDue"));
                        globalVariable.global_visitdays = larr.getString("VisitDays");
                        globalVariable.global_showfree = larr.getString("AllowFreeQty");
                        globalVariable.global_itemsch = larr.getString("ItemScm");
                        globalVariable.global_itemsearchVariable = larr.getString("ItemSearch");
                        globalVariable.global_ConfirmOrder = larr.getString("ConfirmOrder");
                        globalVariable.global_Allowed = larr.getString("Allowed");
                        globalVariable.AndroidVersion = larr.getString("AndroidVersion");
                        globalVariable.DlExpiryAllow = larr.getString("DlExpiryAllow");
                        globalVariable.LockDetailAllow = larr.getString("LockDetailAllow");
                        globalVariable.global_CheckDeviceId = larr.getString("CheckDeviceId");
                        globalVariable.global_ShowCollection = larr.getString("ShowCollection");
                        globalVariable.global_AllowSMS = larr.getString("AllowSMS");
                        globalVariable.global_AllowDM = larr.getString("ShowDM");
                        globalVariable.global_ManufactureType = larr.getString("MfgType");
                        globalVariable.global_showalldue = larr.getString("ShowAllDue");
                        gsdb.execSQL("Delete FROM Setting");
                        gsdb.insert("Setting", null, lvalues);
                    }
                }

            } catch (Exception ex) {
                final GlobalParameter globalVariable = (GlobalParameter) getApplicationContext();
                globalVariable.appendLog(ex);
                Log.d("settingexcept", ex.toString());
                runOnUiThread(new Runnable() {
                    public void run() { }
                });
            }
            return null;
        }

        @Override
        protected void onPostExecute(Object result) {
            LoadGlobalVariables();
        }
    }

    public class SyncClass {
        public String mOptionCode = "";
        public DialogOptionModel mDOM;
        public String mResponse = "";
        public String mAPI = "";
        public String mAPIResult = "";
        public String mJSONBody = "";
    }

    public void OnSelectAll(View v) {
        CheckBox cb = findViewById(R.id.selectall);
        if (cb.isChecked() == true)
            cb.setText("Un-Check All");
        else
            cb.setText("Select All");
        for (int i = 0; i < mProductArrayList.size(); i++) {
            DialogOptionModel dm = mProductArrayList.get(i);
            dm.setIsChecked(cb.isChecked());
            dm.setStatusControl("Wait..");
            adapter.notifyDataSetChanged();
        }
    }

    @SuppressLint("Range")
    public void deletedatafromOutstanding() {
        try {
            Cursor cursor = gsdb.rawQuery("select os.OsAmt as OutAmt,osnew.OsAmt as tempOutAmt,os.Vno from outstandingnew osnew left join outstanding os on osnew.vno=os.vno", null);
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    String outAmt = cursor.getString(cursor.getColumnIndex("OutAmt"));
                    String temOutAmt = cursor.getString(cursor.getColumnIndex("tempOutAmt"));
                    String vno = cursor.getString(cursor.getColumnIndex("Vno"));
                    if (outAmt.equals(temOutAmt)) {
                        gsdb.execSQL("delete from outstandingnew where Vno='" + vno + "'");
                    }
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}