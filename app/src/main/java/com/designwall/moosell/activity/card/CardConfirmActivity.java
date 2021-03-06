package com.designwall.moosell.activity.card;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.Address;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.designwall.moosell.R;
import com.designwall.moosell.config.Url;
import com.designwall.moosell.db.DatabaseHelper;
import com.designwall.moosell.model.Order.Order;
import com.designwall.moosell.model.Order.OrderNote;
import com.designwall.moosell.task.GetDataTask;
import com.designwall.moosell.util.GeocoderNominatim;
import com.designwall.moosell.util.Helper;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.j256.ormlite.dao.Dao;

import org.osmdroid.util.GeoPoint;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;

public class CardConfirmActivity extends AppCompatActivity {

    public static final int RESULT_GEOPOINT = 10;

    @BindView(R.id.etBillFirstName)
    EditText etBillFirstName;
    @BindView(R.id.etBillLastName)
    EditText etBillLastName;
    @BindView(R.id.etBillAddress1)
    EditText etBillAddress1;
    @BindView(R.id.etBillAddress2)
    EditText etBillAddress2;
    @BindView(R.id.etBillCompany)
    EditText etBillCompany;
    @BindView(R.id.etBillState)
    EditText etBillState;
    @BindView(R.id.etBillCity)
    EditText etBillCity;
    @BindView(R.id.etPhone)
    EditText etPhone;
    @BindView(R.id.etEmail)
    EditText etEmail;

    @BindView(R.id.etShipFirstName)
    EditText etShipFirstName;
    @BindView(R.id.etShipLastName)
    EditText etShipLastName;
    @BindView(R.id.etShipAddress1)
    EditText etShipAddress1;
    @BindView(R.id.etShipAddress2)
    EditText etShipAddress2;
    @BindView(R.id.etShipCompany)
    EditText etShipCompany;
    @BindView(R.id.etShipState)
    EditText etShipState;
    @BindView(R.id.etShipCity)
    EditText etShipCity;

    @BindView(R.id.btnValidate)
    Button btnValidate;

    @BindView(R.id.btnBack)
    Button btnBack;

    @BindView(R.id.tvLocation)
    TextView tvLocation;

    @BindView(R.id.tvShippingCopy)
    TextView tvShippingCopy;

    @BindView(R.id.pbLoading)
    ProgressBar pbLoading;

    @BindView(R.id.layoutButtons)
    LinearLayout layoutButtons;

    private Gson mGson;

    private DatabaseHelper dbHelper;
    private Dao<Order, Integer> orderDao;

