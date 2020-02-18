package com.example.smsverificationapiexample;

import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.credentials.Credential;
import com.google.android.gms.auth.api.credentials.Credentials;
import com.google.android.gms.auth.api.credentials.CredentialsOptions;
import com.google.android.gms.auth.api.credentials.HintRequest;
import com.google.android.gms.auth.api.phone.SmsRetriever;
import com.google.android.gms.auth.api.phone.SmsRetrieverClient;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.tasks.Task;

public class MainActivity extends AppCompatActivity {

    private int RESOLVE_HINT = 1001;
    private static final int SMS_CONSENT_REQUEST = 1002;  // Set to an unused request code
    private String SMS_BODY = "Your ExampleApp code is: 123ABC78 \n 4H58dxQOfEu";
    private int beginIndex = 25, endIndex = 33;
    private EditText etOTPSet;
    private Button btnSMSRetrieverAPI, btnSMSUserConsentAPI;
    private static final String TAG = MainActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etOTPSet = findViewById(R.id.tv_otp_set);
        btnSMSRetrieverAPI = findViewById(R.id.btn_sms_retriever_api);
        btnSMSUserConsentAPI = findViewById(R.id.btn_sms_user_consent_api);

        btnSMSRetrieverAPI.setOnClickListener(v -> {
            // uncomment this block if you want to request a hint for phone number.
//            requestHint();
            // Don't know if the broadcast receiver is registered yet. So, try-catch for safety. Not need when you implement just one these two systems.
            try {
                unregisterReceiver(smsVerificationReceiver);   // making SMS User Consent API disable
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            etOTPSet.setText(getString(R.string.otp_to_set));
            Toast.makeText(this, getString(R.string.sms_retriever_api) + " listening started", Toast.LENGTH_SHORT).show();
            SmsRetrieverClient client = SmsRetriever.getClient(this);
            Task<Void> task = client.startSmsRetriever();

            task.addOnSuccessListener(aVoid -> {
                MySMSBroadcastReceiver.initSMSListener(new SMSListener() {
                    @Override
                    public void onSuccess(String message) {
                        if (message != null)
                            etOTPSet.setText(parseOneTimeCode(message));
                    }

                    @Override
                    public void onError(String message) {
                        if (message != null)
                            Log.d(TAG,message);
                    }
                });
            });

            task.addOnFailureListener(e -> {
                e.printStackTrace();
            });
        });

        btnSMSUserConsentAPI.setOnClickListener(v -> {
            // uncomment this block if you want to request a hint for phone number.
//            requestHint();
            etOTPSet.setText(getString(R.string.otp_to_set));
            MySMSBroadcastReceiver.initSMSListener(null);    // making SMS Retriever API disable
            Toast.makeText(this, getString(R.string.sms_user_consent_api) + " listening started", Toast.LENGTH_SHORT).show();
            IntentFilter intentFilter = new IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION);
            registerReceiver(smsVerificationReceiver, intentFilter);
            SmsRetriever.getClient(this).startSmsUserConsent(null);
        });


        //  Don't forget to remove the this block and AppSignatureHelper class before production release
        AppSignatureHelper appSignatureHelper = new AppSignatureHelper(this);
        for (String signature : appSignatureHelper.getAppSignatures()) {
            Log.d(TAG, signature);  // See the log and get app's hash string.
        }

    }

    // Construct a request for phone numbers and show the picker
    // It is not working on all devices, references - https://stackoverflow.com/questions/46900310/play-services-hint-request-cannot-display-phone-numbers-when-requested,
    // https://github.com/android/identity-samples/issues/6
    private void requestHint() {
        HintRequest hintRequest = new HintRequest.Builder()
                .setPhoneNumberIdentifierSupported(true)
                .build();

        CredentialsOptions options = new CredentialsOptions.Builder()
                .forceEnableSaveDialog()
                .build();

        PendingIntent intent = Credentials.getClient(this, options).getHintPickerIntent(hintRequest);

        try {
            startIntentSenderForResult(intent.getIntentSender(),
                    RESOLVE_HINT, null, 0, 0, 0);
        } catch (IntentSender.SendIntentException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // This block for request hint --> getting user phone number.
        if (requestCode == RESOLVE_HINT) {
            if (resultCode == RESULT_OK) {
                Credential credential = data.getParcelableExtra(Credential.EXTRA_KEY);
                if (credential != null) {
                    // credential.getId();  <-- will need to process phone number string
                    // Toast.makeText(this, credential.getId(), Toast.LENGTH_LONG).show();
                    // sendPhoneNumberToServer(credential.getId());
                }
            }
        } else if (requestCode == SMS_CONSENT_REQUEST) {
            if (resultCode == RESULT_OK) {
                // Get SMS message content
                String message = data.getStringExtra(SmsRetriever.EXTRA_SMS_MESSAGE);
                // Extract one-time code from the message and complete verification
                // `sms` contains the entire text of the SMS message, so you will need
                // to parse the string.
                if (message != null) {
                    etOTPSet.setText(parseOneTimeCode(message));
                }
                // send one time code to the server
            } else {
                // Consent canceled, handle the error ...
            }
        }
    }

    private String parseOneTimeCode(String otp) {
        return otp.substring(beginIndex, endIndex);
    }


    private final BroadcastReceiver smsVerificationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (SmsRetriever.SMS_RETRIEVED_ACTION.equals(intent.getAction())) {
                Bundle extras = intent.getExtras();
                Status smsRetrieverStatus = (Status) extras.get(SmsRetriever.EXTRA_STATUS);

                switch (smsRetrieverStatus.getStatusCode()) {
                    case CommonStatusCodes.SUCCESS:
                        // Get consent intent
                        Intent consentIntent = extras.getParcelable(SmsRetriever.EXTRA_CONSENT_INTENT);
                        try {
                            // Start activity to show consent dialog to user, activity must be started in
                            // 5 minutes, otherwise you'll receive another TIMEOUT intent
                            startActivityForResult(consentIntent, SMS_CONSENT_REQUEST);
                        } catch (ActivityNotFoundException e) {
                            // Handle the exception ...
                        }
                        break;
                    case CommonStatusCodes.TIMEOUT:
                        // Time out occurred, handle the error.
                        break;
                }
            }
        }
    };

}