    private String country = "";
    private GeoPoint geoPoint;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_card_confirm);
        ButterKnife.bind(this);
        mGson = new Gson();

        dbHelper = new DatabaseHelper(this);
        try {
            orderDao = dbHelper.getDao();
        } catch (SQLException e) {
            Log.e("Test", "Error: " + e.getMessage());
            e.printStackTrace();
        }

        final int orderId = getIntent().getIntExtra(Helper.LAST_ORDER_ID, 0);
        if (orderId == 0){
            Helper.showDialog(this, getString(R.string.missing_order),
                    getString(R.string.order_number_not_found));
            finish();
            return;
        }

        btnValidate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if ( etBillFirstName.getText().toString().trim().isEmpty() ||
                        etBillLastName.getText().toString().trim().isEmpty() ||
                        etPhone.getText().toString().trim().isEmpty()){
                    Helper.showDialog(CardConfirmActivity.this, getString(R.string.invalid_info),
                            getString(R.string.prompt_provide_info));
                    return;
                }
                validateOrder(orderId);

            }
        });

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setResult(Activity.RESULT_CANCELED, getIntent());
                finish();
            }
        });

        tvLocation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent mapIntent = new Intent(getApplicationContext(), MapActivity.class);
                mapIntent.putExtra("latitude", geoPoint.getLatitude());
                mapIntent.putExtra("longitude", geoPoint.getLongitude());
                startActivityForResult( mapIntent, RESULT_GEOPOINT );
            }
        });

        tvShippingCopy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Helper.showDialog(CardConfirmActivity.this, getString(R.string.copy_billing_info),
                        getString(R.string.copy_billing_info_prompt), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                etShipFirstName.setText(etBillFirstName.getText().toString().trim());
                                etShipLastName.setText(etBillLastName.getText().toString().trim());
                                etShipAddress1.setText(etBillAddress1.getText().toString().trim());
                                etShipAddress2.setText(etBillAddress2.getText().toString().trim());
                                etShipCity.setText(etBillCity.getText().toString().trim());
                                etShipState.setText(etBillState.getText().toString().trim());
                                etShipCompany.setText(etBillCompany.getText().toString().trim());
                            }
                        }, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {}
                        });
            }
        });

        // Load Shipping & Billing info
        loadInfo();

    }

    public void showLoadingView(boolean on) {
        pbLoading.setVisibility(on? View.VISIBLE: View.GONE);
        btnValidate.setEnabled(!on);
        layoutButtons.setVisibility(on? View.GONE: View.VISIBLE);
//        btnBack.setEnabled(!on);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == RESULT_GEOPOINT){
            switch (resultCode){
                case RESULT_OK:
                    GeoPoint point = new GeoPoint(data.getDoubleExtra("lat", 0.0d),
                            data.getDoubleExtra("lon", 0.0d) );
//                    point.setAltitude(data.getDoubleExtra("alt", 0.0d));
                    fillGeoCoderInfo(point);
                    break;
                case RESULT_CANCELED:
                    break;
            }
        }
    }

    private void fillGeoCoderInfo(final GeoPoint point) {
        geoPoint = point;
        Log.d("Test", "Geo: " + point.toString());
        new AsyncTask<Void, Void, Address>() {
            private Address address;

            @Override
            protected void onPreExecute() {
                showLoadingView(true);
            }

            @Override
            protected Address doInBackground(Void... voids) {
                GeocoderNominatim geocoder = new GeocoderNominatim();
                try
                {
                    List<Address> addresses = geocoder.getFromLocation(point.getLatitude(), point.getLongitude(), 1);
                    if (addresses.size() > 0) {
                        address = addresses.get(0);
                    }
                } catch(IOException e){
                    Log.e("Test", "Error geocoder: " + e.getMessage());
                }
                return address;
            }

            @Override
            protected void onPostExecute(Address address) {
                if (address == null) return;
                etBillCity.setText(address.getAdminArea());
                etBillState.setText(address.getCountryName());
                etBillAddress1.setText(address.getSubAdminArea()+", "+address.getLocality()+" - " +
                        address.getCountryName());
                etBillAddress2.setText(address.getSubLocality());
                country = address.getCountryName();
                showLoadingView(false);
            }
        }.execute();
    }

    private void loadInfo() {
        // Billing Info
        etBillFirstName.setText( Helper.loadString(this, "etBillFirstName"));
        etBillLastName.setText( Helper.loadString(this, "etBillLastName"));
        etBillCompany.setText( Helper.loadString(this, "etBillCompany"));
        etBillAddress1.setText( Helper.loadString(this, "etBillAddress1"));
        etBillAddress2.setText( Helper.loadString(this, "etBillAddress2"));
        etBillCity.setText( Helper.loadString(this, "etBillCity"));
        etBillState.setText( Helper.loadString(this, "etBillState"));
        etEmail.setText( Helper.loadString(this, "etEmail"));
        etPhone.setText( Helper.loadString(this, "etPhone"));
        // Shipping Info
        etShipFirstName.setText( Helper.loadString(this, "etShipFirstName"));
        etShipLastName.setText( Helper.loadString(this, "etShipLastName"));
        etShipCompany.setText( Helper.loadString(this, "etShipCompany"));
        etShipAddress1.setText( Helper.loadString(this, "etShipAddress1"));
        etShipAddress2.setText( Helper.loadString(this, "etShipAddress2"));
        etShipCity.setText( Helper.loadString(this, "etShipCity"));
        etShipState.setText( Helper.loadString(this, "etShipState"));
        double latitude = Helper.loadDouble(this, "latitude", 0.0);
        double longitude = Helper.loadDouble(this, "longitude", 0.0);
        geoPoint = new GeoPoint(latitude, longitude);
    }

    private void saveInfo() {
        // Billing Info
        Helper.saveString(this, "etBillFirstName", etBillFirstName.getText().toString().trim());
        Helper.saveString(this, "etBillLastName", etBillLastName.getText().toString().trim());
        Helper.saveString(this, "etBillCompany", etBillCompany.getText().toString().trim());
        Helper.saveString(this, "etBillAddress1", etBillAddress1.getText().toString().trim());
        Helper.saveString(this, "etBillAddress2", etBillAddress2.getText().toString().trim());
        Helper.saveString(this, "etBillCity", etBillCity.getText().toString().trim());
        Helper.saveString(this, "etBillState", etBillState.getText().toString().trim());
        Helper.saveString(this, "etEmail", etEmail.getText().toString().trim());
        Helper.saveString(this, "etPhone", etPhone.getText().toString().trim());
        // Shipping Info
        Helper.saveString(this, "etShipFirstName", etShipFirstName.getText().toString().trim());
        Helper.saveString(this, "etShipLastName", etShipLastName.getText().toString().trim());
        Helper.saveString(this, "etShipCompany", etShipCompany.getText().toString().trim());
        Helper.saveString(this, "etShipAddress1", etShipAddress1.getText().toString().trim());
        Helper.saveString(this, "etShipAddress2", etShipAddress2.getText().toString().trim());
        Helper.saveString(this, "etShipCity", etShipCity.getText().toString().trim());
        Helper.saveString(this, "etShipState", etShipState.getText().toString().trim());
        // GeoLocation Info
        Helper.saveDouble(this, "latitude", geoPoint.getLatitude());
        Helper.saveDouble(this, "longitude", geoPoint.getLongitude());

    }

    @SuppressLint("StaticFieldLeak")
    private void validateOrder(final int orderId) {
        if (country != null && country.isEmpty())
            country = Helper.getCountryISOCode(getApplicationContext());
        String content = "{\n" +
                "  \"order\": {\n" +
                "    \"status\":\"processing\",\n" +
                "    \"payment_details\": {\n" +
                "      \"method_id\": \"cod\",\n" +
                "      \"method_title\": \"Cash on delivery\",\n" +
                "      \"paid\": false\n" + // if true set transaction_id as a string
                "    },\n" +
                "    \"billing_address\":{\n" +
                "      \"first_name\":\""+etBillFirstName.getText().toString().trim()+"\",\n" +
                "      \"last_name\":\""+etBillLastName.getText().toString().trim()+"\",\n" +
                "      \"company\":\""+etBillCompany.getText().toString().trim()+"\",\n" +
                "      \"address_1\":\""+etBillAddress1.getText().toString().trim()+"\",\n" +
                "      \"address_2\":\""+etBillAddress2.getText().toString().trim()+"\",\n" +
                "      \"city\":\""+etBillCity.getText().toString().trim()+"\",\n" +
                "      \"state\":\""+etBillState.getText().toString().trim()+"\",\n" +
                "      \"postcode\": \"\",\n" +
                "      \"country\": \""+country+"\",\n" +
                "      \"email\":\""+etEmail.getText().toString().trim()+"\",\n" +
                "      \"phone\":\""+etPhone.getText().toString().trim()+"\"\n" +
                "    },\n" +
                "    \"shipping_address\": {\n" +
                "      \"first_name\": \""+etShipFirstName.getText().toString().trim()+"\",\n" +
                "      \"last_name\": \""+etShipLastName.getText().toString().trim()+"\",\n" +
                "      \"company\": \""+etShipCompany.getText().toString().trim()+"\",\n" +
                "      \"address_1\": \""+etShipAddress1.getText().toString().trim()+"\",\n" +
                "      \"address_2\": \""+etShipAddress2.getText().toString().trim()+"\",\n" +
                "      \"city\": \""+etShipCity.getText().toString().trim()+"\",\n" +
                "      \"state\": \""+etShipState.getText().toString().trim()+"\",\n" +
                "      \"postcode\": \"\",\n" +
                "      \"country\": \""+country+"\"\n" +
                "    },\n" +
                "    \"shipping_lines\": [\n" +
                "      {\n" +
                "        \"method_id\": \"flat_rate\",\n" +
                "        \"method_title\": \"Flat Rate\",\n" +
                "        \"total\":0\n" +
                "      }\n" +
                "    ] \n" +
/*                "    ,\"order_meta\":\n" +
                "      {\n" +
                "        \"destination\":\""+geoPoint.getLatitude()+","+geoPoint.getLongitude()+"\"\n" +
                "      }\n" +*/
                "  }\n" +
                "}";
        Log.d("Test", "Content: " + content);
        new GetDataTask(GetDataTask.METHOD_PUT, content) {

            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                showLoadingView(true);
            }

            @Override
            protected void onPostExecute(String[] result) {
                super.onPostExecute(result);
                    if (result.length > 0 && (!result[0].isEmpty())) {
                        Log.d("Test", "Result: " + result[0]);
                        JsonElement jsonResponse = new JsonParser().parse(result[0]);
                        JsonElement jsonOrder = jsonResponse.getAsJsonObject().get(Url.OBJ_NAME_ORDER);
                        Order order = mGson.fromJson(jsonOrder, new TypeToken<Order>(){}.getType());
                        if (order != null && order.getStatus() != null){
                            Log.d("Test", "Order Info: " + order.toString());
                            if (order.getStatus().equals("processing")){
                                sendOrderDestination(order);
                            } else {
                                Log.d("Test", "Order ID: " + order.getId()+", Status: " + order.getStatus());
                            }
                        } else {
                            JsonArray error = jsonResponse.getAsJsonObject().getAsJsonArray("errors");
                            Helper.toastShort(CardConfirmActivity.this,getString(R.string.error)+": "+ error.get(0).getAsJsonObject().get("message"));
                            Log.d("Test", "Order is null.");
                        }
                    } else {
                        Log.d("Test", "Result is empty");
                    }
                showLoadingView(false);
            }
        }.execute(Url.getOrderId(orderId));
    }

    @SuppressLint("StaticFieldLeak")
    private void sendOrderDestination(final Order order){
        // sending URL Google Map Address link something like: http://www.google.com/maps?daddr=36.7021411,3.0879278
        String content = "{\n" +
                "  \"order_note\": {\n" +
                "    \"note\":\"<a href=\\\"http://www.google.com/maps?daddr="+geoPoint.getLatitude()+","+geoPoint.getLongitude()+"\\\">Destination</a>\"\n" +
                "  }\n" +
                "}";
        Log.d("Test", "content: " + content);
        new GetDataTask(GetDataTask.METHOD_POST, content) {
            @Override
            protected void onPostExecute(String[] result) {
                super.onPostExecute(result);
                if (result.length > 0 && (!result[0].isEmpty())) {
                    Log.d("Test", "Result: " + result[0]);
                    JsonElement jsonResponse = new JsonParser().parse(result[0]);
                    JsonElement jsonOrderNote = jsonResponse.getAsJsonObject().get(Url.OBJ_NAME_ORDER_NOTE);
                    OrderNote orderNote = mGson.fromJson(jsonOrderNote, new TypeToken<OrderNote>(){}.getType());
                    if (orderNote != null){
                        Log.d("Test", "OrderNote: " + orderNote.toString());
                        if (Helper.saveInt(CardConfirmActivity.this, Helper.LAST_ORDER_ID, 0)){
                            setResult(Activity.RESULT_OK, getIntent());
                            if (orderDao != null){
                                try {
                                    if (orderDao.create(order) > 0){
                                        Log.d("Test", "Order saved with ID: " + order.getId());
                                    }
                                } catch (SQLException e) {
                                    Log.e("Test", "Error: " + e.getMessage());
                                    e.printStackTrace();
                                }
                            }
                            // Save Shipping & Billing info
                            saveInfo();
                            finish();
                        }
                    } else {
                        JsonArray error = jsonResponse.getAsJsonObject().getAsJsonArray("errors");
                        Helper.toastShort(CardConfirmActivity.this, getString(R.string.error)+": "+ error.get(0).getAsJsonObject().get("message"));
                        Log.d("Test", "OrderNote is null.");
                    }
                } else {
                    Log.d("Test", "Result is empty");
                }
            }
        }.execute(Url.getOrderNote(order.getId()));
    }

}
